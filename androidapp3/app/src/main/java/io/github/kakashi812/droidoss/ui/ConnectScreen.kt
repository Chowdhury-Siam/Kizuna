package io.github.kakashi812.droidoss.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kakashi812.droidoss.BuildConfig
import io.github.kakashi812.droidoss.layout.ControllerLayout
import io.github.kakashi812.droidoss.layout.DEFAULT_ID
import io.github.kakashi812.droidoss.layout.MAX_LAYOUTS
import io.github.kakashi812.droidoss.protocol.Protocol
import io.github.kakashi812.droidoss.transport.ConnectionState
import io.github.kakashi812.droidoss.transport.DiscoveredServer

private val REPO_URL = "https://github.com/${BuildConfig.RELEASE_REPOSITORY}"

/**
 * Everything before the pad: pick a server, connect, and manage layouts.
 *
 * The layout gallery lets a user preview and arrange controllers *before*
 * connecting — the pad and the editor both work with no server, so there is
 * nothing to wait for. Kept scrollable because this now holds more than one
 * screenful on a short phone in portrait. Connect, Profiles and Settings now
 * share a floating navigation bar and retain their selection while editing.
 */
@Composable
fun ConnectScreen(
    connectionState: ConnectionState,
    initialHost: String,
    servers: List<DiscoveredServer>,
    scanning: Boolean,
    onScan: () -> Unit,
    layouts: List<ControllerLayout>,
    activeId: String,
    onConnect: (host: String, port: Int) -> Unit,
    onDisconnect: () -> Unit,
    layoutActions: LayoutActions,
    initialDeviceName: String,
    initialSlot: Int,
    themeMode: String,
    onDeviceName: (String) -> Unit,
    onSlot: (Int) -> Unit,
    onTheme: (String) -> Unit,
    playStationButtons: Boolean = false,
    onControllerButtons: (Boolean) -> Unit = {},
    initialSection: Int = 0,
    onSectionChanged: (Int) -> Unit = {},
    updateContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var deviceName by rememberSaveable { mutableStateOf(initialDeviceName) }
    var slot by remember { mutableStateOf(initialSlot) }
    var host by rememberSaveable { mutableStateOf(initialHost) }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableIntStateOf(initialSection.coerceIn(0, 2)) }
    var presetPicker by remember { mutableStateOf(false) }
    val scrollStateHolder = rememberSaveableStateHolder()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    BackHandler(enabled = section != 0) { section = 0; onSectionChanged(0); focus.clearFocus() }

    val busy = connectionState is ConnectionState.Connecting
    val canEdit = connectionState is ConnectionState.Idle ||
        connectionState is ConnectionState.NoServer ||
        connectionState is ConnectionState.ServerFull || connectionState is ConnectionState.SetupFailed

    val colors = MaterialTheme.colorScheme
    val activeName = remember(layouts, activeId) { layouts.firstOrNull { it.id == activeId }?.name ?: "Default" }
    CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
        BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val columns = if (maxWidth >= 360.dp) 2 else 1
            Column(Modifier.widthIn(max = 780.dp).fillMaxWidth().fillMaxHeight()) {
                HomeHeader(activeName)
                scrollStateHolder.SaveableStateProvider(section) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (section) {
                            0 -> {
                                item("status") {
                                    StatusPanel(connectionState)
                                }
                                item("servers") {
                                    ServerList(servers, scanning, initialHost, canEdit, onScan) { server ->
                                        focus.clearFocus()
                                        host = server.host
                                        onConnect(server.host, server.port)
                                    }
                                }
                                item("direct") {
                                    val submit = {
                                        focus.clearFocus()
                                        if (busy) onDisconnect() else if (canEdit && host.isNotBlank()) {
                                            onConnect(host.trim(), Protocol.INPUT_PORT)
                                        }
                                    }
                                    GlassPanel(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Direct connection", style = MaterialTheme.typography.titleMedium)
                                            GlassTextField(host, { host = it }, label = { Text("PC address") }, placeholder = { Text("192.168.1.10") },
                                                singleLine = true, enabled = canEdit, modifier = Modifier.fillMaxWidth(),
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }))
                                            Text("Use the address shown in the Windows dashboard.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                            Button(onClick = submit, enabled = busy || (canEdit && host.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
                                                if (busy) {
                                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.onPrimary)
                                                    Spacer(Modifier.size(8.dp))
                                                }
                                                Text(if (busy) "Cancel" else if (connectionState is ConnectionState.Idle) "Connect" else "Retry")
                                            }
                                        }
                                    }
                                }
                                item("player") {
                                    GlassPanel(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("Your player", style = MaterialTheme.typography.titleMedium)
                                            Text("Occupied slots stay with their current player.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                (-1..3).forEach { player ->
                                                    GlassChoice(slot == player, { slot = player; onSlot(player) },
                                                        label = { Text(if (player == -1) "Auto" else "P${player + 1}") }, enabled = canEdit)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> profileItems(layouts, activeId, columns, layoutActions,
                                onPresets = { presetPicker = true }, onRename = { renamingId = it }, onDelete = { deletingId = it })
                            else -> {
                                item("preferences") {
                                    GlassPanel(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Appearance", style = MaterialTheme.typography.titleMedium)
                                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                listOf("System", "Light", "Dark").forEach { mode ->
                                                    GlassChoice(themeMode == mode, { onTheme(mode) }, label = { Text(mode) })
                                                }
                                            }
                                            HorizontalDivider()
                                            Spacer(Modifier.height(2.dp))
                                            Text("Controller buttons", style = MaterialTheme.typography.titleMedium)
                                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                GlassChoice(!playStationButtons, { onControllerButtons(false) }, label = { Text("Xbox") })
                                                GlassChoice(playStationButtons, { onControllerButtons(true) }, label = { Text("PlayStation") })
                                            }
                                            Text("Choose matching controller output on the Windows dashboard.",
                                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                            GlassTextField(deviceName, { deviceName = it.take(32); onDeviceName(deviceName) }, label = { Text("Device name") },
                                                singleLine = true, enabled = canEdit, modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                                item("updates") { updateContent() }
                                item("about") { Footer() }
                            }
                        }
                    }
                }
                HomeNavigation(section, Modifier.align(Alignment.CenterHorizontally)) { index ->
                    if (section != index) {
                        focus.clearFocus()
                        section = index
                        onSectionChanged(index)
                    }
                }
            }
        }
    }

    if (presetPicker) GamePresetDialog(
        onChoose = { layoutActions.preset(it); presetPicker = false },
        onDismiss = { presetPicker = false },
    )

    renamingId?.let { id ->
        val current = layouts.firstOrNull { it.id == id }?.name.orEmpty()
        RenameDialog(
            current = current,
            onDismiss = { renamingId = null },
            onConfirm = { name ->
                layoutActions.rename(id, name)
                renamingId = null
            },
        )
    }

    deletingId?.let { id ->
        DeleteDialog(
            name = layouts.firstOrNull { it.id == id }?.name.orEmpty(),
            onConfirm = {
                layoutActions.delete(id)
                deletingId = null
            },
            onDismiss = { deletingId = null },
        )
    }
}

@Composable
private fun HomeHeader(activeName: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(io.github.kakashi812.droidoss.R.drawable.kizuna_logo),
            contentDescription = null, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)),
        )
        Text("Kizuna", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
        Box(Modifier.widthIn(max = 124.dp).background(colors.primary.copy(alpha = .14f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(activeName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
    }
}

private val HomeTabs = listOf("Connect", "Profiles", "Settings")

@Composable
private fun HomeNavigation(section: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    GlassPanel(modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        .widthIn(max = 480.dp).fillMaxWidth(), shape = CircleShape, strong = true) {
        Row(
            Modifier.selectableGroup().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            HomeTabs.forEachIndexed { index, title ->
                val selected = section == index
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape)
                        .background(if (selected) colors.primary.copy(alpha = .19f) else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 3.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) colors.primary else colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** Everything the layout gallery can ask for, by layout id where one applies. */
class LayoutActions(
    val use: (String) -> Unit,
    val edit: (String) -> Unit,
    val tune: (String) -> Unit,
    val preset: (String) -> Unit,
    val view: (String) -> Unit,
    val rename: (id: String, name: String) -> Unit,
    val duplicate: (String) -> Unit,
    val delete: (String) -> Unit,
    val share: (String) -> Unit,
    val saveToFile: (String) -> Unit,
    val showQr: (String) -> Unit,
    val importFile: () -> Unit,
    val scanQr: () -> Unit,
)

/**
 * The servers that answered discovery, one tappable row each.
 *
 * Tapping a row connects — with several PCs on one network, the row *is* the
 * choice. A full server stays listed but cannot be tapped, so it is clear it
 * was found rather than mysteriously missing.
 */
@Composable
private fun ServerList(
    servers: List<DiscoveredServer>,
    scanning: Boolean,
    lastHost: String,
    enabled: Boolean,
    onScan: () -> Unit,
    onPick: (DiscoveredServer) -> Unit,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Servers on this network",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    TextButton(onClick = onScan, enabled = enabled) { Text("Scan again") }
                }
            }

            HorizontalDivider(Modifier.padding(bottom = 8.dp))

            if (servers.isEmpty()) {
                Text(
                    text = if (scanning) {
                        "Looking for Kizuna servers…"
                    } else {
                        "None found yet — still looking. Check the " +
                            "server is running on your PC and both devices are on the " +
                            "same Wi-Fi, or enter its address below."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (server in servers) {
                        ServerRow(
                            server = server,
                            lastUsed = server.host == lastHost,
                            enabled = enabled && server.freePads > 0,
                            onClick = { onPick(server) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: DiscoveredServer,
    lastUsed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    GlassCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = server.host,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lastUsed) Text("Last used", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = when (server.freePads) {
                    0 -> "Full"
                    1 -> "1 pad free"
                    else -> "${server.freePads} pads free"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (server.freePads > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

private fun LazyListScope.profileItems(
    layouts: List<ControllerLayout>,
    activeId: String,
    columns: Int,
    actions: LayoutActions,
    onPresets: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val full = layouts.size >= MAX_LAYOUTS
    item(key = "profile-actions", contentType = "profile-actions") {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Game profiles", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("${layouts.size} / $MAX_LAYOUTS", style = MaterialTheme.typography.labelMedium,
                        color = if (full) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onPresets, enabled = !full, modifier = Modifier.fillMaxWidth()) {
                    Text("Browse game presets")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { actions.duplicate(DEFAULT_ID) }, enabled = !full,
                        contentPadding = SmallPadding, modifier = Modifier.weight(1f)) { Text("+ New", textAlign = TextAlign.Center) }
                    OutlinedButton(onClick = actions.importFile, contentPadding = SmallPadding,
                        modifier = Modifier.weight(1f)) { Text("Import file", textAlign = TextAlign.Center) }
                    OutlinedButton(onClick = actions.scanQr, contentPadding = SmallPadding,
                        modifier = Modifier.weight(1f)) { Text("Scan QR", textAlign = TextAlign.Center) }
                }
                if (layouts.size == 1) {
                    Text("Create a layout from a preset or import one someone shared.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    items(layouts.chunked(columns), key = { "profiles-${it.first().id}" }, contentType = { "profile-row" }) { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (layout in row) {
                androidx.compose.runtime.key(layout.id) {
                    LayoutCard(layout, layout.id == activeId, !full, actions,
                        onRenameRequest = { onRename(layout.id) }, onDeleteRequest = { onDelete(layout.id) },
                        modifier = Modifier.weight(1f))
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LayoutCard(
    layout: ControllerLayout,
    isActive: Boolean,
    canDuplicate: Boolean,
    actions: LayoutActions,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDefault = layout.id == DEFAULT_ID
    var menuOpen by remember { mutableStateOf(false) }

    GlassCard(
        selected = isActive,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            // Long-press renames a custom layout. Default is not renamable, so its
            // long-press does nothing rather than offering a rename it can't honour.
            .combinedClickable(
                onClick = {},
                onLongClick = { if (!isDefault) onRenameRequest() },
            ),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = layout.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (layout.game.isNotBlank() && !layout.game.equals(layout.name, ignoreCase = true)) Text(layout.game, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    if (isActive) {
                        Text(
                            "In use",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Box {
                    TextButton(
                        onClick = { menuOpen = true },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Text("⋮", style = MaterialTheme.typography.titleLarge)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // Default is the pristine baseline everyone already has:
                        // it can be copied, but not renamed, shared or deleted.
                        if (!isDefault) {
                            MenuItem("Input & calibration") { menuOpen = false; actions.tune(layout.id) }
                            MenuItem("Rename") {
                                menuOpen = false
                                onRenameRequest()
                            }
                        }
                        MenuItem(if (isDefault) "Make a copy" else "Duplicate", enabled = canDuplicate) {
                            menuOpen = false
                            actions.duplicate(layout.id)
                        }
                        if (!isDefault) {
                            MenuItem("Share…") {
                                menuOpen = false
                                actions.share(layout.id)
                            }
                            MenuItem("Save to file") {
                                menuOpen = false
                                actions.saveToFile(layout.id)
                            }
                            MenuItem("Show QR code") {
                                menuOpen = false
                                actions.showQr(layout.id)
                            }
                            MenuItem("Delete", destructive = true) {
                                menuOpen = false
                                onDeleteRequest()
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                if (isActive) {
                    FilledTonalButton(
                        onClick = {},
                        enabled = false,
                        contentPadding = SmallPadding,
                        modifier = Modifier.weight(1f),
                    ) { CardButtonLabel("Using") }
                } else {
                    Button(
                        onClick = { actions.use(layout.id) },
                        contentPadding = SmallPadding,
                        modifier = Modifier.weight(1f),
                    ) { CardButtonLabel("Use") }
                }

                OutlinedButton(
                    onClick = { if (isDefault) actions.view(layout.id) else actions.edit(layout.id) },
                    contentPadding = SmallPadding,
                    modifier = Modifier.weight(1f),
                ) { CardButtonLabel(if (isDefault) "View" else "Edit") }
            }
        }
    }
}

@Composable
private fun MenuItem(
    label: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (destructive && enabled) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename layout") },
        text = {
            GlassTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Footer() {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            BuildConfig.RELEASE_REPOSITORY,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { uriHandler.openUri(REPO_URL) }
                .padding(4.dp),
        )
    }
}

/** Card buttons are half-card wide, so their labels must stay on one small line
 *  rather than wrapping or clipping ("Using" was the tight one). */
@Composable
private fun CardButtonLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        softWrap = false,
    )
}

private val SmallPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)

/**
 * One line of state and, when something is wrong, what to do about it.
 *
 * The advice matters more than the status. "No answer" on its own sends someone
 * to the wrong place; the three things worth checking are always the same three,
 * so the app says them rather than making anyone guess.
 */
@Composable
private fun StatusPanel(state: ConnectionState) {
    val colour by animateColorAsState(
        targetValue = when (state) {
            is ConnectionState.Connected -> Color(0xFF4CAF50)
            is ConnectionState.Connecting -> Color(0xFFFFB300)
            is ConnectionState.NoServer, is ConnectionState.ServerFull, is ConnectionState.SetupFailed -> Color(0xFFE53935)
            is ConnectionState.Idle -> Color(0xFF9E9E9E)
        },
        label = "status",
    )

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colour),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = headline(state),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            detail(state)?.let { text ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun headline(state: ConnectionState): String = when (state) {
    is ConnectionState.Idle -> "Not connected"
    is ConnectionState.Connecting -> "Looking for the server…"
    is ConnectionState.Connected -> "Connected as player ${state.slot + 1}"
    is ConnectionState.ServerFull -> "Server is full"
    is ConnectionState.NoServer -> "No answer from that server"
    is ConnectionState.SetupFailed -> "Could not start the controller connection"
}

private fun detail(state: ConnectionState): String? = when (state) {
    is ConnectionState.Idle ->
        "Start Kizuna on your PC and connect both devices to the same Wi-Fi network."

    is ConnectionState.Connecting -> null

    is ConnectionState.Connected -> null

    is ConnectionState.ServerFull ->
        "All four slots are occupied, or your preferred player is taken. Choose Auto or another player, then retry."

    is ConnectionState.SetupFailed -> state.message

    is ConnectionState.NoServer ->
        "Discovery uses UDP 27501, while controllers use UDP 27500. Allow both ports through Windows Firewall. Check that:\n" +
            "  •  the server is running on your PC\n" +
            "  •  the address matches one it printed\n" +
            "  •  both devices are on the same Wi-Fi network"
}
