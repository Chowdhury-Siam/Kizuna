/*
 * Shared drawing for the gamepad, used by both the live pad ([PadScreen]) and the
 * layout editor ([LayoutEditorScreen]). Keeping it in one place is what guarantees
 * a control looks identical while you are arranging it and while you are playing.
 *
 * Drawing primitives derived from PadConnect's GPEmulationScreen.
 * Copyright (C) 2026 Ishan
 * Copyright (C) 2026 droidOSS contributors
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 only.
 *
 * This program is distributed without any warranty. See the GNU General Public
 * License for more details.
 */

package io.github.kakashi812.droidoss.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.kakashi812.droidoss.layout.MacroElement
import io.github.kakashi812.droidoss.layout.ButtonElement
import io.github.kakashi812.droidoss.layout.ControlElement
import io.github.kakashi812.droidoss.layout.DpadElement
import io.github.kakashi812.droidoss.layout.DPAD_ARM_HALF_WIDTH
import io.github.kakashi812.droidoss.layout.DPAD_ARM_START
import io.github.kakashi812.droidoss.layout.DPAD_ARM_END
import io.github.kakashi812.droidoss.layout.StickMode
import io.github.kakashi812.droidoss.layout.StickElement
import io.github.kakashi812.droidoss.layout.TriggerElement

/** A control resolved to pixels for the current screen size. */
internal data class Placed(
    val element: ControlElement,
    val centre: Offset,
    val radius: Float,
) {
    fun contains(point: Offset, minimumRadius: Float = radius): Boolean {
        val delta = point - centre
        if (element is DpadElement)
            return kotlin.math.abs(delta.x) <= maxOf(radius, minimumRadius) && kotlin.math.abs(delta.y) <= maxOf(radius, minimumRadius)
        if (element is StickElement && element.mode == StickMode.TOUCHPAD)
            return kotlin.math.abs(delta.x) <= maxOf(radius, minimumRadius) && kotlin.math.abs(delta.y) <= maxOf(radius * 0.7f, minimumRadius)
        return delta.getDistanceSquared() <= maxOf(radius, minimumRadius) * maxOf(radius, minimumRadius)
    }
}

/**
 * Resolve fractional layout elements to pixel positions for a given size.
 *
 * Radius is a fraction of **width** on both axes, so a control stays round rather
 * than stretching with the aspect ratio.
 */
internal fun List<ControlElement>.place(widthPx: Float, heightPx: Float): List<Placed> =
    map { element ->
        var radius = element.size * widthPx / 2f
        var centre = Offset(element.x * widthPx, element.y * heightPx)
        if (element is DpadElement) {
            // Older layouts often put the lower arrow beyond the screen edge.
            radius = radius.coerceIn(0f, minOf(widthPx, heightPx) / 2f)
            centre = Offset(centre.x.coerceIn(radius, widthPx - radius), centre.y.coerceIn(radius, heightPx - radius))
        }
        Placed(element, centre, radius)
    }

/**
 * Measure and cache each control's label once per layout/size change.
 *
 * Measuring inside the draw lambda would allocate on every frame — the same
 * discipline the 125 Hz send path follows.
 */
@Composable
internal fun rememberControlLabels(placed: List<Placed>): Map<String, TextLayoutResult> {
    val controllerButtons = LocalControllerButtons.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(placed, textMeasurer, density, controllerButtons) {
        buildMap {
            for (p in placed) {
                val text = controllerButtons.label(p.element) ?: continue

                val content = AnnotatedString(text)
                val style = TextStyle(fontSize = with(density) { (p.radius * LABEL_FRACTION).toSp() }, fontWeight = FontWeight.Medium)
                val measured = textMeasurer.measure(text = content, style = style)
                val available = p.radius * 1.7f
                put(p.element.id, if (measured.size.width > available) {
                    textMeasurer.measure(text = content, style = style.copy(fontSize = style.fontSize * (available / measured.size.width.coerceAtLeast(1))))
                } else measured)
            }
        }
    }
}

/**
 * Keep the glass brushes in control-local coordinates so drag movement only
 * changes the canvas translation. Moving a knob never rebuilds its shaders.
 */
@Composable
internal fun rememberControlPaints(placed: List<Placed>, foreground: Color): Map<String, ControlPaint> {
    val dark = foreground.luminance() > .5f
    return placed.associate { p ->
        val element = p.element
        val ink = if (element.color == 0xFFE0E4EC) foreground else Color(element.color)
        val alpha = element.opacity * (if (element.enabled && !element.hidden) 1f else .3f)
        val mode = (element as? StickElement)?.mode
        val dpad = element is DpadElement
        val paint = key(element.id) {
            remember(p.radius, ink, alpha, dark, mode, dpad) {
                ControlPaint(p.radius, ink, alpha, dark, mode, dpad)
            }
        }
        element.id to paint
    }
}

internal class ControlPaint(
    radius: Float, val ink: Color, val alpha: Float, dark: Boolean, mode: StickMode?, dpad: Boolean,
) {
    val circle = GlassPaint(Offset(-radius, -radius), Offset(radius, radius),
        if (mode != null) alpha * .65f else alpha, ink, dark)
    private val knobRadius = radius * if (mode == StickMode.TOUCHPAD) .13f else KNOB_FRACTION
    val knob = if (mode != null) GlassPaint(Offset(-knobRadius, -knobRadius),
        Offset(knobRadius, knobRadius), alpha, ink, dark) else null
    val tile = when {
        dpad -> GlassPaint(Offset.Zero, Offset(DPAD_ARM_HALF_WIDTH * 2, DPAD_ARM_END - DPAD_ARM_START) * radius, alpha, ink, dark)
        mode == StickMode.TOUCHPAD -> GlassPaint(Offset.Zero, Offset(radius * 2, radius * 1.4f), alpha * .8f, ink, dark)
        else -> null
    }
    val horizontalTile = if (dpad) GlassPaint(Offset.Zero,
        Offset(DPAD_ARM_END - DPAD_ARM_START, DPAD_ARM_HALF_WIDTH * 2) * radius, alpha, ink, dark) else null
    val activeArrow = (if (ink.luminance() > .35f) Color.Black else Color.White).copy(alpha = alpha * .95f)
}

internal class GlassPaint(from: Offset, to: Offset, val alpha: Float, ink: Color, dark: Boolean) {
    private val tint = Color(0xFF97B8DF).copy(alpha = alpha * if (dark) .10f else .30f)
    val fill = Brush.linearGradient(listOf(Color.White.copy(alpha = alpha * if (dark) .22f else .93f), tint), from, to)
    val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = alpha * if (dark) .68f else .98f),
        ink.copy(alpha = alpha * .14f)), from, to)
    val shade = Color.Black.copy(alpha = alpha * if (dark) .12f else .06f)
    val highlight = Color.White.copy(alpha = alpha * .07f)
    val pressed = ink.copy(alpha = alpha * .22f)
}

private val RimStroke = Stroke(1.5f)
private val InnerStroke = Stroke(1f)
private val DpadArms = arrayOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f))

internal fun DrawScope.drawControl(
    placed: Placed,
    paint: ControlPaint,
    label: TextLayoutResult?,
    isDown: Boolean = false,
    knob: Offset = Offset.Zero,
    centre: Offset = placed.centre,
    dpadMask: Int = 0,
    scale: Float = 1f,
) {
    val element = placed.element
    val r = placed.radius
    if (r <= 0f) return

    withTransform({
        translate(centre.x, centre.y)
        if (scale != 1f) this.scale(scale, scale, Offset.Zero)
    }) {
        when (element) {
            is StickElement -> if (element.mode == StickMode.TOUCHPAD) {
                drawGlassTile(Offset(-r, -r * .7f), Size(r * 2, r * 1.4f), r * .18f, requireNotNull(paint.tile), isDown)
                val cross = paint.ink.copy(alpha = paint.alpha * .10f)
                drawLine(cross, Offset(-r * .6f, 0f), Offset(r * .6f, 0f), 1f)
                drawLine(cross, Offset(0f, -r * .4f), Offset(0f, r * .4f), 1f)
                if (isDown) translate(knob.x, knob.y) { drawGlassCircle(r * .13f, requireNotNull(paint.knob), true) }
            } else {
                drawGlassCircle(r, paint.circle, false)
                translate(knob.x, knob.y) {
                    drawGlassCircle(r * KNOB_FRACTION, requireNotNull(paint.knob), isDown)
                    drawCircle(paint.ink.copy(alpha = paint.alpha * .2f), r * KNOB_FRACTION * .88f, Offset.Zero)
                }
            }
            is DpadElement -> drawDpad(r, dpadMask, paint)
            is ButtonElement, is TriggerElement, is MacroElement -> drawGlassCircle(r, paint.circle, isDown)
        }
        if (label != null) {
            drawText(textLayoutResult = label, color = paint.ink.copy(alpha = paint.alpha * if (isDown) 1f else .92f),
                topLeft = Offset(-label.size.width / 2f, -label.size.height / 2f))
        }
    }
}

private fun DrawScope.drawDpad(r: Float, mask: Int, paint: ControlPaint) {
    drawCircle(paint.ink.copy(alpha = paint.alpha * .05f), r, Offset.Zero)
    val half = DPAD_ARM_HALF_WIDTH
    val start = DPAD_ARM_START
    val end = DPAD_ARM_END
    val armWidth = 2 * half * r
    val armLength = (end - start) * r
    val idleArrow = paint.ink.copy(alpha = paint.alpha * .95f)
    for (index in DpadArms.indices) {
        val direction = DpadArms[index]
        val active = mask and (1 shl index) != 0
        val vertical = index < 2
        val top = when (index) {
            0 -> Offset(-half, -end)
            1 -> Offset(-half, start)
            2 -> Offset(-end, -half)
            else -> Offset(start, -half)
        } * r
        val area = if (vertical) Size(armWidth, armLength) else Size(armLength, armWidth)
        val tile = if (vertical) paint.tile else paint.horizontalTile
        drawGlassTile(top, area, r * .12f, requireNotNull(tile), active)
        val middle = direction * (r * .67f)
        val wing = Offset(-direction.y, direction.x) * (r * .11f)
        val tip = middle + direction * (r * .09f)
        val back = middle - direction * (r * .06f)
        val arrow = if (active) paint.activeArrow else idleArrow
        drawLine(arrow, back + wing, tip, STROKE_WIDTH)
        drawLine(arrow, back - wing, tip, STROKE_WIDTH)
    }
    drawCircle(paint.ink.copy(alpha = paint.alpha * .25f), r * .10f, Offset.Zero)
}

private fun DrawScope.drawGlassCircle(radius: Float, paint: GlassPaint, down: Boolean) {
    drawCircle(paint.shade, radius + 1f, Offset(0f, radius * .055f))
    drawCircle(paint.fill, radius, Offset.Zero)
    if (down) drawCircle(paint.pressed, radius, Offset.Zero)
    drawCircle(paint.rim, radius, Offset.Zero, style = RimStroke)
    drawCircle(paint.highlight, (radius - 2f).coerceAtLeast(0f), Offset.Zero, style = InnerStroke)
}

private fun DrawScope.drawGlassTile(top: Offset, area: Size, corner: Float, paint: GlassPaint, down: Boolean) {
    translate(top.x, top.y) {
        val curve = CornerRadius(corner)
        drawRoundRect(paint.fill, Offset.Zero, area, curve)
        if (down) drawRoundRect(paint.pressed, Offset.Zero, area, curve)
        drawRoundRect(paint.rim, Offset.Zero, area, curve, style = RimStroke)
    }
}

internal const val STROKE_WIDTH = 3f
internal const val KNOB_FRACTION = 0.42f
internal const val LABEL_FRACTION = 0.62f
