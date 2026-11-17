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
        assertTrue(football.input.analogTriggers)
        assertTrue(football.input.trigger(.5f) in 1..254)
        assertEquals(255, football.input.trigger(1f))
        assertTrue(GamePresets.find("tr-original-1")!!.note.contains("Steam Input"))
    }
    @Test fun fiftyProfilesFitWithoutDiscardingOldSlots() {
        val data = LayoutStoreData(custom = (1..50).map { defaultLayout().copy(id = "custom$it", name = "Game $it") })
        val restored = LayoutCodec.decode(LayoutCodec.encode(data))
        assertEquals(50, restored.custom.size)
    }
    @Test fun everyTitleHasNeutralFullRangeSticksAndUsefulTriggerTravel() {
        assertEquals(GamePresets.catalog.size, GamePresets.catalog.map { it.defaults.input }.toSet().size)
        assertEquals(GamePresets.catalog.map { it.id }.toSet(), GamePresetDefaults.byId.keys)
        for (preset in GamePresets.catalog) {
            val layout = preset.layout()
            assertTrue(preset.defaults.summary.isNotBlank())
            for (settings in listOf(layout.input.left, layout.input.right)) {
                assertEquals(0f, settings.centreX, 0f)
                assertEquals(0f, settings.centreY, 0f)
                assertEquals(0.toShort() to 0.toShort(), settings.axes(0f, 0f))
                assertEquals(32767.toShort(), settings.axes(1f, 0f).first)
                assertEquals((-32767).toShort(), settings.axes(-1f, 0f).first)
                assertEquals((-32767).toShort(), settings.axes(0f, 1f).second)
            }
            if (layout.input.analogTriggers) {
                assertEquals(0, layout.input.trigger(0f))
                assertTrue(layout.input.trigger(.5f) in 1..254)
            }
            assertEquals(255, layout.input.trigger(1f))
            assertEquals(0, mixInput(layout, emptyList()).leftTrigger)
            assertEquals(0, mixInput(layout, emptyList()).rightTrigger)
        }
    }

    @Test fun gamesInTheSameSeriesHaveDifferentResponseAndDirectionalBehavior() {
        val football = GamePresets.find("efootball")!!.layout()
        val pes = GamePresets.find("pes2021")!!.layout()
        val fc = GamePresets.find("fc")!!.layout()
        assertNotEquals(football.input.left.axes(.35f, .1f), pes.input.left.axes(.35f, .1f))
        assertNotEquals(football.input.right.axes(.2f, 0f), fc.input.right.axes(.2f, 0f))
        assertEquals(255, pes.input.trigger(.1f))
        val classic = GamePresets.find("tr-original-1")!!.layout()
        val twin = GamePresets.find("lara-coop-0")!!.layout()
        val classicPad = classic.elements.filterIsInstance<DpadElement>().single()
        val twinPad = twin.elements.filterIsInstance<DpadElement>().single()
        assertFalse(classicPad.diagonals)
        assertTrue(twinPad.diagonals)
        val touch = listOf(ControlTouch(classicPad.id, .8f, -.8f))
        val directions = GamepadButton.DPAD_UP or GamepadButton.DPAD_RIGHT
        assertNotEquals(directions, mixInput(classic, touch).buttons and directions)
        assertEquals(directions, mixInput(twin, touch).buttons and directions)
    }

    @Test fun oldFactoryProfilesUpgradeOnceWhileCustomizedCalibrationAndLayoutSurvive() {
        val preset = GamePresets.find("efootball")!!
        val old = preset.layout().copy(id = "custom1", name = "My football", input = preset.legacyInput,
            elements = defaultLayout().elements.map { it.with(x = .45f, opacity = .5f) })
        val calibrated = old.copy(id = "custom2", input = old.input.copy(left = old.input.left.copy(centreX = .03f)),
            elements = old.elements.map { if (it is StickElement) it.copy(touchpadSensitivity = 7f) else it })
        val data = LayoutStoreData(version = 2, activeId = old.id, custom = listOf(old, calibrated))
        val upgraded = LayoutCodec.decode(LayoutCodec.encode(data))
        assertEquals(preset.defaults.input, upgraded.custom[0].input)
        assertEquals(old.id, upgraded.activeId)
        assertEquals(old.name, upgraded.custom[0].name)
        assertEquals(old.elements.map { it.x to it.opacity }, upgraded.custom[0].elements.map { it.x to it.opacity })
        assertEquals(calibrated.input, upgraded.custom[1].input)
        assertTrue(upgraded.custom[1].elements.filterIsInstance<StickElement>().all { it.touchpadSensitivity == 7f })
        assertEquals(upgraded, LayoutCodec.decode(LayoutCodec.encode(upgraded)))
        // Current-version profiles that intentionally use generic values are never re-tuned on read.
        val current = LayoutStoreData(custom = listOf(old))
        assertEquals(current, LayoutCodec.decode(LayoutCodec.encode(current)))
    }

    @Test fun applyingGameDefaultsPreservesControlModeMappingAndGeometry() {
        val preset = GamePresets.find("tr-survivor-0")!!
        val edited = preset.layout().copy(elements = preset.layout().elements.map { control ->
            if (control is StickElement) control.copy(mode = StickMode.TOUCHPAD, x = .5f, touchpadSensitivity = 10f)
            else control.with(color = 0xFF72D5AC, hidden = control.id == "lb")
        })
        val reset = preset.defaults.applyTo(edited)
        assertEquals(edited.elements.map { it.id to it.x }, reset.elements.map { it.id to it.x })
        assertEquals(edited.elements.map { it.hidden to it.color }, reset.elements.map { it.hidden to it.color })
        val right = reset.elements.filterIsInstance<StickElement>().single { it.stick == Stick.RIGHT }
        assertEquals(StickMode.TOUCHPAD, right.mode)
        assertEquals(preset.defaults.touchpadSensitivity, right.touchpadSensitivity, 0f)
        assertEquals(preset.defaults.touchpadDeadzoneCompensation, right.touchpadDeadzoneCompensation, 0f)
        assertNull(GamePresets.forGame("My custom game"))
    }

}
