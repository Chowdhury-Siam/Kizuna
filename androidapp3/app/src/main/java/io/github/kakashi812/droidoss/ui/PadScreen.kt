/*
 * Pointer-handling approach derived from PadConnect's GPEmulationScreen.
 * Copyright (C) 2026 Ishan
 * Copyright (C) 2026 droidOSS contributors
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 only.
 *
 * This program is distributed without any warranty. See the GNU General Public
 * License for more details.
 *
 * MODIFIED FROM THE ORIGINAL. Theirs established the shape: read the raw pointer
 * stream, own controls by PointerId, hit-test yourself, and support slide-off.
 * Changed here: two sticks rather than one hardcoded to the left axis, a real
 * D-pad, radial deadzones, full-range triggers, and pointer release driven by
 * which ids are still present so a cancelled gesture cannot leave a control held.
 *
 * Drawing lives in PadDrawing.kt, shared with the layout editor.
 */

package io.github.kakashi812.droidoss.ui

import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.kakashi812.droidoss.layout.*
import io.github.kakashi812.droidoss.protocol.PadState
import io.github.kakashi812.droidoss.transport.UdpTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

@Composable
fun PadScreen(
    transport: UdpTransport?, layout: ControllerLayout, modifier: Modifier = Modifier,
    onSnapshot: ((PadState) -> Unit)? = null, showStatus: Boolean = true,
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val observer by rememberUpdatedState(onSnapshot)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val minimum = with(density) { 24.dp.toPx() }
        val placed = remember(layout, w, h) { layout.elements.filter { !it.hidden }.place(w, h) }
        val router = remember(layout, w, h, minimum) {
            PadTouchRouter(placed.map { TouchTarget(it.element, it.centre.x, it.centre.y, it.radius) }, w, minimum)
        }
        var routing by remember(router) { mutableStateOf(PadRoutingSnapshot()) }
        val accessible = remember(router) { mutableStateMapOf<String, ControlTouch>() }
        val player = remember(router) { MacroPlayer() }
        var playingId by remember(router) { mutableStateOf<String?>(null) }
        var macroFrame by remember(router) { mutableStateOf<MacroFrame?>(null) }
        var displayState by remember(router) { mutableStateOf(PadState()) }
        var sampling by remember(router) { mutableStateOf(false) }
        fun publish() {
            val manualTouches = if (accessible.isEmpty()) routing.inputs else routing.inputs + accessible.values
            val manual = mixInput(layout, manualTouches)
            val frame = macroFrame
            val mixed = if (frame == null) manual else {
                val extra = if (accessible.isEmpty()) emptyList() else layout.elements.filter { accessible.containsKey(it.id) }
                val sticks = routing.sticks + extra.filterIsInstance<StickElement>().map { it.stick }
                val triggers = routing.triggers + extra.filterIsInstance<TriggerElement>().map { it.trigger }
                mergeMacro(manual, frame, sticks, triggers)
            }
            displayState = mixed
            sampling = player.running || router.needsSampling
            observer?.invoke(mixed)
            transport?.update {
                buttons = mixed.buttons; leftTrigger = mixed.leftTrigger; rightTrigger = mixed.rightTrigger
                thumbLX = mixed.thumbLX; thumbLY = mixed.thumbLY; thumbRX = mixed.thumbRX; thumbRY = mixed.thumbRY
            }
        }

        fun play(macro: MacroElement) {
            if (playingId == macro.id) { player.stop(); playingId = null; macroFrame = null }
            else {
                val now = SystemClock.uptimeMillis()
                player.start(macro, now)
                playingId = if (player.running) macro.id else null
                macroFrame = player.sample(now)
            }
            publish()
        }
        fun cancelMacro() { player.stop(); playingId = null; macroFrame = null; publish() }
        LaunchedEffect(router, transport) {
            snapshotFlow { sampling }.collectLatest { active ->
                if (active) {
                    while (sampling) {
                        delay(8)
                        if (!sampling) break
                        val routingChanged = router.sample(System.nanoTime())
                        if (routingChanged) routing = router.snapshot()
                        val nextFrame = player.sample(SystemClock.uptimeMillis())
                        val frameChanged = macroFrame != nextFrame
                        macroFrame = nextFrame
                        if (!player.running) playingId = null
                        if (routingChanged || frameChanged) publish()
                        else sampling = player.running || router.needsSampling
                    }
                }
            }
        }
        DisposableEffect(router, transport) {
            onDispose {
                router.clear(); accessible.clear(); player.stop()
                observer?.invoke(PadState())
                transport?.update { reset() }
            }
        }
        val controllerButtons = LocalControllerButtons.current
        val labels = rememberControlLabels(placed)
        val paints = rememberControlPaints(placed, MaterialTheme.colorScheme.onBackground)
        val accessiblePlaced = remember(placed) { placed.filter { it.element.enabled } }

        fun isPressed(e: ControlElement): Boolean {
            if (playingId == e.id || accessible.containsKey(e.id) || routing.inputs.any { it.id == e.id }) return true
            if (macroFrame == null) return false
            val state = displayState
            return when (e) {
                is ButtonElement -> state.isPressed(e.mask)
                is TriggerElement -> (if (e.trigger == Trigger.LEFT) state.leftTrigger else state.rightTrigger) > 0
                is StickElement -> if (e.stick == Stick.LEFT) state.thumbLX.toInt() != 0 || state.thumbLY.toInt() != 0
                    else state.thumbRX.toInt() != 0 || state.thumbRY.toInt() != 0
                else -> false
            }
        }

        // Press animations change only the draw pass. Stick positions never run through a spring.
        val visualScales = placed.map { p ->
            key(p.element.id) {
                if (p.element is StickElement || p.element is DpadElement) null
                else {
                    val down by remember(router, p.element) { derivedStateOf { isPressed(p.element) } }
                    animateFloatAsState(if (down) .93f else 1f,
                        animationSpec = spring(dampingRatio = .65f, stiffness = 700f), label = "Control press")
                }
            }
        }
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Controller touch area" }.pointerInput(router, transport) {
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        var touchedDown = false
                        for (c in event.changes) {
                            if (!c.pressed) router.up(c.id.value)
                            else {
                                val now = System.nanoTime(); val eventTime = c.uptimeMillis * 1_000_000L
                                if (!c.previousPressed) {
                                    if (c.isConsumed) continue
                                    val macro = router.down(c.id.value, c.position.x, c.position.y, now, eventTime)
                                    if (macro != null) play(macro)
                                    touchedDown = true
                                } else router.move(c.id.value, c.position.x, c.position.y, now, eventTime)
                                c.consume()
                            }
                        }
                        router.retain(event.changes.filter { it.pressed }.map { it.id.value }.toSet())
                        val next = router.snapshot()
                        if (touchedDown && next.inputs.isNotEmpty()) vibrate(context, layout.input.vibration, 15)
                        if (next != routing) {
                            routing = next
                            publish()
                        }
                    }
                }
            } finally {
                router.clear(); player.stop(); macroFrame = null; playingId = null
                routing = router.snapshot(); publish()
            }
        }) {
            val routed = routing
            val finalState = displayState
            for (index in placed.indices) {
                val p = placed[index]
                val e = p.element
                val id = e.id
                val centre = routed.floatingCentres[id]?.let { Offset(it.x, it.y) } ?: p.centre
                val knob = if (e is StickElement) {
                    routed.knobs[id]?.let { Offset(it.x, it.y) }
                        ?: accessible[id]?.let { Offset(it.x, it.y) * p.radius }
                        ?: if (macroFrame != null) {
                            val left = e.stick == Stick.LEFT
                            Offset((if (left) finalState.thumbLX else finalState.thumbRX) / 32767f,
                                -(if (left) finalState.thumbLY else finalState.thumbRY) / 32767f) * p.radius
                        } else Offset.Zero
                } else Offset.Zero
                drawControl(p, paints.getValue(id), labels[id], isPressed(e), knob, centre,
                    dpadMask = if (e is DpadElement) finalState.buttons and 0x000F else 0,
                    scale = visualScales[index]?.value ?: 1f)
            }
        }
        accessiblePlaced.forEach { p ->
            val e = p.element
            val name = when (e) {
                is MacroElement -> "Macro ${e.label}"
                is ButtonElement -> if (controllerButtons == ControllerButtons.PLAYSTATION) controllerButtons.label(e, spoken = true)!! else when (e.mask) {
                    0x10 -> "Start"; 0x20 -> "Back"; 0x40 -> "Left stick click"; 0x80 -> "Right stick click"
                    0x100 -> "Left shoulder"; 0x200 -> "Right shoulder"; 0x400 -> "Guide"; else -> e.label
                }
                is TriggerElement -> controllerButtons.label(e)!!
                is StickElement -> "${e.stick} ${if (e.mode == StickMode.TOUCHPAD) "touchpad" else "stick"}"
                is DpadElement -> "Directional pad"
            }
            val radius = maxOf(p.radius, minimum)
            val floatingCentre = remember(router, e.id) { derivedStateOf { routing.floatingCentres[e.id] } }
            Box(Modifier.offset {
                val floating = if (e is StickElement) floatingCentre.value else null
                IntOffset(((floating?.x ?: p.centre.x) - radius).roundToInt(),
                    ((floating?.y ?: p.centre.y) - radius).roundToInt())
            }
                .size(with(density) { (radius * 2).toDp() }).semantics {
                    contentDescription = name
                    stateDescription = when (e) {
                        is MacroElement -> if (playingId == e.id) "Playing" else if (e.frames.isEmpty()) "Not recorded" else "Ready"
                        is DpadElement -> DpadDirections.filter { displayState.buttons and it.first != 0 }
                            .joinToString(" + ") { it.second }.ifEmpty { "Released" }
                        is StickElement -> if (e.mode == StickMode.TOUCHPAD) {
                            val moving = routing.inputs.any { it.id == e.id && (it.x != 0f || it.y != 0f) } ||
                                accessible[e.id]?.let { it.x != 0f || it.y != 0f } == true
                            if (moving || (macroFrame != null && isPressed(e))) "Moving" else "Stopped"
                        } else if (isPressed(e)) "Held" else "Released"
                        else -> if (isPressed(e)) "Held" else "Released"
                    }
                    fun toggle(x: Float, y: Float): Boolean {
                        val next = ControlTouch(e.id, x, y)
                        if (accessible[e.id] == next) accessible.remove(e.id) else accessible[e.id] = next
                        publish(); return true
                    }
                    onClick(if (e is MacroElement) "Play or stop macro" else "Toggle hold") {
                        if (e is MacroElement) { play(e); true } else toggle(0f, 0f)
                    }
                    if (e is StickElement || e is DpadElement) customActions = listOf(
                        CustomAccessibilityAction("Hold up or release") { toggle(0f, -1f) },
                        CustomAccessibilityAction("Hold down or release") { toggle(0f, 1f) },
                        CustomAccessibilityAction("Hold left or release") { toggle(-1f, 0f) },
                        CustomAccessibilityAction("Hold right or release") { toggle(1f, 0f) },
                    )
                }.focusable())
        }
        if (showStatus) PadStatus(Modifier.align(Alignment.TopCenter), transport, layout, playingId, ::cancelMacro)
    }
}

private val DpadDirections = listOf(1 to "Up", 2 to "Down", 4 to "Left", 8 to "Right")

@Composable
private fun PadStatus(modifier: Modifier, transport: UdpTransport?, layout: ControllerLayout, playingId: String?, onCancel: () -> Unit) {
    var status by remember(transport) { mutableStateOf(transport?.statusText ?: "Preview · no input sent") }
    if (transport != null) LaunchedEffect(transport) {
        while (true) {
            delay(500)
            status = transport.statusText
        }
    }
    val macro = remember(layout, playingId) {
        layout.elements.firstOrNull { it is MacroElement && it.id == playingId } as? MacroElement
    }
    GlassPanel(shape = RoundedCornerShape(24.dp), modifier = modifier.padding(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (macro != null) "Macro ${macro.label}" else "${layout.name} · $status",
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
            if (macro != null) TextButton(onClick = onCancel) { Text("Stop") }
        }
    }
}

internal fun vibrate(context: android.content.Context, strength: Float, duration: Long = 60) {
    val amount = strength.coerceIn(0f, 1f)
    if (amount == 0f) return
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(duration, (amount * 255).toInt().coerceAtLeast(1)))
}
