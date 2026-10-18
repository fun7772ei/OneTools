package dev.onetools

/** One-shot estimate from existing OS aggregates. No app-side sample history. */
data class BatteryEstimate(val remainingMillis: Long? = null, val reason: String = "使用状況を取得すると表示します") {
    fun display(): String {
        val millis = remainingMillis ?: return "予測できません"
        val minutes = millis / 60_000
        if(minutes < 10) return "あと約10分未満"
        val rounded = minutes / 10 * 10
        val hours = rounded / 60
        val rest = rounded % 60
        return "あと約" + if(hours == 0L) "${rest}分" else if(rest == 0L) "${hours}時間" else "${hours}時間${rest}分"
    }
    companion object {
        fun calculate(summary: String, remainingMicroAh: Double?): BatteryEstimate {
            val timeText = summary.lineSequence().firstOrNull { it.trim().startsWith("Time on battery:") }
                ?.substringAfter(":")?.substringBefore("(")?.trim()
            val duration = timeText?.let(::parseDuration)
            val discharge = Regex("(?m)^\\s*Discharge: ([0-9]+(?:\\.[0-9]+)?) mAh\\s*$")
                .find(summary)?.groupValues?.get(1)?.toDoubleOrNull()
            if(duration == null || discharge == null) return BatteryEstimate(reason="この端末では使用状況を読み取れませんでした")
            if(duration < 30*60_000L || discharge < 20) return BatteryEstimate(reason="予測に必要な使用データがまだ足りません")
            if(remainingMicroAh == null || !remainingMicroAh.isFinite() || remainingMicroAh <= 0 || remainingMicroAh > 100_000_000)
                return BatteryEstimate(reason="現在の電荷量を取得できませんでした")
            val estimate = remainingMicroAh / 1000.0 * duration / discharge
            if(!estimate.isFinite() || estimate <= 0 || estimate > 30*24*60*60_000L)
                return BatteryEstimate(reason="安定した予測を計算できませんでした")
            return BatteryEstimate(estimate.toLong(), "直近の平均消費ペースが続いた場合の目安です")
        }
        internal fun parseDuration(text: String): Long? {
            val regex = Regex("([0-9]+)(ms|d|h|m|s)")
            if(text.isBlank() || text.replace(regex, "").isNotBlank()) return null
            return runCatching { regex.findAll(text).fold(0L) { total, match ->
                val unit = when(match.groupValues[2]) { "d" -> 86_400_000L; "h" -> 3_600_000L; "m" -> 60_000L; "s" -> 1000L; else -> 1L }
                Math.addExact(total, Math.multiplyExact(match.groupValues[1].toLong(), unit))
            } }.getOrNull()
        }
    }
}
