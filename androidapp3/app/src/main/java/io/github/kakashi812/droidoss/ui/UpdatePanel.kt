package io.github.kakashi812.droidoss.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kakashi812.droidoss.BuildConfig
import io.github.kakashi812.droidoss.update.AndroidUpdater

@Composable
fun UpdatePanel(updater: AndroidUpdater, automatic: Boolean, onAutomatic: (Boolean) -> Unit, onInstall: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("App updates", style = MaterialTheme.typography.titleSmall)
            Text(updater.status, style = MaterialTheme.typography.bodySmall)
            updater.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Check automatically", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = automatic, onCheckedChange = onAutomatic, enabled = BuildConfig.UPDATE_ENABLED)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = updater::check, enabled = !updater.busy && BuildConfig.UPDATE_ENABLED) { Text("Check now") }
                when {
                    updater.ready != null -> Button(onClick = onInstall, enabled = !updater.busy) { Text("Install update") }
                    updater.available != null -> FilledTonalButton(onClick = updater::download, enabled = !updater.busy) { Text("Download update") }
                }
            }
        }
    }
}
