package dev.onetools

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Gesture + real Shizuku acceptance. Never stops Shizuku or changes camera/device settings. */
class RefreshNavigationAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @org.junit.Before fun showFeatureUnderTest() { ui.showMainScreen() }

    private fun model() = ViewModelProvider(ui.activity)[ToolsModel::class.java]
    private fun tab(label: String) = ui.onNode(hasText(label) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab))
    private fun waitFinished(id: Long? = null) {
        ui.waitUntil(30_000) {
            val op = model().state.value.operation
            op?.finished == true && (id == null || op.id > id)
        }
        assertTrue(model().state.value.operation.toString(), model().state.value.operation?.success == true)
        assertEquals(OperationPhase.VERIFICATION, model().state.value.operation?.phase)
    }
    private fun settle() {
        ui.waitUntil(10_000) { model().state.value.operation == null }
        ui.onNodeWithTag("refresh-progress").assertDoesNotExist()
        ui.onNodeWithTag("refresh-button").assertIsEnabled()
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val bitmap = inst.uiAutomation.takeScreenshot()
        try { File(inst.targetContext.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { bitmap.recycle() }
    }

    @Test fun firstPullExpandsHeadingAndOnlyANewPullRefreshesEveryPage() {
        waitFinished(); settle()
        val originalShutter = model().state.value.shutter
        val expanded = ui.onNodeWithTag("oneui-header").fetchSemanticsNode().boundsInRoot.height
        for (index in 0..3) {
            tab(listOf("バッテリー", "ツール", "接続", "情報")[index]).performClick()
            ui.onNodeWithTag("refresh-page-$index").performTouchInput { swipeUp(durationMillis = 600) }
            ui.onNode(hasVerticalScroll() and hasAnyAncestor(hasTestTag("refresh-page-$index")))
                .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
            assertTrue(ui.onNodeWithTag("oneui-header").fetchSemanticsNode().boundsInRoot.height < expanded - 100f)
            val previousUpdate = model().state.value.updated
            val previousLog = model().state.value.log
            // Long enough to go beyond full expansion in the same gesture.
            ui.onNodeWithTag("refresh-page-$index").performTouchInput {
                swipe(Offset(width * .06f, height * .05f), Offset(width * .06f, height * .95f), 900)
            }
            assertEquals(expanded, ui.onNodeWithTag("oneui-header").fetchSemanticsNode().boundsInRoot.height, 1f)
            assertNull("Expansion must not start a refresh", model().state.value.operation)
            assertEquals(previousUpdate, model().state.value.updated)
            assertEquals(previousLog, model().state.value.log)
            ui.onNodeWithTag("refresh-page-$index").performTouchInput {
                swipe(Offset(width * .06f, height * .05f), Offset(width * .06f, height * .95f), 900)
            }
            waitFinished()
            settle()
        }
        assertEquals(originalShutter, model().state.value.shutter)
        assertFalse(model().state.value.log.contains("settings --user current put"))
    }

    @Test fun pullRefreshEveryPageAndSwipeInBottomTabOrderWithoutModalConfirmation() {
        waitFinished()
        val originalShutter = model().state.value.shutter
        settle()
        val labels = listOf("バッテリー", "ツール", "接続", "情報")
        for(index in 1..3) {
            ui.onNodeWithTag("tab-pager").performTouchInput { swipeLeft() }
            tab(labels[index]).assertIsSelected()
            ui.onNodeWithTag("refresh-page-$index").assertIsDisplayed()
        }
        ui.onNodeWithTag("tab-pager").performTouchInput { swipeLeft() }
        tab("情報").assertIsSelected() // No wrap from last to first.
        for(index in 2 downTo 0) {
            ui.onNodeWithTag("tab-pager").performTouchInput { swipeRight() }
            tab(labels[index]).assertIsSelected()
        }
        ui.onNodeWithTag("tab-pager").performTouchInput { swipeRight() }
        tab("バッテリー").assertIsSelected()

        // Pulling while still below the beginning must scroll, not reload.
        ui.onNode(hasVerticalScroll()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1400f) }
        val oldUpdated = model().state.value.updated
        ui.onNodeWithTag("refresh-page-0").performTouchInput {
            swipe(Offset(width * .06f, height * .25f), Offset(width * .06f, height * .4f), 500)
        }
        assertNull(model().state.value.operation)
        assertEquals(oldUpdated, model().state.value.updated)
        ui.onNode(hasVerticalScroll()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }

        for(index in 0..3) {
            tab(labels[index]).performClick()
            val beforeId = model().state.value.operation?.id ?: 0L
            val beforeLog = model().state.value.log
            ui.onNodeWithTag("refresh-page-$index").performTouchInput {
                swipe(Offset(width * .06f, height * .1f), Offset(width * .06f, height * .8f), 700)
            }
            waitFinished(beforeId)
            ui.onNodeWithTag("refresh-progress").assertIsDisplayed()
            for(label in listOf("接続確認", "実行", "結果確認")) ui.onNodeWithText(label).assertIsDisplayed()
            ui.onNodeWithText("完了しました").assertDoesNotExist()
            ui.onNodeWithText("詳細ログ").assertDoesNotExist()
            if(index >= 2) assertEquals("Connection/info refresh must not run battery shell commands", beforeLog, model().state.value.log)
            capture("refresh-page-$index.png")
            if(index < 3) {
                tab(labels[index+1]).performClick()
                tab(labels[index+1]).assertIsSelected()
                tab(labels[index]).performClick()
                tab(labels[index]).assertIsSelected()
            }
            settle()
        }
        // The icon button uses the same path and result feedback, in both color schemes.
        for(dark in listOf(true, false)) {
            ui.runOnUiThread { ui.activity.setContent { OneTools(model(), themeOverride = dark) } }
            tab("バッテリー").performClick()
            ui.onNodeWithTag("refresh-button").performClick()
            waitFinished()
            capture("refresh-bubble-${if(dark) "dark" else "light"}.png")
            settle()
        }
        assertEquals(originalShutter, model().state.value.shutter)
        assertFalse(model().state.value.log.contains("settings --user current put"))
        assertEquals(2000, model().state.value.uid)
    }

    @Test fun rapidRequestsAreSerializedAndFailureFeedbackDoesNotBlockContent() {
        waitFinished(); settle()
        val readCount = model().state.value.log.lineSequence().count { it == "$ /system/bin/dumpsys battery" }
        ui.runOnIdle { model().refresh(); model().refresh(); model().refresh() }
        waitFinished()
        assertEquals(readCount + 1, model().state.value.log.lineSequence().count { it == "$ /system/bin/dumpsys battery" })
        settle()
        // Synthetic failure presentation; does not alter actual permission or Shizuku state.
        ui.runOnUiThread { ui.activity.setContent {
            androidx.compose.material3.MaterialTheme {
                RefreshAction(false, Operation("情報を更新", "Shizukuが停止しています", true, false,
                    phase = OperationPhase.CONNECTION), {}, {})
            }
        } }
        ui.onNodeWithText("更新できませんでした").assertIsDisplayed()
        ui.onNodeWithText("Shizukuが停止しています").assertIsDisplayed()
        ui.onNodeWithText("実行").assertExists()
        capture("refresh-failure.png")
    }
}
