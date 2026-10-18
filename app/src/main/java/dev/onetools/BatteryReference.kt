package dev.onetools

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class BatteryReference(val key: String, val title: String, val value: String, val description: String)

fun BatterySnapshot.references(): List<BatteryReference> {
    val labels = listOf(
        Triple("battery FirstUseDate", "使い始めた日", "端末に記録されている、バッテリーの初回使用日です。"),
        Triple("mSavedBatteryAsoc", "充電校正の参考（ASOC）", "Samsung Membersのモデレーターは、充電状態の校正精度に関係する値で、長期的な健康度とは別と説明しています。過去の別機種の実装には容量比に関係する処理もあるため、ASOCを劣化率と読み替えません。"),
        Triple("mSavedBatteryBsoh", "Samsungの推定健康度（BSOH）", "Samsung Membersのモデレーターは設計容量に対するバッテリー全体の健康度を示す値と説明しています。端末が返す推定値で、実容量の直接測定ではありません。"),
        Triple("mSavedBatteryUsage", "これまでの使用量の記録", "使用状況を記録した内部の数値です。そのまま充電回数を表す値ではありません。"),
        Triple("mProtectBatteryMode", "バッテリー保護の設定番号", "端末が保護の設定を区別するための番号です。番号が大きいほど保護が強い、という意味ではありません。"),
        Triple("mProtectionThreshold", "充電を保護する基準値", "端末が充電を制御するための内部の値です。現在の充電上限を示す値とは限りません。"),
        Triple("mMaximumProtectionThreshold", "最大保護の基準値", "最大保護に関する内部の値です。この画面では設定の変更は行いません。"),
        Triple("current now", "Samsung電流の原値", "Samsung独自のcurrent nowの原値です。標準の電池サービスの電流とは取得経路・単位・更新時刻が異なる可能性があります。単位を推測せず、この補足項目では換算していません。")
    )
    return labels.mapNotNull { (key, title, description) ->
        value(key)?.let { raw -> BatteryReference(key, title, referenceValue(key, raw), description) }
    }
}

internal fun referenceValue(key: String, raw: String): String {
    val cleaned = raw.trim().let { if(Regex("\\[-?\\d+(?:\\.\\d+)?]" ).matches(it)) it.substring(1, it.length-1) else it }
    if(key == "battery FirstUseDate" && Regex("[0-9]{8}").matches(cleaned)) {
        return runCatching { LocalDate.parse(cleaned, DateTimeFormatter.BASIC_ISO_DATE).format(DateTimeFormatter.ofPattern("yyyy年M月d日")) }.getOrDefault("日付を確認できません（$cleaned）")
    }
    if(key == "mSavedBatteryAsoc" && cleaned.toIntOrNull() in 0..100) return "$cleaned%"
    if(key == "mSavedBatteryBsoh") {
        val scalar = raw.trim().let { value ->
            if(value.startsWith("[") && value.endsWith("]")) value.substring(1, value.length - 1).trim() else value
        }
        val number = scalar.takeIf { Regex("[0-9]+(?:\\.[0-9]+)?").matches(it) }?.toDoubleOrNull()
        return if(number != null && number.isFinite() && number in 0.0..100.0) "$scalar%" else "不明"
    }
    return cleaned
}
