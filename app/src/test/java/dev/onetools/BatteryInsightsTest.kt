package dev.onetools

import org.junit.Assert.*
import org.junit.Test

class BatteryInsightsTest {
    @Test fun readsOnlyExplicitBackupAfterEventHistory() {
        val b = BatterySnapshot.parse("""temperature: 299
level: 84
[EventLogBuffer]
10-18 12:34:00 event temperature: 999
mSavedBatteryMaxTemp: 900
BatteryInfoBackUp
 mSavedBatteryMaxTemp: 520
 mSavedBatteryMaxCurrent: 5191406
 mSavedBatteryBsoh: 100.00
 temperature: 999
""")
        assertEquals("52.0 °C", b.savedMaxTemperature())
        assertEquals("約5.19 A", b.savedMaxCurrent())
        assertEquals("299", b.value("temperature"))
        assertEquals("100.00", b.value("mSavedBatteryBsoh"))
    }
    @Test fun formatsFullStatusMinutesWithoutClaimingTotalChargingTime() {
        assertEquals("5時間18分", BatterySnapshot(mapOf("mSavedFullStatusDuration" to "[318]")).fullStatusTime())
        assertEquals("0時間0分", BatterySnapshot(mapOf("mSavedFullStatusDuration" to "0")).fullStatusTime())
    }
    @Test fun malformedAndSentinelSavedRecordsStayUnknown() {
        for(raw in listOf("-1", "[-1]", "[1, 2]", "1.2", "NaN", "Infinity", "999999999999999999999", "")) {
            val b = BatterySnapshot(mapOf("mSavedBatteryMaxTemp" to raw, "mSavedBatteryMaxCurrent" to raw, "mSavedFullStatusDuration" to raw))
            assertEquals(raw, "不明", b.savedMaxTemperature())
            assertEquals(raw, "不明", b.savedMaxCurrent())
            assertEquals(raw, "不明", b.fullStatusTime())
        }
        assertEquals("不明", BatterySnapshot(emptyMap()).savedMaxTemperature())
    }
    @Test fun maximumModeUsesConfiguredThresholdNotDefault80() {
        val b = BatterySnapshot(mapOf("mProtectBatteryMode" to "1", "mMaximumProtectionThreshold" to "85", "mProtectionThreshold" to "80"))
        assertEquals("最大", b.protectionMode()); assertEquals("85%", b.protectionLimit())
        assertEquals("不明", b.copy(fields = b.fields - "mMaximumProtectionThreshold").protectionLimit())
    }
    @Test fun protectionModesDoNotInventFixedUpperLimit() {
        for ((mode, limit) in listOf("0" to "上限設定なし", "3" to "100%まで", "4" to "状況に応じて変化", "99" to "不明", "-1" to "不明")) {
            assertEquals(limit, BatterySnapshot(mapOf("mProtectBatteryMode" to mode, "mProtectionThreshold" to "80")).protectionLimit())
        }
        assertEquals("不明", BatterySnapshot(mapOf("mFullCapacityEnable" to "true")).protectionLimit())
    }
    @Test fun flagsAndDuplicateSavedFieldsDoNotBecomeGuesses() {
        assertEquals("オン", BatterySnapshot(mapOf("a" to "true")).flag("a"))
        assertEquals("オフ", BatterySnapshot(mapOf("a" to "false")).flag("a"))
        assertEquals("不明", BatterySnapshot(mapOf("a" to "1")).flag("a"))
        assertEquals("不明", BatterySnapshot.parse("mSavedBatteryMaxTemp: 520\nBatteryInfoBackUp\nmSavedBatteryMaxTemp: 520").savedMaxTemperature())
    }
}
