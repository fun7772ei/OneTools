package dev.onetools

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Rule
import org.junit.Test

/** Synthetic values only. No camera writes, permission changes, or production preferences. */
class UiImprovementsAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private val sample = ToolsState(isSamsungDevice = true, permitted = true, updated = "12:00",
        battery = BatterySnapshot.parse("level: 75\nscale: 100\ntemperature: 300\nvoltage: 4000\nstatus: 3\nhealth: 2\nmSavedBatteryBsoh: [100]\nmSavedBatteryAsoc: [95]"),
        batteryHealth = BatteryHealthSnapshot(sohPercent = 98), estimate = BatteryEstimate(6 * 60 * 60 * 1000L))

    private fun capture(name: String) {
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(ui.activity.filesDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun detailsAreOptionalAndExpandedStateSurvivesRestoration() {
        val restoration = StateRestorationTester(ui)
        restoration.setContent {
            MaterialTheme(colorScheme = oneToolsColorScheme(false)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BatteryPage(sample)
                }
            }
        }
        ui.onNodeWithText("各項目を長押しすると、意味や表示方法を確認できます。").assertExists()
        ui.onNodeWithText("記録上の最大温度").assertDoesNotExist()
        capture("ui-improvements-light.png")
        ui.onNodeWithTag("battery-details-toggle").performScrollTo().performClick()
        ui.onNodeWithText("記録上の最大温度").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("battery-details-toggle").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "展開中"))
        ui.onNodeWithText("報告上の設計容量").performScrollTo().assertIsDisplayed()
        capture("ui-improvements-expanded.png")
        ui.onNodeWithTag("battery-details-toggle").performScrollTo().performClick()
        ui.onNodeWithText("記録上の最大温度").assertDoesNotExist()
        ui.onNodeWithText("温度").performScrollTo().performTouchInput { longClick() }
        ui.onNodeWithText("閉じる").assertIsDisplayed().performClick()
    }

    @Test fun disconnectedUserCanOpenSetupFromBatteryAndTools() {
        ui.showMainScreen()
        ui.onNode(hasTestTag("connection-setup-action") and hasAnyAncestor(hasTestTag("refresh-page-0")))
            .performScrollTo().performClick()
        ui.onNodeWithText("はじめてのセットアップ").assertExists()
        ui.onNode(hasText("ツール") and SemanticsMatcher.expectValue(SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.Tab)).performClick()
        ui.onNode(hasTestTag("connection-setup-action") and hasAnyAncestor(hasTestTag("refresh-page-1")))
            .performScrollTo().performClick()
        ui.onNodeWithText("はじめてのセットアップ").assertExists()
    }

    @Test fun darkBatteryDetailsRemainReadableAndInteractive() {
        ui.activity.runOnUiThread {
            ui.activity.setContent {
                MaterialTheme(colorScheme = oneToolsColorScheme(true)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { BatteryPage(sample) }
                }
            }
        }
        capture("ui-improvements-dark.png")
        ui.onNodeWithTag("battery-details-toggle").performScrollTo().performClick()
        ui.onNodeWithText("充電上限の設定").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("報告上の設計容量").performScrollTo().assertIsDisplayed()
    }
}
