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
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.kakashi812.droidoss.layout.*
import io.github.kakashi812.droidoss.protocol.PadState
import io.github.kakashi812.droidoss.transport.UdpTransport
import kotlinx.coroutines.delay

@Composable
fun PadScreen(
    transport: UdpTransport?, layout: ControllerLayout, modifier: Modifier = Modifier,
    onSnapshot: ((PadState) -> Unit)? = null, showStatus: Boolean = true,
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val observer by rememberUpdatedState(onSnapshot)
    var status by remember { mutableStateOf("Preview · no input sent") }
    LaunchedEffect(transport) {
        while (true) { status = transport?.statusText ?: "Preview · no input sent"; delay(500) }
    }
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
        fun publish() {
            val active = routing.inputs + accessible.values
            val manual = mixInput(layout, active)
            val accessibleControls = layout.elements.filter { accessible.containsKey(it.id) }
            val sticks = routing.sticks + accessibleControls.filterIsInstance<StickElement>().map { it.stick }
            val triggers = routing.triggers + accessibleControls.filterIsInstance<TriggerElement>().map { it.trigger }
            val mixed = mergeMacro(manual, macroFrame, sticks, triggers)
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
            while (true) {
                delay(8)
                val nextRouting = if (router.sample(System.nanoTime())) router.snapshot() else routing
                val nextFrame = player.sample(SystemClock.uptimeMillis())
                val changed = routing != nextRouting || macroFrame != nextFrame
                routing = nextRouting; macroFrame = nextFrame
                if (!player.running) playingId = null
                if (changed) publish()
            }
        }
        DisposableEffect(router, transport) {
            onDispose {
                router.clear(); accessible.clear(); player.stop()
                observer?.invoke(PadState())
                transport?.update { reset() }
            }
        }
        val allTouches = routing.inputs + accessible.values
        val accessibleControls = layout.elements.filter { accessible.containsKey(it.id) }
        val displayState = mergeMacro(mixInput(layout, allTouches), macroFrame,
            routing.sticks + accessibleControls.filterIsInstance<StickElement>().map { it.stick },
            routing.triggers + accessibleControls.filterIsInstance<TriggerElement>().map { it.trigger })
        val pressed = allTouches.associate { it.id to true }.toMutableMap().also { if (playingId != null) it[playingId!!] = true }
        if (macroFrame != null) placed.forEach { p ->
            when (val e = p.element) {
                is ButtonElement -> if (displayState.isPressed(e.mask)) pressed[e.id] = true
                is TriggerElement -> if ((if (e.trigger == Trigger.LEFT) displayState.leftTrigger else displayState.rightTrigger) > 0) pressed[e.id] = true
                is StickElement -> {
                    val moving = if (e.stick == Stick.LEFT) displayState.thumbLX.toInt() != 0 || displayState.thumbLY.toInt() != 0
                        else displayState.thumbRX.toInt() != 0 || displayState.thumbRY.toInt() != 0
                    if (moving) pressed[e.id] = true
                }
                else -> Unit
            }
        }
        val dpadMasks = placed.mapNotNull { p ->
            val dpad = p.element as? DpadElement ?: return@mapNotNull null
            dpad.id to (displayState.buttons and 0x000F)
        }.toMap()
        val labels = rememberControlLabels(placed)
        val controllerButtons = LocalControllerButtons.current
        val ink = MaterialTheme.colorScheme.onBackground
        val visualScale = placed.associate { p ->
            val scale by animateFloatAsState(if (p.element !is DpadElement && pressed[p.element.id] == true) .93f else 1f,
                animationSpec = spring(dampingRatio = .65f, stiffness = 700f), label = "Control press")
            p.element.id to scale
        }
        val visualKnobs = placed.associate { p ->
            val replay = if (macroFrame != null && p.element is StickElement) {
                val left = p.element.stick == Stick.LEFT
                Offset((if (left) displayState.thumbLX else displayState.thumbRX) / 32767f,
                    -(if (left) displayState.thumbLY else displayState.thumbRY) / 32767f) * p.radius
            } else Offset.Zero
            val raw = routing.knobs[p.element.id]?.let { Offset(it.x, it.y) } ?: accessible[p.element.id]?.let { Offset(it.x, it.y) * p.radius } ?: replay
            val offset by animateOffsetAsState(raw, animationSpec = spring(dampingRatio = .8f, stiffness = 1000f), label = "Stick return")
            p.element.id to offset
        }
        val visualPlaced = placed.map { p -> routing.floatingCentres[p.element.id]?.let { p.copy(centre = Offset(it.x, it.y)) } ?: p }
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Controller touch area" }.pointerInput(router, transport) {
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (c in event.changes) {
                            if (!c.pressed) router.up(c.id.value)
                            else {
                                val now = System.nanoTime(); val eventTime = c.uptimeMillis * 1_000_000L
                                if (!c.previousPressed) {
                                    if (c.isConsumed) continue
                                    val macro = router.down(c.id.value, c.position.x, c.position.y, now, eventTime)
                                    if (macro != null) play(macro)
                                    if (router.snapshot().inputs.isNotEmpty()) vibrate(context, layout.input.vibration, 15)
                                } else router.move(c.id.value, c.position.x, c.position.y, now, eventTime)
                                c.consume()
                            }
                        }
                        router.retain(event.changes.filter { it.pressed }.map { it.id.value }.toSet())
                        routing = router.snapshot()
                        publish()
                    }
                }
            } finally {
                router.clear(); player.stop(); macroFrame = null; playingId = null
                routing = router.snapshot(); publish()
            }
        }) {
            visualPlaced.forEach { p ->
                withTransform({ scale(visualScale[p.element.id] ?: 1f, visualScale[p.element.id] ?: 1f, p.centre) }) {
                    drawControl(p, pressed, visualKnobs, labels, ink, dpadMasks[p.element.id] ?: 0)
                }
            }
        }
        visualPlaced.filter { it.element.enabled }.forEach { p ->
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
            Box(Modifier.offset(with(density) { (p.centre.x - radius).toDp() }, with(density) { (p.centre.y - radius).toDp() })
                .size(with(density) { (radius * 2).toDp() }).semantics {
                    contentDescription = name
                    stateDescription = when (e) {
                        is MacroElement -> if (playingId == e.id) "Playing" else if (e.frames.isEmpty()) "Not recorded" else "Ready"
                        is DpadElement -> listOf(1 to "Up", 2 to "Down", 4 to "Left", 8 to "Right")
                            .filter { (dpadMasks[e.id] ?: 0) and it.first != 0 }.joinToString(" + ") { it.second }.ifEmpty { "Released" }
                        is StickElement -> if (e.mode == StickMode.TOUCHPAD) {
                            if (allTouches.any { it.id == e.id && (it.x != 0f || it.y != 0f) } || (macroFrame != null && pressed[e.id] == true)) "Moving" else "Stopped"
                        } else if (pressed[e.id] == true) "Held" else "Released"
                        else -> if (pressed[e.id] == true) "Held" else "Released"
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
        if (showStatus) GlassPanel(shape = RoundedCornerShape(24.dp),
            modifier = Modifier.align(Alignment.TopCenter).padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val macro = layout.elements.filterIsInstance<MacroElement>().firstOrNull { it.id == playingId }
                Text(if (macro != null) "Macro ${macro.label}" else "${layout.name} · $status", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                if (macro != null) TextButton(onClick = ::cancelMacro) { Text("Stop") }
            }
        }
    }
}

internal fun vibrate(context: android.content.Context, strength: Float, duration: Long = 60) {
    val amount = strength.coerceIn(0f, 1f)
    if (amount == 0f) return
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(duration, (amount * 255).toInt().coerceAtLeast(1)))
}
