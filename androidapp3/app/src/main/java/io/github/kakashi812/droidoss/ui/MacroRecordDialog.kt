package io.github.kakashi812.droidoss.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kakashi812.droidoss.layout.*
import kotlinx.coroutines.delay

@Composable
internal fun MacroRecordDialog(layout: ControllerLayout, onRecorded: (List<MacroFrame>) -> Unit, onCancel: () -> Unit) {
    val recorder = remember { MacroRecorder() }
    val preview = remember(layout) { layout.copy(elements = layout.elements.filterNot { it is MacroElement }) }
    var elapsed by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    fun finish() {
        if (done) return
        done = true
        val frames = recorder.finish(SystemClock.uptimeMillis())
        if (frames.isEmpty()) onCancel() else onRecorded(frames)
    }
    LaunchedEffect(recorder) {
        while (!done) {
            delay(50)
            elapsed = recorder.elapsed(SystemClock.uptimeMillis())
            ready = recorder.hasActions
            if (ready && (elapsed >= MAX_MACRO_DURATION_MS || recorder.full)) finish()
        }
    }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassBackdrop {
            PadScreen(null, preview, Modifier.fillMaxSize(), showStatus = false,
                onSnapshot = { recorder.capture(it, SystemClock.uptimeMillis()); ready = recorder.hasActions })
            GlassPanel(strong = true,
                shape = MaterialTheme.shapes.large, modifier = Modifier.align(Alignment.TopCenter).padding(6.dp).widthIn(max = 560.dp).fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (ready) "Recording · %.1fs / 30s".format(elapsed / 1000f) else "Perform the actions to record", Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = ::finish, enabled = ready) { Text("Stop & use") }
                }
            }
        }
    }
}
