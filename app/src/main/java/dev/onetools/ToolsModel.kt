package dev.onetools

import android.app.Application
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.Build
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OperationKind { REFRESH, SETTING, PERMISSION }
enum class OperationPhase { CONNECTION, EXECUTION, VERIFICATION }
data class Operation(val title: String, val stage: String, val finished: Boolean = false, val success: Boolean = false,
    val kind: OperationKind = OperationKind.REFRESH, val phase: OperationPhase = OperationPhase.CONNECTION, val id: Long = 0)
data class ToolsState(
    val connection: String = "接続を確認しています", val installed: Boolean = false, val permitted: Boolean = false,
    val battery: BatterySnapshot? = null, val shutter: Boolean? = null, val updated: String = "未取得",
    val estimate: BatteryEstimate = BatteryEstimate(), val operation: Operation? = null, val log: String = "", val uid: Int? = null,
    val batteryHealth: BatteryHealthSnapshot? = null, val isSamsungDevice: Boolean = false,
    val batteryHal: BatteryHalSnapshot? = null
)

class ToolsModel(application: Application) : AndroidViewModel(application) {
    private companion object { const val MAX_LOG_CHARS = 70_000 }
    private val mutable = MutableStateFlow(ToolsState(isSamsungDevice = DeviceSupport.isSamsung(Build.MANUFACTURER)))
    val state = mutable.asStateFlow()
    private var remote: IOneToolsService? = null
    private var waiting: CompletableDeferred<IOneToolsService>? = null
    private var bound = false
    private var running = false
    private var operationId = 0L
    private var dismissJob: Job? = null
    private val args = Shizuku.UserServiceArgs(ComponentName(application, ToolService::class.java))
        .daemon(false).processNameSuffix("tools").tag("onetools-service").version(BuildConfig.VERSION_CODE)
    private fun update(block: (ToolsState) -> ToolsState) {
        // IO reads can report progress frequently. Publish UI state on the main thread,
        // preserving the order of progress, log entries, and the final result.
        if (Looper.myLooper() == Looper.getMainLooper()) mutable.update(block)
        else viewModelScope.launch(Dispatchers.Main.immediate) { mutable.update(block) }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            remote = IOneToolsService.Stub.asInterface(binder)
            remote?.let { waiting?.complete(it) }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            waiting?.completeExceptionally(IllegalStateException("権限付きサービスが切断されました"))
            update { it.copy(shutter = null) }
        }
    }
    private val received = Shizuku.OnBinderReceivedListener { refreshConnection() }
    private val dead = Shizuku.OnBinderDeadListener {
        remote = null; bound = false
        waiting?.completeExceptionally(IllegalStateException("Shizukuが停止しました"))
        refreshConnection()
    }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, result ->
        refreshConnection()
        if(result == PackageManager.PERMISSION_GRANTED) refresh()
        else update { it.copy(connection = "権限が許可されていません。Shizukuで許可してください") }
    }
    init {
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        refreshConnection()
    }
    fun refreshConnection() {
        viewModelScope.launch { checkConnection() }
    }
    private suspend fun checkConnection() = withContext(Dispatchers.IO) {
        val installed = runCatching { getApplication<Application>().packageManager.getPackageInfo("moe.shizuku.privileged.api",0) }.isSuccess
        val alive = Shizuku.pingBinder()
        val supported = alive && runCatching { !Shizuku.isPreV11() && Shizuku.getVersion() >= 12 }.getOrDefault(false)
        val permitted = supported && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        withContext(Dispatchers.Main.immediate) { update { it.copy(installed = installed, permitted = permitted, shutter = if(permitted) it.shutter else null,
            connection = when { !installed && !alive -> "Shizukuをインストールしてください"; !alive -> "Shizukuが停止しています"; !supported -> "Shizukuを最新版に更新してください"; !permitted -> "OneToolsへの権限を許可してください"; else -> "Shizuku 接続済み" }) } }
    }
    fun requestPermission() {
        runCatching {
            check(Shizuku.pingBinder()) { "Shizukuを起動してください" }
            check(Shizuku.getVersion() >= 12) { "Shizukuを更新してください" }
            if(Shizuku.shouldShowRequestPermissionRationale()) error("Shizukuの「認証済みアプリ」でOneToolsを許可してください")
            Shizuku.requestPermission(10)
        }.onFailure { e -> update { it.copy(operation = Operation("権限の要求", e.message ?: "要求できませんでした", true, kind = OperationKind.PERMISSION, id = ++operationId)) } }
    }
    private suspend fun service(): IOneToolsService {
        check(state.value.permitted && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { "Shizukuの権限がありません" }
        remote?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        val deferred = CompletableDeferred<IOneToolsService>()
        waiting = deferred
        return try {
            withContext(Dispatchers.Main) { Shizuku.bindUserService(args, connection); bound = true }
            withTimeout(12_000) { deferred.await() }
        } finally { waiting = null }
    }
    private fun appendLog(message: String) {
        update { it.copy(log = (it.log + "\n" + message).takeLast(MAX_LOG_CHARS)) }
    }
    private fun read(result: Bundle): String {
        val output = result.getString("output").orEmpty()
        appendLog("$ " + result.getString("command") + "\nexit=" + result.getInt("exit") + "\n" + output)
        check(result.getInt("exit", -1) == 0) { output.ifBlank { "コマンドが失敗しました" } }
        return output
    }
    private fun progress(phase: OperationPhase, stage: String) {
        update { it.copy(operation = it.operation?.copy(phase = phase, stage = stage)) }
    }
    private fun operation(title: String, kind: OperationKind = OperationKind.REFRESH, needsService: Boolean = true,
        action: suspend (IOneToolsService?) -> Unit) {
        if(running) return
        running = true
        dismissJob?.cancel()
        val id = ++operationId
        viewModelScope.launch {
            update { it.copy(operation = Operation(title, "接続確認", kind = kind, id = id)) }
            try {
                checkConnection()
                val service = if(needsService) service() else null
                if(service != null) {
                    val uid = withContext(Dispatchers.IO) { service.uid() }
                    check(uid == 2000 || uid == 0) { "権限付きサービスのUIDが不正です: $uid" }
                    update { it.copy(uid = uid) }
                    appendLog("Shizuku UserService UID=$uid")
                }
                progress(OperationPhase.EXECUTION, if(needsService) "コマンド実行" else "表示情報を再確認")
                withContext(Dispatchers.IO) { action(service) }
                update { it.copy(operation = it.operation?.copy(stage = "結果を確認しました", phase = OperationPhase.VERIFICATION, finished = true, success = true)) }
            } catch(e: Exception) {
                if(e is CancellationException && e !is TimeoutCancellationException) throw e
                update { it.copy(shutter = null, operation = it.operation?.copy(stage = (e.message ?: e.javaClass.simpleName).take(240), finished = true, success = false)) }
                appendLog("ERROR: $e")
                refreshConnection()
            } finally {
                running = false
                if(kind == OperationKind.REFRESH) dismissJob = viewModelScope.launch {
                    // Briefly confirm success; leave failures visible long enough to read the cause.
                    delay(if(state.value.operation?.success == true) 800 else 8_000)
                    if(state.value.operation?.id == id) dismiss()
                }
            }
        }
    }
    fun refreshPage(page: Int) {
        if(page < 2) { refresh(); return }
        operation(if(page == 2) "接続を再確認" else "アプリ情報を再確認", needsService = false) {
            checkConnection()
            progress(OperationPhase.VERIFICATION, "起動状態と権限を確認")
            if(page == 2) check(state.value.permitted) { state.value.connection }
        }
    }
    fun refresh() = operation("情報を更新") { remoteService ->
        val service = requireNotNull(remoteService)
        val battery = BatterySnapshot.parse(read(service.battery()))
        check(battery.fields.containsKey("level")) { "バッテリー情報を解析できませんでした" }
        // Each optional property is independent. Clear unavailable values on every refresh,
        // so an old successful health read is never mistaken for a new result.
        val unavailable = mutableListOf<String>()
        fun property(id: Int, label: String, validate: (Long) -> Long?): Long? {
            progress(OperationPhase.EXECUTION, "$label を取得")
            return try {
                val value = BatteryPropertyReply.parse(read(service.batteryProperty(id)))
                validate(value) ?: error("未報告または範囲外の値です")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                unavailable += label
                appendLog("追加情報 $label: ${e.message}")
                null
            }
        }
        val health = BatteryHealthSnapshot(
            sohPercent = property(10, "容量維持率（SOH）") { BatteryHealthSnapshot.validSoh(it)?.toLong() }?.toInt(),
            manufacturingSeconds = property(7, "製造日") { BatteryHealthSnapshot.validDate(it) },
            firstUsageSeconds = property(8, "初回使用日") { BatteryHealthSnapshot.validDate(it) },
            unavailable = unavailable.toList()
        )
        progress(OperationPhase.EXECUTION, "電流と容量の報告値を取得")
        val hal = try {
            BatteryHalSnapshot.parse(read(service.batteryHalInfo()))
        } catch(e: Exception) {
            if(e is CancellationException) throw e
            appendLog("電池HAL: ${e.message}")
            null
        }
        progress(OperationPhase.EXECUTION, "残り時間の目安を計算")
        val estimate = try {
            BatteryEstimate.calculate(read(service.dischargeStats()), battery.number("Charge counter"))
        } catch(e: Exception) {
            if(e is CancellationException) throw e
            appendLog("予測: ${e.message}")
            BatteryEstimate(reason="使用状況を取得できませんでした。更新してお試しください")
        }
        progress(OperationPhase.VERIFICATION, "結果確認")
        // An unavailable Samsung setting must not discard successfully read battery data.
        val shutter = if(state.value.isSamsungDevice) try {
            BatterySnapshot.shutter(read(service.shutter()))
        } catch(e: Exception) {
            if(e is CancellationException) throw e
            appendLog("シャッター設定: ${e.message}")
            null
        } else null
        update { it.copy(battery = battery, batteryHealth = health, batteryHal = hal, shutter = shutter, updated = SimpleDateFormat("HH:mm:ss", Locale.JAPAN).format(Date()), estimate = estimate) }
    }
    fun setShutter(enabled: Boolean) = operation("シャッター音の強制設定を変更", kind = OperationKind.SETTING) { remoteService ->
        val service = requireNotNull(remoteService)
        check(state.value.isSamsungDevice) { "この機能はGalaxy向けです" }
        // Always read before writing; unknown or missing values must never be replaced silently.
        val before = BatterySnapshot.shutter(read(service.shutter()))
        update { it.copy(shutter = before) }
        check(before != null) { "現在値が不明なため変更しませんでした" }
        var writeFailure: Exception? = null
        try { read(service.setShutter(if(enabled) 1 else 0)) } catch(e: Exception) { writeFailure = e }
        progress(OperationPhase.VERIFICATION, "設定値を再取得して確認")
        val actual = BatterySnapshot.shutter(read(service.shutter()))
        update { it.copy(shutter = actual) }
        if(writeFailure != null) throw writeFailure
        check(actual == enabled) { "設定値が一致しません。実際の値を再取得してください" }
    }
    fun dismiss() { if(state.value.operation?.finished == true) update { it.copy(operation = null) } }
    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead); Shizuku.removeRequestPermissionResultListener(permission)
        if(bound) runCatching { Shizuku.unbindUserService(args, connection, true) }
        super.onCleared()
    }
}
