package dev.onetools

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/** Synthetic values only. No camera writes, permission changes, or production preferences. */
class UiImprovementsAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(ui.activity.filesDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun detailsAreOptionalAndExplainable() {
        ui.runOnUiThread { ui.activity.setContent { SampleBattery(dark = false) } }
        ui.onNodeWithText("各項目を長押しすると、意味や表示方法を確認できます。").assertExists()
        ui.onNodeWithText("記録上の最大温度").assertDoesNotExist()
        capture("ui-improvements-light.png")
        ui.onNodeWithTag("battery-details-toggle").performScrollTo().performClick()
        ui.onNodeWithText("記録上の最大温度").assertExists()
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
        ui.runOnUiThread { ui.activity.setContent { SampleBattery(dark = true) } }
        capture("ui-improvements-dark.png")
        ui.onNodeWithTag("battery-details-toggle").performScrollTo().performClick()
        ui.onNodeWithText("充電上限の設定").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("報告上の設計容量").performScrollTo().assertIsDisplayed()
    }
}

class BatteryDetailsRestorationTest {
    @get:Rule val ui = createComposeRule()

    @Test fun expandedStateSurvivesRestoration() {
        val restoration = StateRestorationTester(ui)
        restoration.setContent { SampleBattery(dark = false) }
        ui.onNodeWithTag("battery-details-toggle").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("battery-details-toggle").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "展開中"))
        ui.onNodeWithText("報告上の設計容量").performScrollTo().assertIsDisplayed()
    }
}

@Composable private fun SampleBattery(dark: Boolean) {
    val sample = ToolsState(isSamsungDevice = true, permitted = true, updated = "12:00",
        battery = BatterySnapshot.parse("level: 75\nscale: 100\ntemperature: 300\nvoltage: 4000\nstatus: 3\nhealth: 2\nmSavedBatteryBsoh: [100]\nmSavedBatteryAsoc: [95]"),
        batteryHealth = BatteryHealthSnapshot(sohPercent = 98), estimate = BatteryEstimate(6 * 60 * 60 * 1000L))
    MaterialTheme(colorScheme = oneToolsColorScheme(dark)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) { BatteryPage(sample) }
    }
}
