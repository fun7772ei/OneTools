package dev.onetools

import org.junit.Assert.*
import org.junit.Test

class BatteryCyclesTest {
    @Test fun convertsOnlySingleNonnegativeIntegersAndTruncates() {
        for ((raw, cycles) in listOf("[72106]" to 721L, "72199" to 721L,
            " [ 100 ] " to 1L, "[99]" to 0L, "[0]" to 0L)) {
            val snapshot = BatterySnapshot.parse("mSavedBatteryUsage: $raw")
            assertEquals(raw, cycles, snapshot.estimatedCycles)
            assertEquals("約${cycles}回", snapshot.estimatedCyclesDisplay())
        }
    }

    @Test fun missingMultipleMalformedNegativeAndOverflowValuesStayUnknown() {
        for (raw in listOf("", "[]", "[72106, 72000]", "[72106 72000]", "[-1]", "-100",
            "[72106.0]", "NaN", "Infinity", "[+72106]", "[72106] text", "[[72106]]",
            "9223372036854775808", "[9999999999999999999999999]")) {
            val snapshot = BatterySnapshot.parse("mSavedBatteryUsage: $raw")
            assertNull(raw, snapshot.estimatedCycles)
            assertEquals("不明", snapshot.estimatedCyclesDisplay())
        }
        assertEquals("不明", BatterySnapshot.parse("cycleCount: 0").estimatedCyclesDisplay())
    }

    @Test fun rejectsRepeatedUsageRecordsAndIgnoresEventHistory() {
        assertNull(BatterySnapshot.parse("mSavedBatteryUsage: [72106]\nmSavedBatteryUsage: [72106]").estimatedCycles)
        assertNull(BatterySnapshot.parse("[EventLogBuffer]\nmSavedBatteryUsage: [72106]").estimatedCycles)
        assertEquals(721L, BatterySnapshot.parse("mSavedBatteryUsage: [72106]\n[EventLogBuffer]\nmSavedBatteryUsage: [1]").estimatedCycles)
    }
}
