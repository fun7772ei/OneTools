package dev.onetools

import android.os.Handler
import android.graphics.Bitmap
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.test.*
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Collections

/** Identical read-only interaction workload for before/after frame measurements. */
class MotionAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(ui.activity)[ToolsModel::class.java]

    private fun capture(name: String) {
        ui.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { File(ui.activity.filesDir, "motion-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        } } finally { bitmap.recycle() }
    }

    @Test fun popupAnimatesOutAndCanReopenDuringExitInBothThemes() {
        var operation by mutableStateOf<Operation?>(null)
        for (dark in listOf(false, true)) {
            ui.runOnUiThread { ui.activity.setContent {
                MaterialTheme(colorScheme = oneToolsColorScheme(dark)) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().padding(top = 40.dp, end = 12.dp), contentAlignment = Alignment.TopEnd) {
                            RefreshAction(false, operation, {}, { operation = null })
                        }
                    }
                }
            } }
            ui.mainClock.autoAdvance = false
            ui.runOnIdle { operation = Operation("情報を更新", "バッテリー情報を取得しています") }
            ui.mainClock.advanceTimeBy(32)
            capture("$dark-enter-032")
            ui.mainClock.advanceTimeBy(64)
            capture("$dark-enter-096")
            ui.mainClock.advanceTimeBy(200)
            ui.onNodeWithTag("refresh-progress").assertIsDisplayed()
            capture("$dark-visible")
            ui.runOnIdle { operation = operation!!.copy(finished = true, success = true,
                stage = "結果を確認しました", phase = OperationPhase.VERIFICATION) }
            ui.mainClock.advanceTimeBy(240)
            ui.onNodeWithText("更新しました").assertIsDisplayed()
            capture("$dark-result")
            ui.onNodeWithText("閉じる").performClick()
            // Commit the visibility change before measuring the exit's elapsed time.
            repeat(2) { ui.mainClock.advanceTimeByFrame(); ui.waitForIdle() }
            ui.mainClock.advanceTimeBy(64)
            ui.onNodeWithTag("refresh-progress").assertExists()
            capture("$dark-exit-064")
            // A new refresh during exit must reverse smoothly, without dropping the popup.
            ui.runOnIdle { operation = Operation("情報を更新", "再度取得しています", id = 2) }
            ui.mainClock.advanceTimeBy(300)
            ui.onNodeWithText("再度取得しています").assertIsDisplayed()
            ui.runOnIdle { operation = Operation("情報を更新", "接続を確認してください", true,
                success = false, phase = OperationPhase.CONNECTION, id = 2) }
            ui.mainClock.advanceTimeBy(240)
            ui.onNodeWithText("更新できませんでした").assertIsDisplayed()
            capture("$dark-error")
            ui.onNodeWithText("閉じる").performClick()
            ui.mainClock.advanceTimeBy(300)
            ui.onNodeWithTag("refresh-progress").assertDoesNotExist()
            capture("$dark-hidden")
            ui.mainClock.autoAdvance = true
        }
    }

    @Test fun measureRefreshScrollAndNavigation() {
        ui.showMainScreen()
        ui.waitUntil(30_000) { model().state.value.battery != null && model().state.value.operation?.finished != false }
        ui.runOnIdle { model().dismiss() }
        ui.waitForIdle()
        val original = model().state.value.shutter
        val samples = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
        val worker = HandlerThread("motion-frames").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, _ ->
            samples.add(frame.getMetric(FrameMetrics.TOTAL_DURATION) to frame.getMetric(FrameMetrics.DEADLINE))
        }
        ui.runOnUiThread { ui.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper)) }
        try {
            repeat(3) {
                ui.onNodeWithTag("refresh-page-0").performTouchInput { swipeUp(durationMillis = 450) }
                ui.onNodeWithTag("refresh-page-0").performTouchInput { swipeDown(durationMillis = 450) }
                for (index in listOf(1, 2, 3, 2, 1, 0)) {
                    val label = listOf("バッテリー", "ツール", "接続", "情報")[index]
                    ui.onNode(hasText(label) and SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.Role,
                        androidx.compose.ui.semantics.Role.Tab)).performClick()
                }
                ui.onNodeWithTag("refresh-button").performClick()
                ui.waitUntil(30_000) { model().state.value.operation == null }
                ui.waitForIdle()
            }
        } finally {
            ui.runOnUiThread { ui.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            worker.quitSafely(); worker.join()
        }
        val frames = samples.toList()
        assertTrue("Must capture rendered frames", frames.size > 100)
        assertEquals(original, model().state.value.shutter)
        assertFalse(model().state.value.log.contains("settings --user current put"))
        val sorted = frames.map { it.first / 1_000_000.0 }.sorted()
        val label = InstrumentationRegistry.getArguments().getString("motionLabel") ?: "current"
        File(ui.activity.filesDir, "motion-$label.txt").writeText(
            "frames=${frames.size}\nmissedDeadline=${frames.count { it.second > 0 && it.first > it.second }}\n" +
                "p50Ms=${sorted[sorted.size / 2]}\np95Ms=${sorted[(sorted.size * .95).toInt()]}\nmaxMs=${sorted.last()}\n" +
                "workload=3 x scroll up/down, six tab clicks, real Shizuku refresh\n")
    }
}
