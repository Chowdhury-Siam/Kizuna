package io.github.kakashi812.droidoss.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** A static, cached backdrop gives translucent surfaces depth without full-screen blur passes. */
@Composable
internal fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .4f
    Box(modifier.fillMaxSize().drawWithCache {
        val base = Brush.linearGradient(if (dark) listOf(Color(0xFF081321), Color(0xFF162944), Color(0xFF102434))
            else listOf(Color(0xFFE0EBFC), Color(0xFFF1EFFE), Color(0xFFDBF1F5)), end = Offset(size.width, size.height))
        val blue = Brush.radialGradient(listOf(Color(0xFF6BAAFF).copy(alpha = if (dark) .26f else .35f), Color.Transparent),
            center = Offset(size.width * .1f, size.height * .16f), radius = (size.maxDimension * .65f).coerceAtLeast(1f))
        onDrawBehind { drawRect(base); drawRect(blue) }
    }, content = content)
}

@Composable
internal fun Modifier.glassMaterial(
    shape: Shape = RoundedCornerShape(24.dp), strong: Boolean = false, selected: Boolean = false,
): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < .4f
    val accent = MaterialTheme.colorScheme.primary
    val fill = remember(dark, strong, selected, accent) {
        Brush.linearGradient(if (dark) listOf(
            Color(0xFF466683).copy(alpha = if (strong) .95f else .43f),
            Color(0xFF1A2D47).copy(alpha = if (strong) .96f else .76f),
            (if (selected) Color(0xFF254E7B) else Color(0xFF20354E)).copy(alpha = if (strong) .96f else .72f),
        ) else listOf(Color.White.copy(alpha = if (strong) .98f else .88f),
            Color(0xFFF5F9FF).copy(alpha = if (strong) .96f else .64f),
            (if (selected) Color(0xFFD6E9FF) else Color(0xFFEAF2FF)).copy(alpha = if (strong) .94f else .58f)))
    }
    val rim = remember(dark, selected, accent) {
        Brush.linearGradient(listOf(if (selected) accent.copy(alpha = .8f) else Color.White.copy(alpha = if (dark) .28f else .96f),
            if (selected) accent.copy(alpha = .55f) else Color.White.copy(alpha = if (dark) .10f else .28f)))
    }
    val raised = if (strong) shadow(5.dp, shape, clip = false) else this
    return raised.background(fill, shape).border(BorderStroke(1.dp, rim), shape)
}

@Composable
internal fun GlassPanel(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(24.dp), strong: Boolean = false,
    selected: Boolean = false, content: @Composable () -> Unit) {
    Surface(modifier = modifier.glassMaterial(shape, strong, selected), color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface, content = content)
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(22.dp),
    selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(modifier, shape, selected = selected) { Column(content = content) }
}

@Composable
internal fun GlassChoice(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    FilterChip(selected, onClick, label, modifier = modifier, enabled = enabled, shape = CircleShape,
        border = BorderStroke(1.dp, if (selected) colors.primary.copy(alpha = .46f) else colors.outlineVariant),
        colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface.copy(alpha = .22f),
            selectedContainerColor = colors.primary.copy(alpha = .17f), selectedLabelColor = colors.primary))
}

@Composable
internal fun GlassTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false, textStyle: TextStyle = LocalTextStyle.current,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = RoundedCornerShape(18.dp)) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(value, onValueChange, modifier = modifier, enabled = enabled,
        label = label, placeholder = placeholder, singleLine = singleLine, textStyle = textStyle,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = colors.outline, focusedBorderColor = colors.primary,
            focusedContainerColor = colors.surface.copy(alpha = .32f),
            unfocusedContainerColor = colors.surface.copy(alpha = .24f),
        ))
}

@Composable
internal fun GlassAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null) {
    androidx.compose.material3.AlertDialog(onDismissRequest, confirmButton,
        modifier = modifier.glassMaterial(RoundedCornerShape(28.dp), strong = true),
        dismissButton = dismissButton, title = title, text = text,
        containerColor = Color.Transparent, tonalElevation = 0.dp, shape = RoundedCornerShape(28.dp))
}
