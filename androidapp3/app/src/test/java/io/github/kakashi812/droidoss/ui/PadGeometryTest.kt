package io.github.kakashi812.droidoss.ui

import androidx.compose.ui.geometry.Offset
import io.github.kakashi812.droidoss.layout.DpadElement
import org.junit.Assert.*
import org.junit.Test

class PadGeometryTest {
    @Test fun dpadArrowCornersAndDiagonalGapsAreTouchable() {
        val placed = Placed(DpadElement("pad", .5f, .5f, .2f), Offset(100f, 100f), 50f)
        assertTrue(placed.contains(Offset(117f, 52f)))
        assertTrue(placed.contains(Offset(145f, 55f)))
        assertFalse(placed.contains(Offset(151f, 100f)))
        assertTrue(placed.contains(Offset(159f, 100f), minimumRadius = 60f))
    }

    @Test fun existingEdgeLayoutsKeepAllFourArrowsOnScreen() {
        val pad = DpadElement("pad", .99f, .99f, .2f)
        val placed = listOf(pad).place(800f, 320f).single()
        assertEquals(80f, placed.radius, 0f)
        assertEquals(Offset(720f, 240f), placed.centre)
        assertTrue(placed.centre.y + placed.radius <= 320f)
        assertEquals(.99f, (placed.element as DpadElement).y, 0f)
    }
}
