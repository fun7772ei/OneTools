package dev.onetools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryHealthValueTest {
    @Test fun showsSamsungBsohAsPercentOnlyForOnePlausibleValue() {
        for ((raw, expected) in listOf(
            "100.00" to "100.00%", "[100.00]" to "100.00%", "98.5" to "98.5%",
            "0" to "0%", "100" to "100%", "[100.00, 99.00]" to "不明",
            "[100.00] extra" to "不明", "-1" to "不明", "101" to "不明",
            "NaN" to "不明", "Infinity" to "不明", "" to "不明"
        )) {
            assertEquals(raw, expected, referenceValue("mSavedBatteryBsoh", raw))
        }
    }

    @Test fun referenceLabelsDistinguishBsohSohAndAsoc() {
        val refs = BatterySnapshot(mapOf(
            "mSavedBatteryBsoh" to "100.00",
            "mSavedBatteryAsoc" to "[98]"
        )).references().associateBy { it.key }
        assertEquals("Samsungの推定健康度（BSOH）", refs["mSavedBatteryBsoh"]?.title)
        assertEquals("100.00%", refs["mSavedBatteryBsoh"]?.value)
        assertEquals("充電校正の参考（ASOC）", refs["mSavedBatteryAsoc"]?.title)
        assertTrue(refs["mSavedBatteryAsoc"]!!.description.contains("長期的な健康度とは別"))
    }
}
