package io.github.kakashi812.droidoss.layout

import org.junit.Assert.*
import org.junit.Test
import io.github.kakashi812.droidoss.protocol.ClientHello

class ModernizationTest {
    @Test fun radialDeadzoneAndCurvesPreserveDirection() {
        val s = StickSettings(deadzone = 0.2f)
        assertEquals(0.toShort() to 0.toShort(), s.axes(0.1f, 0.1f))
        assertEquals(32767.toShort(), s.axes(1f, 0f).first)
        val (x, y) = s.axes(0.6f, -0.6f)
        assertEquals(x, y)
        assertTrue(s.copy(curve = 2f).axes(0.5f, 0f).first < s.axes(0.5f, 0f).first)
        assertEquals(0.toShort() to 0.toShort(), s.copy(centreX = 0.1f).axes(0.1f, 0f))
    }
    @Test fun duplicateButtonsAreMixedAndHiddenControlsDoNotSend() {
        val button = defaultLayout().elements.filterIsInstance<ButtonElement>().first { it.label == "A" }
        val copy = button.copy(id = "copy")
        val layout = defaultLayout().copy(elements = listOf(button, copy))
        assertEquals(0x1000, mixInput(layout, listOf(ControlTouch(copy.id))).buttons)
        assertEquals(0x1000, mixInput(layout, listOf(ControlTouch(button.id), ControlTouch(copy.id))).buttons)
        assertEquals(0, mixInput(layout.copy(elements = listOf(copy.copy(hidden = true))), listOf(ControlTouch(copy.id))).buttons)
        assertEquals(0, mixInput(layout, emptyList()).buttons)
    }
    @Test fun analogTriggersReachFullRangeAndRelease() {
        val layout = steamPreset("Racing")
        assertEquals(0, mixInput(layout, listOf(ControlTouch("lt", y = 1f))).leftTrigger)
        assertEquals(128, mixInput(layout, listOf(ControlTouch("lt", y = 0f))).leftTrigger)
        assertEquals(255, mixInput(layout, listOf(ControlTouch("lt", y = -1f))).leftTrigger)
        assertEquals(0, mixInput(layout, emptyList()).leftTrigger)
    }
    @Test fun settingsSurviveFileQrAndStoreRoundTrips() {
        val profile = steamPreset("Shooter").copy(id = "custom1", elements = defaultLayout().elements.map { it.with(color = 0xFF72D5AC, hidden = it.id == "lb") })
        for (text in listOf(LayoutShare.encodeFile(profile), LayoutShare.encodeQr(profile))) {
            val imported = LayoutShare.decode(text) as ImportResult.Ok
            assertEquals(profile.input, imported.input); assertEquals(profile.game, imported.game)
            assertEquals(profile.elements, imported.elements)
        }
        val store = LayoutStoreData(activeId = profile.id, custom = listOf(profile))
        assertEquals(store, LayoutCodec.decode(LayoutCodec.encode(store)))
    }
    @Test fun editHistoryBranchesAndIsBounded() {
        val history = EditHistory<Int>(2)
        history.checkpoint(1); history.checkpoint(2); history.checkpoint(3)
        assertEquals(3, history.undo(4)); assertEquals(2, history.undo(3)); assertFalse(history.canUndo)
        assertEquals(3, history.redo(2)); history.checkpoint(5); assertFalse(history.canRedo)
    }
    @Test fun metadataMatchesCSharpGoldenVectorAndRejectsInvalidStatus() {
        assertArrayEquals(byteArrayOf(0xDA.toByte(), 1, 7, 2, 2, 0x50, 0x43), ClientHello.encode("PC", 2))
        assertTrue(ClientHello.encode("🎮".repeat(100), -1).size <= 53)
        assertNull(ClientHello.status(byteArrayOf(0xDA.toByte(), 1, 8, 4, 0), 5))
    }
    @Test fun invalidSettingsAreSanitized() {
        assertEquals(1f, StickSettings(sensitivity = Float.NaN).sanitized().sensitivity, 0f)
        assertEquals(0.8f, StickSettings(deadzone = 5f).sanitized().deadzone, 0f)
    }
}
