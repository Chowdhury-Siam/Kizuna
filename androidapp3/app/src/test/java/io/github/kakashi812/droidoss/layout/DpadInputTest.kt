package io.github.kakashi812.droidoss.layout

import org.junit.Assert.*
import org.junit.Test

class DpadInputTest {
    @Test fun arrowFacesStayCardinalAllTheWayToTheirEdges() {
        for (side in listOf(-1f, 1f)) {
            for (across in listOf(-DPAD_ARM_HALF_WIDTH, 0f, DPAD_ARM_HALF_WIDTH)) {
                for (along in listOf(DPAD_ARM_START, 0.67f, DPAD_ARM_END)) {
                    assertEquals(if (side < 0) 1 else 2, dpadButtons(across, side * along))
                    assertEquals(if (side < 0) 4 else 8, dpadButtons(side * along, across))
                }
            }
        }
    }

    @Test fun gapsProduceAllFourDiagonalsAndFourWayNeverDoes() {
        for (x in listOf(-0.65f, 0.65f)) for (y in listOf(-0.65f, 0.65f)) {
            val expected = (if (x < 0) 4 else 8) or (if (y < 0) 1 else 2)
            assertEquals(expected, dpadButtons(x, y))
            assertEquals(1, Integer.bitCount(dpadButtons(x, y, diagonals = false)))
        }
        assertEquals(8, dpadButtons(0.8f, 0.4f, diagonals = false))
        assertEquals(1, dpadButtons(0.4f, -0.8f, diagonals = false))
    }

    @Test fun neutralIsRadialAndInvalidCoordinatesCannotPressAnything() {
        assertEquals(0, dpadButtons(0f, 0f, deadzone = 0f))
        assertEquals(0, dpadButtons(0.15f, 0.15f))
        assertEquals(10, dpadButtons(0.20f, 0.20f))
        assertEquals(0, dpadButtons(0f, -0.4f, deadzone = 0.5f))
        assertEquals(1, dpadButtons(0f, -0.8f, deadzone = 0.5f))
        assertEquals(0, dpadButtons(Float.NaN, 1f))
        assertEquals(0, dpadButtons(1f, Float.POSITIVE_INFINITY))
    }

    @Test fun mixerUsesTheSameDirectionsAndReleasesWithoutTouches() {
        val dpad = DpadElement("dpad", 0.5f, 0.5f, 0.2f)
        val button = ButtonElement("a", 0.8f, 0.5f, 0.1f, mask = 0x1000, label = "A")
        val layout = defaultLayout().copy(elements = listOf(dpad, button))
        val touches = listOf(ControlTouch("dpad", .65f, -.65f), ControlTouch("a"))
        assertEquals(0x1009, mixInput(layout, touches).buttons)
        assertEquals(0, mixInput(layout, emptyList()).buttons)
        assertEquals(0x1000, mixInput(layout.copy(elements = listOf(dpad.copy(hidden = true), button)), touches).buttons)
        assertEquals(0, mixInput(layout.copy(elements = listOf(dpad.copy(enabled = false))), touches).buttons)
    }

    @Test fun directionOptionsSurviveStoreFileAndQrWithoutMigratingOldLayouts() {
        val dpad = DpadElement("dpad", .3f, .7f, .2f, deadzone = .1f, diagonals = false)
        val layout = defaultLayout().copy(id = "custom1", elements = listOf(dpad))
        val data = LayoutStoreData(activeId = layout.id, custom = listOf(layout))
        assertEquals(data, LayoutCodec.decode(LayoutCodec.encode(data)))
        for (encoded in listOf(LayoutShare.encodeFile(layout), LayoutShare.encodeQr(layout))) {
            assertEquals(listOf(dpad), (LayoutShare.decode(encoded) as ImportResult.Ok).elements)
        }
        // Old files contain no direction-mode field and keep the default 8-way behavior.
        val legacy = LayoutCodec.encode(data).replace(Regex(""",\s*"diagonals"\s*:\s*false"""), "")
        assertTrue((LayoutCodec.decode(legacy).custom.single().elements.single() as DpadElement).diagonals)
    }
}
