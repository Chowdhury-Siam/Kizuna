package io.github.kakashi812.droidoss.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kakashi812.droidoss.layout.*
import io.github.kakashi812.droidoss.protocol.GamepadButton
import java.util.UUID
import kotlin.math.roundToInt

/** An unobstructed, aspect-correct preview beside a compact inspector. */
@Composable
fun LayoutEditorScreen(
    initial: ControllerLayout,
    onSave: (ControllerLayout) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elements = remember(initial) { initial.elements.toMutableStateList() }
    val history = remember(initial) { EditHistory<List<ControlElement>>() }
    var revision by remember(initial) { mutableIntStateOf(0) }
    var selectedId by remember(initial) { mutableStateOf<String?>(null) }
    var inspector by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var sliderEditing by remember { mutableStateOf(false) }
    fun checkpoint() { history.checkpoint(elements.toList()); revision++ }
    fun restore(value: List<ControlElement>) { elements.clear(); elements.addAll(value); revision++ }
    fun change(id: String, edit: (ControlElement) -> ControlElement) {
        val index = elements.indexOfFirst { it.id == id }
        if (index >= 0) elements[index] = edit(elements[index])
    }
    fun action(block: () -> Unit) { checkpoint(); sliderEditing = false; block() }
    fun sliderChange(block: () -> Unit) {
        if (!sliderEditing) { checkpoint(); sliderEditing = true }
        block()
    }
    val selected = elements.firstOrNull { it.id == selectedId }
    val palette = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxSize().background(palette.background)) {
        val aspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val wide = maxWidth >= 600.dp
        fun positioned(e: ControlElement, x: Float = e.x, y: Float = e.y, size: Float = e.size): ControlElement {
            val radius = size / 2f
            val ry = (radius * aspect).coerceAtMost(0.5f)
            return e.with(x = x.coerceIn(radius, 1f - radius), y = y.coerceIn(ry, 1f - ry), size = size)
        }
        Column(Modifier.fillMaxSize()) {
            Surface(color = palette.surfaceContainer, tonalElevation = 1.dp) {
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        EditorIcon("‹", "Cancel editing", onClick = onCancel)
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(if (wide) initial.name else "Edit layout", style = MaterialTheme.typography.titleSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (wide) Text("Tap to edit · Drag to move", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                        }
                        if (wide) {
                            EditorIcon("↶", "Undo", enabled = revision >= 0 && history.canUndo) { restore(history.undo(elements.toList())) }
                            EditorIcon("↷", "Redo", enabled = history.canRedo) { restore(history.redo(elements.toList())) }
                            TextButton(onClick = { picker = true }) { Text("Controls") }
                        }
                        Box {
                            EditorIcon("⋮", "More editor actions") { menu = true }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(if (showGrid) "Hide grid" else "Show grid") }, onClick = { showGrid = !showGrid; menu = false })
                                DropdownMenuItem(text = { Text("Reset layout") }, onClick = { resetDialog = true; menu = false })
                            }
                        }
                        Button(onClick = { onSave(initial.copy(elements = elements.toList())) }, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Save") }
                    }
                    if (!wide) {
                        // Keep Save reachable when the selected-control action appears on a narrow phone.
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            EditorIcon("↶", "Undo", enabled = revision >= 0 && history.canUndo) { restore(history.undo(elements.toList())) }
                            EditorIcon("↷", "Redo", enabled = history.canRedo) { restore(history.redo(elements.toList())) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { picker = true }) { Text("Controls") }
                            if (selected != null) TextButton(onClick = { inspector = true }) { Text("Edit") }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().background(palette.surfaceContainerLowest), contentAlignment = Alignment.Center) {
                    // Letterboxing preserves the live pad's proportions; editor chrome never covers a control.
                    BoxWithConstraints(Modifier.aspectRatio(aspect)) {
                        val w = constraints.maxWidth.toFloat()
                        val h = constraints.maxHeight.toFloat()
                        val minimumHit = with(LocalDensity.current) { 20.dp.toPx() }
                        val placed = elements.place(w, h)
                        val labels = rememberControlLabels(placed)
                        val foreground = palette.onBackground
                        val primary = palette.primary
                        fun hit(position: Offset) = elements.place(w, h).lastOrNull { !it.element.hidden && it.contains(position, minimumHit) }
                        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Layout preview" }
                            .pointerInput(w, h) {
                                detectTapGestures { position ->
                                    val found = hit(position)
                                    selectedId = found?.element?.id
                                    inspector = found != null
                                    sliderEditing = false
                                }
                            }
                            .pointerInput(w, h) {
                                var dragging: String? = null
                                detectDragGestures(
                                    onDragStart = { position ->
                                        dragging = hit(position)?.element?.id
                                        if (dragging != null) { selectedId = dragging; checkpoint() }
                                    },
                                    onDragEnd = { if (dragging != null) inspector = true; dragging = null },
                                    onDragCancel = { dragging = null },
                                    onDrag = { event, delta ->
                                        val id = dragging
                                        if (id != null) {
                                            event.consume()
                                            change(id) { positioned(it, it.x + delta.x / w, it.y + delta.y / h) }
                                        }
                                    },
                                )
                            }) {
                            if (showGrid) for (step in 1 until 20) {
                                drawLine(foreground.copy(alpha = 0.07f), Offset(w * step / 20, 0f), Offset(w * step / 20, h))
                                drawLine(foreground.copy(alpha = 0.07f), Offset(0f, h * step / 20), Offset(w, h * step / 20))
                            }
                            placed.filter { !it.element.hidden }.forEach { p ->
                                drawControl(p, emptyMap(), emptyMap(), labels, foreground)
                                if (p.element.id == selectedId) {
                                    if (p.element is StickElement && p.element.mode == StickMode.TOUCHPAD)
                                        drawRoundRect(primary, p.centre - Offset(p.radius + 4, p.radius * 0.7f + 4),
                                            Size(p.radius * 2 + 8, p.radius * 1.4f + 8), CornerRadius(12f), style = Stroke(3f))
                                    else drawCircle(primary, p.radius + 4, p.centre, style = Stroke(3f))
                                }
                            }
                        }
                    }
                }
                if (wide && inspector && selected != null) {
                    VerticalDivider()
                    Inspector(selected, tab, { tab = it }, { sliderEditing = false; inspector = false },
                        Modifier.width(264.dp).fillMaxHeight(),
                        change = { edit -> action { change(selected.id, edit) } },
                        slider = { edit -> sliderChange { change(selected.id, edit) } },
                        finished = { sliderEditing = false },
                        move = { x, y -> sliderChange { change(selected.id) { positioned(it, x ?: it.x, y ?: it.y) } } },
                        align = { x, y -> action { change(selected.id) { positioned(it, x ?: it.x, y ?: it.y) } } },
                        resize = { value -> sliderChange { change(selected.id) { positioned(it, size = value) } } },
                        layer = { front -> action { elements.remove(selected); if (front) elements.add(selected) else elements.add(0, selected) } },
                        duplicate = {
                            action {
                                val copy = positioned(selected.with(id = UUID.randomUUID().toString().take(32)), x = selected.x + 0.04f)
                                elements.add(copy); selectedId = copy.id
                            }
                        }, canDuplicate = elements.size < LayoutShare.MAX_ELEMENTS,
                        reset = { action { change(selected.id) { original -> initial.elements.firstOrNull { it.id == original.id }
                            ?: defaultLayout().elements.firstOrNull { it.id == original.id } ?: original.with(size = 0.1f, opacity = 0.85f, hidden = false) } } },
                    )
                }
            }
        }
        if (!wide && inspector && selected != null) {
            // Compact phones use one bounded dialog instead of squeezing the preview into a strip.
            AlertDialog(onDismissRequest = { inspector = false }, confirmButton = {}, text = {
                Inspector(selected, tab, { tab = it }, { sliderEditing = false; inspector = false }, Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    change = { edit -> action { change(selected.id, edit) } },
                    slider = { edit -> sliderChange { change(selected.id, edit) } }, finished = { sliderEditing = false },
                    resize = { value -> sliderChange { change(selected.id) { positioned(it, size = value) } } },
                    move = { x, y -> sliderChange { change(selected.id) { positioned(it, x ?: it.x, y ?: it.y) } } },
                    align = { x, y -> action { change(selected.id) { positioned(it, x ?: it.x, y ?: it.y) } } },
                    layer = { front -> action { elements.remove(selected); if (front) elements.add(selected) else elements.add(0, selected) } },
                    duplicate = { action { val copy = positioned(selected.with(id = UUID.randomUUID().toString().take(32)), x = selected.x + 0.04f); elements.add(copy); selectedId = copy.id } },
                    canDuplicate = elements.size < LayoutShare.MAX_ELEMENTS,
                    reset = { action { change(selected.id) { original -> initial.elements.firstOrNull { it.id == original.id } ?: original.with(size = 0.1f, opacity = 0.85f, hidden = false) } } },
                )
            })
        }
    }
    if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Text("Choose a control") }, confirmButton = {
        TextButton(onClick = { picker = false }) { Text("Close") }
    }, text = {
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            elements.forEachIndexed { index, e ->
                val name = controlName(e)
                val ordinal = elements.take(index).count { controlName(it) == name } + 1
                ListItem(headlineContent = { Text(if (ordinal > 1) "$name · $ordinal" else name) },
                    supportingContent = { Text(if (e.hidden) "Hidden" else controlKind(e)) },
                    trailingContent = { RadioButton(selected = e.id == selectedId, onClick = null) },
                    modifier = Modifier.clickable { selectedId = e.id; inspector = true; picker = false },
                )
            }
        }
    })
    if (resetDialog) AlertDialog(onDismissRequest = { resetDialog = false }, title = { Text("Reset layout?") },
        text = { Text("Restore the default controls. You can undo this change.") },
        confirmButton = { TextButton(onClick = { action { restore(defaultLayout().elements) }; selectedId = null; inspector = false; resetDialog = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Cancel") } })
}

@Composable
private fun Inspector(
    selected: ControlElement, tab: Int, onTab: (Int) -> Unit, close: () -> Unit, modifier: Modifier,
    change: ((ControlElement) -> ControlElement) -> Unit,
    slider: ((ControlElement) -> ControlElement) -> Unit, finished: () -> Unit,
    resize: (Float) -> Unit, move: (Float?, Float?) -> Unit, align: (Float?, Float?) -> Unit,
    layer: (Boolean) -> Unit, duplicate: () -> Unit, canDuplicate: Boolean, reset: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(controlName(selected), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = !selected.hidden, onCheckedChange = { visible -> change { it.with(hidden = !visible) } },
                    modifier = Modifier.semantics { contentDescription = "Control visible" })
                EditorIcon("×", "Close inspector", onClick = close)
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("Style", "Input", "Arrange").forEachIndexed { index, name ->
                    Tab(selected = tab == index, onClick = { finished(); onTab(index) }, text = { Text(name, style = MaterialTheme.typography.labelMedium) })
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (tab) {
                    0 -> {
                        EditorSlider("Size", selected.size, 0.04f..0.30f, "${(selected.size * 100).roundToInt()}%", resize, finished)
                        EditorSlider("Opacity", selected.opacity, 0.2f..1f, "${(selected.opacity * 100).roundToInt()}%", { value -> slider { it.with(opacity = value) } }, finished)
                        Text("Color", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("Default" to 0xFFE0E4EC, "Mint" to 0xFF72D5AC, "Blue" to 0xFF91BFFF, "Rose" to 0xFFFFB3BA, "Gold" to 0xFFF5D47A).forEach { (name, color) ->
                                Surface(onClick = { change { it.with(color = color) } }, shape = CircleShape,
                                    color = if (color == 0xFFE0E4EC) MaterialTheme.colorScheme.onSurface else Color(color),
                                    border = if (selected.color == color) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier.size(48.dp).semantics { contentDescription = "$name color" }) {}
                            }
                        }
                    }
                    1 -> when (selected) {
                        is StickElement -> {
                            StickModeSelector(selected, onChange = { value -> change { value } })
                            if (selected.mode == StickMode.TOUCHPAD) {
                                EditorSlider("Touchpad sensitivity", selected.touchpadSensitivity, 0.25f..MAX_TOUCHPAD_SENSITIVITY, "%.2f×".format(selected.touchpadSensitivity),
                                    { value -> slider { (it as StickElement).copy(touchpadSensitivity = value) } }, finished)
                                EditorSlider("Game deadzone compensation", selected.touchpadDeadzoneCompensation, 0f..0.6f,
                                    "${(selected.touchpadDeadzoneCompensation * 100).roundToInt()}%",
                                    { value -> slider { (it as StickElement).copy(touchpadDeadzoneCompensation = value) } }, finished)
                                Text((if (selected.stick == Stick.RIGHT) "Swipe to look around. " else "Swipe to move. ") + "Raise sensitivity for faster swipes. Leaving the pad pauses input; lift to reposition.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else Text("A fixed joystick with your profile's deadzone and response curve.", style = MaterialTheme.typography.bodySmall)
                            Text("Output stick", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Stick.entries.forEach { stick -> FilterChip(selected = selected.stick == stick, onClick = { change { (it as StickElement).copy(stick = stick) } }, label = { Text(if (stick == Stick.LEFT) "Left" else "Right") }) }
                            }
                        }
                        is ButtonElement -> {
                            var mapping by remember(selected.id) { mutableStateOf(false) }
                            Text("Button action", style = MaterialTheme.typography.labelLarge)
                            Box {
                                OutlinedButton(onClick = { mapping = true }, modifier = Modifier.fillMaxWidth()) { Text(buttonMappings.firstOrNull { it.second == selected.mask }?.first ?: selected.label) }
                                DropdownMenu(mapping, { mapping = false }) {
                                    buttonMappings.forEach { (label, mask) -> DropdownMenuItem(text = { Text(label) }, onClick = { change { (it as ButtonElement).copy(mask = mask, label = label) }; mapping = false }) }
                                }
                            }
                        }
                        is TriggerElement -> {
                            Text("Trigger output", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Trigger.entries.forEach { trigger ->
                                FilterChip(selected = selected.trigger == trigger, onClick = { change { (it as TriggerElement).copy(trigger = trigger, label = if (trigger == Trigger.LEFT) "LT" else "RT") } }, label = { Text(if (trigger == Trigger.LEFT) "Left" else "Right") })
                            } }
                        }
                        is DpadElement -> {
                            Text("Directions", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(false to "4-way", true to "8-way").forEach { (diagonals, label) ->
                                    FilterChip(selected = selected.diagonals == diagonals,
                                        onClick = { change { (it as DpadElement).copy(diagonals = diagonals) } }, label = { Text(label) })
                                }
                            }
                            EditorSlider("Center deadzone", selected.deadzone, 0f..0.6f, "${(selected.deadzone * 100).roundToInt()}%",
                                { value -> slider { (it as DpadElement).copy(deadzone = value) } }, finished)
                            Text("Press an arrow or slide between directions. Diagonals use the gaps between arrows. Slide off to release.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    2 -> {
                        EditorSlider("Horizontal", selected.x, 0f..1f, "${(selected.x * 100).roundToInt()}%", { move(it, null) }, finished)
                        EditorSlider("Vertical", selected.y, 0f..1f, "${(selected.y * 100).roundToInt()}%", { move(null, it) }, finished)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { align(0.5f, null) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Center X") }
                            OutlinedButton(onClick = { align(null, 0.5f) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Center Y") }
                        }
                        TextButton(onClick = { align((selected.x * 20).roundToInt() / 20f, (selected.y * 20).roundToInt() / 20f) }) { Text("Snap to grid") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { layer(false) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Send back") }
                            OutlinedButton(onClick = { layer(true) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Bring front") }
                        }
                        Text("Where controls overlap, the front layer receives touches.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = duplicate, enabled = canDuplicate, modifier = Modifier.fillMaxWidth()) { Text("Duplicate control") }
                        TextButton(onClick = reset, modifier = Modifier.fillMaxWidth()) { Text("Reset this control") }
                    }
                }
                if (selected.hidden) Text("Hidden from play. Restore it here or from Controls.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun StickModeSelector(stick: StickElement, onChange: (StickElement) -> Unit) {
    Text("Control mode", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StickMode.entries.forEach { mode ->
            FilterChip(selected = stick.mode == mode, onClick = { onChange(stick.copy(mode = mode)) },
                label = { Text(if (mode == StickMode.JOYSTICK) "Joystick" else "Touchpad") }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EditorIcon(glyph: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = description }) { Text(glyph, fontSize = 24.sp) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String,
    update: (Float) -> Unit, finished: () -> Unit = {}) {
    val colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), thumbColor = MaterialTheme.colorScheme.primary)
    Row { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge); Text(display, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    val state = remember(range) { SliderState(value.coerceIn(range.start, range.endInclusive), 0, range) }
    // The profile/history owns the value; synchronize after undo, reset or selecting another control.
    state.value = value.coerceIn(range.start, range.endInclusive)
    Slider(state = state, onValueChange = update,
        onValueChangeFinished = finished, colors = colors,
        thumb = { Box(Modifier.size(18.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) },
        track = { state -> SliderDefaults.Track(sliderState = state, colors = colors, modifier = Modifier.height(4.dp),
            drawStopIndicator = null, thumbTrackGapSize = 4.dp) },
        modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = label })
}

internal fun controlName(e: ControlElement): String = when (e) {
    is StickElement -> if (e.stick == Stick.LEFT) "Left stick" else "Right stick"
    is DpadElement -> "D-pad"
    is TriggerElement -> if (e.trigger == Trigger.LEFT) "Left trigger" else "Right trigger"
    is ButtonElement -> when (e.mask) { 0x10 -> "Start"; 0x20 -> "Back"; 0x40 -> "Left stick click"; 0x80 -> "Right stick click"; 0x100 -> "Left shoulder"; 0x200 -> "Right shoulder"; else -> e.label }
}
private fun controlKind(e: ControlElement): String = when (e) {
    is StickElement -> if (e.mode == StickMode.TOUCHPAD) "Relative touchpad" else "Analog joystick"
    is DpadElement -> "Directional pad"
    is TriggerElement -> "Trigger"
    is ButtonElement -> "Button"
}

internal val buttonMappings = listOf(
    "A" to GamepadButton.A, "B" to GamepadButton.B, "X" to GamepadButton.X, "Y" to GamepadButton.Y,
    "LB" to GamepadButton.LEFT_SHOULDER, "RB" to GamepadButton.RIGHT_SHOULDER,
    "L3" to GamepadButton.LEFT_THUMB, "R3" to GamepadButton.RIGHT_THUMB,
    "Start" to GamepadButton.START, "Back" to GamepadButton.BACK, "Guide" to GamepadButton.GUIDE,
    "Up" to 1, "Down" to 2, "Left" to 4, "Right" to 8,
)
