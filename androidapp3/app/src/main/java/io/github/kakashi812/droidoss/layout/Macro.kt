package io.github.kakashi812.droidoss.layout

import io.github.kakashi812.droidoss.protocol.PadState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MAX_MACROS = 4
const val MAX_MACRO_FRAMES = 4096
const val MAX_MACRO_DURATION_MS = 30_000

/** Calibrated output snapshots: replay does not apply sensitivity a second time. */
@Serializable
data class MacroFrame(
    val atMs: Int,
    val buttons: Int = 0,
    val leftTrigger: Int = 0, val rightTrigger: Int = 0,
    val leftX: Short = 0, val leftY: Short = 0, val rightX: Short = 0, val rightY: Short = 0,
) {
    fun state() = PadState().also {
        it.buttons = buttons; it.leftTrigger = leftTrigger; it.rightTrigger = rightTrigger
        it.thumbLX = leftX; it.thumbLY = leftY; it.thumbRX = rightX; it.thumbRY = rightY
    }
    fun sameInput(other: MacroFrame) = copy(atMs = 0) == other.copy(atMs = 0)
    val neutral: Boolean get() = sameInput(MacroFrame(0))
    companion object {
        fun from(state: PadState, atMs: Int) = MacroFrame(atMs, state.buttons and 0xF7FF,
            state.leftTrigger.coerceIn(0, 255), state.rightTrigger.coerceIn(0, 255),
            state.thumbLX, state.thumbLY, state.thumbRX, state.thumbRY)
    }
}

@Serializable
@SerialName("macro")
data class MacroElement(
    override val id: String,
    override val x: Float = .5f, override val y: Float = .5f, override val size: Float = .09f,
    override val opacity: Float = .85f, override val enabled: Boolean = true,
    override val hidden: Boolean = false, override val color: Long = 0xFF72D5AC,
    val label: String = "M1",
    val frames: List<MacroFrame> = emptyList(),
) : ControlElement

/** Reject malformed time/order/ranges rather than replaying untrusted data. */
fun MacroElement.validRecording(): Boolean = frames.isEmpty() || (
    frames.size in 2..MAX_MACRO_FRAMES && frames.first().atMs == 0 && frames.last().neutral &&
    frames.last().atMs in 1..MAX_MACRO_DURATION_MS && frames.zipWithNext().all { (a, b) -> a.atMs < b.atMs } &&
    frames.all { it.atMs in 0..MAX_MACRO_DURATION_MS && it.buttons and 0xF7FF == it.buttons &&
        it.leftTrigger in 0..255 && it.rightTrigger in 0..255 }
)

class MacroRecorder {
    private val frames = mutableListOf<MacroFrame>()
    private var startedAt: Long? = null
    private var finished = false
    val hasActions: Boolean get() = frames.any { !it.neutral }
    val full: Boolean get() = frames.size >= MAX_MACRO_FRAMES - 1
    fun elapsed(nowMs: Long): Int = startedAt?.let { (nowMs - it).coerceIn(0, MAX_MACRO_DURATION_MS.toLong()).toInt() } ?: 0
    fun capture(state: PadState, nowMs: Long) {
        if (finished || full) return
        val frame = MacroFrame.from(state, elapsed(nowMs))
        if (startedAt == null) {
            if (frame.neutral) return
            startedAt = nowMs
        }
        val next = frame.copy(atMs = elapsed(nowMs))
        val last = frames.lastOrNull()
        if (last?.sameInput(next) == true) return
        // Preserve rapid down/up edges even when the clock returns the same millisecond.
        val at = if (last == null) 0 else maxOf(next.atMs, last.atMs + 1)
        if (at >= MAX_MACRO_DURATION_MS) return
        frames.add(next.copy(atMs = at))
    }
    fun finish(nowMs: Long): List<MacroFrame> {
        if (finished) return frames.toList()
        finished = true
        if (!hasActions) { frames.clear(); return emptyList() }
        // Reserve a strictly later neutral frame, even if Stop is tapped during a held action.
        val end = elapsed(nowMs).coerceAtLeast((frames.lastOrNull()?.atMs ?: 0) + 1).coerceAtMost(MAX_MACRO_DURATION_MS)
        while (frames.lastOrNull()?.atMs?.let { it >= end } == true) frames.removeAt(frames.lastIndex)
        frames.add(MacroFrame(end))
        return frames.toList()
    }
}

class MacroPlayer {
    private var frames: List<MacroFrame> = emptyList()
    private var startedAt = 0L
    private var index = 0
    val running: Boolean get() = frames.isNotEmpty()
    fun start(macro: MacroElement, nowMs: Long) {
        stop()
        if (!macro.validRecording() || macro.frames.isEmpty()) return
        frames = macro.frames; startedAt = nowMs
    }
    fun stop() { frames = emptyList(); index = 0 }
    fun sample(nowMs: Long): MacroFrame? {
        if (!running) return null
        val elapsed = (nowMs - startedAt).coerceAtLeast(0)
        while (index + 1 < frames.size && frames[index + 1].atMs <= elapsed) {
            val previous = frames[index]
            val next = frames[++index]
            // Catch up analog motion, but never skip a button or trigger press/release edge.
            if (previous.buttons != next.buttons || (previous.leftTrigger == 0) != (next.leftTrigger == 0) ||
                (previous.rightTrigger == 0) != (next.rightTrigger == 0)) break
        }
        if (index == frames.lastIndex && elapsed >= frames.last().atMs) { stop(); return null }
        return frames[index]
    }
}

/** Manual holds cannot be released by a replay; an actively touched axis takes priority even at zero. */
fun mergeMacro(manual: PadState, frame: MacroFrame?, sticks: Set<Stick> = emptySet(), triggers: Set<Trigger> = emptySet()): PadState {
    if (frame == null) return manual
    return PadState().also {
        val macroButtons = if (manual.buttons and 0x000F != 0) frame.buttons and 0xFFF0 else frame.buttons
        it.buttons = manual.buttons or macroButtons
        it.leftTrigger = if (Trigger.LEFT in triggers) manual.leftTrigger else maxOf(manual.leftTrigger, frame.leftTrigger)
        it.rightTrigger = if (Trigger.RIGHT in triggers) manual.rightTrigger else maxOf(manual.rightTrigger, frame.rightTrigger)
        val left = Stick.LEFT in sticks || manual.thumbLX.toInt() != 0 || manual.thumbLY.toInt() != 0
        val right = Stick.RIGHT in sticks || manual.thumbRX.toInt() != 0 || manual.thumbRY.toInt() != 0
        it.thumbLX = if (left) manual.thumbLX else frame.leftX; it.thumbLY = if (left) manual.thumbLY else frame.leftY
        it.thumbRX = if (right) manual.thumbRX else frame.rightX; it.thumbRY = if (right) manual.thumbRY else frame.rightY
    }
}
