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
        motion.move(.0015f, 0f, 24_000_000)
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
    @Test fun everyEdgeAndCornerContinuesWhileHeldAndInwardSwipesRespond() {
        val edges = listOf(1.1f to 0f, -1.1f to 0f, 0f to .8f, 0f to -.8f,
            1.1f to -.8f, -1.1f to -.8f, 1.1f to .8f, -1.1f to .8f)
        for ((x, y) in edges) {
            val motion = TouchpadMotion(0f, 0f, 0)
            motion.move(x, y, 16_000_000)
            val edge = motion.sample("pad", 16_000_000)
            assertEquals(1f, kotlin.math.hypot(edge.x, edge.y), .0001f)
            assertTrue(edge.x * x + edge.y * y > 0f)
            // No new pointer events are needed for continued camera movement at the edge.
            assertEquals(edge, motion.sample("pad", 2_000_000_000))
            motion.move(x * 1.2f, y * 1.2f, 2_016_000_000)
            val outside = motion.sample("pad", 3_000_000_000)
            assertEquals(edge.x, outside.x, .0001f)
            assertEquals(edge.y, outside.y, .0001f)
            motion.move(x * .4f, y * .4f, 3_016_000_000)
            val inward = motion.sample("pad", 3_016_000_000)
            assertTrue("An inward swipe must respond without lifting", inward.x * x + inward.y * y < 0f)
            assertEquals(ControlTouch("pad"), motion.sample("pad", 3_076_000_000))
        }
    }
    @Test fun edgeLandingDoesNotTurnAndMinorCornerJitterCannotChangeDirection() {
        val landing = TouchpadMotion(.98f, -.68f, 0)
        landing.move(.98f, -.68f, 16_000_000)
        assertEquals(ControlTouch("pad"), landing.sample("pad", 2_000_000_000))
        val motion = TouchpadMotion(.8f, -.6f, 0)
        motion.move(.98f, -.6f, 16_000_000)
        val right = ControlTouch("pad", 1f, 0f)
        assertEquals(right, motion.sample("pad", 16_000_000))
        motion.move(.979f, -.602f, 32_000_000)
        motion.move(.982f, -.599f, 48_000_000)
        assertEquals(right, motion.sample("pad", 2_000_000_000))
        // Deliberate movement along an edge can steer the continued turn.
        motion.move(.98f, -.67f, 2_016_000_000)
        assertEquals(ControlTouch("pad", 0f, -1f), motion.sample("pad", 3_000_000_000))
    }
    @Test fun reversalOutsideThePadIsImmediateAndDoesNotStick() {
        val motion = TouchpadMotion(.8f, 0f, 0)
        motion.move(1.1f, 0f, 16_000_000)
        assertEquals(1f, motion.sample("pad", 1_000_000_000).x, 0f)
        motion.move(1.07f, 0f, 1_016_000_000)
        assertTrue(motion.sample("pad", 1_016_000_000).x < 0f)
        assertEquals(ControlTouch("pad"), motion.sample("pad", 1_076_000_000))
    }
    @Test fun slowSwipesProduceStrongerOutputAndDoNotDropOutBetweenSamples() {
        val motion = TouchpadMotion(0f, 0f, 0)
        // The old 0.006-radius threshold ignored all these deliberate small movements.
        for (i in 1..6) {
            val now = i * 16_000_000L
            motion.move(i * .0025f, 0f, now)
            val touch = motion.sample("pad", now)
            assertTrue(touch.x > 0f)
            val one = touchpadAxes(touch.x, touch.y, 1f).first
            val four = touchpadAxes(touch.x, touch.y, 4f).first
            val sixteen = touchpadAxes(touch.x, touch.y, 16f).first
            assertTrue(one > 8689)
            assertTrue(four > one)
            assertTrue(sixteen > four)
        }
        // A 0.02-radius / 16 ms swipe at 4x now reaches full travel (formerly 80%).
        val normal = TouchpadMotion(0f, 0f, 0)
        normal.move(.02f, 0f, 16_000_000)
        val touch = normal.sample("pad", 16_000_000)
        assertEquals(32767.toShort(), touchpadAxes(touch.x, touch.y, 4f).first)
    }
    @Test fun firstSwipeAfterAnIdlePauseIsNotDilutedByThePauseDuration() {
        val moving = TouchpadMotion(0f, 0f, 0)
        moving.move(.02f, 0f, 16_000_000)
        val paused = TouchpadMotion(0f, 0f, 0)
        paused.move(.02f, 0f, 2_016_000_000)
        assertEquals(moving.sample("pad", 16_000_000), paused.sample("pad", 2_016_000_000))
    }
    @Test fun lateBatchedDeliveryUsesEventTimeWithoutVelocitySpikes() {
        val normal = TouchpadMotion(0f, 0f, 0)
        normal.move(.02f, 0f, 16_000_000)
        normal.move(.04f, 0f, 32_000_000)
        val delayed = TouchpadMotion(0f, 0f, 0)
        delayed.move(.02f, 0f, now = 1_000_000_000, eventTime = 16_000_000)
        delayed.move(.04f, 0f, now = 1_000_001_000, eventTime = 32_000_000)
        assertEquals(normal.sample("pad", 32_000_000), delayed.sample("pad", 1_000_001_000))
        assertEquals(ControlTouch("pad"), delayed.sample("pad", 1_060_001_000))
    }
    @Test fun invalidOrReorderedMotionStopsAndRequiresANewAnchor() {
        val motion = TouchpadMotion(0f, 0f, 0)
        motion.move(.1f, 0f, 16_000_000)
        motion.move(.2f, 0f, now = 20_000_000, eventTime = 15_000_000)
        assertEquals(ControlTouch("pad"), motion.sample("pad", 20_000_000))
        motion.move(.2f, 0f, 32_000_000)
        assertEquals(ControlTouch("pad"), motion.sample("pad", 32_000_000))
        motion.move(.3f, 0f, 48_000_000)
        assertTrue(motion.sample("pad", 48_000_000).x > 0f)
        motion.move(Float.NaN, 0f, 64_000_000)
        assertEquals(ControlTouch("pad"), motion.sample("pad", 64_000_000))
    }
    @Test fun higherGainWorksAboveFourWithoutOverflowOrIdleDrift() {
        val four = touchpadAxes(.02f, 0f, 4f).first
        val sixteen = touchpadAxes(.02f, 0f, 16f).first
        assertTrue(sixteen > four)
        assertEquals(32767.toShort(), touchpadAxes(.1f, 0f, 16f).first)
        assertEquals((-32767).toShort(), touchpadAxes(-.1f, 0f, 16f).first)
        assertEquals(0.toShort() to 0.toShort(), touchpadAxes(0f, 0f, 16f))
    }
    @Test fun settingsPersistInFilesQrAndStoreWithOldLayoutsStillReadable() {
        val e = defaultLayout().elements.filterIsInstance<StickElement>().last().copy(mode = StickMode.TOUCHPAD, touchpadSensitivity = 12.5f, touchpadDeadzoneCompensation = .42f)
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
        assertEquals(16f, e.copy(touchpadSensitivity = 999f).sanitized().touchpadSensitivity, 0f)
        assertEquals(.28f, e.copy(touchpadDeadzoneCompensation = Float.NaN).sanitized().touchpadDeadzoneCompensation, 0f)
        assertEquals(.6f, e.copy(touchpadDeadzoneCompensation = 999f).sanitized().touchpadDeadzoneCompensation, 0f)
        val h = EditHistory<StickElement>(); h.checkpoint(e)
        val pad = e.copy(mode = StickMode.TOUCHPAD)
        assertEquals(e, h.undo(pad)); assertEquals(pad, h.redo(e))
    }
}
