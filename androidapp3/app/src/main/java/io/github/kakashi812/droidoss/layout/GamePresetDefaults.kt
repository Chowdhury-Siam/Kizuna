package io.github.kakashi812.droidoss.layout

/** Suggested app-side response, not measured game deadzones or device calibration. */
data class GameInputDefaults(
    val input: InputSettings,
    val touchpadSensitivity: Float,
    val touchpadDeadzoneCompensation: Float,
    val dpadDeadzone: Float,
    val dpadDiagonals: Boolean,
    val summary: String,
) {
    /** Retain the user's positions, mappings, visibility and joystick/touchpad modes. */
    fun applyTo(layout: ControllerLayout): ControllerLayout = layout.copy(
        input = input,
        elements = layout.elements.map(::tuneControl),
    )

    fun tuneControl(control: ControlElement): ControlElement = when (control) {
        is StickElement -> control.copy(
            touchpadSensitivity = if (control.stick == Stick.RIGHT) touchpadSensitivity else 1f,
            touchpadDeadzoneCompensation = if (control.stick == Stick.RIGHT) touchpadDeadzoneCompensation else 0f,
        )
        is DpadElement -> control.copy(deadzone = dpadDeadzone, diagonals = dpadDiagonals)
        else -> control
    }
}

/** Explicit title-by-title defaults. Centre offsets stay zero to avoid synthesizing drift. */
internal object GamePresetDefaults {
    private fun profile(
        leftZone: Float, leftCurve: Float, rightZone: Float, rightCurve: Float,
        vibration: Float, touchGain: Float, touchFloor: Float, dpadZone: Float,
        diagonals: Boolean, analog: Boolean, triggerZone: Float, triggerRange: Float, summary: String,
    ) = GameInputDefaults(
        input = InputSettings(
            left = StickSettings(deadzone = leftZone, curve = leftCurve),
            right = StickSettings(deadzone = rightZone, curve = rightCurve),
            analogTriggers = analog, triggerDeadzone = triggerZone, triggerRange = triggerRange,
            vibration = vibration,
        ),
        touchpadSensitivity = touchGain, touchpadDeadzoneCompensation = touchFloor,
        dpadDeadzone = dpadZone, dpadDiagonals = diagonals, summary = summary,
    )

    val byId: Map<String, GameInputDefaults> = mapOf(
        "efootball" to profile(0.04f, 1.15f, 0.06f, 1.05f, 0.35f, 1.2f, 0.16f, 0.2f, true, true, 0.04f, 0.92f, "Close dribbling and skill-stick flicks; slide RT for dash strength, reach the top for full kicks."),
        "pes2021" to profile(0.06f, 1.1f, 0.08f, 1.1f, 0.4f, 1.1f, 0.18f, 0.22f, true, false, 0f, 1f, "Direct movement and controlled skill-stick flicks with full-press sprint modifiers."),
        "fc" to profile(0.05f, 1.08f, 0.05f, 1f, 0.38f, 1.3f, 0.14f, 0.18f, true, true, 0.03f, 0.94f, "Quick skill-stick gestures and progressive sprint; enable Analog Sprint in games that offer it."),
        "tr-original-1" to profile(0.2f, 1.2f, 0.16f, 1.1f, 0.3f, 1f, 0.12f, 0.2f, false, false, 0f, 1f, "Cardinal tank turns and deliberate grid-aligned jumps."),
        "tr-original-2" to profile(0.19f, 1.18f, 0.15f, 1.12f, 0.34f, 1.05f, 0.12f, 0.19f, false, false, 0f, 1f, "Cardinal tank movement with a slightly earlier movement response."),
        "tr-original-3" to profile(0.18f, 1.16f, 0.14f, 1.14f, 0.36f, 1.1f, 0.14f, 0.18f, false, false, 0f, 1f, "Cardinal tank turns and deliberate run/jump direction changes."),
        "tr-original-4" to profile(0.19f, 1.22f, 0.15f, 1.16f, 0.32f, 1f, 0.14f, 0.21f, false, false, 0f, 1f, "Deliberate tank movement for ledges, ropes and grid-based platforming."),
        "tr-original-5" to profile(0.18f, 1.2f, 0.14f, 1.18f, 0.33f, 1.05f, 0.14f, 0.2f, false, false, 0f, 1f, "Cardinal movement with controlled starts for classic traversal."),
        "tr-original-6" to profile(0.14f, 1.28f, 0.1f, 1.3f, 0.4f, 1.2f, 0.18f, 0.22f, true, false, 0f, 1f, "Softer movement onset and smoother manual camera; match the edition or emulator mapping."),
        "tr-remastered-1" to profile(0.09f, 1.18f, 0.07f, 1.32f, 0.4f, 1.3f, 0.16f, 0.18f, false, false, 0f, 1f, "Modern analog movement and camera with a cardinal D-pad for tank-control use."),
        "tr-remastered-2" to profile(0.08f, 1.16f, 0.065f, 1.3f, 0.42f, 1.35f, 0.17f, 0.17f, false, false, 0f, 1f, "Responsive modern movement and camera; cardinal tank directions remain available."),
        "tr-remastered-3" to profile(0.075f, 1.14f, 0.06f, 1.28f, 0.44f, 1.4f, 0.18f, 0.16f, false, false, 0f, 1f, "Quick modern traversal with a light camera curve and cardinal tank directions."),
        "tr-remastered-4" to profile(0.095f, 1.22f, 0.075f, 1.36f, 0.38f, 1.25f, 0.16f, 0.19f, false, false, 0f, 1f, "Gentle modern movement onset for ledge and rope positioning; cardinal tank directions."),
        "tr-remastered-5" to profile(0.085f, 1.2f, 0.07f, 1.34f, 0.39f, 1.3f, 0.17f, 0.185f, false, false, 0f, 1f, "Measured modern traversal and smooth camera; cardinal tank directions remain available."),
        "tr-remastered-6" to profile(0.1f, 1.26f, 0.08f, 1.38f, 0.43f, 1.2f, 0.18f, 0.21f, true, false, 0f, 1f, "Gentle analog movement and camera for Angel of Darkness; eight-way D-pad available."),
        "tr-adventure-0" to profile(0.07f, 1.1f, 0.08f, 1.28f, 0.5f, 1.4f, 0.18f, 0.22f, true, false, 0f, 1f, "Responsive traversal with a smooth exploration camera and full-press combat triggers."),
        "tr-adventure-1" to profile(0.08f, 1.2f, 0.075f, 1.38f, 0.45f, 1.25f, 0.16f, 0.2f, true, false, 0f, 1f, "Gentler movement onset for platform positioning and a fine camera response."),
        "tr-adventure-2" to profile(0.065f, 1.14f, 0.065f, 1.24f, 0.52f, 1.5f, 0.2f, 0.21f, true, false, 0f, 1f, "Fluid traversal and faster camera response for exploration and combat."),
        "tr-survivor-0" to profile(0.07f, 1.08f, 0.055f, 1.45f, 0.48f, 1.4f, 0.24f, 0.2f, true, false, 0f, 1f, "Bow-friendly fine camera response with responsive movement and full aim/fire triggers."),
        "tr-survivor-1" to profile(0.065f, 1.1f, 0.05f, 1.5f, 0.46f, 1.35f, 0.22f, 0.19f, true, false, 0f, 1f, "Fine bow adjustments and steady traversal; full aim/fire triggers."),
        "tr-survivor-2" to profile(0.06f, 1.14f, 0.045f, 1.55f, 0.42f, 1.25f, 0.2f, 0.18f, true, false, 0f, 1f, "Gentle fine aiming and movement onset for stealth and traversal."),
        "lara-coop-0" to profile(0.055f, 1.02f, 0.05f, 1f, 0.5f, 1.2f, 0.1f, 0.18f, true, false, 0f, 1f, "Near-linear independent movement and directional aiming for twin-stick play."),
        "lara-coop-1" to profile(0.05f, 1.04f, 0.045f, 1.02f, 0.48f, 1.25f, 0.1f, 0.17f, true, false, 0f, 1f, "Quick twin-stick movement and aim with a small neutral region."),
        "lara-go" to profile(0.24f, 1f, 0.2f, 1f, 0.15f, 0.8f, 0.08f, 0.24f, false, false, 0f, 1f, "Four distinct puzzle directions; requires a Steam Input keyboard/mouse template."),
        "re-classic-0" to profile(0.22f, 1.24f, 0.18f, 1.15f, 0.35f, 0.9f, 0.12f, 0.22f, false, false, 0f, 1f, "Deliberate cardinal tank movement for fixed-camera rooms."),
        "re-classic-1" to profile(0.21f, 1.22f, 0.17f, 1.16f, 0.38f, 0.95f, 0.12f, 0.21f, false, false, 0f, 1f, "Cardinal tank turns with a clear neutral zone for fixed-camera navigation."),
        "re-classic-2" to profile(0.18f, 1.16f, 0.16f, 1.12f, 0.42f, 1f, 0.14f, 0.19f, false, false, 0f, 1f, "Quicker tank-movement onset for evasive movement; cardinal directions."),
        "re-classic-3" to profile(0.12f, 1.12f, 0.08f, 1.48f, 0.3f, 1.1f, 0.18f, 0.2f, true, false, 0f, 1f, "Fine first-person aiming; use the original edition or emulator control mapping."),
        "re-classic-4" to profile(0.2f, 1.25f, 0.16f, 1.2f, 0.4f, 0.95f, 0.14f, 0.215f, false, false, 0f, 1f, "Measured cardinal tank turns and full-press action inputs."),
        "re-classic-5" to profile(0.11f, 1.1f, 0.075f, 1.44f, 0.32f, 1.15f, 0.18f, 0.195f, true, false, 0f, 1f, "Responsive movement and fine first-person aiming with emulator mappings."),
        "re-classic-6" to profile(0.1f, 1.12f, 0.06f, 1.6f, 0.28f, 1f, 0.16f, 0.18f, true, false, 0f, 1f, "Fine aiming onset for the hybrid aiming interface; custom emulator mapping required."),
        "re-classic-7" to profile(0.19f, 1.24f, 0.15f, 1.18f, 0.36f, 1f, 0.14f, 0.205f, false, false, 0f, 1f, "Measured fixed-camera tank movement with cardinal D-pad navigation."),
        "re-classic-8" to profile(0.185f, 1.22f, 0.145f, 1.2f, 0.37f, 1.05f, 0.14f, 0.2f, false, false, 0f, 1f, "Slightly earlier movement onset while retaining deliberate cardinal tank turns."),
        "re-classic-9" to profile(0.16f, 1f, 0.2f, 1f, 0.15f, 0.75f, 0.08f, 0.18f, false, false, 0f, 1f, "Four-way handheld navigation; emulator mappings must cover the battle timing controls."),
        "re-hd-0" to profile(0.12f, 1.22f, 0.12f, 1.2f, 0.42f, 1f, 0.14f, 0.2f, false, false, 0f, 1f, "Gentle analog movement for the alternate scheme and cardinal D-pad for tank controls."),
        "re-hd-1" to profile(0.13f, 1.24f, 0.13f, 1.22f, 0.4f, 0.95f, 0.14f, 0.21f, false, false, 0f, 1f, "Deliberate fixed-camera navigation with cardinal tank directions available."),
        "re-action-0" to profile(0.13f, 1.16f, 0.09f, 1.38f, 0.45f, 1.15f, 0.22f, 0.22f, true, false, 0f, 1f, "Deliberate classic movement and fine camera input; use the selected 2005 control type."),
        "re-action-1" to profile(0.09f, 1.12f, 0.075f, 1.42f, 0.48f, 1.25f, 0.24f, 0.21f, true, false, 0f, 1f, "Measured movement and fine aim response for the classic shoulder-camera scheme."),
        "re-action-2" to profile(0.055f, 1.04f, 0.055f, 1.26f, 0.52f, 1.6f, 0.22f, 0.18f, true, false, 0f, 1f, "Quick movement and camera response for mobile combat and evasive actions."),
        "re-action-3" to profile(0.1f, 1.18f, 0.065f, 1.5f, 0.4f, 1.15f, 0.2f, 0.2f, true, false, 0f, 1f, "Measured movement and gentle camera onset for close-range aiming."),
        "re-action-4" to profile(0.085f, 1.14f, 0.06f, 1.46f, 0.42f, 1.25f, 0.2f, 0.19f, true, false, 0f, 1f, "Responsive movement with fine camera input for combat and partner play."),
        "re-action-5" to profile(0.08f, 1.16f, 0.05f, 1.58f, 0.36f, 1.1f, 0.18f, 0.18f, true, false, 0f, 1f, "Gentle fine aiming for deliberate survival combat; full-press aim/fire triggers."),
        "re-action-6" to profile(0.065f, 1.08f, 0.045f, 1.4f, 0.44f, 1.4f, 0.2f, 0.17f, true, false, 0f, 1f, "Faster movement and camera response for evasive action with fine aim retained."),
        "re-action-7" to profile(0.06f, 1.1f, 0.04f, 1.52f, 0.4f, 1.3f, 0.18f, 0.16f, true, false, 0f, 1f, "Fine camera control and quick movement for modern shoulder-camera combat."),
        "re-action-8" to profile(0.05f, 1.03f, 0.05f, 1.22f, 0.5f, 1.65f, 0.22f, 0.18f, true, false, 0f, 1f, "Fast movement and low-latency camera onset for squad action."),
        "re-action-9" to profile(0.04f, 1f, 0.04f, 1.18f, 0.3f, 1.8f, 0.16f, 0.16f, true, false, 0f, 1f, "Fast directional movement and camera response for competitive action."),
        "re-first-0" to profile(0.08f, 1.16f, 0.045f, 1.68f, 0.32f, 1.1f, 0.18f, 0.19f, true, false, 0f, 1f, "Gentle first-person camera onset for deliberate exploration and aiming."),
        "re-first-1" to profile(0.06f, 1.08f, 0.04f, 1.48f, 0.4f, 1.45f, 0.2f, 0.17f, true, false, 0f, 1f, "Quicker first-person movement and camera response with fine aiming retained."),
        "re-first-2" to profile(0.07f, 1.12f, 0.05f, 1.55f, 0.35f, 1.25f, 0.18f, 0.18f, true, false, 0f, 1f, "Conservative camera and movement defaults; adjust for the chosen camera mode and edition."),
        "re-console-0" to profile(0.16f, 1.1f, 0.05f, 1.6f, 0.3f, 1f, 0.16f, 0.2f, true, false, 0f, 1f, "Fine cursor aiming for an emulator mapping; a stick is not a light-gun pointer."),
        "re-console-1" to profile(0.15f, 1.12f, 0.045f, 1.64f, 0.28f, 0.95f, 0.16f, 0.19f, true, false, 0f, 1f, "Gentle cursor-aim response for an emulator mapping; pointer mapping still required."),
        "re-console-2" to profile(0.08f, 1.08f, 0.07f, 1.36f, 0.38f, 1.3f, 0.2f, 0.18f, true, false, 0f, 1f, "Responsive movement and measured aiming with a suitable handheld emulator mapping."),
        "re-online-0" to profile(0.065f, 1.1f, 0.05f, 1.44f, 0.36f, 1.4f, 0.18f, 0.175f, true, false, 0f, 1f, "Responsive movement and controlled aiming; online service availability is separate."),
        "re-online-1" to profile(0.045f, 1.02f, 0.04f, 1.24f, 0.32f, 1.7f, 0.16f, 0.155f, true, false, 0f, 1f, "Fast movement and camera response; online service availability is separate."),
        "steam-standard" to profile(0.1f, 1.05f, 0.1f, 1.08f, 0.6f, 1.25f, 0.18f, 0.22f, true, false, 0f, 1f, "Balanced movement and camera response for a standard controller layout."),
        "steam-racing" to profile(0.035f, 1.55f, 0.1f, 1.15f, 0.55f, 1f, 0.12f, 0.24f, true, true, 0.04f, 0.92f, "Progressive steering near centre and full-range slide throttle/brake."),
        "steam-shooter" to profile(0.06f, 1.08f, 0.045f, 1.52f, 0.4f, 1.4f, 0.2f, 0.18f, true, false, 0f, 1f, "Responsive movement and fine camera response with full-press aim/fire triggers."),
        "steam-platformer" to profile(0.08f, 1.02f, 0.12f, 1.1f, 0.45f, 1.1f, 0.12f, 0.16f, true, false, 0f, 1f, "Direct movement, eight-way D-pad and full analog movement range for platforming."),
    )
}
