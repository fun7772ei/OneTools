package dev.onetools

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/** Original line icons; no Samsung artwork or font assets. */
@Composable internal fun ToolGlyph(kind: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round)
            when(kind) {
                0 -> { // battery
                    drawRoundRect(color, Offset(3f,6f), Size(16f,12f), CornerRadius(3f), style = stroke)
                    drawLine(color, Offset(22f,10f), Offset(22f,14f), 1.8f, StrokeCap.Round)
                    drawRoundRect(color, Offset(6f,9f), Size(8f,6f), CornerRadius(1f))
                }
                1 -> { // adjustable controls
                    listOf(5f to 8f, 12f to 16f, 19f to 10f).forEach { (x,y) ->
                        drawLine(color, Offset(x,4f), Offset(x,y-3f), 1.8f, StrokeCap.Round)
                        drawLine(color, Offset(x,y+3f), Offset(x,20f), 1.8f, StrokeCap.Round)
                        drawCircle(color,2.5f,Offset(x,y),style=stroke)
                    }
                }
                2 -> { // protected connection
                    val path=Path().apply { moveTo(12f,2.5f);lineTo(20f,6f);lineTo(20f,12f);quadraticTo(20f,18f,12f,22f);quadraticTo(4f,18f,4f,12f);lineTo(4f,6f);close() }
                    drawPath(path,color,style=stroke)
                    drawLine(color,Offset(8f,12f),Offset(11f,15f),1.8f,StrokeCap.Round)
                    drawLine(color,Offset(11f,15f),Offset(16f,9f),1.8f,StrokeCap.Round)
                }
                5 -> { // information
                    drawCircle(color, 9f, Offset(12f,12f), style=stroke)
                    drawCircle(color, 1f, Offset(12f,7.5f))
                    drawLine(color,Offset(12f,11f),Offset(12f,17f),1.8f,StrokeCap.Round)
                }
                else -> listOf(5f,12f,19f).forEach { drawCircle(color,1.7f,Offset(12f,it)) }
            }
        }
    }
}

@Composable internal fun FloatingTabs(selected: Int, onSelect: (Int) -> Unit) {
    val scheme=MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(top=8.dp,bottom=12.dp),contentAlignment=Alignment.Center) {
        Surface(shape=CircleShape,color=scheme.surfaceContainerHigh.copy(alpha=.97f),shadowElevation=4.dp,
            border=BorderStroke(.5.dp,scheme.onSurface.copy(alpha=.06f))) {
            Row(Modifier.widthIn(max=336.dp).fillMaxWidth(.88f).padding(5.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                listOf("バッテリー","ツール","接続","情報").forEachIndexed { index,label ->
                    val isSelected=selected==index
                    val background by animateColorAsState(if(isSelected) scheme.onSurface.copy(alpha=.09f) else Color.Transparent,label="tab background")
                    val foreground by animateColorAsState(if(isSelected) scheme.primary else scheme.onSurface,label="tab foreground")
                    Column(Modifier.weight(1f).clip(CircleShape).background(background)
                        .selectable(isSelected,onClick={onSelect(index)},role=Role.Tab)
                        .heightIn(min=56.dp).padding(vertical=7.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        ToolGlyph(if(index == 3) 5 else index,foreground)
                        Text(label,color=foreground,fontSize=11.sp,fontWeight=if(isSelected) FontWeight.Bold else FontWeight.Medium,maxLines=1)
                    }
                }
            }
        }
    }
}

/** The reference apps place the large title above the reachable action row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun OneToolsPageHeader(title: String, subtitle: String, state: TopAppBarState,
    actions: @Composable () -> Unit) {
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val expanded = with(density) { (windowHeight.toDp() * .25f).coerceIn(144.dp, 224.dp) }
    val limit = with(density) { expanded.toPx() }
    SideEffect { state.heightOffsetLimit = -limit }
    Column(Modifier.fillMaxWidth().testTag("oneui-header")) {
        // Read the scroll offset in layout/draw only. The title keeps a stable measurement,
        // and scrolling no longer recomposes the whole page on every frame.
        Box(Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, (limit + state.heightOffset).roundToInt().coerceAtLeast(0)) {
                placeable.placeRelative(0, (state.heightOffset / 2f).roundToInt())
            }
        }.height(expanded), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(horizontal=24.dp).graphicsLayer { alpha = (1f - state.collapsedFraction / .65f).coerceIn(0f,1f) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, fontSize=30.sp, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary,
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Text(subtitle, fontSize=14.sp, color=MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        actions()
    }
}

@Composable internal fun PageActions(title: String, busy: Boolean, permitted: Boolean, refresh: () -> Unit, log: () -> Unit, modifier: Modifier=Modifier, connectionAction: (() -> Unit)? = null, showRefresh: Boolean = true,
    refreshOperation: Operation? = null, dismissRefresh: () -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().height(52.dp).padding(start=12.dp,end=2.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
        if(connectionAction != null) TextButton(onClick=connectionAction,contentPadding=PaddingValues(horizontal=8.dp)) {
            Box(Modifier.size(6.dp).background(if(permitted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(if(permitted) "接続済み" else "未接続",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(showRefresh) RefreshAction(busy, refreshOperation, refresh, dismissRefresh)
        Box {
            IconButton(onClick={menu=true},modifier=Modifier.semantics {contentDescription="その他の操作"}) { ToolGlyph(4,MaterialTheme.colorScheme.primary) }
            DropdownMenu(expanded=menu,onDismissRequest={menu=false},shape=RoundedCornerShape(24.dp)) {
                DropdownMenuItem(text={Text("実行ログ・技術情報")},onClick={menu=false;log()})
            }
        }
    }
}

/** Keep keyboard/accessibility focus clear of the floating controls. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable internal fun SafeContentScroll(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val spec = remember(density) {
        val top = with(density) { 8.dp.toPx() }
        val bottom = with(density) { 116.dp.toPx() }
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val end = (containerSize-bottom).coerceAtLeast(top)
                return when {
                    size > end-top -> offset-top
                    offset < top -> offset-top
                    offset+size > end -> offset+size-end
                    else -> 0f
                }
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content=content)
}
