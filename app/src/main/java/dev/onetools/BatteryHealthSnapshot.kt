package dev.onetools

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Android's SOH and dates are independent of Samsung's saved ASOC indicator. */
data class BatteryHealthSnapshot(
    val sohPercent: Int? = null,
    val manufacturingSeconds: Long? = null,
    val firstUsageSeconds: Long? = null,
    val unavailable: List<String> = emptyList()
) {
    fun manufacturingDate(zone: ZoneId = ZoneId.systemDefault()) = formatDate(manufacturingSeconds, zone)
    fun firstUsageDate(zone: ZoneId = ZoneId.systemDefault()) = formatDate(firstUsageSeconds, zone)

    private fun formatDate(seconds: Long?, zone: ZoneId): String? = seconds?.let {
        Instant.ofEpochSecond(it).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    }

    companion object {
        fun validSoh(value: Long): Int? = value.takeIf { it in 1L..100L }?.toInt()
        fun validDate(value: Long, nowSeconds: Long = Instant.now().epochSecond): Long? =
            value.takeIf { it > 0 && it <= nowSeconds + 86_400 }
    }
}

/** Decode the CLI's hex dump, never its printable ASCII column (which can contain hex digits).
 * IBatteryPropertiesRegistrar.getProperty returns exception, status, presence, then an int64.
 * A successful shell exit alone does not prove that the Binder call or property read succeeded.
 */
internal object BatteryPropertyReply {
    fun parse(output: String): Long {
        val words = mutableListOf<Int>()
        output.lineSequence().forEach { line ->
            val match = Regex("^\\s*0x([0-9a-fA-F]{8}):\\s*(.*?)\\s*'").find(line) ?: return@forEach
            require(match.groupValues[1].toLong(16) == words.size * 4L) { "応答の並びが不正です" }
            val hexWords = match.groupValues[2].trim().split(Regex("\\s+"))
            require(hexWords.all { Regex("[0-9a-fA-F]{8}").matches(it) }) { "応答を解析できません" }
            words += hexWords.map { it.toLong(16).toInt() }
        }
        require(words.size >= 5) { "プロパティの応答を取得できません" }
        require(words[0] == 0) { "権限不足またはBinder呼び出しエラーです" }
        require(words[1] == 0) { "このプロパティは未対応、または取得に失敗しました (${words[1]})" }
        require(words[2] == 1) { "プロパティの値がありません" }
        val bytes = ByteBuffer.allocate(words.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        words.forEach(bytes::putInt)
        val value = bytes.getLong(12)
        require(value != Long.MIN_VALUE) { "このプロパティは未対応です" }
        return value
    }
}
