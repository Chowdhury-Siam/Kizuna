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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(placed, textMeasurer, density) {
        buildMap {
            for (p in placed) {
                val text = when (val e = p.element) {
                    is ButtonElement -> e.label
                    is TriggerElement -> e.label
                    else -> null
                } ?: continue

                put(
                    p.element.id,
                    textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = TextStyle(
                            fontSize = with(density) { (p.radius * LABEL_FRACTION).toSp() },
                            fontWeight = FontWeight.Medium,
                        ),
                    ),
                )
            }
        }
    }
}

internal fun DrawScope.drawControl(
    placed: Placed,
    pressed: Map<String, Boolean>,
    knobs: Map<String, Offset>,
    labels: Map<String, TextLayoutResult>,
    foreground: Color = Color.White,
    dpadMask: Int = 0,
) {
    val element = placed.element
    val isDown = pressed[element.id] == true
    val ink = if (element.color == 0xFFE0E4EC) foreground else Color(element.color)
    val alpha = element.opacity * (if (element.enabled && !element.hidden) 1f else 0.3f)

    when (element) {
        is StickElement -> if (element.mode == StickMode.TOUCHPAD) {
            val top = placed.centre - Offset(placed.radius, placed.radius * 0.7f)
            val area = Size(placed.radius * 2f, placed.radius * 1.4f)
            drawRoundRect(ink.copy(alpha = alpha * 0.08f), top, area, CornerRadius(placed.radius * 0.16f))
            drawRoundRect(ink.copy(alpha = alpha * 0.5f), top, area, CornerRadius(placed.radius * 0.16f), style = Stroke(STROKE_WIDTH))
            drawLine(ink.copy(alpha = alpha * 0.16f), placed.centre - Offset(placed.radius * 0.6f, 0f), placed.centre + Offset(placed.radius * 0.6f, 0f), 1.5f)
            drawLine(ink.copy(alpha = alpha * 0.16f), placed.centre - Offset(0f, placed.radius * 0.4f), placed.centre + Offset(0f, placed.radius * 0.4f), 1.5f)
            if (isDown) drawCircle(ink.copy(alpha = alpha * 0.7f), placed.radius * 0.12f, placed.centre + (knobs[element.id] ?: Offset.Zero))
        } else {
            drawCircle(
                color = ink.copy(alpha = alpha * 0.10f),
                radius = placed.radius,
                center = placed.centre,
            )
            drawCircle(
                color = ink.copy(alpha = alpha * 0.35f),
                radius = placed.radius,
                center = placed.centre,
                style = Stroke(width = STROKE_WIDTH),
            )

            // The knob tracking the thumb is most of what replaces the missing
            // tactile feel -- you can see exactly what the stick is reporting.
            val knob = knobs[element.id] ?: Offset.Zero
            drawCircle(
                color = ink.copy(alpha = alpha * 0.70f),
                radius = placed.radius * KNOB_FRACTION,
                center = placed.centre + knob,
            )
        }

        // A cross, not a circle with a knob. Drawing it like a stick made it
        // read as a third stick, which is exactly what it must never look like:
        // a D-pad promises four discrete directions and a stick promises smooth
        // travel, and the shape is the only thing telling you which you have.
        is DpadElement -> drawDpad(placed, dpadMask, alpha, ink)

        is ButtonElement, is TriggerElement -> {
            drawCircle(
                color = ink.copy(alpha = if (isDown) alpha * 0.80f else alpha * 0.15f),
                radius = placed.radius,
                center = placed.centre,
            )
            drawCircle(
                color = ink.copy(alpha = alpha * 0.5f),
                radius = placed.radius,
                center = placed.centre,
                style = Stroke(width = STROKE_WIDTH),
            )
        }
    }

    // Labels last, so they sit on top of the pressed fill.
    labels[element.id]?.let { measured ->
        drawText(
            textLayoutResult = measured,
            color = ink.copy(alpha = alpha * if (isDown) 0.95f else 0.7f),
            topLeft = placed.centre - Offset(
                measured.size.width / 2f,
                measured.size.height / 2f,
            ),
        )
    }
}

/** Large, separated arrow pads use the full footprint and highlight the actual output bits. */
internal fun DrawScope.drawDpad(placed: Placed, mask: Int, alpha: Float, ink: Color) {
    val r = placed.radius
    val c = placed.centre
    drawCircle(ink.copy(alpha = alpha * 0.05f), r, c)
    val half = DPAD_ARM_HALF_WIDTH
    val start = DPAD_ARM_START
    val end = DPAD_ARM_END
    val pads = listOf(
        Triple(1, Offset(-half, -end), Size(half * 2, end - start)),
        Triple(2, Offset(-half, start), Size(half * 2, end - start)),
        Triple(4, Offset(-end, -half), Size(end - start, half * 2)),
        Triple(8, Offset(start, -half), Size(end - start, half * 2)),
    )
    for ((bit, offset, size) in pads) {
        val active = mask and bit != 0
        val top = c + offset * r
        val area = Size(size.width * r, size.height * r)
        val corner = CornerRadius(r * 0.12f)
        drawRoundRect(ink.copy(alpha = alpha * if (active) 0.75f else 0.16f), top, area, corner)
        drawRoundRect(ink.copy(alpha = alpha * if (active) 0.95f else 0.55f), top, area, corner, style = Stroke(STROKE_WIDTH))
        val direction = when (bit) { 1 -> Offset(0f, -1f); 2 -> Offset(0f, 1f); 4 -> Offset(-1f, 0f); else -> Offset(1f, 0f) }
        val middle = c + direction * (r * 0.67f)
        val wing = Offset(-direction.y, direction.x) * (r * 0.11f)
        val tip = middle + direction * (r * 0.09f)
        val back = middle - direction * (r * 0.06f)
        val arrow = (if (active) { if (ink.luminance() > 0.35f) Color.Black else Color.White } else ink).copy(alpha = alpha * 0.95f)
        drawLine(arrow, back + wing, tip, STROKE_WIDTH)
        drawLine(arrow, back - wing, tip, STROKE_WIDTH)
    }
    drawCircle(ink.copy(alpha = alpha * 0.25f), r * 0.10f, c)
}

internal const val STROKE_WIDTH = 3f
internal const val CORNER = 8f
internal const val KNOB_FRACTION = 0.42f
internal const val LABEL_FRACTION = 0.62f
