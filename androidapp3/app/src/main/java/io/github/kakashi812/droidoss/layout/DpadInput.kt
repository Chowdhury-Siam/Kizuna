package io.github.kakashi812.droidoss.layout

import kotlin.math.abs
import kotlin.math.hypot

// Shared with the drawing so the visible arrow faces always select one direction.
const val DPAD_ARM_HALF_WIDTH = 0.34f
const val DPAD_ARM_START = 0.38f
const val DPAD_ARM_END = 0.96f

/** Arrow faces snap to a cardinal; the gaps support deliberate diagonals around a radial neutral zone. */
fun dpadButtons(x: Float, y: Float, deadzone: Float = 0.25f, diagonals: Boolean = true): Int {
    if (!x.isFinite() || !y.isFinite()) return 0
    val neutral = if (deadzone.isFinite()) deadzone.coerceIn(0f, 0.9f) else 0.25f
    if (hypot(x, y) <= neutral) return 0
    val ax = abs(x)
    val ay = abs(y)
    val horizontal = if (x < 0f) 4 else 8
    val vertical = if (y < 0f) 1 else 2
    if (!diagonals) return if (ax > ay) horizontal else vertical
    if (ax <= DPAD_ARM_HALF_WIDTH && ay >= DPAD_ARM_START) return vertical
    if (ay <= DPAD_ARM_HALF_WIDTH && ax >= DPAD_ARM_START) return horizontal
    // tan(22.5°): four 45° cardinal sectors alternating with four diagonal sectors.
    if (ay < ax * 0.41421356f) return horizontal
    if (ax < ay * 0.41421356f) return vertical
    return horizontal or vertical
}
