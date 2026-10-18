package dev.onetools

data class BatterySnapshot(val fields: Map<String, String>) {
    fun value(key: String) = fields[key]
    fun number(key: String) = value(key)?.toDoubleOrNull()?.takeIf { it.isFinite() }
    // Samsung's saved cumulative discharge percentage, not Health HAL cycleCount.
    val estimatedCycles: Long? get() {
        val raw = value("mSavedBatteryUsage")?.trim() ?: return null
        val match = Regex("(?:\\[\\s*([0-9]+)\\s*]|([0-9]+))").matchEntire(raw) ?: return null
        val usage = (match.groups[1]?.value ?: match.groups[2]?.value)?.toLongOrNull() ?: return null
        return usage / 100
    }
    fun estimatedCyclesDisplay() = estimatedCycles?.let { "約${it}回" } ?: "不明"
    val percent: Int? get() {
        val level = number("level") ?: return null
        val scale = number("scale") ?: return null
        return if (scale > 0 && level in 0.0..scale) (level * 100 / scale).toInt() else null
    }
    val status get() = when(value("status")) { "2" -> "充電中"; "3" -> "放電中"; "4" -> "充電していません"; "5" -> "満充電"; else -> "不明" }
    val health get() = when(value("health")) { "2" -> "良好"; "3" -> "過熱"; "4" -> "故障"; "5" -> "過電圧"; "6" -> "異常"; "7" -> "低温"; else -> "不明" }
    val sources get() = listOf("AC" to "AC powered", "USB" to "USB powered", "ワイヤレス" to "Wireless powered", "ドック" to "Dock powered").filter { value(it.second) == "true" }.joinToString(" + ") { it.first }.ifEmpty { if(value("USB powered") == null) "不明" else "未接続" }
    companion object {
        fun parse(raw: String): BatterySnapshot {
            // Ignore event histories: old level/temperature entries are not current readings.
            val fields = linkedMapOf<String, String>()
            var inHistory = false
            var inBackup = false
            val savedKeys = setOf("mSavedBatteryMaxTemp", "mSavedBatteryMaxCurrent", "mSavedBatteryBsoh",
                "mSavedBatteryAsoc", "mSavedBatteryUsage", "mSavedFullStatusDuration", "battery FirstUseDate")
            raw.lineSequence().forEach {
                val line = it.trim()
                if(line.startsWith("[EventLogBuffer]")) { inHistory = true; inBackup = false }
                if(line == "BatteryInfoBackUp") { inBackup = true; inHistory = false; return@forEach }
                if(inHistory) return@forEach
                val p = it.indexOf(':')
                if(p > 0) {
                    val key = it.substring(0,p).trim()
                    if(inBackup && key !in savedKeys) return@forEach
                    // Multiple current usage records are ambiguous, even if they agree.
                    if(key in savedKeys && fields.containsKey(key)) fields[key] = ""
                    else fields.putIfAbsent(key, it.substring(p+1).trim())
                }
            }
            return BatterySnapshot(fields)
        }
        fun shutter(raw: String): Boolean? = when(raw.trim()) { "0" -> false; "1" -> true; else -> null }
    }
}
