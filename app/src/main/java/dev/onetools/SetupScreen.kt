package dev.onetools

import androidx.compose.ui.res.stringResource

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.core.content.edit
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

/** Only the completed introduction is persisted; authorization is always queried from Shizuku. */
internal class SetupPreferences(private val preferences: SharedPreferences) {
    val completed: Boolean get() = preferences.getBoolean("completed", false)
    fun complete() { preferences.edit { putBoolean("completed", true) } }
}

@Composable
internal fun OneToolsApp(model: ToolsModel = viewModel(), preferences: SetupPreferences? = null) {
    val context = LocalContext.current
    val store = remember(context, preferences) {
        preferences ?: SetupPreferences(context.getSharedPreferences("initial_setup", Context.MODE_PRIVATE))
    }
    var completed by remember { mutableStateOf(store.completed) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    if (!completed || reviewing) {
        val state by model.state.collectAsStateWithLifecycle()
        val actions = remember(context) { ShizukuSetupActions(context) }
        val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshConnection() }
        LaunchedEffect(owner) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) { model.refreshConnection(); delay(5_000) }
            }
        }
        val finish = { store.complete(); completed = true; reviewing = false; model.dismiss() }
        key(reviewing) {
            SetupScreen(state, finish, onCancel = if (reviewing) ({ reviewing = false }) else null,
                connectionContent = { ShizukuSetupContent(state, actions, model::requestPermission, model::refreshConnection) })
        }
    } else {
        OneTools(model, onSetup = { reviewing = true })
    }
}

@Composable
internal fun SetupScreen(state: ToolsState, onFinish: () -> Unit, onCancel: (() -> Unit)? = null,
    dark: Boolean = isSystemInDarkTheme(), connectionContent: @Composable ColumnScope.() -> Unit) {
    SystemBarAppearance(dark)
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0 || onCancel != null) {
        if (page > 0) page-- else onCancel?.invoke()
    }
    MaterialTheme(colorScheme = oneToolsColorScheme(dark)) {
        Scaffold(modifier = Modifier.testTag("initial-setup"), contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                Surface {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Button(onClick = { if (page < 2) page++ else onFinish() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("setup-next")) {
                            Text(if (page < 2) "次へ" else if (state.permitted) "OneToolsをはじめる" else "あとで接続してはじめる")
                        }
                        if (page > 0) TextButton(onClick = { page-- }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("戻る") }
                        else if (onCancel != null) TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("案内を閉じる") }
                    }
                }
            }) { padding ->
            key(page) {
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Spacer(Modifier.height(20.dp))
                    ToolGlyph(if (page == 2) 2 else 5, MaterialTheme.colorScheme.primary, Modifier.size(48.dp))
                    Text("${page + 1} / 3", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(listOf("OneToolsへようこそ", "データと使い方", "Shizukuの準備")[page],
                        fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    when (page) {
                        0 -> {
                            Text("Galaxyの状態を、もっとわかりやすく。", style = MaterialTheme.typography.titleMedium)
                            Group {
                                Text("バッテリーの状態", fontWeight = FontWeight.SemiBold)
                                Text("残量、温度、健康度の報告値、推定サイクル数などを確認できます。")
                                Text("Galaxyの便利な機能", fontWeight = FontWeight.SemiBold)
                                Text("カメラのシャッター音強制設定や、端末の更新履歴を確認できます。")
                            }
                            Text("Galaxy向け · Android 11以降。利用できる項目は機種・One UI・地域によって異なります。")
                            if (!state.isSamsungDevice) Text("この端末ではGalaxy専用の情報やカメラ設定は利用できません。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        1 -> {
                            Group {
                                Text("データは端末内で処理", fontWeight = FontWeight.SemiBold)
                                Text("取得した情報や実行ログを外部へ送信しません。バッテリー履歴の継続記録は行わず、ログはメモリ内だけに保持します。")
                                Text("この案内の完了状態と、更新履歴の販売元の選択を端末内に保存します。アプリの更新確認・ダウンロードを操作したときだけGitHubへ接続します。外部リンクはブラウザやGoogle Playで開きます。")
                            }
                            Group {
                                Text("権限と設定の変更", fontWeight = FontWeight.SemiBold)
                                Text("バッテリー情報の取得や設定の操作にはShizukuへの接続と許可が必要です。この案内ではカメラの設定を変更しません。")
                                Text("健康度や残り時間は端末の報告値・推定値です。設定値の変更だけで実際の撮影音の変化は保証されません。")
                            }
                            Text(stringResource(R.string.unofficial_notice))
                        }
                        2 -> {
                            Text("接続は後から「接続」タブでも行えます。端末の設定変更はShizukuの案内を確認して進めてください。")
                            connectionContent()
                        }
                    }
                }
            }
        }
    }
}
