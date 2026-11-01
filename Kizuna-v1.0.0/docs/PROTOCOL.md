# Kizuna UDP protocol

Kizuna retains the droidOSS version 1 protocol described below without wire-format changes.

**Version 1** · UDP · little-endian

This is the canonical specification. It is implemented by hand in **three languages** — C# (server), Kotlin (Android app), Python (`tools/fake_phone.py`) — and there is no shared code generating it. A mismatch between implementations **fails silently**: no exception, no error, just a stick that moves when you press B and a long confusing evening.

**Changing an existing message means changing all three implementations and bumping `ver`. Optional new message types are ignored by legacy peers and do not change existing formats.**

---

## Ports

| Port | Purpose |
|---|---|
| `27500` | UDP — input stream and session messages |
| `27501` | UDP — discovery broadcast only |

Discovery is kept on its own port so broadcast traffic never touches the input socket's hot path.

## Input packet — 20 bytes

Message type `0x01`. Sent by the phone at a fixed 125 Hz.

```
byte   0     1     2     3     4-7      8-9     10    11   12-13 14-15 16-17 18-19
     0xDA   ver  type   pad    seq    buttons   LT    RT    LX    LY    RX    RY
      u8    u8    u8    u8     u32      u16     u8    u8   i16   i16   i16   i16
     └───── header ─────┘  └ordering┘  └────────────── payload ──────────────────┘
```

| Field | Type | Meaning |
|---|---|---|
| `0xDA` | `u8` | Magic byte. If absent, the packet is not ours — discard immediately. |
| `ver` | `u8` | Protocol version. Currently `1`. |
| `type` | `u8` | Message type — see table below. |
| `pad` | `u8` | Which of the four virtual pads (`0`–`3`) this belongs to. |
| `seq` | `u32` | Monotonic counter, +1 per packet sent. |
| `buttons` | `u16` | Button bitmask — see below. |
| `LT` / `RT` | `u8` | Trigger travel, `0`–`255`. |
| `LX`/`LY`/`RX`/`RY` | `i16` | Stick axes, `-32768`–`+32767`. Y is **positive-up**. |

**Bytes 8–19 are byte-for-byte identical to `XINPUT_GAMEPAD`.** This is deliberate: the server performs zero conversion, reading straight off the wire into the driver. A future Linux/`uinput` backend does its own conversion, because it is the exception rather than the common case.

### Button bitmask (bytes 8–9)

| Bit | Value | Button | Bit | Value | Button |
|---|---|---|---|---|---|
| 0 | `0x0001` | D-pad Up | 8 | `0x0100` | Left shoulder |
| 1 | `0x0002` | D-pad Down | 9 | `0x0200` | Right shoulder |
| 2 | `0x0004` | D-pad Left | 10 | `0x0400` | Guide |
| 3 | `0x0008` | D-pad Right | 11 | `0x0800` | *unused* |
| 4 | `0x0010` | Start | 12 | `0x1000` | A |
| 5 | `0x0020` | Back | 13 | `0x2000` | B |
| 6 | `0x0040` | Left stick click | 14 | `0x4000` | X |
| 7 | `0x0080` | Right stick click | 15 | `0x8000` | Y |

## Message types (byte 2)

| Type | Direction | Purpose |
|---|---|---|
| `0x01` INPUT | phone → PC | The 20-byte snapshot, 125×/sec. |
| `0x02` HELLO | phone → PC | "I'm here." Server assigns a pad slot and plugs in a virtual pad. |
| `0x03` WELCOME | PC → phone | "You're pad 2." Also proves a server exists at this address. |
| `0x04` BYE | phone → PC | Clean exit — unplug now rather than waiting for the timeout. |
| `0x05` RUMBLE | PC → phone | Vibration intensity from the game. |
| `0x06` DISCOVER | broadcast | "Any servers out there?" Answered with a WELCOME announcement — see [Discovery](#discovery). |

Non-INPUT messages share the same 4-byte header; their payloads are defined as each is implemented.

## Discovery

Lets the phone find servers instead of the user typing an address. Everything here travels on port `27501`, never the input port.

1. The phone broadcasts **DISCOVER**, the bare header `DA 01 06 FF`, to each of its interfaces' subnet broadcast addresses and to `255.255.255.255`. It repeats every 300 ms for a 1.5 s scan, because a broadcast can be lost like any other datagram.
2. Every server that hears it replies **unicast to the sender** with a WELCOME *announcement*:

```
byte   0     1    2     3      4-5        6         7          8..
     0xDA   ver  0x03  0xFF  inputPort  freePads  nameLength  name
      u8    u8    u8    u8     u16        u8        u8       UTF-8, ≤ 64 bytes
```

| Field | Meaning |
|---|---|
| `pad` | Always `0xFF`. An announcement assigns no slot; the phone still says HELLO on `inputPort` to get one. |
| `inputPort` | Where to send HELLO, normally `27500`. |
| `freePads` | Slots still free, `0`–`4`. A full server is shown as full instead of being tried. |
| `name` | The PC's name, shown to the user. The server cuts it on a character boundary if it is too long. |

3. The phone takes the server's **address from the reply's source**, groups replies by address and `inputPort`, and lists every server it found. When several servers share a network, the user picks one.

An announcement is at least 8 bytes long, so it can never be mistaken for the 4-byte session WELCOME that shares its type byte. Golden vector, server `"PC"`, port 27500, 3 pads free: `DA 01 03 FF 6C 6B 03 02 50 43`.

**There is deliberately no heartbeat message.** The input stream *is* the heartbeat — a packet every 8 ms means silence is unmistakable.

---

## The three rules that break things silently

### 1. Little-endian, set explicitly on Kotlin

ARM and x86 are both little-endian natively, so this costs nothing on either side. **But Java/Kotlin's `ByteBuffer` defaults to big-endian.** Forget `.order(ByteOrder.LITTLE_ENDIAN)` and every multi-byte field is silently wrong — `1000` reads back as `59395`, with no error anywhere.

C#'s `BitConverter` and Python's `struct` with `<` prefix are already little-endian.

### 2. Sequence comparison must handle wrap

UDP can deliver out of order. Discard anything not newer:

```csharp
if ((int)(seq - lastSeq) <= 0) return;   // stale or duplicate
lastSeq = seq;
```

**Use the subtract-and-cast form, not `seq <= lastSeq`.** At 125 packets/sec the `u32` counter wraps after ~1.1 years of continuous play; the naive comparison would then reject every subsequent packet forever, permanently freezing the controller. Subtracting unsigned and interpreting as signed handles the boundary correctly.

### 3. Validate before trusting

Check the magic byte *and* the version before reading any field. Sockets receive port scans, other apps' strays, and mistyped-IP traffic. Without the magic byte, garbage becomes controller state and the character spasms.

---

## Version history

| `ver` | Status | Changes |
|---|---|---|
| `1` | current | Initial format — 20-byte input packet, six message types. |

## Optional client metadata and status (0.3.0)

The version remains 1. INPUT, legacy HELLO/WELCOME/BYE, and discovery are unchanged.

- `CLIENT_HELLO` type `0x07`: `DA 01 07 preferred nameLength name...`. Preferred is 0–3 or FF (auto); name is strictly valid UTF-8, at most 48 bytes. Total length is exactly 5 + nameLength. Golden vector for PC, player 3: `DA 01 07 02 02 50 43`.
- `STATUS` type `0x08`: `DA 01 08 assigned occupiedMask`, exactly 5 bytes. Assigned is 0–3 or FF (requested slot occupied / server full). Occupied bits 0–3 identify logical server slots. It responds to CLIENT_HELLO only. An occupied preferred slot is rejected without displacing its player. Duplicate handshakes return the existing slot, even if preferences changed.
- `RUMBLE` type `0x05`: `DA 01 05 slot large small`, exactly 6 bytes. The client validates the source socket and its assigned slot, then scales motor intensity by profile vibration strength. Zero intensity expires the bounded pulse within 80 ms.

Clients try metadata first and alternate legacy HELLO as fallback if no metadata status has been received. While connected they probe every second. Legacy servers reply to HELLO with four-byte WELCOME, new servers reply to metadata with STATUS. No acknowledgement for 3 seconds starts a new handshake and clears held input. After 20 unsuccessful attempts the app returns to the connect/retry screen. The 125 Hz input stream remains the server's liveness signal. RTT shown on the phone is handshake round-trip time, not a one-way input-latency measurement. Packet rates and time since last input shown on Windows are measured locally.

These LAN messages do not provide authentication or encryption; device names are display labels, never identity credentials. Player slots here are the server's logical slots, not a guarantee of Windows' global XInput ordering.
