package io.github.kakashi812.droidoss.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System sans preserves language coverage; tighter metrics keep the glass interface compact.
private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) =
    TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = height.sp,
        fontWeight = weight, letterSpacing = spacing.sp)

val Typography = Typography(
    displaySmall = type(32, 38, FontWeight.SemiBold, -.9f),
    headlineLarge = type(30, 36, FontWeight.SemiBold, -.8f),
    headlineMedium = type(27, 33, FontWeight.SemiBold, -.6f),
    headlineSmall = type(24, 30, FontWeight.SemiBold, -.5f),
    titleLarge = type(21, 27, FontWeight.SemiBold, -.35f),
    titleMedium = type(16, 22, FontWeight.SemiBold, -.15f),
    titleSmall = type(14, 20, FontWeight.SemiBold, -.1f),
    bodyLarge = type(15, 22), bodyMedium = type(14, 20), bodySmall = type(12, 18),
    labelLarge = type(13, 18, FontWeight.Medium), labelMedium = type(12, 16, FontWeight.Medium),
    labelSmall = type(11, 15, FontWeight.Medium, .15f),
)
