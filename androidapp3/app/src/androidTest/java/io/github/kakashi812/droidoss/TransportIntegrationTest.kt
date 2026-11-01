package io.github.kakashi812.droidoss

import io.github.kakashi812.droidoss.transport.*
import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TransportIntegrationTest {
    @Test fun legacyFallbackStreamsInputAndSendsNeutralBeforeBye() {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 100
            val connected = CountDownLatch(1)
            val heldInput = CountDownLatch(1)
            val goodbye = CountDownLatch(1)
            val active = AtomicBoolean(true)
            var lastInput: ByteArray? = null
            var neutralBeforeBye = false
            val peer = Thread {
                val bytes = ByteArray(128)
                val packet = DatagramPacket(bytes, bytes.size)
                while (active.get()) {
                    try { packet.length = bytes.size; server.receive(packet) }
                    catch (_: SocketTimeoutException) { continue }
                    catch (_: java.io.IOException) { break }
                    when {
                        packet.length == 4 && bytes[2].toInt() == 2 -> server.send(
                            DatagramPacket(byteArrayOf(0xDA.toByte(), 1, 3, 0), 4, packet.address, packet.port))
                        packet.length == 20 -> {
                            lastInput = bytes.copyOf(packet.length)
                            val buttons = (bytes[8].toInt() and 255) or ((bytes[9].toInt() and 255) shl 8)
                            if (buttons == 0x1000) heldInput.countDown()
                        }
                        packet.length == 4 && bytes[2].toInt() == 4 -> {
                            neutralBeforeBye = lastInput?.sliceArray(8..19)?.all { it.toInt() == 0 } == true; goodbye.countDown()
                        }
                    }
                }
            }.apply { start() }
            val transport = UdpTransport("127.0.0.1", server.localPort, onStateChange = {
                if (it is ConnectionState.Connected) connected.countDown()
            })
            try {
                transport.start()
                assertTrue("Legacy fallback handshake", connected.await(5, TimeUnit.SECONDS))
                transport.update { setButton(0x1000, true); leftTrigger = 128; thumbRX = 12345 }
                assertTrue("Full controller input", heldInput.await(2, TimeUnit.SECONDS))
                transport.stop()
                assertTrue("BYE sent", goodbye.await(2, TimeUnit.SECONDS))
                assertTrue("Neutral state preceded BYE", neutralBeforeBye)
            } finally {
                transport.stop(); active.set(false); peer.join(500)
            }
        }
    }
}
