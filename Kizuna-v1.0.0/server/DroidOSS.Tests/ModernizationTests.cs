using System.Net;
using System.Net.Sockets;
using DroidOSS.App;
using DroidOSS.Core;

namespace DroidOSS.Tests;

public class ModernizationTests
{
    [Fact]
    public void Names_and_preferences_have_a_golden_vector()
    {
        Assert.Equal(new byte[] { 0xDA, 1, 7, 2, 2, 0x50, 0x43 }, ClientHello.Write(2, "PC"));
        Assert.True(ClientHello.TryRead(ClientHello.Write(2, "PC"), out var slot, out var name));
        Assert.Equal(2, slot); Assert.Equal("PC", name);
    }
    [Theory]
    [InlineData(new byte[] { 0xDA, 1, 7, 4, 0 })]
    [InlineData(new byte[] { 0xDA, 1, 7, 0, 2, 0xC0, 0x80 })]
    [InlineData(new byte[] { 0xDA, 2, 7, 0, 0 })]
    [InlineData(new byte[] { 0xDA, 1, 7, 0, 1 })]
    public void Bad_metadata_is_rejected(byte[] bytes) => Assert.False(ClientHello.TryRead(bytes, out _, out _));

    [Fact]
    public void Preferred_slots_never_displace_players_and_legacy_remains_usable()
    {
        var manager = new SessionManager(new FakePadBackend());
        var reply = new byte[64];
        var first = new ClientKey(1, 1); var second = new ClientKey(1, 2);
        Assert.Equal(MessageOutcome.SessionOpened, manager.Handle(ClientHello.Write(2, "Siam"), first, reply, out var size));
        Assert.Equal(5, size); Assert.Equal(2, reply[3]); Assert.Equal(4, reply[4]);
        Assert.Equal(MessageOutcome.SessionRejected, manager.Handle(ClientHello.Write(2, "Guest"), second, reply, out _));
        Assert.Equal(255, reply[3]);
        var legacy = new byte[4]; SessionMessage.Write(legacy, MessageType.Hello, Protocol.NoPad);
        Assert.Equal(MessageOutcome.SessionOpened, manager.Handle(legacy, second, reply, out size));
        Assert.Equal(4, size); Assert.Equal(0, reply[3]);
        manager.Handle(ClientHello.Write(0, "Siam renamed"), first, reply, out _);
        Assert.Equal(2, reply[3]); // an active player cannot migrate under a running game
        Assert.Equal("Siam renamed", manager.Snapshot().Single(s => s.Client == first).DeviceName);
    }

    [Fact]
    public void Timeout_reconnect_resets_sequence_and_neutralizes_old_input()
    {
        var clock = new ManualClock(); var backend = new FakePadBackend();
        var manager = new SessionManager(backend, clock); var reply = new byte[64];
        var phone = new ClientKey(1, 1);
        manager.Handle(ClientHello.Write(3, "Phone"), phone, reply, out _);
        var input = new byte[20]; var held = new PadState { Buttons = 0x1000, ThumbLX = 123 };
        InputPacket.Write(input, 3, 900, held); manager.Handle(input, phone, reply, out _);
        clock.Advance(TimeSpan.FromSeconds(3)); Assert.Equal(1, manager.SweepTimeouts());
        Assert.Equal((ushort)0, backend.LastSubmitted.Buttons);
        manager.Handle(ClientHello.Write(3, "Phone"), phone, reply, out _);
        InputPacket.Write(input, 3, 1, held);
        Assert.Equal(MessageOutcome.InputApplied, manager.Handle(input, phone, reply, out _));
    }

    [Fact]
    public async Task Actual_udp_listener_routes_four_clients_rejects_fifth_and_forwards_rumble()
    {
        var backend = new FakePadBackend(); var manager = new SessionManager(backend);
        using var listener = new UdpSessionListener(manager, 0); listener.Bind();
        using var stop = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        var loop = listener.ListenAsync(stop.Token);
        var phones = Enumerable.Range(0, 5).Select(_ => new UdpClient(new IPEndPoint(IPAddress.Loopback, 0))).ToArray();
        try {
            foreach (var phone in phones) phone.Connect(IPAddress.Loopback, listener.BoundPort);
            for (byte i = 0; i < 4; i++) {
                var hello = i % 2 == 0 ? ClientHello.Write(i, $"Phone {i}") : new byte[] { 0xDA, 1, 2, 255 };
                await phones[i].SendAsync(hello, stop.Token);
                var welcome = await phones[i].ReceiveAsync(stop.Token);
                Assert.Equal(i, welcome.Buffer[3]);
                if (i % 2 == 0) Assert.Equal((1 << (i + 1)) - 1, welcome.Buffer[4]);
                else Assert.Equal(4, welcome.Buffer.Length);
                var input = new byte[20]; var state = new PadState { Buttons = (ushort)(0x1000 << i), LeftTrigger = (byte)(i * 60), ThumbLX = (short)(i * 1000) };
                InputPacket.Write(input, i, 1, state); await phones[i].SendAsync(input, stop.Token);
                // A probe after input is processed acknowledges all earlier datagrams on this socket.
                await phones[i].SendAsync(ClientHello.Write(i, $"Phone {i}"), stop.Token);
                await phones[i].ReceiveAsync(stop.Token);
                var snapshot = manager.Snapshot().Single(s => s.Slot == i);
                Assert.Equal(state.Buttons, snapshot.LastState.Buttons); Assert.Equal(state.LeftTrigger, snapshot.LastState.LeftTrigger);
            }
            await phones[4].SendAsync(ClientHello.Write(Protocol.NoPad, "Fifth"), stop.Token);
            Assert.Equal(255, (await phones[4].ReceiveAsync(stop.Token)).Buffer[3]);
            Assert.Equal(4, manager.SessionCount);
            var session = manager.Snapshot()[2];
            listener.SendRumble(session.Client, 2, 128, 255);
            Assert.Equal(new byte[] { 0xDA, 1, 5, 2, 128, 255 }, (await phones[2].ReceiveAsync(stop.Token)).Buffer);
            var bye = new byte[4]; SessionMessage.Write(bye, MessageType.Bye, 0);
            await phones[0].SendAsync(bye, stop.Token);
            await phones[0].SendAsync(ClientHello.Write(0, "Rejoined"), stop.Token);
            Assert.Equal(0, (await phones[0].ReceiveAsync(stop.Token)).Buffer[3]);
            Assert.Equal(4, manager.SessionCount);
        } finally {
            stop.Cancel(); foreach (var phone in phones) phone.Dispose(); await loop; manager.CloseAll();
        }
        Assert.Equal(0, manager.SessionCount);
    }
}
