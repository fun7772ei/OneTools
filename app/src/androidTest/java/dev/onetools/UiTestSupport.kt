package dev.onetools

import androidx.activity.compose.setContent

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction

internal fun hasVerticalScroll() = hasScrollAction() and
    SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

/** Do not write the user's onboarding preferences just to run feature tests. */
internal fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, MainActivity>.showMainScreen() {
    runOnUiThread {
        val model = androidx.lifecycle.ViewModelProvider(activity)[ToolsModel::class.java]
        activity.setContent { OneTools(model) }
    }
}
