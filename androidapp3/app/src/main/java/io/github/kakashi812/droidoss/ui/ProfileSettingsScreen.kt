package io.github.kakashi812.droidoss.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.kakashi812.droidoss.layout.*

@Composable
fun ProfileSettingsScreen(initial: ControllerLayout, onSave: (ControllerLayout) -> Unit, onCancel: () -> Unit) {
    var profile by remember(initial) { mutableStateOf(initial) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tune ${profile.name}", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(profile.game, { profile = profile.copy(game = it.take(80)) }, label = { Text("Game / Steam title") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        Text("Steam receives a standard Xbox 360 controller. These settings change this profile's input.", style = MaterialTheme.typography.bodySmall)
        listOf(Stick.LEFT, Stick.RIGHT).forEach { side ->
            val s = if (side == Stick.LEFT) profile.input.left else profile.input.right
            fun update(value: StickSettings) {
                profile = profile.copy(input = if (side == Stick.LEFT) profile.input.copy(left = value) else profile.input.copy(right = value))
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${side.name.lowercase().replaceFirstChar { it.uppercase() }} stick", style = MaterialTheme.typography.titleMedium)
                    val control = profile.elements.filterIsInstance<StickElement>().firstOrNull { it.stick == side }
                    if (control != null) {
                        fun updateControl(value: StickElement) { profile = profile.copy(elements = profile.elements.map { if (it.id == control.id) value else it }) }
                        StickModeSelector(control, onChange = ::updateControl)
                        if (control.mode == StickMode.TOUCHPAD) {
                            TuneSlider("Touchpad sensitivity", control.touchpadSensitivity, 0.25f..4f) { updateControl(control.copy(touchpadSensitivity = it)) }
                            TuneSlider("Game deadzone compensation", control.touchpadDeadzoneCompensation, 0f..0.6f) {
                                updateControl(control.copy(touchpadDeadzoneCompensation = it))
                            }
                            Text((if (side == Stick.RIGHT) "Swipes rotate the camera. " else "Swipes control movement. ") + "Raise compensation if slow swipes do nothing. Lift or hold still to stop.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (control?.mode != StickMode.TOUCHPAD) {
                    TuneSlider("Sensitivity", s.sensitivity, 0.25f..2f) { update(s.copy(sensitivity = it)) }
                    TuneSlider("Radial deadzone", s.deadzone, 0f..0.8f) { update(s.copy(deadzone = it)) }
                    TuneSlider("Response curve (1 = linear)", s.curve, 0.3f..3f) { update(s.copy(curve = it)) }
                    Text("Calibration: adjust centre offsets while checking Preview. Reset sets an exact neutral centre.", style = MaterialTheme.typography.bodySmall)
                    TuneSlider("Centre X", s.centreX, -0.25f..0.25f) { update(s.copy(centreX = it)) }
                    TuneSlider("Centre Y", s.centreY, -0.25f..0.25f) { update(s.copy(centreY = it)) }
                    TextButton(onClick = { update(s.copy(centreX = 0f, centreY = 0f)) }) { Text("Reset calibration") }
                    }
                }
            }
        }
        Row {
            Text("Analog slide triggers", Modifier.weight(1f))
            Switch(profile.input.analogTriggers, { profile = profile.copy(input = profile.input.copy(analogTriggers = it)) })
        }
        Text("Slide upward for more travel; digital mode gives full travel on touch.", style = MaterialTheme.typography.bodySmall)
        TuneSlider("Trigger deadzone", profile.input.triggerDeadzone, 0f..0.8f) { profile = profile.copy(input = profile.input.copy(triggerDeadzone = it)) }
        TuneSlider("Trigger travel range", profile.input.triggerRange, 0.2f..1f) { profile = profile.copy(input = profile.input.copy(triggerRange = it)) }
        TuneSlider("Vibration strength", profile.input.vibration, 0f..1f) { profile = profile.copy(input = profile.input.copy(vibration = it)) }
        TextButton(onClick = { vibrate(context, profile.input.vibration) }) { Text("Test vibration") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = { profile = profile.copy(input = InputSettings()) }) { Text("Reset input") }
            Button(onClick = { onSave(profile.copy(input = profile.input.sanitized())) }) { Text("Save profile") }
        }
    }
}

@Composable
private fun TuneSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, update: (Float) -> Unit) {
    Text("$label · ${"%.2f".format(value)}", style = MaterialTheme.typography.labelLarge)
    Slider(value = value, onValueChange = update, valueRange = range, modifier = Modifier.fillMaxWidth())
}
