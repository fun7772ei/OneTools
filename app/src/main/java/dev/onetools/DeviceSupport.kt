package dev.onetools

/** Use the manufacturer, never a Galaxy model-number allowlist. Data availability is separate. */
internal object DeviceSupport {
    fun isSamsung(manufacturer: String?) = manufacturer?.trim()?.equals("samsung", ignoreCase = true) == true

    const val CYCLE_EXPLANATION = "満量放電相当の回数です。充電器を挿した回数ではありません。\n\n公開されている別版のSamsung実装の計算方法に基づく目安です。機種やOne UIのバージョンによって、記録の意味や計算方法が異なる可能性があります。お使いの端末で同じ計算方法が使われているかは未確認です。\n\ndumpsys batteryのmSavedBatteryUsageを100で割り、小数点以下を切り捨てて表示します。値が欠けている、複数ある、数値として不正な場合は「不明」と表示します。AndroidのHealth HALのサイクル数は使用していません。"
}
