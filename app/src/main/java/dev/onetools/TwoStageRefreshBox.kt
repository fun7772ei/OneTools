package dev.onetools

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** A gesture that starts below the top or with a collapsed heading cannot reload. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TwoStageRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    state: PullToRefreshState,
    headerState: TopAppBarState,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    indicator: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var refreshAllowed by remember { mutableStateOf(false) }
    Box(modifier
        .pointerInput(headerState, scrollState) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // Latch at finger-down, never when the heading expands mid-gesture.
                // Keep this value through release/fling, where refresh is triggered.
                refreshAllowed = headerState.heightOffset >= -0.5f && scrollState.value == 0
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            }
        }
        .pullToRefresh(isRefreshing = isRefreshing, state = state, enabled = refreshAllowed,
            onRefresh = { if (refreshAllowed) onRefresh() })
    ) {
        content()
        indicator()
    }
}
