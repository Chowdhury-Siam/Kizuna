package io.github.kakashi812.droidoss.layout

import io.github.kakashi812.droidoss.protocol.PadState
import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class StickSettings(
    val sensitivity: Float = 1f,
    val deadzone: Float = 0.12f,
    val curve: Float = 1f,
    val centreX: Float = 0f,
    val centreY: Float = 0f,
) {
    fun sanitized() = copy(
        sensitivity = sensitivity.safe(1f, 0.25f, 2f),
        deadzone = deadzone.safe(0.12f, 0f, 0.8f),
        curve = curve.safe(1f, 0.3f, 3f),
        centreX = centreX.safe(0f, -0.25f, 0.25f),
        centreY = centreY.safe(0f, -0.25f, 0.25f),
    )

    /** Radial normalization preserves direction and reaches full travel at the rim. */
    fun axes(x: Float, y: Float): Pair<Short, Short> {
        val s = sanitized()
        val dx = x - s.centreX
        val dy = y - s.centreY
        val distance = hypot(dx, dy)
        if (!distance.isFinite() || distance <= s.deadzone) return 0.toShort() to 0.toShort()
        val travel = (((distance.coerceAtMost(1f) - s.deadzone) / (1f - s.deadzone))
            .pow(s.curve) * s.sensitivity).coerceIn(0f, 1f)
        fun axis(v: Float) = (v / distance * travel * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
        return axis(dx) to axis(-dy)
    }
}

@Serializable
data class InputSettings(
    val left: StickSettings = StickSettings(),
    val right: StickSettings = StickSettings(),
    val analogTriggers: Boolean = false,
    val triggerDeadzone: Float = 0f,
    val triggerRange: Float = 1f,
    val vibration: Float = 0.7f,
) {
    fun sanitized() = copy(left = left.sanitized(), right = right.sanitized(),
        triggerDeadzone = triggerDeadzone.safe(0f, 0f, 0.8f),
        triggerRange = triggerRange.safe(1f, 0.2f, 1f), vibration = vibration.safe(0.7f, 0f, 1f))

    fun trigger(travel: Float): Int {
        val s = sanitized()
        if (!s.analogTriggers) return 255
        return (((travel - s.triggerDeadzone) / s.triggerRange).coerceIn(0f, 1f) * 255).roundToInt()
    }
}

private fun Float.safe(fallback: Float, min: Float, max: Float) = if (isFinite()) coerceIn(min, max) else fallback

/** Active touches are mixed into one snapshot, so duplicate mappings cannot release each other. */
data class ControlTouch(val id: String, val x: Float = 0f, val y: Float = 0f)

fun mixInput(layout: ControllerLayout, touches: Collection<ControlTouch>): PadState {
    val result = PadState()
    for (touch in touches) {
        val e = layout.elements.firstOrNull { it.id == touch.id && it.enabled && !it.hidden } ?: continue
        when (e) {
            is ButtonElement -> result.setButton(e.mask, true)
            is TriggerElement -> {
                val value = layout.input.trigger((1f - touch.y) / 2f)
                if (e.trigger == Trigger.LEFT) result.leftTrigger = max(result.leftTrigger, value)
                else result.rightTrigger = max(result.rightTrigger, value)
            }
            is StickElement -> {
                val settings = if (e.stick == Stick.LEFT) layout.input.left else layout.input.right
                val (x, y) = if (e.mode == StickMode.TOUCHPAD) touchpadAxes(touch.x, touch.y, e.touchpadSensitivity)
                    else settings.axes(touch.x, touch.y)
                if (e.stick == Stick.LEFT) { result.thumbLX = x; result.thumbLY = y }
                else { result.thumbRX = x; result.thumbRY = y }
            }
            is DpadElement -> {
                result.setButton(1, touch.y < -e.deadzone || result.isPressed(1))
                result.setButton(2, touch.y > e.deadzone || result.isPressed(2))
                result.setButton(4, touch.x < -e.deadzone || result.isPressed(4))
                result.setButton(8, touch.x > e.deadzone || result.isPressed(8))
            }
        }
    }
    return result
}

fun steamPreset(name: String): ControllerLayout {
    val base = defaultLayout().copy(name = "Steam · $name", game = name)
    return when (name) {
        "Racing" -> base.copy(input = InputSettings(analogTriggers = true, left = StickSettings(curve = 1.4f)))
        "Shooter" -> base.copy(input = InputSettings(right = StickSettings(sensitivity = 0.85f, curve = 1.6f)))
        "Platformer" -> base.copy(elements = base.elements.map { if (it is DpadElement) it.copy(size = 0.20f) else it })
        else -> base
    }
}


fun StickElement.sanitized(): StickElement = copy(touchpadSensitivity = touchpadSensitivity.safe(1f, 0.25f, 4f))

/** Relative touchpad velocity drives the existing XInput stick axes, without joystick deadzones. */
fun touchpadAxes(x: Float, y: Float, sensitivity: Float): Pair<Short, Short> {
    if (!x.isFinite() || !y.isFinite()) return 0.toShort() to 0.toShort()
    val gain = sensitivity.safe(1f, 0.25f, 4f)
    val length = hypot(x, y)
    val scale = gain / maxOf(1f, length * gain)
    return (x * scale * 32767f).roundToInt().toShort() to (-y * scale * 32767f).roundToInt().toShort()
}

/** Coordinates are normalized by control radius. A landing never jumps to an absolute position. */
class TouchpadMotion(private var x: Float, private var y: Float, private var time: Long) {
    private var movedAt = Long.MIN_VALUE
    private var velocityX = 0f
    private var velocityY = 0f
    fun move(nextX: Float, nextY: Float, now: Long) {
        if (!nextX.isFinite() || !nextY.isFinite() || now < time) return
        val dx = nextX - x
        val dy = nextY - y
        if (dx != 0f || dy != 0f) {
            val frame = 16_000_000f / (now - time).coerceIn(4_000_000, 50_000_000).toFloat()
            velocityX = dx * frame; velocityY = dy * frame; movedAt = now
        }
        x = nextX; y = nextY; time = now
    }
    fun sample(id: String, now: Long): ControlTouch =
        if (movedAt == Long.MIN_VALUE || now - movedAt >= IDLE_NANOS || now < movedAt) ControlTouch(id)
        else ControlTouch(id, velocityX, velocityY)
    companion object { const val IDLE_NANOS = 60_000_000L }
}
