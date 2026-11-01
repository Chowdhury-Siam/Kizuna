package io.github.kakashi812.droidoss

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
                ProfileSettingsScreen(steamPreset("Racing"), onSave = { saved = it }, onCancel = {})
            }
        }
        compose.onNodeWithText("Game / Steam title").performTextReplacement("Steam game")
        compose.onNodeWithText("Save profile").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Steam game", saved?.game)
            assertTrue(saved!!.input.analogTriggers)
        }
    }

    @Test fun accessibleButtonCanBeHeldAndReleased() {
        compose.setContent { KizunaTheme(themeMode = "Dark") { PadScreen(null, defaultLayout()) } }
        compose.onNodeWithContentDescription("A").performClick()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Held"))
            .performClick()
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
        compose.setContent { KizunaTheme { LayoutEditorScreen(defaultLayout().copy(id = "custom1"), onSave = { saved = it }, onCancel = {}) } }
        compose.onNodeWithText("Controls").performClick()
        compose.onNodeWithText("Right stick").performScrollTo().performClick()
        compose.onNodeWithText("Input").performClick()
        compose.onNodeWithText("Touchpad").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Touchpad sensitivity").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2f) }
        compose.runOnIdle {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val target = java.io.File(context.getExternalFilesDir(null), "editor-touchpad.png")
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        compose.onNodeWithContentDescription("Close inspector").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            val right = saved!!.elements.filterIsInstance<StickElement>().single { it.stick == Stick.RIGHT }
            assertEquals(StickMode.TOUCHPAD, right.mode)
            assertEquals(2f, right.touchpadSensitivity, 0.01f)
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
}
