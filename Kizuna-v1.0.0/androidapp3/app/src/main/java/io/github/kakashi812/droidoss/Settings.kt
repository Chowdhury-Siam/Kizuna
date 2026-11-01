package io.github.kakashi812.droidoss

import android.content.Context

/**
 * The handful of things worth remembering between sessions.
 *
 * Plain `SharedPreferences` rather than DataStore: there is one string in here,
 * it is read once at startup and written when it changes, and pulling in a
 * coroutine-based storage library to hold an IP address would be ceremony for
 * its own sake.
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("droidoss", Context.MODE_PRIVATE)

    /**
     * The server address, remembered so it is typed once rather than every time.
     *
     * Empty by default. It deliberately does **not** ship with a guess baked in:
     * a hardcoded address is right for exactly one machine on exactly one
     * network, and wrong -- confusingly, silently wrong -- for everyone else.
     * Discovery (B6) is what removes the typing properly.
     */
    var host: String
        get() = prefs.getString(KEY_HOST, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HOST, value.trim()).apply()

    var deviceName: String
        get() = prefs.getString("deviceName", android.os.Build.MODEL).orEmpty()
        set(value) = prefs.edit().putString("deviceName", value.trim().take(32)).apply()
    var preferredSlot: Int
        get() = prefs.getInt("preferredSlot", -1)
        set(value) = prefs.edit().putInt("preferredSlot", value.coerceIn(-1, 3)).apply()
    var theme: String
        get() = prefs.getString("theme", "System").orEmpty()
        set(value) = prefs.edit().putString("theme", value).apply()

    var automaticUpdates: Boolean
        get() = prefs.getBoolean("automaticUpdates", true)
        set(value) = prefs.edit().putBoolean("automaticUpdates", value).apply()
    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0L)
        set(value) = prefs.edit().putLong("lastUpdateCheck", value).apply()

    private companion object {
        const val KEY_HOST = "host"
    }
}
