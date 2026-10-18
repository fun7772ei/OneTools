package dev.onetools

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Rule
import org.junit.Test

class SetupStateRestorationTest {
    @get:Rule val ui = createComposeRule()

    @Test fun currentStepSurvivesStateRestoration() {
        val restoration = StateRestorationTester(ui)
        restoration.setContent { SetupScreen(ToolsState(), {}) { Text("接続の案内") } }
        ui.onNodeWithTag("setup-next").performClick()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("データと使い方").assertIsDisplayed()
        ui.onNodeWithTag("setup-next").performClick()
        ui.onNodeWithText("Shizukuの準備").assertIsDisplayed()
    }
}
