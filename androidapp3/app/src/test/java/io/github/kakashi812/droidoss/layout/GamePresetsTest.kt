package io.github.kakashi812.droidoss.layout

import org.junit.Assert.*
import org.junit.Test
import io.github.kakashi812.droidoss.protocol.GamepadButton

class GamePresetsTest {
    @Test fun everyPresetPreservesFullControllerAndRoundTrips() {
        assertEquals(58, GamePresets.catalog.size)
        assertEquals(GamePresets.catalog.size, GamePresets.catalog.map { it.id }.toSet().size)
        for (preset in GamePresets.catalog) {
            val layout = preset.layout()
            assertTrue(layout.name.length <= MAX_NAME_LENGTH)
            assertEquals(layout.elements.size, layout.elements.map { it.id }.toSet().size)
            assertEquals(setOf(Stick.LEFT, Stick.RIGHT), layout.elements.filterIsInstance<StickElement>().map { it.stick }.toSet())
            assertEquals(setOf(Trigger.LEFT, Trigger.RIGHT), layout.elements.filterIsInstance<TriggerElement>().map { it.trigger }.toSet())
            assertTrue(layout.elements.filterIsInstance<ButtonElement>().any { it.mask == GamepadButton.START })
            val decoded = LayoutShare.decode(LayoutShare.encodeFile(layout)) as ImportResult.Ok
            assertEquals(layout.elements, decoded.elements); assertEquals(layout.input, decoded.input); assertEquals(layout.game, decoded.game)
            assertEquals(0.toShort() to 0.toShort(), layout.input.left.axes(0f, 0f))
            assertEquals(layout.input, layout.input.sanitized())
        }
    }
    @Test fun sportAimAndRacingHaveDistinctUsefulTuning() {
        val football = GamePresets.find("efootball")!!.layout()
        val horror = GamePresets.find("re-first-2")!!.layout()
        val racing = GamePresets.find("steam-racing")!!.layout()
        assertTrue(football.input.left.deadzone < .1f)
        assertTrue(horror.input.right.curve > 1f)
        assertTrue(horror.elements.filterIsInstance<StickElement>().single { it.stick == Stick.RIGHT }.size > .20f)
        assertTrue(racing.input.analogTriggers)
        assertEquals(255, football.input.trigger(.1f))
        assertTrue(GamePresets.find("tr-original-1")!!.note.contains("Steam Input"))
    }
    @Test fun fiftyProfilesFitWithoutDiscardingOldSlots() {
        val data = LayoutStoreData(custom = (1..50).map { defaultLayout().copy(id = "custom$it", name = "Game $it") })
        val restored = LayoutCodec.decode(LayoutCodec.encode(data))
        assertEquals(50, restored.custom.size)
    }
}
