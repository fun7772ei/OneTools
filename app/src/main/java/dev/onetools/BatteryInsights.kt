package dev.onetools

import java.util.Locale

internal fun singleInteger(raw: String?): Long? {
    val m = Regex("(?:\\[\\s*(-?[0-9]+)\\s*]|(-?[0-9]+))").matchEntire(raw?.trim() ?: return null) ?: return null
    return (m.groups[1]?.value ?: m.groups[2]?.value)?.toLongOrNull()
}
private fun decimal(value: Double, digits: Int) = String.format(Locale.JAPAN, "%.${digits}f", value)
internal fun BatterySnapshot.savedMaxTemperature() = singleInteger(value("mSavedBatteryMaxTemp"))
    ?.takeIf { it >= 0 }?.let { decimal(it / 10.0, 1) + " °C" } ?: "不明"
internal fun BatterySnapshot.savedMaxCurrent() = singleInteger(value("mSavedBatteryMaxCurrent"))
    ?.takeIf { it > 0 }?.let { "約" + decimal(it / 1_000_000.0, 2) + " A" } ?: "不明"
internal fun BatterySnapshot.fullStatusTime() = singleInteger(value("mSavedFullStatusDuration"))
    ?.takeIf { it >= 0 }?.let { "${it / 60}時間${it % 60}分" } ?: "不明"
internal fun BatterySnapshot.protectionMode(): String = when(singleInteger(value("mProtectBatteryMode"))) {
    0L -> "オフ"; 1L -> "最大"; 2L -> "高残量の長時間維持を抑制"; 3L -> "基本"; 4L -> "睡眠中に調整"
    null -> if(value("mFullCapacityEnable") == "true") "有効（旧方式）" else "不明"
    else -> "不明"
}
internal fun BatterySnapshot.protectionLimit(): String = when(singleInteger(value("mProtectBatteryMode"))) {
    0L -> "上限設定なし"
    1L -> singleInteger(value("mMaximumProtectionThreshold"))?.takeIf { it in 1..100 }?.let { "$it%" } ?: "不明"
    2L -> singleInteger(value("mProtectionThreshold"))?.takeIf { it in 1..100 }?.let { "$it%（内部基準）" } ?: "不明"
    3L -> "100%まで"
    4L -> "状況に応じて変化"
    else -> "不明"
}
internal fun BatterySnapshot.flag(key: String) = when(value(key)) { "true" -> "オン"; "false" -> "オフ"; else -> "不明" }
internal fun microamps(value: Int?) = value?.let { decimal(it / 1000.0, 1) + " mA" } ?: "不明"
internal fun microampHours(value: Int?) = value?.let { decimal(it / 1000.0, 0) + " mAh" } ?: "不明"
internal fun BatterySnapshot.rawExplanation(keys: List<String>, description: String) = description +
    "\n\n取得元：dumpsys battery\n" + keys.joinToString("\n") { "$it: ${value(it) ?: "未取得"}" }
