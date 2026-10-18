package dev.onetools

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Independent palette informed by the S25 reference screens, retaining the user's accent. */
@Composable
internal fun oneToolsColorScheme(dark: Boolean): ColorScheme {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, dark, configuration) {
    val accent = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()
    accent.copy(
        background = if (dark) Color.Black else Color(0xFFF4F5F7),
        onBackground = if (dark) Color(0xFFF5F5F7) else Color(0xFF171719),
        surface = if (dark) Color(0xFF171719) else Color(0xFFFCFCFF),
        onSurface = if (dark) Color(0xFFF5F5F7) else Color(0xFF171719),
        surfaceContainerLowest = if (dark) Color.Black else Color.White,
        surfaceContainerLow = if (dark) Color(0xFF121214) else Color(0xFFF9F9FC),
        surfaceContainer = if (dark) Color(0xFF171719) else Color(0xFFFCFCFF),
        surfaceContainerHigh = if (dark) Color(0xFF252527) else Color(0xFFFCFCFF),
        surfaceContainerHighest = if (dark) Color(0xFF303033) else Color(0xFFEDEDF1),
        surfaceVariant = if (dark) Color(0xFF303033) else Color(0xFFEDEDF1),
        onSurfaceVariant = if (dark) Color(0xFFABABB1) else Color(0xFF64646B),
        outline = if (dark) Color(0xFF85858C) else Color(0xFF74747B),
        outlineVariant = if (dark) Color(0xFF36363B) else Color(0xFFE2E2E8),
        surfaceTint = Color.Transparent
    )
    }
}

/** Keep status/navigation icons readable in the introduction and the normal screens. */
@Composable
internal fun SystemBarAppearance(dark: Boolean) {
    val context = LocalContext.current
    SideEffect {
        (context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}
