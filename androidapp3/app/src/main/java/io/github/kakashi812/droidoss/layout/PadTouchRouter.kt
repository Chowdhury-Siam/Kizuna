package io.github.kakashi812.droidoss.layout

import kotlin.math.*

data class TouchPoint(val x: Float, val y: Float)

data class TouchTarget(val element: ControlElement, val x: Float, val y: Float, val radius: Float) {
    fun contains(px: Float, py: Float, minimum: Float): Boolean {
        val dx = px - x; val dy = py - y
        val rx = max(radius, minimum)
        return when {
            element is DpadElement -> abs(dx) <= rx && abs(dy) <= rx
            element is StickElement && element.mode == StickMode.TOUCHPAD -> abs(dx) <= rx && abs(dy) <= max(radius * .7f, minimum)
            else -> dx * dx + dy * dy <= rx * rx
        }
    }
}

data class PadRoutingSnapshot(
    val inputs: List<ControlTouch> = emptyList(),
    val floatingCentres: Map<String, TouchPoint> = emptyMap(),
    val knobs: Map<String, TouchPoint> = emptyMap(),
    val sticks: Set<Stick> = emptySet(),
    val triggers: Set<Trigger> = emptySet(),
)

/** Hit-test buttons before sticks at touchdown. Captured sticks keep their pointer until release. */
class PadTouchRouter(private val targets: List<TouchTarget>, private val width: Float, private val minimum: Float) {
    private class Gesture(var target: TouchTarget?, val stickGesture: Boolean, val floating: Boolean,
        val anchorX: Float, val anchorY: Float) {
        val buttonGesture = target != null && !stickGesture
        var motion: TouchpadMotion? = null
        var output: ControlTouch? = null
        var knob = TouchPoint(0f, 0f)
    }
    private val gestures = linkedMapOf<Long, Gesture>()
    private val usable = targets.filter { it.element.enabled && !it.element.hidden && it.radius > 0f }
    private fun buttonAt(x: Float, y: Float) = usable.lastOrNull { it.element !is StickElement && it.contains(x, y, minimum) }
    private fun occupied(target: TouchTarget, except: Long): Boolean = gestures.any { (id, g) ->
        id != except && g.target?.let { other ->
            other.element.id == target.element.id || (other.element is StickElement && target.element is StickElement && other.element.stick == target.element.stick)
        } == true
    }

    /** Returns a macro only on its initial press, never on slide-in or while held. */
    fun down(id: Long, x: Float, y: Float, now: Long, eventTime: Long = now): MacroElement? {
        up(id)
        val button = buttonAt(x, y)
        val direct = if (button == null) usable.lastOrNull { it.element is StickElement && it.contains(x, y, minimum) } else null
        val side = if (x < width / 2f) Stick.LEFT else Stick.RIGHT
        val floating = if (button == null && direct == null) usable.lastOrNull {
            val stick = it.element as? StickElement
            stick != null && stick.stick == side && stick.touchAnywhere
        } else null
        val target = button ?: direct ?: floating
        // Remember an ignored touchdown too, so a second finger cannot steal a stick later.
        val chosen = target?.takeUnless { occupied(it, id) }
        val gesture = Gesture(chosen, chosen?.element is StickElement, floating != null && chosen != null,
            if (floating != null) x else chosen?.x ?: x, if (floating != null) y else chosen?.y ?: y)
        gestures[id] = gesture
        move(id, x, y, now, eventTime)
        return chosen?.element as? MacroElement
    }

    fun move(id: Long, x: Float, y: Float, now: Long, eventTime: Long = now) {
        val g = gestures[id] ?: return
        if (g.buttonGesture) {
            var target = g.target
            val tolerance = target?.let { if (it.element is DpadElement) max(it.radius * 1.2f, minimum) else minimum } ?: minimum
            if (target == null || !target.contains(x, y, tolerance)) {
                val next = buttonAt(x, y)?.takeUnless { occupied(it, id) }
                g.target = next
                if (next == null) { g.output = null; return }
                target = next
            }
            val active = target ?: return
            g.output = ControlTouch(active.element.id, (x - active.x) / active.radius, (y - active.y) / active.radius)
            return
        }
        val target = g.target ?: return
        if (!g.stickGesture) return
        val stick = target.element as StickElement
        // The drag keeps its centre and can cross other controls without pressing them.
        val dx = (x - g.anchorX) / target.radius; val dy = (y - g.anchorY) / target.radius
        if (stick.mode == StickMode.TOUCHPAD) {
            val motion = g.motion ?: TouchpadMotion(dx, dy, eventTime).also { g.motion = it }
            motion.move(dx, dy, now, eventTime)
            g.output = motion.sample(stick.id, now)
            g.knob = TouchPoint(dx.coerceIn(-1f, 1f) * target.radius, dy.coerceIn(-.7f, .7f) * target.radius)
        } else {
            g.output = ControlTouch(stick.id, dx, dy)
            val length = hypot(dx, dy).coerceAtLeast(1f)
            g.knob = TouchPoint(dx / length * target.radius, dy / length * target.radius)
        }
    }

    val needsSampling: Boolean
        get() = gestures.values.any { gesture ->
            gesture.motion?.needsIdleCheck == true && gesture.output?.let { it.x != 0f || it.y != 0f } == true
        }

    fun sample(now: Long): Boolean {
        var changed = false
        gestures.values.forEach { g ->
            g.motion?.let {
                val next = it.sample(g.target!!.element.id, now)
                if (next != g.output) { g.output = next; changed = true }
            }
        }
        return changed
    }
    fun up(id: Long) { gestures.remove(id) }
    fun retain(ids: Set<Long>) { gestures.keys.retainAll(ids) }
    fun clear() { gestures.clear() }
    fun snapshot(): PadRoutingSnapshot {
        val centres = mutableMapOf<String, TouchPoint>(); val knobs = mutableMapOf<String, TouchPoint>()
        val sticks = mutableSetOf<Stick>(); val triggers = mutableSetOf<Trigger>()
        gestures.values.forEach { g ->
            val target = g.target ?: return@forEach
            when (val e = target.element) {
                is StickElement -> {
                    sticks.add(e.stick)
                    if (g.floating) centres[e.id] = TouchPoint(g.anchorX, g.anchorY)
                    knobs[e.id] = g.knob
                }
                is TriggerElement -> if (g.output != null) triggers.add(e.trigger)
                else -> Unit
            }
        }
        return PadRoutingSnapshot(gestures.values.mapNotNull { it.output }, centres, knobs, sticks, triggers)
    }
}
