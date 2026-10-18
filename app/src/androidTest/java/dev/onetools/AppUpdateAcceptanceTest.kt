package dev.onetools

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppUpdateAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun updateDownloadCancelRetryAndInstallPermissionInBothThemes() {
        val release = UpdateRelease(BuildConfig.VERSION_CODE + 1L, "1.9.1", 30,
            "$UPDATE_RELEASES_URL/tag/v1.9.1", "更新内容", UpdateAsset("OneTools-1.9.1.apk", "", 24_000_000, "a".repeat(64)))
        var state by mutableStateOf(AppUpdateState())
        var allowed by mutableStateOf(false)
        var checks = 0; var downloads = 0; var installs = 0; var permissions = 0; var cancelled = 0
        for (dark in listOf(false, true)) {
            ui.runOnUiThread {
                state = AppUpdateState()
                allowed = false
                ui.activity.setContent {
                    MaterialTheme(colorScheme = oneToolsColorScheme(dark)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            AppUpdateContent(state, allowed, false,
                                onCheck = { checks++ }, onDownload = { downloads++ }, onInstall = { installs++ },
                                onAllow = { permissions++ }, onCancel = { cancelled++ }, onOpenPage = {})
                        }
                    }
                }
            }
            ui.onNodeWithText("アップデートを確認").performScrollTo().performClick()
            ui.runOnUiThread { state = AppUpdateState(busy = true, downloading = true, progress = .4f, release = release) }
            ui.onNodeWithTag("app-update-check").assertIsNotEnabled()
            ui.onNodeWithText("40%").assertExists()
            ui.onNodeWithText("キャンセル").performScrollTo().performClick()
            ui.runOnUiThread { state = AppUpdateState(error = true, message = "通信に失敗しました", release = release) }
            ui.onNodeWithText("通信に失敗しました").assertExists()
            ui.onNodeWithText("1.9.1 をダウンロード", substring = true).performScrollTo().performClick()
            ui.runOnUiThread { state = state.copy(ready = true, error = false) }
            ui.onNodeWithText("インストールを許可").performScrollTo().performClick()
            ui.onNodeWithText("インストール").assertDoesNotExist()
            ui.runOnUiThread { allowed = true }
            ui.onNodeWithText("インストール").performScrollTo().performClick()
        }
        assertEquals(2, checks); assertEquals(2, downloads); assertEquals(2, installs)
        assertEquals(2, permissions); assertEquals(2, cancelled)
    }

    @Test fun realGithubMetadataCanBeReadWithoutSendingDeviceInformation() {
        val latest = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { GithubUpdateClient().latest() }
        assertTrue(latest.versionCode >= 17)
        assertEquals("$UPDATE_RELEASES_URL/tag/v${latest.versionName}", latest.pageUrl)
        assertTrue(latest.apk.sha256.matches(Regex("[a-f0-9]{64}")))
    }

    @Test fun realPublishedApkDownloadsAndHasExpectedPackageVersionAndCertificate() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.cacheDir, "github-update-acceptance")
        try {
            val latest = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val client = GithubUpdateClient()
                val release = client.latest()
                val apk = client.download(release, directory) { }
                @Suppress("DEPRECATION")
                val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)!!
                val signers = archive.signingInfo!!.apkContentsSigners.map { it.toByteArray().sha256().hex() }.toSet()
                UpdateMetadata.verifyIdentity(archive.packageName, archive.longVersionCode, signers,
                    release, release.versionCode - 1, setOf(RELEASE_CERTIFICATE_SHA256))
                assertEquals(release.versionName, archive.versionName)
                assertFalse(java.io.File(directory, "update.part").exists())
                release
            }
            assertTrue(latest.apk.size > 0)
        } finally {
            java.io.File(directory, "update.part").delete()
            java.io.File(directory, "update.apk").delete()
            directory.delete()
        }
    }

    @Test fun actualUpdateCardChecksGithubAndKeepsTheResultAcrossRecreation() {
        val model = androidx.lifecycle.ViewModelProvider(ui.activity)[AppUpdateModel::class.java]
        ui.runOnUiThread {
            ui.activity.setContent {
                MaterialTheme(colorScheme = oneToolsColorScheme(false)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { AppUpdateCard(model) }
                }
            }
        }
        ui.onNodeWithTag("app-update-check").performScrollTo().performClick()
        ui.waitUntil(90_000) { !model.state.value.busy && model.state.value.release != null || model.state.value.error }
        assertFalse(model.state.value.message, model.state.value.error)
        assertTrue(model.state.value.release!!.versionCode >= 17)
        ui.onNodeWithTag("app-update-status").assertExists()
        ui.activityRule.scenario.recreate()
        assertNotNull(androidx.lifecycle.ViewModelProvider(ui.activity)[AppUpdateModel::class.java].state.value.release)
    }
}
