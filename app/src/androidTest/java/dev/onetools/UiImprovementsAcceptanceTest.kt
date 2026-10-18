package dev.onetools

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Rule
import org.junit.Test

/** Synthetic values only. No camera writes, permission changes, or production preferences. */
class UiImprovementsAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun detailsAreOptionalAndExpandedStateSurvivesRestoration() {
        val restoration = StateRestorationTester(ui)
        restoration.setContent {
            MaterialTheme(colorScheme = oneToolsColorScheme(false)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BatteryPage(ToolsState(isSamsungDevice = true))
                }
            }
        }
        ui.onNodeWithText("各項目を長押しすると、意味や表示方法を確認できます。").assertExists()
        ui.onNodeWithText("記録上の最大温度").assertDoesNotExist()
        ui.onNodeWithTag("battery-details-toggle").performScrollTo().performClick()
        ui.onNodeWithText("記録上の最大温度").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("battery-details-toggle").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "展開中"))
        ui.onNodeWithText("報告上の設計容量").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("battery-details-toggle").performScrollTo().performClick()
        ui.onNodeWithText("記録上の最大温度").assertDoesNotExist()
        ui.onNodeWithText("温度").performScrollTo().performTouchInput { longClick() }
        ui.onNodeWithText("閉じる").assertIsDisplayed().performClick()
    }

    @Test fun disconnectedUserCanOpenSetupFromBatteryAndTools() {
        ui.showMainScreen()
        ui.onNodeWithTag("connection-setup-action").performScrollTo().performClick()
        ui.onNodeWithText("はじめてのセットアップ").assertExists()
        ui.onNode(hasText("ツール") and SemanticsMatcher.expectValue(SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.Tab)).performClick()
        ui.onNodeWithTag("connection-setup-action").performScrollTo().performClick()
        ui.onNodeWithText("はじめてのセットアップ").assertExists()
    }
}
