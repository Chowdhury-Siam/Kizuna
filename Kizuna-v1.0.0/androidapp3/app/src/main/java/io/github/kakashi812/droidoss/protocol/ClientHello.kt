package io.github.kakashi812.droidoss.protocol

object ClientHello {
    fun encode(name: String, preferred: Int): ByteArray {
        var clean = name.filter { !it.isISOControl() }.trim()
        while (clean.toByteArray(Charsets.UTF_8).size > 48) clean = clean.dropLast(1)
        if (clean.lastOrNull()?.isHighSurrogate() == true) clean = clean.dropLast(1)
        val bytes = clean.toByteArray(Charsets.UTF_8)
        return byteArrayOf(Protocol.MAGIC_BYTE, Protocol.VERSION, 7, preferred.coerceIn(-1, 3).toByte(), bytes.size.toByte()) + bytes
    }
    data class Status(val slot: Byte, val occupied: Int)
    fun status(data: ByteArray, length: Int): Status? {
        if (length != 5 || data.size < length || data[0] != Protocol.MAGIC_BYTE || data[1] != Protocol.VERSION || data[2].toInt() != 8) return null
        if (data[3] != Protocol.NO_PAD && data[3].toInt() !in 0..3) return null
        val occupied = data[4].toInt() and 255
        if (occupied > 15) return null
        return Status(data[3], occupied)
    }
}
