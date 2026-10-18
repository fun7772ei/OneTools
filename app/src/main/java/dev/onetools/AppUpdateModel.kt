package dev.onetools

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.SocketTimeoutException

internal data class AppUpdateState(
    val busy: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val message: String = "GitHubで公開されている最新版を確認できます。",
    val error: Boolean = false,
    val release: UpdateRelease? = null,
    val ready: Boolean = false
)

internal class AppUpdateModel(application: Application) : AndroidViewModel(application) {
    private val client = GithubUpdateClient()
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null
    private var downloaded: File? = null
    private val app get() = getApplication<Application>()

    fun check() = runWork {
        downloaded = null
        mutableState.value = AppUpdateState(busy = true, message = "GitHubで最新版を確認しています…")
        val release = withContext(Dispatchers.IO) { client.latest() }
        val message = when {
            release.versionCode < BuildConfig.VERSION_CODE -> "公開版は ${release.versionName} です。この端末にはそれより新しいバージョンが入っています。"
            release.versionCode == BuildConfig.VERSION_CODE.toLong() -> "最新バージョンです（${release.versionName}）。"
            release.minSdk > Build.VERSION.SDK_INT -> "新しいバージョン ${release.versionName} は、このAndroidでは利用できません。"
            BuildConfig.DEBUG -> "新しいバージョン ${release.versionName} があります。Debug版は署名が異なるため、公開版への上書き更新はできません。"
            else -> "新しいバージョン ${release.versionName} があります。"
        }
        mutableState.value = AppUpdateState(release = release, message = message)
    }

    fun download() {
        val release = mutableState.value.release ?: return
        if (BuildConfig.DEBUG || release.versionCode <= BuildConfig.VERSION_CODE || release.minSdk > Build.VERSION.SDK_INT) return
        runWork {
            downloaded = null
            mutableState.update { it.copy(busy = true, downloading = true, ready = false, progress = 0f, error = false, message = "APKをダウンロードしています…") }
            val file = withContext(Dispatchers.IO) {
                var lastPercent = -1
                client.download(release, File(app.cacheDir, "updates")) { progress ->
                    val percent = (progress * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        mutableState.update { it.copy(progress = progress) }
                    }
                }.also { verify(it, release) }
            }
            downloaded = file
            mutableState.update { it.copy(busy = false, downloading = false, ready = true, message = "ダウンロードと検証が完了しました。Androidの確認画面で更新してください。") }
        }
    }

    fun install() {
        val release = mutableState.value.release ?: return
        val file = downloaded ?: return
        runWork {
            mutableState.update { it.copy(busy = true, error = false) }
            withContext(Dispatchers.IO) { verify(file, release) }
            if (!app.packageManager.canRequestPackageInstalls()) {
                mutableState.update { it.copy(busy = false, message = "インストールを許可してから、もう一度「インストール」を押してください。") }
                return@runWork
            }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            mutableState.update { it.copy(busy = false, message = "Androidの画面で更新を確定してください。キャンセルした場合は、もう一度インストールできます。") }
        }
    }

    fun allowInstall() {
        runCatching {
            app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${app.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { mutableState.update { it.copy(error = true, message = "設定画面を開けませんでした。端末の設定からOneToolsのインストール許可をご確認ください。") } }
    }

    fun cancel() { work?.cancel() }

    private fun runWork(block: suspend () -> Unit) {
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) {
                mutableState.update { it.copy(busy = false, downloading = false, message = "更新操作をキャンセルしました。") }
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(busy = false, downloading = false, error = true, message = when (failure) {
                    is UpdateFailure -> failure.message ?: "更新情報を確認できませんでした。"
                    is SocketTimeoutException -> "通信がタイムアウトしました。接続を確認して、もう一度お試しください。"
                    else -> "更新操作を完了できませんでした。接続状況を確認するか、公開ページをご確認ください。"
                }) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun verify(file: File, release: UpdateRelease) {
        try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            UpdateMetadata.verifyBytes(release.apk, file.length(), digest.digest())
            val manager = app.packageManager
            val archive = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: throw UpdateFailure("APKの内容を確認できませんでした。")
            val installed = manager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val archiveSigners = archive.signingInfo?.apkContentsSigners?.map { it.toByteArray().sha256().hex() }?.toSet().orEmpty()
            val installedSigners = installed.signingInfo?.apkContentsSigners?.map { it.toByteArray().sha256().hex() }?.toSet().orEmpty()
            UpdateMetadata.verifyIdentity(archive.packageName, archive.longVersionCode, archiveSigners, release, installed.longVersionCode, installedSigners)
            if (archive.versionName != release.versionName || (archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT) {
                throw UpdateFailure("APKのバージョンや対応Androidが更新情報と一致しません。")
            }
        } catch (failure: Exception) {
            downloaded = null
            mutableState.update { it.copy(ready = false) }
            file.delete()
            throw failure
        }
    }
}
