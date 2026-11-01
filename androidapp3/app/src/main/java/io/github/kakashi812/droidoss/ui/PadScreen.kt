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

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.focusable
import io.github.kakashi812.droidoss.layout.*
import io.github.kakashi812.droidoss.transport.UdpTransport
import kotlinx.coroutines.delay

/** Raw multitouch stays independent of the 125 Hz sender. Accessibility actions toggle a hold. */
@Composable
fun PadScreen(transport: UdpTransport?, layout: ControllerLayout, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val minimumTouchRadius = with(density) { 24.dp.toPx() }
    val touches = remember(layout) { mutableStateMapOf<PointerId, ControlTouch>() }
    val accessible = remember(layout) { mutableStateMapOf<String, ControlTouch>() }
    val touchpads = remember(layout) { mutableMapOf<PointerId, TouchpadMotion>() }
    val touchPoints = remember(layout) { mutableStateMapOf<String, Offset>() }
    val pressed = remember { mutableStateMapOf<String, Boolean>() }
    val knobs = remember { mutableStateMapOf<String, Offset>() }
    var status by remember { mutableStateOf("Preview · no input sent") }
    LaunchedEffect(transport) {
        while (true) {
            status = transport?.statusText ?: "Preview · no input sent"
            delay(500)
        }
    }
    fun publish() {
        val active = touches.values + accessible.values
        val mixed = mixInput(layout, active)
        transport?.update {
            buttons = mixed.buttons; leftTrigger = mixed.leftTrigger; rightTrigger = mixed.rightTrigger
            thumbLX = mixed.thumbLX; thumbLY = mixed.thumbLY; thumbRX = mixed.thumbRX; thumbRY = mixed.thumbRY
        }
    }
    LaunchedEffect(layout, transport) {
        while (true) {
            delay(16)
            var changed = false
            touchpads.forEach { (id, motion) ->
                val previous = touches[id] ?: return@forEach
                val current = motion.sample(previous.id, System.nanoTime())
                if (current != previous) { touches[id] = current; changed = true }
            }
            if (changed) publish()
        }
    }
    DisposableEffect(layout, transport) {
        onDispose { touchpads.clear(); touchPoints.clear(); touches.clear(); accessible.clear(); transport?.update { reset() } }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val placed = remember(layout, w, h) { layout.elements.filter { !it.hidden }.place(w, h) }
        val labels = rememberControlLabels(placed)
        val ink = MaterialTheme.colorScheme.onBackground
        val visualScale = placed.associate { p ->
            val scale by animateFloatAsState(if (pressed[p.element.id] == true || accessible.containsKey(p.element.id)) 0.93f else 1f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 700f), label = "Control press")
            p.element.id to scale
        }
        val visualKnobs = placed.associate { p ->
            val offset by animateOffsetAsState(knobs[p.element.id] ?: Offset.Zero,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 1000f), label = "Stick return")
            p.element.id to offset
        }
        Canvas(Modifier.fillMaxSize().pointerInput(placed, transport) {
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (c in event.changes) {
                            if (!c.pressed) { touches.remove(c.id); touchpads.remove(c.id); continue }
                            val previous = touches[c.id]
                            var owned = placed.firstOrNull { it.element.id == previous?.id }
                            if (owned != null && (owned.element is ButtonElement || owned.element is TriggerElement) && !owned.contains(c.position, minimumTouchRadius)) owned = null
                            if (owned == null) owned = placed.lastOrNull { p ->
                                p.element.enabled && p.contains(c.position, minimumTouchRadius) && touches.none { it.key != c.id && it.value.id == p.element.id }
                            }
                            if (owned == null) { touches.remove(c.id); touchpads.remove(c.id); continue }
                            val delta = (c.position - owned.centre) / owned.radius
                            val element = owned.element
                            if (element is StickElement && element.mode == StickMode.TOUCHPAD) {
                                val now = System.nanoTime()
                                val motion = touchpads.getOrPut(c.id) { TouchpadMotion(delta.x, delta.y, now) }
                                motion.move(delta.x, delta.y, now)
                                touches[c.id] = motion.sample(element.id, now)
                                touchPoints[element.id] = Offset(delta.x.coerceIn(-1f, 1f), delta.y.coerceIn(-0.7f, 0.7f)) * owned.radius
                            } else touches[c.id] = ControlTouch(element.id, delta.x, delta.y)
                            if (previous?.id != owned.element.id) vibrate(context, layout.input.vibration, 15)
                            c.consume()
                        }
                        val stillDown = event.changes.filter { it.pressed }.map { it.id }.toSet()
                        touches.keys.filter { it !in stillDown }.forEach { touches.remove(it); touchpads.remove(it) }
                        pressed.clear(); knobs.clear()
                        for (t in touches.values + accessible.values) {
                            pressed[t.id] = true
                            val p = placed.firstOrNull { it.element.id == t.id } ?: continue
                            if (p.element is StickElement && p.element.mode == StickMode.TOUCHPAD) {
                                knobs[t.id] = touchPoints[t.id] ?: Offset.Zero
                                continue
                            }
                            val delta = Offset(t.x, t.y) * p.radius
                            knobs[t.id] = if (delta.getDistance() > p.radius) delta * (p.radius / delta.getDistance()) else delta
                        }
                        publish()
                    }
                }
            } finally { touchpads.clear(); touchPoints.clear(); touches.clear(); publish() }
        }) {
            placed.forEach { p ->
                withTransform({ scale(visualScale[p.element.id] ?: 1f, visualScale[p.element.id] ?: 1f, p.centre) }) {
                    drawControl(p, pressed, visualKnobs, labels, ink)
                }
            }
        }
        // Separate semantic bounds preserve simultaneous raw touch handling.
        placed.filter { it.element.enabled }.forEach { p ->
            val name = when (val e = p.element) {
                is ButtonElement -> when (e.mask) {
                    0x10 -> "Start"; 0x20 -> "Back"; 0x40 -> "Left stick click"; 0x80 -> "Right stick click"
                    0x100 -> "Left shoulder"; 0x200 -> "Right shoulder"; 0x400 -> "Guide"
                    else -> e.label
                }
                is TriggerElement -> e.label
                is StickElement -> "${e.stick} ${if (e.mode == StickMode.TOUCHPAD) "touchpad" else "stick"}"
                is DpadElement -> "Directional pad"
            }
            val touchRadius = maxOf(p.radius, minimumTouchRadius)
            Box(Modifier.offset(with(density) { (p.centre.x - touchRadius).toDp() }, with(density) { (p.centre.y - touchRadius).toDp() })
                .size(with(density) { (touchRadius * 2).toDp() }).semantics {
                    contentDescription = name
                    stateDescription = if (accessible.containsKey(p.element.id)) "Held" else "Released"
                    fun toggle(x: Float, y: Float): Boolean {
                        if (accessible.containsKey(p.element.id)) accessible.remove(p.element.id)
                        else accessible[p.element.id] = ControlTouch(p.element.id, x, y)
                        publish(); return true
                    }
                    onClick("Toggle hold") { toggle(0f, 0f) }
                    if (p.element is StickElement || p.element is DpadElement) customActions = listOf(
                        CustomAccessibilityAction("Hold up or release") { toggle(0f, -1f) },
                        CustomAccessibilityAction("Hold down or release") { toggle(0f, 1f) },
                        CustomAccessibilityAction("Hold left or release") { toggle(-1f, 0f) },
                        CustomAccessibilityAction("Hold right or release") { toggle(1f, 0f) },
                    )
                }.focusable())
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.align(Alignment.TopCenter).padding(4.dp)) {
            Text("${layout.name} · $status", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
        }
    }
}

internal fun vibrate(context: android.content.Context, strength: Float, duration: Long = 60) {
    val amount = strength.coerceIn(0f, 1f)
    if (amount == 0f) return
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(duration, (amount * 255).toInt().coerceAtLeast(1)))
}
