package dev.onetools

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** modelNumber and the URL callback allow device-independent acceptance tests without launching a browser. */
@Composable
internal fun GalaxyUpdateCard(
    modelNumber: String = Build.MODEL,
    selectionOverride: GalaxyNotifySelection? = null,
    openUrlOverride: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val model = GalaxyNotifyResolver.normalize(modelNumber)
    val selection = remember(context, selectionOverride) { selectionOverride ?: GalaxyNotifySelection(
        context.getSharedPreferences("galaxy_notify", Context.MODE_PRIVATE)) }
    var loadFinished by remember(context) { mutableStateOf(false) }
    val resolver by produceState<GalaxyNotifyResolver?>(null, context) {
        value = withContext(Dispatchers.IO) { runCatching { GalaxyNotifyResolver.load(context) }.getOrNull() }
        loadFinished = true
    }
    val device = remember(resolver, model) { resolver?.resolve(model) }
    var selectedCarrier by remember(device, selection) { mutableStateOf(device?.let(selection::selected)?.carrier) }
    val channel = device?.channels?.firstOrNull { it.carrier == selectedCarrier }
    var choosing by rememberSaveable(model) { mutableStateOf(false) }
    var message by rememberSaveable(model) { mutableStateOf<String?>(null) }
    fun open(url: String) {
        runCatching {
            if(openUrlOverride != null) openUrlOverride(url)
            else context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure { message = "ページを開けませんでした。ブラウザが利用できるか確認してください。" }
    }
    fun openChannel(chosen: GalaxyNotifyResolver.Channel) {
        val url = chosen.url
        if(url == null) message = "${chosen.carrier}専用の更新履歴URLは未確認です。販売元のサポート情報をご確認ください。"
        else open(url)
    }

    Group {
        Text("端末のアップデート情報", fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Text(device?.let { "${it.name}（$model）" } ?: "型番：$model", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Samsung公式の更新履歴をブラウザで確認します。", fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(device == null) {
            Text(if(!loadFinished) "対応表を読み込み中…"
                else if(resolver == null) "対応表を読み込めませんでした。アプリを開き直してください。"
                else "この型番は対応表に登録されていません。", fontSize = 13.sp)
        } else {
            device.historyWarning?.let { Text(it, fontSize = 13.sp) }
            if(device.requiresChannel) Text("購入時の販売元：${channel?.carrier ?: "未選択"}", fontSize = 13.sp)
            Button(onClick = {
                when {
                    device.automaticUrl != null -> open(device.automaticUrl)
                    device.requiresChannel && channel != null -> openChannel(channel)
                    device.requiresChannel -> choosing = true
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("更新履歴を確認") }
            if(device.requiresChannel) {
                TextButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) { Text("購入時の販売元を変更") }
                if(channel != null) TextButton(onClick = {
                    selection.reset(model); selectedCarrier = null; message = null
                }, modifier = Modifier.fillMaxWidth()) { Text("販売元の選択をリセット") }
            }
        }
        message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
        Text("掲載情報は配信状況と異なる場合があります。対応表：2026年10月18日版", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if(choosing && device != null) AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text("この端末の販売元") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("現在のSIM回線ではなく、端末を購入したときの販売元を選んでください。中古の場合は元の販売元を選びます。")
                device.channels.forEach { option ->
                    TextButton(onClick = {
                        selection.select(device, option); selectedCarrier = option.carrier
                        choosing = false; message = null; openChannel(option)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(option.carrier + if(option.url == null) "（専用URL未確認）" else "")
                    }
                }
                TextButton(onClick = { choosing = false }) { Text("わからない・一覧にない") }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("キャンセル") } }
    )
}
