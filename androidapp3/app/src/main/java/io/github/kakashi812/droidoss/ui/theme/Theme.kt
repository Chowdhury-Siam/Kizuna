package io.github.kakashi812.droidoss.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import io.github.kakashi812.droidoss.ui.GlassBackdrop

private val DarkGlass = darkColorScheme(
    primary = Color(0xFF8BC1FF), onPrimary = Color(0xFF062345),
    primaryContainer = Color(0xFF244A74), onPrimaryContainer = Color(0xFFE1EFFF),
    secondary = Color(0xFFBCD3E9), onSecondary = Color(0xFF102942),
    secondaryContainer = Color(0xFF2B415D).copy(alpha = .84f), onSecondaryContainer = Color(0xFFE4F0FF),
    tertiary = Color(0xFFD3C4FF), background = Color(0xFF0B1728), onBackground = Color(0xFFF2F7FF),
    surface = Color(0xFF1B2E47).copy(alpha = .94f), onSurface = Color(0xFFF2F7FF),
    surfaceVariant = Color(0xFF344D68).copy(alpha = .65f), onSurfaceVariant = Color(0xFFB3C6DC),
    surfaceContainer = Color(0xFF243A55).copy(alpha = .88f),
    surfaceContainerLow = Color(0xFF1C304B).copy(alpha = .8f),
    surfaceContainerLowest = Color(0xFF101E30), surfaceContainerHigh = Color(0xFF2D4664).copy(alpha = .94f),
    surfaceContainerHighest = Color(0xFF344D6B), outline = Color(0xFF8FA9C5).copy(alpha = .6f),
    outlineVariant = Color.White.copy(alpha = .16f), error = Color(0xFFFFA5A0), scrim = Color(0xFF030A17),
)
private val LightGlass = lightColorScheme(
    primary = Color(0xFF0066E8), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF), onPrimaryContainer = Color(0xFF07386C),
    secondary = Color(0xFF436487), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEBFA).copy(alpha = .84f), onSecondaryContainer = Color(0xFF203D5E),
    tertiary = Color(0xFF7058A8), background = Color(0xFFE7EFFB), onBackground = Color(0xFF10233E),
    surface = Color.White.copy(alpha = .94f), onSurface = Color(0xFF10233E),
    surfaceVariant = Color(0xFFE4EDF9).copy(alpha = .65f), onSurfaceVariant = Color(0xFF4B627C),
    surfaceContainer = Color.White.copy(alpha = .88f), surfaceContainerLow = Color.White.copy(alpha = .72f),
    surfaceContainerLowest = Color(0xFFF3F7FD), surfaceContainerHigh = Color(0xFFF2F7FF).copy(alpha = .96f),
    surfaceContainerHighest = Color(0xFFE4EEFA), outline = Color(0xFF8B9EB7).copy(alpha = .65f),
    outlineVariant = Color(0xFF7895B5).copy(alpha = .24f), error = Color(0xFFBD302D), scrim = Color(0xFF0E1C33),
)
private val GlassShapes = Shapes(extraSmall = RoundedCornerShape(14.dp), small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp))

@Composable
fun KizunaTheme(themeMode: String = "System",
    darkTheme: Boolean = if (themeMode == "System") isSystemInDarkTheme() else themeMode == "Dark",
    dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) DarkGlass else LightGlass
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (context as? Activity)?.let { activity ->
            WindowInsetsControllerCompat(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialExpressiveTheme(colorScheme = colors, typography = Typography, motionScheme = MotionScheme.expressive()) {
        MaterialTheme(shapes = GlassShapes) { GlassBackdrop { content() } }
    }
}
