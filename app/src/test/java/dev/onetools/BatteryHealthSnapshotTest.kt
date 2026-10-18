package dev.onetools

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class BatteryHealthSnapshotTest {
    private fun reply(value: Long, result: Int = 0, exception: Int = 0, present: Int = 1): String = """
        Result: Parcel(
        0x00000000: ${"%08x".format(exception)} ${"%08x".format(result)} ${"%08x".format(present)} ${"%08x".format(value.toInt())} '............dead'
        0x00000010: ${"%08x".format((value ushr 32).toInt())} ffffffff '........        ')
    """.trimIndent()

    @Test fun decodesSuccessfulRepliesAndIgnoresPrintableColumn() {
        assertEquals(100L, BatteryPropertyReply.parse(reply(100)))
        assertEquals(1_735_743_600L, BatteryPropertyReply.parse(reply(1_735_743_600)))
        assertEquals(4_294_967_301L, BatteryPropertyReply.parse(reply(4_294_967_301)))
    }

    @Test fun rejectsUnavailableAndMalformedRepliesEvenWhenShellExitedSuccessfully() {
        listOf(
            reply(100, result = -1), reply(100, result = -2),
            reply(100, exception = -1), reply(100, present = 0), reply(Long.MIN_VALUE),
            "Permission denied", "Result: Parcel(00000000)",
            reply(100).replace("0x00000010:", "0x00000020:"),
            reply(100).replace("00000064", "xxxxxxxx")
        ).forEach { malformed ->
            assertTrue(malformed, runCatching { BatteryPropertyReply.parse(malformed) }.isFailure)
        }
    }

    @Test fun unknownHealthAndDatesNeverBecomeZeroPercentOrEpochDates() {
        listOf(-1L, 0L, 101L, Long.MIN_VALUE).forEach { assertNull(BatteryHealthSnapshot.validSoh(it)) }
        assertEquals(98, BatteryHealthSnapshot.validSoh(98))
        assertEquals(100, BatteryHealthSnapshot.validSoh(100))
        listOf(-1L, 0L, Long.MIN_VALUE, 1_735_743_600_000L).forEach {
            assertNull(BatteryHealthSnapshot.validDate(it, nowSeconds = 1_800_000_000))
        }
        assertNull(BatteryHealthSnapshot().manufacturingDate())
    }

    @Test fun showsEpochDatesInDeviceTimezoneAndKeepsAsocIndependent() {
        val health = BatteryHealthSnapshot(100, 1_735_743_600L, 1_739_890_800L)
        assertEquals("2025年1月2日", health.manufacturingDate(ZoneId.of("Asia/Tokyo")))
        assertEquals("2025年2月19日", health.firstUsageDate(ZoneId.of("Asia/Tokyo")))
        assertEquals("2025年1月1日", health.manufacturingDate(ZoneId.of("UTC")))
        val battery = BatterySnapshot.parse("mSavedBatteryAsoc: [98]")
        assertEquals("98%", battery.references().single().value)
        assertEquals(100, health.sohPercent)
    }
}
