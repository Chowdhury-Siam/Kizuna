package io.github.kakashi812.droidoss.layout

import io.github.kakashi812.droidoss.protocol.GamepadButton
import org.junit.Assert.*
import org.junit.Test

class ControllerButtonsTest {
    @Test fun playStationNamesFollowTheMappedActionWithoutChangingInput() {
        val ps = ControllerButtons.PLAYSTATION
        val expected = mapOf(GamepadButton.A to "×", GamepadButton.B to "○", GamepadButton.X to "□", GamepadButton.Y to "△",
            GamepadButton.LEFT_SHOULDER to "L1", GamepadButton.RIGHT_SHOULDER to "R1",
            GamepadButton.START to "Options", GamepadButton.BACK to "Share", GamepadButton.GUIDE to "PS")
        for ((mask, label) in expected) {
            val control = ButtonElement("test", .5f, .5f, .1f, mask = mask, label = "Original")
            val layout = defaultLayout().copy(elements = listOf(control))
            assertEquals(label, ps.label(control))
            assertEquals("Original", ControllerButtons.XBOX.label(control))
            assertEquals(mask, mixInput(layout, listOf(ControlTouch("test"))).buttons)
            assertEquals("Original", control.label)
        }
        assertEquals("Cross", ps.label(GamepadButton.A, "A", spoken = true))
        assertEquals("Square", ps.label(GamepadButton.X, "X", spoken = true))
        assertEquals("Custom", ps.label(0, "Custom"))
    }

    @Test fun triggerLabelsAndSerializationRetainTheProfileAndItsCalibration() {
        val layout = GamePresets.find("efootball")!!.layout()
        val encoded = LayoutShare.encodeFile(layout)
        val left = layout.elements.filterIsInstance<TriggerElement>().single { it.trigger == Trigger.LEFT }
        val right = layout.elements.filterIsInstance<TriggerElement>().single { it.trigger == Trigger.RIGHT }
        assertEquals("L2", ControllerButtons.PLAYSTATION.label(left))
        assertEquals("R2", ControllerButtons.PLAYSTATION.label(right))
        assertEquals("LT", ControllerButtons.XBOX.label(left))
        layout.elements.forEach { ControllerButtons.PLAYSTATION.label(it) }
        assertEquals(encoded, LayoutShare.encodeFile(layout))
    }
}
