package io.github.kakashi812.droidoss.layout

import io.github.kakashi812.droidoss.protocol.GamepadButton

/** Button names change presentation only; profile mappings and network bits stay stable. */
enum class ControllerButtons(val title: String) {
    XBOX("Xbox"), PLAYSTATION("PlayStation");

    fun label(mask: Int, fallback: String, spoken: Boolean = false): String {
        if (this == XBOX) return fallback
        return when (mask) {
            GamepadButton.A -> if (spoken) "Cross" else "×"
            GamepadButton.B -> if (spoken) "Circle" else "○"
            GamepadButton.X -> if (spoken) "Square" else "□"
            GamepadButton.Y -> if (spoken) "Triangle" else "△"
            GamepadButton.LEFT_SHOULDER -> "L1"
            GamepadButton.RIGHT_SHOULDER -> "R1"
            GamepadButton.LEFT_THUMB -> "L3"
            GamepadButton.RIGHT_THUMB -> "R3"
            GamepadButton.START -> "Options"
            GamepadButton.BACK -> "Share"
            GamepadButton.GUIDE -> "PS"
            else -> fallback
        }
    }

    fun label(control: ControlElement, spoken: Boolean = false): String? = when (control) {
        is MacroElement -> control.label
        is ButtonElement -> label(control.mask, control.label, spoken)
        is TriggerElement -> if (this == PLAYSTATION) {
            if (control.trigger == Trigger.LEFT) "L2" else "R2"
        } else control.label
        else -> null
    }
}
