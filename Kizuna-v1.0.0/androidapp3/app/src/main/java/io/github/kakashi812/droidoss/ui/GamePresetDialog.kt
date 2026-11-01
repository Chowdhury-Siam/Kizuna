package io.github.kakashi812.droidoss.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.kakashi812.droidoss.layout.GamePresets

@Composable
fun GamePresetDialog(onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var series by remember { mutableStateOf("All") }
    val matches = GamePresets.catalog.filter { (series == "All" || it.series == series) && it.title.contains(search, ignoreCase = true) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.widthIn(max = 560.dp).heightIn(max = 560.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Game presets", style = MaterialTheme.typography.titleLarge)
                Text("Tuned starting profiles, ready to customise. Game support varies by edition; legacy titles may need Steam Input.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(search, { search = it }, singleLine = true, label = { Text("Find a game") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Football", "Tomb Raider", "Resident Evil", "Steam").forEach { item -> FilterChip(series == item, { series = item }, label = { Text(item) }) }
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    if (matches.isEmpty()) item { Text("No matching preset", Modifier.padding(12.dp)) }
                    items(matches, key = { it.id }) { preset ->
                        Column(Modifier.fillMaxWidth().clickable { onChoose(preset.id) }.padding(vertical = 10.dp)) {
                            Text(preset.title, style = MaterialTheme.typography.titleSmall)
                            Text(preset.tuning.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            if (preset.note.isNotEmpty()) Text(preset.note, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}
