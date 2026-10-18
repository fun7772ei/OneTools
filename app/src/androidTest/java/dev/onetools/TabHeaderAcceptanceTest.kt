package dev.onetools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Navigation only; never changes camera settings or onboarding preferences. */
class TabHeaderAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private val labels = listOf("バッテリー", "ツール", "接続", "情報")
    private fun tab(index: Int) = ui.onNode(hasText(labels[index]) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab))
    private fun headerHeight() = ui.onNodeWithTag("oneui-header").fetchSemanticsNode().boundsInRoot.height
    private fun assertHeight(expected: Float) {
        assertEquals("Tab navigation must preserve the header height", expected, headerHeight(), 1f)
    }
    private fun swipeTo(index: Int, left: Boolean) {
        ui.onNodeWithTag("tab-pager").performTouchInput {
            // Include a small downward drift, as in a natural horizontal gesture.
            val startX = width * if (left) .85f else .15f
            val endX = width * if (left) .15f else .85f
            swipe(Offset(startX, height * .4f), Offset(endX, height * .4f + 24f), 400)
        }
        tab(index).assertIsSelected()
    }

    @Test fun horizontalSwipesAndTabTapsPreserveCollapsedAndExpandedHeader() {
        ui.showMainScreen()
        tab(3).performClick()
        val expanded = headerHeight()
        ui.onNodeWithTag("tab-pager").performTouchInput { swipeUp(durationMillis = 600) }
        val collapsed = headerHeight()
        assertTrue("Vertical scrolling must still collapse the header", collapsed < expanded - 100f)

        for (index in 2 downTo 0) {
            swipeTo(index, left = false)
            assertHeight(collapsed)
        }
        for (index in 1..3) {
            swipeTo(index, left = true)
            assertHeight(collapsed)
        }
        for (index in listOf(0, 2, 1, 3)) {
            tab(index).performClick()
            assertHeight(collapsed)
        }

        // A deliberate vertical gesture must still expand the heading.
        ui.onNode(hasVerticalScroll() and hasAnyAncestor(hasTestTag("refresh-page-3")))
            .performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, -100_000f)
        }
        ui.onNodeWithTag("tab-pager").performTouchInput { swipeDown(durationMillis = 600) }
        assertHeight(expanded)
        for (index in 2 downTo 0) {
            swipeTo(index, left = false)
            assertHeight(expanded)
        }
    }
}
