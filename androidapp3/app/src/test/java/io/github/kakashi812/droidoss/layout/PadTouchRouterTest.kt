package io.github.kakashi812.droidoss.layout

import io.github.kakashi812.droidoss.protocol.GamepadButton
import org.junit.Assert.*
import org.junit.Test

class PadTouchRouterTest {
    private val left = StickElement("left", .15f, .7f, .15f, stick = Stick.LEFT)
    private val right = StickElement("right", .7f, .7f, .15f, stick = Stick.RIGHT)
    private val a = ButtonElement("a", .85f, .4f, .08f, mask = GamepadButton.A, label = "A")
    private val dpad = DpadElement("dpad", .3f, .75f, .12f)
    private val trigger = TriggerElement("rt", .9f, .1f, .1f, trigger = Trigger.RIGHT, label = "RT")
    private fun router(vararg extra: ControlElement): PadTouchRouter {
        val elements = listOf(left, right, a, dpad, trigger) + extra
        return PadTouchRouter(elements.map { TouchTarget(it, it.x * 1000, it.y * 600, it.size * 500) }, 1000f, 24f)
    }
    @Test fun emptySpaceOnEachSideStartsNeutralAndControlsItsOwnStickIndependently() {
        val r = router()
        r.down(1, 100f, 200f, 0)
        r.down(2, 600f, 200f, 0)
        assertEquals(listOf(0f, 0f), r.snapshot().inputs.map { it.x })
        r.move(1, 140f, 200f, 10_000_000)
        r.move(2, 600f, 160f, 10_000_000)
        val inputs = r.snapshot().inputs.associateBy { it.id }
        assertTrue(inputs.getValue("left").x > 0f)
        assertTrue(inputs.getValue("right").y < 0f)
        assertEquals(TouchPoint(100f, 200f), r.snapshot().floatingCentres["left"])
        r.up(1)
        assertEquals("right", r.snapshot().inputs.single().id)
        r.clear()
        assertTrue(r.snapshot().inputs.isEmpty())
        assertTrue(r.snapshot().floatingCentres.isEmpty())
    }
    @Test fun buttonsDpadTriggersAndMacrosNeverFallThroughToAStick() {
        val macro = MacroElement("macro", .55f, .3f)
        val r = router(macro)
        assertEquals(macro, r.down(1, 550f, 180f, 0))
        assertEquals("macro", r.snapshot().inputs.single().id)
        r.clear()
        for ((x, y, name) in listOf(Triple(850f, 240f, "a"), Triple(300f, 450f, "dpad"), Triple(900f, 60f, "rt"))) {
            r.down(1, x, y, 0)
            assertEquals(name, r.snapshot().inputs.single().id)
            assertTrue(r.snapshot().sticks.isEmpty())
            r.move(1, 450f, 250f, 10_000_000)
            assertTrue(r.snapshot().inputs.isEmpty())
            r.up(1)
        }
    }
    @Test fun bothAnalogSticksPauseOverButtonsAndKeepTheirOriginalCentres() {
        for ((stick, blocker) in listOf(left to dpad, right to a)) {
            for (floating in listOf(false, true)) {
                val r = router()
                val x = stick.x * 1000
                val y = if (floating) 120f else stick.y * 600
                r.down(1, x, y, 0)
                r.move(1, x + 25f, y, 10_000_000)
                val before = r.snapshot()
                assertTrue(before.inputs.single().x > 0f)
                r.move(1, blocker.x * 1000, blocker.y * 600, 20_000_000)
                val blocked = r.snapshot()
                assertTrue(blocked.inputs.isEmpty())
                assertEquals(setOf(stick.stick), blocked.sticks)
                assertEquals(TouchPoint(0f, 0f), blocked.knobs[stick.id])
                assertEquals(before.floatingCentres, blocked.floatingCentres)
                r.move(1, x + 25f, y, 30_000_000)
                assertEquals(before, r.snapshot())
                r.move(1, x, y, 40_000_000)
                assertEquals(ControlTouch(stick.id, 0f, 0f), r.snapshot().inputs.single())
                r.up(1)
                assertEquals(PadRoutingSnapshot(), r.snapshot())
            }
        }
    }
    @Test fun anotherFingerCannotStealAStickButCanPressANeighboringButton() {
        val r = router()
        r.down(1, 100f, 200f, 0)
        r.down(2, 300f, 200f, 0)
        r.down(3, 850f, 240f, 0)
        assertEquals(setOf("left", "a"), r.snapshot().inputs.map { it.id }.toSet())
        r.up(1)
        r.move(2, 320f, 200f, 10_000_000)
        assertEquals(listOf("a"), r.snapshot().inputs.map { it.id })
        r.up(2)
        r.down(2, 300f, 200f, 20_000_000)
        assertEquals(setOf("left", "a"), r.snapshot().inputs.map { it.id }.toSet())
    }
    @Test fun hiddenOrOptedOutStickDoesNotClaimEmptySpaceAndButtonsWinOverAnOverlappingStick() {
        val off = left.copy(touchAnywhere = false)
        val overlap = right.copy(x = a.x, y = a.y)
        val r = PadTouchRouter(listOf(a, overlap, off).map { TouchTarget(it, it.x * 1000, it.y * 600, it.size * 500) }, 1000f, 24f)
        r.down(1, 100f, 200f, 0)
        assertTrue(r.snapshot().inputs.isEmpty())
        r.down(2, 850f, 240f, 0)
        assertEquals("a", r.snapshot().inputs.single().id)
    }
    @Test fun floatingTouchpadRetainsEdgeTurnAndCanPauseOverAControl() {
        val touchpad = right.copy(mode = StickMode.TOUCHPAD)
        val r = PadTouchRouter(listOf(touchpad, a).map { TouchTarget(it, it.x * 1000, it.y * 600, it.size * 500) }, 1000f, 24f)
        r.down(1, 600f, 200f, 0)
        r.move(1, 680f, 200f, 16_000_000)
        r.sample(2_000_000_000)
        assertTrue(r.snapshot().inputs.single().x > 0)
        r.move(1, 850f, 240f, 2_010_000_000)
        assertTrue(r.snapshot().inputs.isEmpty())
        r.move(1, 680f, 200f, 2_020_000_000)
        assertEquals(ControlTouch(touchpad.id, 0f, 0f), r.snapshot().inputs.single())
        r.up(1)
        assertTrue(r.snapshot().sticks.isEmpty())
    }
}
