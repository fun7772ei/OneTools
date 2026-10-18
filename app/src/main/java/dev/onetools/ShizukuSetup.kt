package dev.onetools

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

/** Shared by onboarding and the connection tab. External app failures stay visible. */
internal class ShizukuSetupActions(private val context: Context) {
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onFailure { Toast.makeText(context, "ページを開けませんでした。ブラウザをご確認ください。", Toast.LENGTH_LONG).show() }
    }

    fun store() {
        val url = "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).setPackage("com.android.vending")) }
            .onFailure { open(url) }
    }

    fun manager() {
        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent == null) store()
        else runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "Shizukuを開けませんでした。アプリ一覧から起動してください。", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
internal fun ShizukuSetupContent(state: ToolsState, actions: ShizukuSetupActions,
    requestPermission: () -> Unit, refresh: () -> Unit) {
    Group {
        SetupStep("1", "Shizukuをインストール", if (state.installed) "インストール済み" else "Google Playからインストールできます")
        OutlinedButton(onClick = actions::store, modifier = Modifier.fillMaxWidth()) { Text("Google Playで開く") }
    }
    Group {
        SetupStep("2", "Shizukuを起動", "Shizukuの案内に従い、ワイヤレスデバッグなどで開始してください。端末の再起動後は再び開始が必要です。")
        Button(onClick = actions::manager, modifier = Modifier.fillMaxWidth()) { Text("Shizukuを開く") }
    }
    Group {
        SetupStep("3", "OneToolsを許可", "権限要求で許可を選択してください。拒否済みの場合はShizukuの「認証済みアプリ」から変更できます。")
        Button(onClick = requestPermission, enabled = !state.permitted, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.permitted) "許可済み" else "権限を要求")
        }
        state.operation?.takeIf { it.kind == OperationKind.PERMISSION && it.finished && !it.success }?.let {
            Text(it.stage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
    Group {
        Text(state.connection, color = if (state.permitted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = refresh, modifier = Modifier.fillMaxWidth()) { Text("接続を再確認") }
        TextButton(onClick = { actions.open("https://shizuku.rikka.app/guide/setup/") }) { Text("公式セットアップガイド（英語）") }
    }
}

@Composable
private fun SetupStep(number: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$number. $title", style = MaterialTheme.typography.titleMedium)
        Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
