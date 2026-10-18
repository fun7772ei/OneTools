package dev.onetools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable internal fun AppUpdateCard(model: AppUpdateModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = context.packageManager.canRequestPackageInstalls() }
    val actions = remember(context) { ShizukuSetupActions(context) }
    AppUpdateContent(state, canInstall, BuildConfig.DEBUG,
        onCheck = model::check, onDownload = model::download, onInstall = model::install,
        onAllow = model::allowInstall, onCancel = model::cancel,
        onOpenPage = { actions.open(state.release?.pageUrl ?: UPDATE_RELEASES_URL) })
}

@Composable internal fun AppUpdateContent(state: AppUpdateState, canInstall: Boolean, debug: Boolean,
    onCheck: () -> Unit, onDownload: () -> Unit, onInstall: () -> Unit,
    onAllow: () -> Unit, onCancel: () -> Unit, onOpenPage: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text("アプリのアップデート", fontSize = 15.sp, fontWeight = FontWeight.Medium)
    Text(state.message, color = if (state.error) scheme.error else scheme.onSurfaceVariant, fontSize = 13.sp,
        modifier = Modifier.testTag("app-update-status"))
    if (state.busy) {
        if (state.downloading) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            Text("${(state.progress * 100).toInt()}%", fontSize = 12.sp)
        } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("キャンセル") }
    }
    val release = state.release
    if (release != null && release.versionCode > BuildConfig.VERSION_CODE) {
        if (release.notes.isNotBlank()) {
            Text("変更内容", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            // Release notes are displayed as plain text; no remote markup is executed.
            Text(release.notes, fontSize = 13.sp, maxLines = 12, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (!debug && release.minSdk <= android.os.Build.VERSION.SDK_INT) {
            if (state.ready) {
                if (canInstall) Button(onClick = onInstall, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("インストール") }
                else {
                    Text("初回は、OneToolsからのアプリのインストールを許可してください。戻ってからインストールを進めます。", fontSize = 13.sp)
                    Button(onClick = onAllow, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("インストールを許可") }
                }
            } else Button(onClick = onDownload, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("${release.versionName} をダウンロード（${release.apk.size / 1024 / 1024} MB）")
            }
        }
    }
    OutlinedButton(onClick = onCheck, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("app-update-check")) { Text("アップデートを確認") }
    TextButton(onClick = onOpenPage, modifier = Modifier.fillMaxWidth()) { Text("GitHubの公開ページ") }
    Text("確認・ダウンロード時だけGitHubへ接続します。端末の取得情報や実行ログは送信しません。更新の確定はAndroidの確認画面で行います。", fontSize = 12.sp, color = scheme.onSurfaceVariant)
}
