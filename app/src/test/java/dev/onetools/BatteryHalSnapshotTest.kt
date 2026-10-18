package dev.onetools

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class BatteryHalSnapshotTest {
    private fun fixture(change: (ByteBuffer) -> Unit = {}): String {
        val b = ByteBuffer.allocate(136).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0).putInt(1).putInt(128)
        intArrayOf(0,1,0,0,0,0,4,2,1,84,4217,297,-200000,0,3900000,3400000).forEach(b::putInt)
        b.putInt(6); "Li-ion\u0000".forEach { b.putChar(it) }; b.putShort(0)
        b.putInt(-190000).putInt(0).putInt(0).putInt(-1).putLong(0).putInt(3900000)
        b.putInt(0).putInt(0).putInt(0)
        change(b)
        b.rewind()
        return "Result: Parcel(\n" + (0 until 34).chunked(4).joinToString("\n") { indexes ->
            "0x%08x: ".format(indexes.first() * 4) + indexes.joinToString(" ") { "%08x".format(b.int) } + " 'deadbeef 1234'"
        } + "\n)"
    }
    @Test fun readsStandardizedUnitsAndIgnoresPrintableHexAndHalZeroCycles() {
        val h = BatteryHalSnapshot.parse(fixture())
        assertEquals(-200000, h.currentUa); assertEquals(-190000, h.averageCurrentUa)
        assertEquals(3900000, h.fullChargeUah); assertEquals(3900000, h.designUah)
        assertEquals("-200.0 mA", microamps(h.currentUa)); assertEquals("3900 mAh", microampHours(h.fullChargeUah))
    }
    @Test fun rejectsErrorsTruncationAndInvalidParcelableBounds() {
        for(s in listOf("", "Can't find service", fixture { it.putInt(0, -1) }, fixture { it.putInt(8, Int.MAX_VALUE) },
            fixture { it.putInt(76, Int.MAX_VALUE) }, fixture().substringBefore("0x00000070"))) {
            assertTrue(runCatching { BatteryHalSnapshot.parse(s) }.isFailure)
        }
    }
    @Test fun unavailableCapacitiesRemainUnknown() {
        val h = BatteryHalSnapshot.parse(fixture { it.putInt(68, 0); it.putInt(120, -1) })
        assertNull(h.fullChargeUah)
        assertNull(h.designUah)
        // No health percentage or cycle estimate is synthesized from these capacities.
        assertEquals("不明", microampHours(h.fullChargeUah))
    }
}
