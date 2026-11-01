package io.github.kakashi812.droidoss.layout

import org.junit.Assert.*
import org.junit.Test

class TouchpadTest {
    @Test fun landingAnywhereDoesNotMoveTheStick() {
        val motion = TouchpadMotion(0.8f, -0.4f, 0)
        assertEquals(ControlTouch("right"), motion.sample("right", 1))
        motion.move(0.8f, -0.4f, 16_000_000)
        assertEquals(ControlTouch("right"), motion.sample("right", 16_000_000))
    }
    @Test fun relativeMotionStopsWhenStillAndDoesNotDependOnLandingPosition() {
        val a = TouchpadMotion(0f, 0f, 0)
        val b = TouchpadMotion(0.5f, 0.5f, 0)
        a.move(0.25f, -0.25f, 16_000_000); b.move(0.75f, 0.25f, 16_000_000)
        assertEquals(a.sample("pad", 20_000_000), b.sample("pad", 20_000_000))
        assertTrue(a.sample("pad", 20_000_000).x > 0f)
        assertEquals(ControlTouch("pad"), a.sample("pad", 76_000_000))
    }
    @Test fun velocityIsFrameRateIndependentAndDirectionIsXInputCompatible() {
        val slow = TouchpadMotion(0f, 0f, 0); val fast = TouchpadMotion(0f, 0f, 0)
        slow.move(0.5f, 0f, 32_000_000); fast.move(0.25f, 0f, 16_000_000)
        assertEquals(slow.sample("pad", 32_000_000).x, fast.sample("pad", 16_000_000).x, 0.001f)
        val (x, y) = touchpadAxes(0.25f, -0.25f, 1f)
        assertTrue(x > 0); assertEquals(x, y)
    }
    @Test fun gainIsIndependentFromJoystickDeadzoneAndReleaseIsNeutral() {
        val element = defaultLayout().elements.filterIsInstance<StickElement>().first { it.stick == Stick.RIGHT }.copy(mode = StickMode.TOUCHPAD)
        val layout = defaultLayout().copy(elements = listOf(element), input = InputSettings(right = StickSettings(deadzone = 0.8f)))
        val touch = listOf(ControlTouch(element.id, 0.1f, 0f))
        val normal = mixInput(layout, touch)
        val high = mixInput(layout.copy(elements = listOf(element.copy(touchpadSensitivity = 2f))), touch)
        assertTrue(normal.thumbRX > 0); assertTrue(high.thumbRX > normal.thumbRX)
        assertEquals(0.toShort(), normal.thumbLX)
        assertEquals(0.toShort(), mixInput(layout, emptyList()).thumbRX)
        val (x, y) = touchpadAxes(100f, 100f, 4f)
        assertTrue(x > 0 && y < 0)
    }
    @Test fun ordinarySlowSwipesCrossTheRecommendedXInputCameraDeadzone() {
        val motion = TouchpadMotion(.5f, -.2f, 0)
        motion.move(.52f, -.2f, 16_000_000)
        val moved = motion.sample("right", 16_000_000)
        val (right, vertical) = touchpadAxes(moved.x, moved.y, 1f)
        assertTrue("Slow camera swipe must exceed XInput's recommended 8689 deadzone", right > 8689)
        assertEquals(0.toShort(), vertical)
        val stronger = touchpadAxes(moved.x, moved.y, 2f).first
        assertTrue(stronger > right)
        val stopped = motion.sample("right", 76_000_000)
        assertEquals(0.toShort() to 0.toShort(), touchpadAxes(stopped.x, stopped.y, 1f))
    }
    @Test fun landingJitterStaysNeutralButIntentionalTravelAccumulates() {
        val motion = TouchpadMotion(0f, 0f, 0)
        motion.move(.001f, 0f, 8_000_000)
        motion.move(-.001f, .001f, 16_000_000)
        assertEquals(ControlTouch("right"), motion.sample("right", 16_000_000))
        motion.move(.004f, 0f, 24_000_000)
        assertEquals(ControlTouch("right"), motion.sample("right", 24_000_000))
        motion.move(.008f, 0f, 32_000_000)
        assertTrue(motion.sample("right", 32_000_000).x > 0f)
    }
    @Test fun compensationCanBeDisabledAndNeverCreatesIdleDrift() {
        assertEquals(0.toShort() to 0.toShort(), touchpadAxes(0f, 0f, 4f, .6f))
        val raw = touchpadAxes(.01f, 0f, 1f, 0f).first
        assertTrue(raw.toInt() in 327..328)
        assertTrue(touchpadAxes(.01f, 0f, 1f, .4f).first > raw)
        val (x, y) = touchpadAxes(.25f, -.25f, 1f, .4f)
        assertTrue(x > 0); assertEquals(x, y)
        assertEquals(0.toShort() to 0.toShort(), touchpadAxes(Float.NaN, 1f, 1f, .4f))
    }
    @Test fun settingsPersistInFilesQrAndStoreWithOldLayoutsStillReadable() {
        val e = defaultLayout().elements.filterIsInstance<StickElement>().last().copy(mode = StickMode.TOUCHPAD, touchpadSensitivity = 2.25f, touchpadDeadzoneCompensation = .42f)
        val layout = defaultLayout().copy(id = "custom1", elements = listOf(e))
        for (text in listOf(LayoutShare.encodeFile(layout), LayoutShare.encodeQr(layout))) {
            val decoded = LayoutShare.decode(text) as ImportResult.Ok
            assertEquals(e, decoded.elements.single())
        }
        assertEquals(layout, LayoutCodec.decode(LayoutCodec.encode(LayoutStoreData(activeId = layout.id, custom = listOf(layout)))).custom.single())
        val old = """{"format":"droidoss-layout","version":1,"name":"Old","elements":[{"type":"stick","id":"old","x":0.5,"y":0.5,"size":0.1,"stick":"RIGHT"}]}"""
        val imported = (LayoutShare.decode(old) as ImportResult.Ok).elements.single() as StickElement
        assertEquals(StickMode.JOYSTICK, imported.mode); assertEquals(1f, imported.touchpadSensitivity, 0f)
        assertEquals(.28f, imported.touchpadDeadzoneCompensation, 0f)
        assertTrue(LayoutShare.decode(old.replace("\"version\":1", "\"version\":2")) is ImportResult.Ok)
    }
    @Test fun invalidSettingsAreBoundedAndHistoryRestoresMode() {
        val e = defaultLayout().elements.filterIsInstance<StickElement>().first()
        assertEquals(1f, e.copy(touchpadSensitivity = Float.NaN).sanitized().touchpadSensitivity, 0f)
        assertEquals(4f, e.copy(touchpadSensitivity = 999f).sanitized().touchpadSensitivity, 0f)
        assertEquals(.28f, e.copy(touchpadDeadzoneCompensation = Float.NaN).sanitized().touchpadDeadzoneCompensation, 0f)
        assertEquals(.6f, e.copy(touchpadDeadzoneCompensation = 999f).sanitized().touchpadDeadzoneCompensation, 0f)
        val h = EditHistory<StickElement>(); h.checkpoint(e)
        val pad = e.copy(mode = StickMode.TOUCHPAD)
        assertEquals(e, h.undo(pad)); assertEquals(pad, h.redo(e))
    }
}
