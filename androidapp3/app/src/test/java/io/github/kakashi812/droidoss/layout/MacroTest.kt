package io.github.kakashi812.droidoss.layout

import io.github.kakashi812.droidoss.protocol.GamepadButton
import io.github.kakashi812.droidoss.protocol.PadState
import org.junit.Assert.*
import org.junit.Test

class MacroTest {
    @Test fun recordsSimultaneousActionsWithTimingAndFinalRelease() {
        val recorder = MacroRecorder()
        recorder.capture(PadState(), 100)
        val held = PadState().also { it.buttons = GamepadButton.A or GamepadButton.RIGHT_SHOULDER; it.rightTrigger = 170; it.thumbLX = 20000 }
        recorder.capture(held, 1000)
        recorder.capture(held, 1100)
        recorder.capture(PadState(), 1250)
        val frames = recorder.finish(1400)
        assertEquals(0, frames.first().atMs)
        assertEquals(held.buttons, frames.first().buttons)
        assertEquals(170, frames.first().rightTrigger)
        assertEquals(20000.toShort(), frames.first().leftX)
        assertEquals(250, frames[1].atMs)
        assertEquals(400, frames.last().atMs)
        assertTrue(frames.last().neutral)
        assertTrue(MacroElement("m", frames = frames).validRecording())
        assertEquals(frames, recorder.finish(2000))
    }
    @Test fun replayUsesRecordedTimingAndCanBeCancelledWithoutReleasingManualHolds() {
        val macro = MacroElement("m", frames = listOf(MacroFrame(0, buttons = GamepadButton.A), MacroFrame(100, buttons = GamepadButton.B), MacroFrame(300)))
        val player = MacroPlayer()
        player.start(macro, 1000)
        assertEquals(GamepadButton.A, player.sample(1099)!!.buttons)
        assertEquals(GamepadButton.B, player.sample(1100)!!.buttons)
        assertNull(player.sample(1300))
        assertFalse(player.running)
        player.start(macro, 2000)
        val manual = PadState().also { it.buttons = GamepadButton.A; it.thumbRX = 12000 }
        val mixed = mergeMacro(manual, MacroFrame(0, buttons = GamepadButton.B, rightX = 32000))
        assertEquals(GamepadButton.A or GamepadButton.B, mixed.buttons)
        assertEquals(12000.toShort(), mixed.thumbRX)
        player.stop()
        assertNull(player.sample(2010))
        assertEquals(GamepadButton.A, mergeMacro(manual, null).buttons)
        val neutralOverride = mergeMacro(PadState(), MacroFrame(0, leftX = 32000, rightTrigger = 255), setOf(Stick.LEFT), setOf(Trigger.RIGHT))
        assertEquals(0.toShort(), neutralOverride.thumbLX)
        assertEquals(0, neutralOverride.rightTrigger)
        val direction = mergeMacro(PadState().also { it.buttons = GamepadButton.DPAD_DOWN }, MacroFrame(0, buttons = GamepadButton.DPAD_UP))
        assertEquals(GamepadButton.DPAD_DOWN, direction.buttons)
    }
    @Test fun malformedOrOversizedSequencesAreNotPlayedOrImported() {
        val bad = MacroElement("m", frames = listOf(MacroFrame(0, buttons = 0x0800), MacroFrame(100)))
        assertFalse(bad.validRecording())
        assertTrue(LayoutShare.decode(LayoutShare.encodeFile(defaultLayout().copy(elements = listOf(bad)))) is ImportResult.Invalid)
        val player = MacroPlayer(); player.start(bad, 0)
        assertFalse(player.running)
        assertFalse(bad.copy(frames = listOf(MacroFrame(0), MacroFrame(0))).validRecording())
        assertFalse(bad.copy(frames = listOf(MacroFrame(0), MacroFrame(MAX_MACRO_DURATION_MS + 1))).validRecording())
        assertFalse(bad.copy(frames = List(MAX_MACRO_FRAMES + 1) { MacroFrame(it) }).validRecording())
        val tooMany = defaultLayout().copy(elements = defaultLayout().elements + List(MAX_MACROS + 1) { MacroElement("macro$it") })
        assertTrue(LayoutShare.decode(LayoutShare.encodeFile(tooMany)) is ImportResult.Invalid)
    }
    @Test fun recordedMacrosSurviveSharingStoreAndControlDuplication() {
        val m = MacroElement("m", frames = listOf(MacroFrame(0, buttons = GamepadButton.X, rightX = 20000), MacroFrame(700)))
        val profile = defaultLayout().copy(id = "custom1", elements = defaultLayout().elements + m)
        for (text in listOf(LayoutShare.encodeFile(profile), LayoutShare.encodeQr(profile))) {
            val result = LayoutShare.decode(text) as ImportResult.Ok
            assertEquals(m, result.elements.filterIsInstance<MacroElement>().single())
        }
        val store = LayoutStoreData(custom = listOf(profile))
        assertEquals(store, LayoutCodec.decode(LayoutCodec.encode(store)))
        assertEquals(m.frames, (m.with(id = "copy", x = .6f) as MacroElement).frames)
        assertEquals("M1", ControllerButtons.PLAYSTATION.label(m))
    }
    @Test fun recorderRemainsBoundedAndReleasesAStillHeldAction() {
        val recorder = MacroRecorder()
        repeat(MAX_MACRO_FRAMES + 100) { i -> recorder.capture(PadState().also { it.buttons = if (i % 2 == 0) GamepadButton.A else GamepadButton.B }, i.toLong()) }
        val frames = recorder.finish(10000)
        assertTrue(frames.size <= MAX_MACRO_FRAMES)
        assertTrue(frames.last().neutral)
        assertTrue(MacroElement("m", frames = frames).validRecording())
        val holding = MacroRecorder(); holding.capture(PadState().also { it.leftTrigger = 255 }, 10)
        assertTrue(holding.finish(10).last().neutral)
    }
    @Test fun subMillisecondButtonEdgesArePreserved() {
        val recorder = MacroRecorder()
        recorder.capture(PadState().also { it.buttons = GamepadButton.A }, 10)
        recorder.capture(PadState(), 10)
        val frames = recorder.finish(10)
        assertEquals(GamepadButton.A, frames.first().buttons)
        assertTrue(frames[1].neutral)
        assertTrue(frames.zipWithNext().all { (a, b) -> a.atMs < b.atMs })
        assertTrue(MacroElement("m", frames = frames).validRecording())
        val player = MacroPlayer()
        player.start(MacroElement("rapid", frames = listOf(MacroFrame(0, buttons = GamepadButton.A),
            MacroFrame(1, buttons = GamepadButton.B), MacroFrame(2))), 0)
        assertEquals(GamepadButton.A, player.sample(0)!!.buttons)
        assertEquals(GamepadButton.B, player.sample(8)!!.buttons)
        assertNull(player.sample(16))
    }

}
