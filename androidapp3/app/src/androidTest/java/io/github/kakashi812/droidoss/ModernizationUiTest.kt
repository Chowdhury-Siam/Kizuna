package io.github.kakashi812.droidoss

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import io.github.kakashi812.droidoss.layout.*
import io.github.kakashi812.droidoss.ui.*
import io.github.kakashi812.droidoss.ui.theme.KizunaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ModernizationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun gamePickerSearchSelectsTheExactProfile() {
        var chosen: String? = null
        compose.setContent { KizunaTheme { GamePresetDialog(onChoose = { chosen = it }, onDismiss = {}) } }
        compose.onNodeWithText("Find a game").performTextInput("Requiem")
        compose.onNodeWithText("Resident Evil Requiem").performClick()
        compose.runOnIdle { assertEquals("re-first-2", chosen) }
    }

    @Test fun profileSettingsSaveGameAndInput() {
        var saved: ControllerLayout? = null
        compose.setContent {
            KizunaTheme(themeMode = "Light") {
                NarrowPhone { ProfileSettingsScreen(steamPreset("Racing"), onSave = { saved = it }, onCancel = {}) }
            }
        }
        compose.onNodeWithText("Game / Steam title").performTextReplacement("Steam game")
        compose.onNodeWithText("Game / Steam title").performImeAction()
        val save = compose.onNode(hasText("Save profile") and hasClickAction()).performScrollTo()
        assertInsidePhone(save)
        save.performClick()
        compose.waitUntil(5_000) { saved != null }
        compose.runOnIdle {
            assertEquals("Steam game", saved?.game)
            assertTrue(saved!!.input.analogTriggers)
        }
    }

    @Test fun accessibleButtonCanBeHeldAndReleased() {
        compose.setContent { KizunaTheme(themeMode = "Dark") { PadScreen(null, defaultLayout()) } }
        // TalkBack invokes the semantic action. A physical tap presses and releases normally.
        compose.onNodeWithContentDescription("A").performSemanticsAction(SemanticsActions.OnClick) { it() }
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Held"))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Released"))
    }

    @Test fun previewIsNotCoveredByTheToolbar() {
        compose.setContent { KizunaTheme { LayoutEditorScreen(defaultLayout().copy(id = "custom1"), onSave = {}, onCancel = {}) } }
        val preview = compose.onNodeWithContentDescription("Layout preview").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithText("Save").fetchSemanticsNode().boundsInRoot
        assertTrue("Editor toolbar must not cover controls", preview.top >= save.bottom)
    }

    @Test fun editorCanChooseTouchpadAndSaveSensitivity() {
        var saved: ControllerLayout? = null
        compose.setContent { KizunaTheme { NarrowPhone { LayoutEditorScreen(defaultLayout().copy(id = "custom1"), onSave = { saved = it }, onCancel = {}) } } }
        compose.onNodeWithText("Controls").performClick()
        compose.onNodeWithText("Right stick").performScrollTo().performClick()
        compose.onNodeWithText("Input").performClick()
        compose.onNodeWithText("Touchpad").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Touchpad sensitivity").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(12f) }
        compose.onNodeWithContentDescription("Game deadzone compensation").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.42f) }
        compose.onNodeWithContentDescription("Close inspector").performClick()
        val save = compose.onNode(hasText("Save") and hasClickAction())
        assertInsidePhone(save)
        save.performClick()
        compose.waitUntil(5_000) { saved != null }
        compose.runOnIdle {
            val right = requireNotNull(saved).elements.filterIsInstance<StickElement>().single { it.stick == Stick.RIGHT }
            assertEquals(StickMode.TOUCHPAD, right.mode)
            assertEquals(12f, right.touchpadSensitivity, 0.01f)
            assertEquals(.42f, right.touchpadDeadzoneCompensation, .001f)
        }
    }

    @Test fun editorCanHideControlAndUndo() {
        compose.setContent {
            KizunaTheme { LayoutEditorScreen(defaultLayout().copy(id = "custom1"), onSave = {}, onCancel = {}) }
        }
        compose.onNodeWithText("Controls", useUnmergedTree = true).performClick()
        compose.onNodeWithText("A").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Control visible").performClick()
        compose.onNodeWithContentDescription("Control visible").assertIsOff()
        compose.onNodeWithContentDescription("Close inspector").performClick()
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithText("Controls").performClick()
        compose.onNodeWithText("A").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Control visible").assertIsOn()
    }

    @Test fun dpadThumbSlidesThroughDirectionsAndReleasesOutside() {
        val layout = defaultLayout().copy(elements = listOf(DpadElement("dpad", .5f, .5f, .4f)))
        compose.setContent { KizunaTheme { NarrowPhone { PadScreen(null, layout) } } }
        val pad = compose.onNodeWithContentDescription("Directional pad")
        fun direction(value: String) = pad.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))
        pad.performTouchInput { down(Offset(width * .5f, height * .17f)) }
        direction("Up")
        pad.performTouchInput { moveTo(Offset(width * .83f, height * .17f)) }
        direction("Up + Right")
        pad.performTouchInput { moveTo(Offset(width * .83f, height * .5f)) }
        direction("Right")
        pad.performTouchInput { moveTo(Offset(width * 1.2f, height * .5f)) }
        direction("Released")
        pad.performTouchInput { up() }
        direction("Released")
    }

    @Test fun dpadEditorSavesDirectionModeAndDeadzone() {
        var saved: ControllerLayout? = null
        compose.setContent { KizunaTheme { NarrowPhone {
            LayoutEditorScreen(defaultLayout().copy(id = "custom1"), onSave = { saved = it }, onCancel = {})
        } } }
        compose.onNodeWithText("Controls").performClick()
        compose.onNodeWithText("D-pad").performScrollTo().performClick()
        compose.onNodeWithText("Input").performClick()
        compose.onNodeWithText("4-way").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Center deadzone").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.1f) }
        compose.onNodeWithContentDescription("Close inspector").performClick()
        compose.onNode(hasText("Save") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { saved != null }
        compose.runOnIdle {
            val dpad = requireNotNull(saved).elements.filterIsInstance<DpadElement>().single()
            assertFalse(dpad.diagonals)
            assertEquals(.1f, dpad.deadzone, .001f)
        }
    }

    @Test fun touchpadPausesOutsideAndReentryDoesNotJump() {
        val pad = StickElement("camera", .5f, .5f, .4f, stick = Stick.RIGHT, mode = StickMode.TOUCHPAD)
        compose.setContent { KizunaTheme { NarrowPhone { PadScreen(null, defaultLayout().copy(elements = listOf(pad))) } } }
        val target = compose.onNodeWithContentDescription("RIGHT touchpad")
        target.performTouchInput {
            down(center)
            moveTo(Offset(width * .6f, height * .5f))
            moveTo(Offset(width * 1.15f, height * .1f))
        }
        target.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Stopped"))
        target.performTouchInput { moveTo(Offset(width * .75f, height * .5f)) }
        target.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Stopped"))
        target.performTouchInput { up() }
        target.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Stopped"))
    }

    private fun assertInsidePhone(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val viewport = compose.onNodeWithTag("Phone viewport").getUnclippedBoundsInRoot()
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue("Action must fit the phone width", bounds.left >= viewport.left && bounds.right <= viewport.right)
        assertTrue("Action must fit the phone height", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
    }
}

@Composable
private fun NarrowPhone(content: @Composable () -> Unit) {
    Box(Modifier.width(320.dp).fillMaxHeight().testTag("Phone viewport")) { content() }
}
