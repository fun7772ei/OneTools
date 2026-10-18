package dev.onetools

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SetupAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        val inst = InstrumentationRegistry.getInstrumentation()
        ui.waitForIdle()
        val bitmap = inst.uiAutomation.takeScreenshot()
        try { File(inst.targetContext.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun introductionBackNavigationAndDeferredConnectionInBothThemes() {
        for (dark in listOf(false, true)) {
            var finished = 0
            ui.runOnUiThread { ui.activity.setContent {
                key(dark) { SetupScreen(ToolsState(isSamsungDevice = true), { finished++ }, dark = dark) { Text("接続の案内") } }
            } }
            ui.onNodeWithText("OneToolsへようこそ").assertIsDisplayed()
            capture("setup-welcome-$dark.png")
            ui.onNodeWithTag("setup-next").performClick()
            ui.onNodeWithText("データと使い方").assertIsDisplayed()
            ui.onNodeWithText("戻る").performClick()
            ui.onNodeWithText("OneToolsへようこそ").assertIsDisplayed()
            ui.onNodeWithTag("setup-next").performClick()
            capture("setup-data-$dark.png")
            ui.onNodeWithTag("setup-next").performClick()
            ui.onNodeWithText("Shizukuの準備").assertIsDisplayed()
            ui.onNodeWithText("あとで接続してはじめる").performClick()
            ui.runOnIdle { assertEquals(1, finished) }
        }
    }

    @Test fun completionPersistsAndInformationCanReopenIntroductionWithoutResettingPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "setup_acceptance_${System.nanoTime()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val store = SetupPreferences(prefs)
        try {
            assertFalse(store.completed)
            val model = ViewModelProvider(ui.activity)[ToolsModel::class.java]
            ui.runOnUiThread { ui.activity.setContent { OneToolsApp(model, store) } }
            ui.onNodeWithTag("setup-next").performClick()
            ui.onNodeWithText("データと使い方").assertIsDisplayed()
            ui.onNodeWithTag("setup-next").performClick()
            ui.onNodeWithText("Shizukuの準備").assertIsDisplayed()
            capture("setup-connection.png")
            ui.onNodeWithTag("setup-next").performClick()
            ui.onNodeWithTag("initial-setup").assertDoesNotExist()
            assertTrue(SetupPreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE)).completed)
            ui.runOnUiThread { ui.activity.setContent { OneToolsApp(model, SetupPreferences(prefs)) } }
            ui.onNodeWithTag("initial-setup").assertDoesNotExist()
            ui.waitUntil(30_000) { !model.state.value.permitted || (model.state.value.battery != null && model.state.value.operation == null) }
            ui.onAllNodesWithText("情報").onLast().performClick()
            val reopen = ui.onNodeWithText("初期セットアップを表示")
            reopen.performScrollTo()
            val bounds = reopen.fetchSemanticsNode().boundsInRoot
            val offset = bounds.center.y - ui.onRoot().fetchSemanticsNode().size.height * .5f
            ui.onNode(hasVerticalScroll()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, offset) }
            reopen.assertIsDisplayed().performClick()
            capture("setup-reopened.png")
            ui.onNodeWithText("OneToolsへようこそ").assertIsDisplayed()
            ui.onNodeWithText("案内を閉じる").performClick()
            ui.onNodeWithTag("initial-setup").assertDoesNotExist()
            assertTrue(store.completed)
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun permissionReadyStateHasStartAction() {
        var finished = false
        ui.runOnUiThread { ui.activity.setContent {
            SetupScreen(ToolsState(permitted = true, isSamsungDevice = true), { finished = true }) { Text("許可済み") }
        } }
        repeat(2) { ui.onNodeWithTag("setup-next").performClick() }
        ui.onNodeWithText("OneToolsをはじめる").assertIsDisplayed().performClick()
        ui.runOnIdle { assertTrue(finished) }
    }
}
