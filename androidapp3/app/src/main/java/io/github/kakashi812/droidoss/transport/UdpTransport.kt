/*
 * Derived from PadConnect's UdpTransport.
 * Copyright (C) 2026 Ishan
 * Copyright (C) 2026 droidOSS contributors
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 only.
 *
 * This program is distributed without any warranty. See the GNU General Public
 * License for more details.
 *
 * MODIFIED FROM THE ORIGINAL. The two-thread shape and the Wi-Fi-aware retry
 * with exponential backoff come from PadConnect. Everything to do with the wire
 * format is new: PadConnect sends a 21-byte packet with no magic byte, no
 * version, no sequence number and no pad index, and its receiver identifies a
 * client as "whoever spoke last", which supports exactly one controller and
 * leaves a pad held down forever when the phone dies. droidOSS speaks
 * docs/PROTOCOL.md instead, which adds the handshake, four pad slots, sequence
 * ordering and the disconnect timeout. The send loop's timing was also rewritten
 * — see sendLoop().
 */

package io.github.kakashi812.droidoss.transport

import io.github.kakashi812.droidoss.protocol.*
import java.io.IOException
import java.net.*
import java.util.concurrent.locks.LockSupport

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val slot: Int) : ConnectionState
    data object ServerFull : ConnectionState
    data object NoServer : ConnectionState
    data class SetupFailed(val message: String) : ConnectionState
}

class UdpTransport(
    private val host: String,
    private val port: Int = Protocol.INPUT_PORT,
    private val deviceName: String = "Phone",
    private val preferredSlot: Int = -1,
    private val vibration: Float = 0.7f,
    private val onStateChange: (ConnectionState) -> Unit = {},
    private val onRumble: (Float) -> Unit = {},
) {
    // Resolve first, so a failed DNS lookup never leaks a socket.
    private val address = InetAddress.getByName(host)
    // Use an explicit receiver: DatagramSocket.port is -1 before connect.
    // An apply block would shadow this transport's requested server port.
    private val socket = DatagramSocket().also { it.connect(address, port); it.soTimeout = 250 }
    private val state = PadState()
    private val lock = Any()
    @Volatile private var running = false
    @Volatile private var slot: Byte = Protocol.NO_PAD
    @Volatile private var lastAck = 0L
    @Volatile private var helloAt = 0L
    @Volatile private var rtt = 0L
    @Volatile private var occupancy = -1
    private var sequence = 0
    private var sender: Thread? = null
    private var receiver: Thread? = null
    val statusText: String get() = if (slot == Protocol.NO_PAD) "Reconnecting…" else
        "P${slot + 1} · ${rtt} ms handshake RTT" + if (occupancy >= 0) " · ${Integer.bitCount(occupancy)}/4 occupied" else " · legacy server"

    fun update(block: PadState.() -> Unit) { synchronized(lock) { state.block() } }
    fun start() {
        if (running || socket.isClosed) return
        running = true
        receiver = Thread(::receiveLoop, "droidoss-recv").apply { isDaemon = true; start() }
        sender = Thread(::sendLoop, "droidoss-send").apply { isDaemon = true; start() }
    }
    fun stop() {
        running = false
        sender?.interrupt()
        sender?.join(100)
        socket.close()
        receiver?.join(100)
        slot = Protocol.NO_PAD
    }
    private fun sendLoop() {
        val writer = PacketWriter()
        val extended = ClientHello.encode(deviceName, preferredSlot)
        val legacy = byteArrayOf(Protocol.MAGIC_BYTE, Protocol.VERSION, MessageType.HELLO.id, Protocol.NO_PAD)
        val outgoing = DatagramPacket(writer.bytes, Protocol.INPUT_PACKET_SIZE)
        var attempts = 0
        var next = System.nanoTime()
        var nextHello = 0L
        onStateChange(ConnectionState.Connecting)
        try {
            while (running) {
              try {
                val now = System.nanoTime()
                if (slot != Protocol.NO_PAD && now - lastAck > 3_000_000_000L) {
                    slot = Protocol.NO_PAD; occupancy = -1; attempts = 0
                    update { reset() }
                    onStateChange(ConnectionState.Connecting)
                }
                if (now >= nextHello) {
                    if (slot == Protocol.NO_PAD && attempts++ >= 20) {
                        onStateChange(ConnectionState.NoServer); return
                    }
                    helloAt = now
                    // First try the extension; fallback HELLO preserves old server compatibility.
                    val bytes = if (attempts % 2 == 0 && occupancy < 0) legacy else extended
                    socket.send(DatagramPacket(bytes, bytes.size))
                    // Legacy servers acknowledge header-only HELLO, including periodic health probes.
                    if (slot != Protocol.NO_PAD && occupancy < 0) socket.send(DatagramPacket(legacy, legacy.size))
                    nextHello = now + if (slot == Protocol.NO_PAD) 200_000_000L else 1_000_000_000L
                }
                if (slot != Protocol.NO_PAD) {
                    val size = synchronized(lock) { writer.writeInput(slot, ++sequence, state) }
                    outgoing.setData(writer.bytes, 0, size)
                    socket.send(outgoing)
                }
                next += 8_000_000L
                val remaining = next - System.nanoTime()
                if (remaining > 0) LockSupport.parkNanos(remaining) else next = System.nanoTime()
                if (Thread.interrupted()) return
              } catch (_: IOException) {
                if (!running || socket.isClosed) return
                slot = Protocol.NO_PAD; occupancy = -1; update { reset() }
                onStateChange(ConnectionState.Connecting)
                nextHello = 0L
                LockSupport.parkNanos(200_000_000L)
              }
            }
        } catch (_: IOException) { if (running) onStateChange(ConnectionState.NoServer) }
        finally {
            running = false
            if (!socket.isClosed && slot != Protocol.NO_PAD) try {
                val length = synchronized(lock) { state.reset(); writer.writeInput(slot, ++sequence, state) }
                socket.send(DatagramPacket(writer.bytes, length))
                socket.send(DatagramPacket(writer.bytes, writer.writeSession(MessageType.BYE, slot)))
            } catch (_: IOException) { }
            socket.close()
        }
    }
    private fun receiveLoop() {
        val bytes = ByteArray(128)
        val packet = DatagramPacket(bytes, bytes.size)
        while (running) {
            try { packet.length = bytes.size; socket.receive(packet) }
            catch (_: SocketTimeoutException) { continue }
            catch (_: IOException) { if (!running) return; LockSupport.parkNanos(100_000_000L); continue }
            // DatagramSocket.connect filters unexpected source IPs and ports.
            if (packet.length == 6 && bytes[0] == Protocol.MAGIC_BYTE && bytes[1] == Protocol.VERSION &&
                bytes[2] == MessageType.RUMBLE.id && bytes[3] == slot && slot != Protocol.NO_PAD) {
                val strength = maxOf(bytes[4].toInt() and 255, bytes[5].toInt() and 255) / 255f
                onRumble(strength * vibration)
                continue
            }
            val status = ClientHello.status(bytes, packet.length)
            val session = PacketReader.readSession(bytes, packet.length)
            val assigned = status?.slot ?: session?.takeIf { it.type == MessageType.WELCOME }?.pad ?: continue
            if (assigned != Protocol.NO_PAD && assigned.toInt() !in 0..3) continue
            if (assigned == Protocol.NO_PAD) {
                onStateChange(ConnectionState.ServerFull); running = false; socket.close(); return
            }
            lastAck = System.nanoTime()
            rtt = ((lastAck - helloAt) / 1_000_000).coerceAtLeast(0)
            if (status != null) occupancy = status.occupied
            if (slot != assigned) {
                update { reset() }
                slot = assigned
                onStateChange(ConnectionState.Connected(slot.toInt()))
            }
        }
    }
}


fun connectionSetupMessage(error: Exception): String = when (error) {
    is UnknownHostException -> "Cannot resolve this PC address. Check the address and try again."
    is SecurityException -> "Android denied network access. Check the app's network permissions."
    is IllegalArgumentException -> "Invalid controller server address or UDP port. Check the server details."
    else -> "Could not open the controller connection. Try again or restart the app."
}
