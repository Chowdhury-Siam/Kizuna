package io.github.kakashi812.droidoss.transport

import org.junit.Assert.*
import org.junit.Test
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Executes the production phone transport on the JVM, without an Android emulator. */
class UdpTransportTest {
    private class Peer(val legacy: Boolean = false) : AutoCloseable {
        val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).also { it.soTimeout = 50 }
        val port get() = socket.localPort
        val clients = ConcurrentHashMap<SocketAddress, Int>()
        val packets = LinkedBlockingQueue<ByteArray>()
        val names = LinkedBlockingQueue<String>()
        val addresses = LinkedBlockingQueue<SocketAddress>()
        val bye = LinkedBlockingQueue<ByteArray>()
        @Volatile var muted = false
        private val active = AtomicBoolean(true)
        private val lastInput = ConcurrentHashMap<SocketAddress, ByteArray>()
        private val thread = Thread {
            val data = ByteArray(128)
            val incoming = DatagramPacket(data, data.size)
            while (active.get()) {
                try { incoming.length = data.size; socket.receive(incoming) }
                catch (_: SocketTimeoutException) { continue }
                catch (_: java.io.IOException) { break }
                val bytes = data.copyOf(incoming.length)
                val sender = incoming.socketAddress
                addresses.offer(sender)
                when {
                    bytes.size == 20 && bytes[2].toInt() == 1 -> { lastInput[sender] = bytes; packets.offer(bytes) }
                    bytes.size == 4 && bytes[2].toInt() == 4 -> {
                        bye.offer(lastInput[sender] ?: ByteArray(0)); clients.remove(sender)
                    }
                    !muted && ((bytes.size == 4 && bytes[2].toInt() == 2) || (!legacy && bytes.size >= 5 && bytes[2].toInt() == 7)) -> {
                        val extended = bytes[2].toInt() == 7
                        val preferred = if (extended) bytes[3].toInt() and 255 else 255
                        if (extended) names.offer(String(bytes, 5, bytes[4].toInt() and 255, Charsets.UTF_8))
                        val slot = clients[sender] ?: (if (preferred in 0..3) preferred.takeIf { it !in clients.values }
                            else (0..3).firstOrNull { it !in clients.values }) ?: 255
                        if (slot != 255) clients[sender] = slot
                        val reply = if (extended) byteArrayOf(0xDA.toByte(), 1, 8, slot.toByte(), clients.values.fold(0) { mask, i -> mask or (1 shl i) }.toByte())
                            else byteArrayOf(0xDA.toByte(), 1, 3, slot.toByte())
                        send(reply, sender)
                    }
                }
            }
        }.also { it.isDaemon = true; it.start() }
        fun send(bytes: ByteArray, address: SocketAddress) = socket.send(DatagramPacket(bytes, bytes.size, address))
        fun inputMatching(predicate: (ByteArray) -> Boolean): ByteArray {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (System.nanoTime() < until) {
                val packet = packets.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (predicate(packet)) return packet
            }
            throw AssertionError("Expected input snapshot did not arrive")
        }
        override fun close() { active.set(false); socket.close(); thread.join(500) }
    }

    @Test fun startupFailuresAreNotReportedAsASilentServer() {
        assertTrue(connectionSetupMessage(UnknownHostException()).contains("resolve"))
        assertTrue(connectionSetupMessage(IllegalArgumentException()).contains("port"))
        assertTrue(connectionSetupMessage(SecurityException()).contains("permissions"))
    }

    @Test fun requestedPortIsUsedAndLegacyInputEndsNeutralBeforeBye() {
        Peer(legacy = true).use { peer ->
            val connected = CountDownLatch(1)
            val transport = UdpTransport("127.0.0.1", peer.port, onStateChange = { if (it is ConnectionState.Connected) connected.countDown() })
            try {
                transport.start()
                assertTrue("The phone must send its handshake to the supplied server port", connected.await(3, TimeUnit.SECONDS))
                transport.update { buttons = 0x1000; leftTrigger = 128; thumbRX = 12345 }
                val input = peer.inputMatching { (it[9].toInt() and 255) == 0x10 }
                assertEquals(20, input.size)
                assertEquals(128, input[10].toInt() and 255)
                assertEquals(12345.toShort(), ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN).getShort(16))
                transport.stop()
                val final = peer.bye.poll(2, TimeUnit.SECONDS)
                assertNotNull("BYE must follow a neutral input packet", final)
                assertTrue(final!!.sliceArray(8..19).all { it.toInt() == 0 })
            } finally { transport.stop() }
        }
    }

    @Test fun extendedHandshakeHonorsPlayerNameAndRumbleWithoutAndroidDependencies() {
        Peer().use { peer ->
            val connected = LinkedBlockingQueue<ConnectionState.Connected>()
            val rumble = LinkedBlockingQueue<Float>()
            val transport = UdpTransport("127.0.0.1", peer.port, deviceName = "Siam 🎮", preferredSlot = 2,
                vibration = 0.5f, onStateChange = { if (it is ConnectionState.Connected) connected.offer(it) }, onRumble = { rumble.offer(it) })
            try {
                transport.start(); assertEquals(2, connected.poll(3, TimeUnit.SECONDS)?.slot)
                assertEquals("Siam 🎮", peer.names.poll(1, TimeUnit.SECONDS))
                assertTrue(transport.statusText.contains("1/4 occupied"))
                val address = peer.addresses.poll(1, TimeUnit.SECONDS)!!
                peer.send(byteArrayOf(0xDA.toByte(), 1, 5, 2, 0, 255.toByte()), address)
                assertEquals(0.5f, rumble.poll(2, TimeUnit.SECONDS)!!, 0.001f)
            } finally { transport.stop() }
        }
    }

    @Test fun fourPhoneTransportsConnectIndependentlyAndFifthIsRejected() {
        Peer().use { peer ->
            val ready = CountDownLatch(4)
            val slots = ConcurrentHashMap.newKeySet<Int>()
            val transports = (0..3).map { i -> UdpTransport("127.0.0.1", peer.port, preferredSlot = i, onStateChange = {
                if (it is ConnectionState.Connected) { slots.add(it.slot); ready.countDown() }
            }) }
            val refused = CountDownLatch(1)
            val fifth = UdpTransport("127.0.0.1", peer.port, onStateChange = { if (it == ConnectionState.ServerFull) refused.countDown() })
            try {
                transports.forEach { it.start() }; assertTrue(ready.await(3, TimeUnit.SECONDS)); assertEquals(setOf(0, 1, 2, 3), slots)
                transports.forEachIndexed { index, t -> t.update { buttons = 0x1000 shl index } }
                for (i in 0..3) peer.inputMatching { (it[3].toInt() and 255) == i && (it[9].toInt() and 255) == (0x10 shl i) }
                fifth.start(); assertTrue(refused.await(3, TimeUnit.SECONDS)); assertEquals(4, peer.clients.size)
            } finally { fifth.stop(); transports.forEach { it.stop() } }
        }
    }

    @Test fun welcomeFromAnotherSocketCannotHijackThePhone() {
        Peer().use { peer ->
            peer.muted = true
            val connected = CountDownLatch(1)
            val transport = UdpTransport("127.0.0.1", peer.port, onStateChange = { if (it is ConnectionState.Connected) connected.countDown() })
            try {
                transport.start(); val phone = peer.addresses.poll(2, TimeUnit.SECONDS)!!
                DatagramSocket().use { wrong -> wrong.send(DatagramPacket(byteArrayOf(0xDA.toByte(), 1, 3, 0), 4, phone)) }
                assertFalse(connected.await(150, TimeUnit.MILLISECONDS))
                peer.muted = false; assertTrue(connected.await(3, TimeUnit.SECONDS))
            } finally { transport.stop() }
        }
    }

    @Test fun lostAcknowledgementsTriggerFreshHandshakeAndNeutralState() {
        Peer().use { peer ->
            val count = AtomicInteger()
            val first = CountDownLatch(1); val second = CountDownLatch(1); val reconnecting = CountDownLatch(1)
            val transport = UdpTransport("127.0.0.1", peer.port, onStateChange = {
                if (it is ConnectionState.Connected) { if (count.incrementAndGet() == 1) first.countDown() else second.countDown() }
                if (it == ConnectionState.Connecting && count.get() > 0) reconnecting.countDown()
            })
            try {
                transport.start(); assertTrue(first.await(3, TimeUnit.SECONDS))
                transport.update { buttons = 0x1000 }; peer.inputMatching { it[9].toInt() == 0x10 }
                peer.muted = true
                assertTrue(reconnecting.await(5, TimeUnit.SECONDS))
                peer.clients.clear(); peer.packets.clear(); peer.muted = false
                assertTrue(second.await(3, TimeUnit.SECONDS))
                peer.inputMatching { it.sliceArray(8..19).all { b -> b.toInt() == 0 } }
            } finally { transport.stop() }
        }
    }
}
