package dev.onetools

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable internal fun RefreshAction(busy: Boolean, operation: Operation?, onRefresh: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val refreshing = operation?.finished == false
    val rotation = if(refreshing) {
        val transition = rememberInfiniteTransition(label = "refresh rotation")
        val angle = transition.animateFloat(0f, 360f,
            infiniteRepeatable(tween(900, easing = LinearEasing)), label = "refresh angle")
        angle
    } else remember { mutableFloatStateOf(0f) }
    // Keep the popup and its last result alive until the exit transition finishes.
    val visibility = remember { MutableTransitionState(false) }
    var retained by remember { mutableStateOf(operation) }
    SideEffect {
        if (operation != null) retained = operation
        visibility.targetState = operation != null
    }
    Box {
        IconButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.testTag("refresh-button").semantics {
            contentDescription = "情報を更新"
            stateDescription = when { refreshing -> "更新中"; busy -> "別の操作を実行中"; else -> "更新できます" }
        }) {
            Icon(painterResource(R.drawable.ic_refresh), contentDescription = null,
                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation.value }, tint = scheme.primary)
        }
        if(visibility.currentState || visibility.targetState || !visibility.isIdle) {
            val shown = operation ?: retained ?: return@Box
            val offset = with(LocalDensity.current) { 48.dp.roundToPx() }
            Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, offset),
                onDismissRequest = { if(shown.finished) onDismiss() },
                properties = PopupProperties(focusable = false, dismissOnClickOutside = false)) {
                AnimatedVisibility(visibleState = visibility,
                    enter = fadeIn(tween(180)) + scaleIn(tween(240, easing = FastOutSlowInEasing),
                        initialScale = .92f, transformOrigin = TransformOrigin(.9f, 0f)) +
                        slideInVertically(tween(240, easing = FastOutSlowInEasing)) { -it / 20 },
                    exit = fadeOut(tween(150)) + scaleOut(tween(150, easing = FastOutLinearInEasing),
                        targetScale = .96f, transformOrigin = TransformOrigin(.9f, 0f))) {
                Column(Modifier.width(304.dp).testTag("refresh-progress").semantics { liveRegion = LiveRegionMode.Polite }) {
                    val background = scheme.surfaceContainerHigh
                    Canvas(Modifier.align(Alignment.End).padding(end = 15.dp).size(18.dp, 9.dp)) {
                        drawPath(Path().apply { moveTo(0f,size.height); lineTo(size.width/2,0f); lineTo(size.width,size.height); close() }, background)
                    }
                    Surface(shape = RoundedCornerShape(24.dp), color = background, shadowElevation = 6.dp) {
                        Column(Modifier.fillMaxWidth().animateContentSize(tween(180)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(when { !shown.finished -> shown.title; shown.success -> "更新しました"; else -> "更新できませんでした" },
                                    Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                    color = if(shown.finished && !shown.success) scheme.error else scheme.onSurface)
                                if(shown.finished) TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp), modifier = Modifier.widthIn(min = 40.dp)) { Text("閉じる", fontSize = 11.sp) }
                            }
                            OperationSteps(shown)
                            Text(shown.stage, Modifier.heightIn(min = 36.dp), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 4)
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable internal fun OperationSteps(operation: Operation) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("接続確認", "実行", "結果確認").forEachIndexed { index, label ->
            val completed = index < operation.phase.ordinal || (operation.finished && operation.success)
            val active = index == operation.phase.ordinal
            val failed = active && operation.finished && !operation.success
            val color = when { failed -> scheme.error; completed || active -> scheme.primary; else -> scheme.outline }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .12f)) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Text(when { failed -> "!"; completed -> "✓"; else -> "${index+1}" }, color = color, fontSize = 12.sp)
                    }
                }
                Text(label, color = color, fontSize = 11.sp)
            }
        }
    }
}
