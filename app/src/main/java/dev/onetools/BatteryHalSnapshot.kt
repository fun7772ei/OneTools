package dev.onetools

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Only standardized readings; HAL cycleCount is deliberately not exposed. */
data class BatteryHalSnapshot(val currentUa: Int?, val averageCurrentUa: Int?, val fullChargeUah: Int?, val designUah: Int?) {
    companion object {
        fun parse(output: String): BatteryHalSnapshot {
            val words = mutableListOf<Int>()
            output.lineSequence().forEach { line ->
                val m = Regex("^\\s*0x([0-9a-fA-F]{8}):\\s*(.*?)\\s*'").find(line) ?: return@forEach
                require(m.groupValues[1].toLong(16) == words.size * 4L)
                val hex = m.groupValues[2].trim().split(Regex("\\s+"))
                require(hex.all { Regex("[0-9a-fA-F]{8}").matches(it) })
                words += hex.map { it.toLong(16).toInt() }
            }
            val b = ByteBuffer.allocate(words.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            words.forEach(b::putInt); b.rewind()
            require(b.remaining() >= 12 && b.int == 0 && b.int == 1)
            val start = b.position()
            val size = b.int
            require(size >= 68 && size <= b.limit() - start)
            val end = start + size
            b.limit(end)
            val values = IntArray(16) { b.int }
            require(values.take(4).all { it in 0..1 } && values[8] in 0..1 && values[9] in 0..100)
            val current = values[12].takeUnless { it == Int.MIN_VALUE }
            val full = values[14].takeIf { it > 0 }
            fun skipString() {
                val length = b.int
                require(length >= -1 && length <= b.remaining() / 2)
                if(length >= 0) {
                    val bytes = (length.toLong() + 1) * 2
                    val next = (b.position().toLong() + bytes + 3) and -4L
                    require(next <= end)
                    b.position(next.toInt())
                }
            }
            fun skipArray() {
                val count = b.int
                require(count in -1..1024)
                repeat(count.coerceAtLeast(0)) {
                    val present = b.int
                    require(present in 0..1)
                    if(present == 1) {
                        val pos = b.position(); val length = b.int
                        require(length >= 4 && length <= end - pos)
                        b.position(pos + length)
                    }
                }
            }
            // Older compatible parcelables can end before optional trailing fields.
            if(b.remaining() == 0) return BatteryHalSnapshot(current, null, full, null)
            skipString()
            val average = b.int.takeUnless { it == Int.MIN_VALUE }
            skipArray(); skipArray()
            b.int // capacityLevel
            b.long // chargeTimeToFullNowSeconds
            val design = b.int.takeIf { it > 0 }
            return BatteryHalSnapshot(current, average, full, design)
        }
    }
}
