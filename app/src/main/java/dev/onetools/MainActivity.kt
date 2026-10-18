package dev.onetools

import androidx.compose.ui.res.stringResource

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OneToolsApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneTools(model: ToolsModel = viewModel(), themeOverride: Boolean? = null, onSetup: (() -> Unit)? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val dark = themeOverride ?: isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = oneToolsColorScheme(dark)
    val pager = rememberPagerState(pageCount = { 4 })
    val tab = pager.currentPage
    val scope = rememberCoroutineScope()
    fun selectTab(index: Int) { scope.launch {
        pager.animateScrollToPage(index, animationSpec = tween(280, easing = FastOutSlowInEasing))
    } }
    var details by remember { mutableStateOf(false) }
    var licenseDetails by remember { mutableStateOf(false) }
    val pageTitle = listOf("バッテリー", "ツール", "接続の状態", "情報")[tab]
    val headerState = rememberTopAppBarState()
    val headerBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(headerState)
    val compactTitle by remember { derivedStateOf { headerState.collapsedFraction > .65f } }
    // Keep the shared header position across tabs. Only vertical scrolling should
    // expand/collapse it; resetting it here also moves the pager during a swipe.
    SystemBarAppearance(dark)
    val resources = androidx.compose.ui.platform.LocalResources.current
    val license = remember(resources) { resources.openRawResource(R.raw.shizuku_license).bufferedReader().use { it.readText() } }
    val setupActions = remember(context) { ShizukuSetupActions(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshConnection() }
    // Recheck authorization while visible. Consumption is read from OS aggregates on demand.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(state.permitted, tab) { if(tab < 2 && state.permitted && state.battery == null) model.refresh() }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while(true) { delay(5_000); model.refreshConnection() }
        }
    }
    MaterialTheme(colorScheme = scheme) {
        Scaffold(containerColor = scheme.background, contentWindowInsets = WindowInsets.statusBars) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize().nestedScroll(headerBehavior.nestedScrollConnection)) {
                    OneToolsPageHeader(pageTitle, listOf("残量とバッテリーの状態", "Galaxyの便利な機能", "Shizukuのセットアップ", "OneToolsについて")[tab], headerState) {
                    PageActions(if(compactTitle) pageTitle else "", state.operation?.finished == false, state.permitted,
                        { model.refreshPage(tab) }, { details = true }, Modifier.padding(horizontal = 12.dp),
                        connectionAction = if(tab < 2) ({ selectTab(2) }) else null,
                        refreshOperation = state.operation?.takeIf { it.kind == OperationKind.REFRESH },
                        dismissRefresh = model::dismiss)
                    }
                    HorizontalPager(state = pager, modifier = Modifier.weight(1f).testTag("tab-pager"), key = { it }) { page ->
                        val pageScroll = rememberScrollState()
                        val pullState = rememberPullToRefreshState()
                        val refreshing = state.operation?.let { it.kind == OperationKind.REFRESH && !it.finished } == true
                        TwoStageRefreshBox(isRefreshing = refreshing,
                            onRefresh = { if(state.operation?.finished != false) model.refreshPage(page) },
                            headerState = headerState, scrollState = pageScroll,
                            state = pullState, modifier = Modifier.fillMaxSize().testTag("refresh-page-$page"),
                            indicator = {
                                if(!refreshing) PullToRefreshDefaults.Indicator(
                                    state = pullState, isRefreshing = false, modifier = Modifier.align(Alignment.TopCenter),
                                    containerColor = scheme.primaryContainer, color = scheme.onPrimaryContainer)
                            }) {
                            SafeContentScroll {
                                Column(Modifier.fillMaxSize().verticalScroll(pageScroll)
                                    .padding(horizontal = 10.dp).padding(top = 8.dp, bottom = 124.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if(page == 2) Surface(color = scheme.surfaceContainer, shape = RoundedCornerShape(28.dp)) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(Modifier.size(38.dp).background(if(state.permitted) scheme.primary.copy(alpha=.12f) else scheme.surfaceVariant,CircleShape),contentAlignment=Alignment.Center) {
                                                ToolGlyph(2,if(state.permitted) scheme.primary else scheme.outline,Modifier.size(22.dp))
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(state.connection, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                                if(state.uid != null && state.permitted) Text(if(state.uid == 2000) "ADB権限で利用可能" else "root権限で利用可能", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                                            }
                                            if(!state.permitted) TextButton(onClick = { selectTab(2) }) { Text("設定") }
                                        }
                                    }
                                    if(page < 2 && !state.permitted) Surface(
                                        color = scheme.secondaryContainer,
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("Shizukuへの接続が必要です", fontWeight = FontWeight.SemiBold, color = scheme.onSecondaryContainer)
                                                Text(state.connection, fontSize = 12.sp, color = scheme.onSecondaryContainer)
                                            }
                                            Button(onClick = { selectTab(2) }, contentPadding = PaddingValues(horizontal = 14.dp),
                                                modifier = Modifier.testTag("connection-setup-action")) { Text("接続を設定") }
                                        }
                                    }
                                    when(page) {
                                        0 -> BatteryPage(state)
                                        1 -> if(!state.isSamsungDevice) {
                                            Group {
                                                Text("Galaxy向けの機能です", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                                                Text("Samsung製以外の端末では、カメラの設定変更は利用できません。", color = scheme.onSurfaceVariant)
                                            }
                                        } else {
                                            Group {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text("シャッター音を強制", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                                                        Text(when(state.shutter) { true -> "強制設定が有効です"; false -> "強制設定は無効です"; null -> "未対応、または現在値を取得できません" }, color = scheme.onSurfaceVariant, fontSize = 13.sp)
                                                    }
                                                    Switch(checked = state.shutter == true, onCheckedChange = model::setShutter,
                                                        colors = SwitchDefaults.colors(),
                                                        enabled = state.permitted && state.shutter != null && state.operation?.finished != false,
                                                        modifier = Modifier.semantics { contentDescription = "シャッター音の強制設定" })
                                                }
                                                HorizontalDivider(color = scheme.surfaceVariant)
                                                Text("Samsung独自のシステム設定です。OFFは強制を解除する設定値です。撮影時の音はカメラアプリ・地域・マナーモード等にも依存します。", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                                                OutlinedButton(onClick = { model.refreshPage(page) }, enabled = state.permitted && state.operation?.finished != false, modifier = Modifier.fillMaxWidth()) { Text("現在値を再取得") }
                                            }
                                            Text("変更後は端末から設定値を読み直して確認します。実際に撮影音が変化したかは、カメラで別途確認してください。", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                                        }
                                        3 -> InformationPage(onSetup = onSetup, onLicense = { licenseDetails = true })
                                        2 -> {
                                            Text("はじめてのセットアップ", Modifier.padding(start=20.dp, top=8.dp), fontSize = 14.sp,
                                                color = scheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                            ShizukuSetupContent(state, setupActions, model::requestPermission, model::refreshConnection)
                                            Text(stringResource(R.string.unofficial_notice), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                                        }
                                    }
                                    if(page == 1) GalaxyUpdateCard()
                                }
                            }
                        }
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent,scheme.background.copy(alpha=.92f),scheme.background)))) {
                    Box(Modifier.padding(top=24.dp)) { FloatingTabs(tab, ::selectTab) }
                }
            }
        }
        state.operation?.takeIf { it.kind != OperationKind.REFRESH }?.let { operation ->
            var show by remember(operation.title) { mutableStateOf(false) }
            LaunchedEffect(operation.finished) { if(operation.finished) show = true else { delay(180); show = true } }
            if(show) Dialog(onDismissRequest = model::dismiss) {
                Surface(shape = RoundedCornerShape(32.dp), color = scheme.surfaceContainerHigh) {
                    Column(Modifier.heightIn(max=580.dp).verticalScroll(rememberScrollState()).padding(28.dp).animateContentSize(), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(Modifier.size(58.dp).background((if(operation.finished && !operation.success) scheme.error else scheme.primary).copy(alpha=.12f),CircleShape),contentAlignment=Alignment.Center) {
                            if(!operation.finished) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                            else Text(if(operation.success) "✓" else "!",color=if(operation.success) scheme.primary else scheme.error,fontSize=28.sp,fontWeight=FontWeight.Medium)
                        }
                        Text(if(!operation.finished) operation.title else if(operation.success) "完了しました" else "処理できませんでした", fontSize = 21.sp, fontWeight = FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        if(operation.finished && operation.success) Text(operation.title,fontSize=14.sp,color=scheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        Text(operation.stage, fontSize = 14.sp,color=scheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        if(!operation.finished || operation.success) OperationSteps(operation)
                        if(operation.finished) {
                            TextButton(onClick = { details = true }) { Text("詳細ログ") }
                            Button(onClick = model::dismiss, modifier = Modifier.fillMaxWidth().height(48.dp),colors=ButtonDefaults.buttonColors(containerColor=scheme.primaryContainer,contentColor=scheme.primary)) { Text("閉じる",fontWeight=FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
        if(licenseDetails) AlertDialog(onDismissRequest = { licenseDetails = false }, title = { Text("Shizukuのライセンス") }, text = {
            SelectionContainer { Text(license, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), fontSize = 12.sp) }
        }, confirmButton = { TextButton(onClick = { licenseDetails = false }) { Text("閉じる") } })
        if(details) AlertDialog(onDismissRequest = { details = false }, title = { Text("実行ログ") }, text = {
            SelectionContainer { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("OneTools ${BuildConfig.VERSION_NAME}\nShizuku API 13.1.5 · MIT\nRikkaApps/Shizuku-API\n\nログはメモリ内のみ保持されます。Samsung独自値の意味が不明な場合は劣化率に換算しません。\n\n" + state.log + "\n\n" + license, fontSize = 11.sp)
            } }
        }, confirmButton = { TextButton(onClick = { details = false }) { Text("閉じる") } })
    }
}
@Composable internal fun Group(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
