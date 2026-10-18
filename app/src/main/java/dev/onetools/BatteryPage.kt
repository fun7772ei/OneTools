package dev.onetools

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable internal fun BatteryPage(state: ToolsState) {
    // Progress and log updates do not change battery values. Reuse the presentation so
    // those updates can skip the long battery content while the popup animates.
    val presentation = remember(state.battery, state.batteryHealth, state.batteryHal,
        state.estimate, state.updated, state.permitted, state.isSamsungDevice) {
        state.copy(operation = null, log = "")
    }
    BatteryContent(presentation)
}

@Composable private fun BatteryContent(state: ToolsState) {
    val b = state.battery
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant
    Group {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(b?.percent?.let { "$it%" } ?: "—",fontSize=44.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
                Text(if(state.permitted) state.estimate.display() else "再接続してください",fontSize=18.sp,fontWeight=FontWeight.Medium,color=primary)
                Text("使用できる時間の目安",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val fraction = animateFloatAsState((b?.percent ?: 0)/100f,label="battery level")
        Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(primary.copy(alpha=.12f))
            .semantics { contentDescription = "バッテリー残量 ${b?.percent?.let { "${it}パーセント" } ?: "不明"}" }) {
            if(b?.percent != null) Box(Modifier.fillMaxSize().drawBehind {
                drawRoundRect(primary, size = Size(size.width * fraction.value.coerceIn(0f, 1f), size.height),
                    cornerRadius = CornerRadius(size.height / 2f))
            })
        }
        Text(if(state.estimate.remainingMillis != null && state.permitted) {
            if(b?.sources != "未接続" && b?.sources != "不明") "充電器を外し、同じ使い方を続けた場合の目安です。" else "同じ使い方を続けた場合の目安です。"
        } else if(!state.permitted) "Shizukuに接続して情報を更新してください。" else state.estimate.reason,
            fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${b?.status ?: "未取得"} · ${b?.sources ?: "—"}  /  更新 ${state.updated}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!state.permitted && b != null) Text("残量は前回取得した値です。",fontSize=12.sp,color=MaterialTheme.colorScheme.error)
        Text("各項目を長押しすると、意味や表示方法を確認できます。", fontSize=12.sp,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric("温度", b?.number("temperature")?.let { format(it/10) + " °C" } ?: "—", Modifier.weight(1f), b?.rawExplanation(listOf("temperature"), "現在のバッテリー温度です。0.1℃単位の整数を10で割って表示します。端末表面やCPUの温度ではありません。") ?: "まだ取得していません。")
        Metric("電圧", b?.number("voltage")?.let { format(it/1000,3) + " V" } ?: "—", Modifier.weight(1f), b?.rawExplanation(listOf("voltage"), "バッテリー自体の現在の電圧です。mV単位の値を1,000で割ってVに変換しています。充電器の出力電圧とは異なります。") ?: "まだ取得していません。")
    }
    if(state.isSamsungDevice) ExplanationArea("BSOH",
        "Samsung Membersのモデレーターは、BSOHを設計容量に対するバッテリー全体の健康度と説明し、ASOCより健康度を見るのに信頼できる指標としています。これはSamsung端末が返す推定診断値で、実容量の直接測定や劣化がないことの保証ではありません。機種やソフトウェアによって記録や算出方法が異なる可能性があります。",
        Modifier.fillMaxWidth()) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("BSOH", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(b?.value("mSavedBatteryBsoh")?.let { referenceValue("mSavedBatteryBsoh", it) } ?: "不明",
                    fontSize = 36.sp, fontWeight = FontWeight.Medium)
                Text("Samsungの推定健康度", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HealthTile("SOH", "Android参考値", state.batteryHealth?.sohPercent?.let { "$it%" } ?: "不明",
                Modifier.weight(1f).fillMaxHeight(), "Android Health HALが返す参考値です。Androidの仕様では、推定満充電容量を定格容量と比べた割合です。Samsung独自のBSOHとは取得元と定義が異なります。端末が報告する推定値で、実容量の直接測定ではありません。")
            if(state.isSamsungDevice) HealthTile("ASOC", "充電校正の参考", b?.value("mSavedBatteryAsoc")?.let { referenceValue("mSavedBatteryAsoc", it) } ?: "不明",
                Modifier.weight(1f).fillMaxHeight(), "Samsung Membersのモデレーターは、ASOCを充電状態の校正精度に関係する値で、長期的なバッテリー健康度とは別と説明しています。一方、公開された旧世代のSamsung実装には、ASOCと容量比の関係を示す処理もあります。機種やソフトウェアで意味・計算方法が異なる可能性があるため、この値から劣化率を読み替えません。")
        }
    }
    if(state.isSamsungDevice) ExplanationArea("推定サイクル数", DeviceSupport.CYCLE_EXPLANATION,
        Modifier.fillMaxWidth()) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("推定サイクル数", Modifier.weight(1f), fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(b?.estimatedCyclesDisplay() ?: "不明", Modifier.weight(1f), fontSize = 28.sp,
                    fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    ExpandableBatteryDetails(expanded = showDetails, onToggle = { showDetails = !showDetails }) {
    if(state.isSamsungDevice) {
        Text("これまでの記録", Modifier.padding(horizontal = 8.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("記録上の最大温度", b?.savedMaxTemperature() ?: "不明", Modifier.weight(1f),
                b?.rawExplanation(listOf("mSavedBatteryMaxTemp"), "Samsungが保存した最大バッテリー温度です。0.1℃単位の値を10で割ります。現在の温度や危険度の判定ではありません。記録期間・初期化や電池交換の影響は機種によって異なります。負数や不正な値は不明です。") ?: "まだ取得していません。")
            Metric("最大電流（µA換算）", b?.savedMaxCurrent() ?: "不明", Modifier.weight(1f),
                b?.rawExplanation(listOf("mSavedBatteryMaxCurrent"), "Samsungが保存した電池電流の最大値を、µA単位として1,000,000で割った参考換算です。例：5191406 → 約5.19 A。\n\n一部のGalaxyでは元の値がmA単位の場合があるため、この換算を全機種の実電流と断定できません。単位を数値の大きさで自動推測していません。\n\n実機の電池サービスでは、電池電流の正方向の最大値を保存しています。絶対値の最大や、充電器・ケーブルの電流上限ではありません。記録期間や電池交換による初期化も保証されません。") ?: "まだ取得していません。")
        }
        Group {
            Info("残量100%で過ごした時間", b?.fullStatusTime() ?: "不明",
                b?.rawExplanation(listOf("mSavedFullStatusDuration"), "残量が100%だった時間の累積記録です。分単位の値を時間と分に整形します。例：318分 → 5時間18分。\n\n公開されたSamsung実装と接続実機の電池サービスで、残量100%の経過時間を分単位で保存する処理を確認しました。機種によって未対応・更新間隔・初期化条件が異なる可能性があります。充電器につないでいた総時間ではありません。") )
            Info("ワイヤレス急速充電の使用歴", when(b?.value("mWasUsedWirelessFastChargerPreviously")) { "true" -> "記録あり"; "false" -> "記録なし"; else -> "不明" },
                b?.rawExplanation(listOf("mWasUsedWirelessFastChargerPreviously"), "ワイヤレス急速充電を以前使用した、という端末内のフラグです。trueは記録あり、falseは記録なしです。現在ワイヤレス充電中という意味ではなく、回数や日時は分かりません。"))
        }
        Group {
            Text("バッテリー保護", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Info("保護モード", b?.protectionMode() ?: "不明", b?.rawExplanation(listOf("mProtectBatteryMode", "mFullCapacityEnable"),
                "Samsungの電池サービスが報告する保護モードです。確認した実装では0=オフ、1=最大、2=高残量の長時間維持を抑制、3=基本、4=睡眠中に調整です。2は内部の保護状態で、設定アプリの選択肢と一致するとは限りません。\n\n基本は100%で停止して95%付近で再開、睡眠中の保護は使用パターンに合わせて調整します。機種・One UIによって名称や意味が異なる可能性があります。未知の番号は不明と表示し、推測して設定名を付けません。"))
            Info("充電上限の設定", b?.protectionLimit() ?: "不明", b?.rawExplanation(listOf("mProtectBatteryMode", "mMaximumProtectionThreshold", "mProtectionThreshold"),
                "最大モード（1）ではmMaximumProtectionThresholdを上限の設定値として表示します。例：モード1、最大保護85、共通基準80なら、設定上限は85%です。共通基準の80を現在の上限には使いません。\n\nオフ・基本・睡眠中の保護を固定の80%/85%上限とは扱いません。内部状態2では共通基準を「内部基準」と明記します。設定値の表示であり、その瞬間の充電制御や実際に停止する残量を測ったものではありません。"))
        }
        Group {
            Text("急速充電の設定", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            listOf("Adaptive Fast Charging Settings" to "急速充電", "Super Fast Charging Settings" to "超急速充電",
                "mWirelessFastChargingSettingsEnable" to "ワイヤレス急速充電").forEach { (key, label) ->
                Info(label, b?.flag(key) ?: "不明", b?.rawExplanation(listOf(key), "端末が報告する設定フラグです。trueはオン、falseはオフ、それ以外や未取得は不明です。現在の充電速度や充電方式を測定した値ではありません。機種によって項目の有無や設定画面との対応が異なります。"))
            }
        }
    }
    Group {
        Text("バッテリー情報", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Info("健康状態", b?.health ?: "—", "OSが報告する電池の状態です。「良好」は、容量が新品と同じという意味ではありません。")
        HorizontalDivider(color = muted)
        Info("製造日", state.batteryHealth?.manufacturingDate() ?: "不明", "Androidに記録されたバッテリーの製造日です。端末のタイムゾーンで表示しています。")
        Info("初回使用日", state.batteryHealth?.firstUsageDate() ?: "不明", "Androidに記録されたバッテリーの初回使用日です。端末のタイムゾーンで表示しています。")
        Info("バッテリーの種類", if(b?.value("technology") == "Li-ion") "リチウムイオン" else b?.value("technology") ?: "—", "端末が報告するバッテリーの種類です。")
        if(state.batteryHealth?.unavailable?.isNotEmpty() == true) {
            Text("未取得：${state.batteryHealth.unavailable.joinToString("、")}。詳細は実行ログで確認できます。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Group {
        Text("充電の情報", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Info("電荷量", b?.number("Charge counter")?.takeIf { it >= 0 }?.let { format(it/1000,0) + " mAh" } ?: "—", "現在残っている電荷の推定値です。満充電時の容量や、新品に対する健康度ではありません。")
        HorizontalDivider(color = muted)
        Info("最大充電電流", b?.number("Max charging current")?.takeIf { it > 0 }?.let { format(it/1000,0) + " mA" } ?: "未報告", "端末が報告する充電電流の上限値です。現在実際に流れている電流とは異なります。0などの値は未報告として表示します。")
        Info("最大充電電圧", b?.number("Max charging voltage")?.takeIf { it > 0 }?.let { format(it/1_000_000,2) + " V" } ?: "未報告", "端末が報告する充電電圧の上限値です。バッテリー自体の現在の電圧とは異なります。0などの値は未報告として表示します。")
    }
    Group {
        Text("電池サービスの報告値", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Info("現在の電池電流", microamps(state.batteryHal?.currentUa), "Android Health HALのgetHealthInfoが報告する電流です。µAを1,000で割ってmA表示します。正は電池に入る方向、負は電池から出る方向という標準の規約です。機種側の実装に依存し、充電器の入力電流ではありません。更新時点の値で、常時記録はしません。")
        Info("平均電池電流", microamps(state.batteryHal?.averageCurrentUa), "同じ電池HALのbatteryCurrentAverageMicroampsを1,000で割ります。平均の対象期間は端末側が決めるため、このアプリでは固定の時間を表示しません。")
        HorizontalDivider(color = muted)
        Info("報告上の満充電容量", microampHours(state.batteryHal?.fullChargeUah), "電池HALのbatteryFullChargeUahを1,000で割ってmAh表示します。電池サービスが返す値であり、このアプリが充放電して測定した実容量ではありません。固定値や粗い推定値の場合もあるため、これだけで劣化を判定しません。")
        Info("報告上の設計容量", microampHours(state.batteryHal?.designUah), "電池HALのbatteryFullChargeDesignCapacityUahを1,000で割ってmAh表示します。メーカー公称の標準容量・定格容量と一致するとは限りません。この値と満充電容量の比を別の健康度として生成しません。")
    }
    val extraRecords = if(state.isSamsungDevice) b?.references().orEmpty().filterNot { it.key in setOf("mSavedBatteryAsoc", "mSavedBatteryBsoh", "battery FirstUseDate", "mSavedBatteryUsage", "mProtectBatteryMode", "mProtectionThreshold", "mMaximumProtectionThreshold") } else emptyList()
    if(extraRecords.isNotEmpty()) Group {
        var showRecords by rememberSaveable { mutableStateOf(false) }
        Text("端末の補足記録", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { showRecords = !showRecords }, modifier = Modifier.fillMaxWidth()) {
            Text(if(showRecords) "詳しい記録を閉じる" else "詳しい記録を表示")
        }
        AnimatedVisibility(showRecords, enter = expandVertically(tween(220)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        extraRecords.forEachIndexed { index, reference ->
            if(index > 0) HorizontalDivider(color = muted)
            Info(reference.title, reference.value, reference.description + "\n\n内部名：${reference.key}")
        }
        }
        }
    }
    }
}

@Composable private fun ExpandableBatteryDetails(expanded: Boolean, onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .testTag("battery-details-toggle").semantics { stateDescription = if(expanded) "展開中" else "折りたたみ中" }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (expanded) "詳しいバッテリー情報を閉じる" else "詳しいバッテリー情報を表示",
                            color = scheme.primary, fontWeight = FontWeight.SemiBold)
                        Text("記録・保護設定・充電値", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                    Text(if (expanded) "−" else "+", fontSize = 22.sp, color = scheme.primary)
                }
            }
            AnimatedVisibility(expanded, enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}

@Composable private fun HealthTile(label: String, description: String, value: String, modifier: Modifier, explanation: String) {
    ExplanationArea(label, explanation, modifier) {
    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(value, fontSize = 36.sp, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
}
private fun format(value: Double, digits: Int = 1) = String.format(Locale.JAPAN, "%.${digits}f", value)
@Composable private fun Metric(label: String, value: String, modifier: Modifier, explanation: String) {
    ExplanationArea(label, explanation, modifier) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
    }
}
@Composable private fun Info(label: String, value: String, explanation: String? = null) {
    ExplanationArea(label, explanation, Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
    }
}

@Composable private fun ExplanationArea(title: String, explanation: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var showing by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(if(explanation == null) modifier else modifier
        .pointerInput(title, explanation) {
            detectTapGestures(onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                showing = true
            })
        }
        .semantics(mergeDescendants = true) {
            onLongClick(label = "説明を表示") { showing = true; true }
        }) { content() }
    if(showing && explanation != null) AlertDialog(
        onDismissRequest = { showing = false },
        title = { Text(title) },
        text = { Text(explanation, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showing = false }) { Text("閉じる") } }
    )
}
