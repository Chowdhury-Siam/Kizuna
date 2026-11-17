package io.github.kakashi812.droidoss.layout

/** Starting points for Xbox/Steam Input layouts; game settings remain under the player's control. */
data class GamePreset(val id: String, val title: String, val series: String, val tuning: Tuning, val note: String = "") {
    val defaults: GameInputDefaults get() = requireNotNull(GamePresetDefaults.byId[id]) { "Missing defaults for $id" }

    // Version 1.0.6 factory values identify unmodified settings on existing saved copies.
    internal val legacyInput: InputSettings
        get() = when (tuning) {
            Tuning.SPORT -> InputSettings(left = StickSettings(deadzone = .07f, curve = 1.1f), right = StickSettings(deadzone = .08f), vibration = .45f)
            Tuning.ADVENTURE -> InputSettings(left = StickSettings(deadzone = .08f), right = StickSettings(sensitivity = .9f, deadzone = .08f, curve = 1.3f))
            Tuning.AIM -> InputSettings(left = StickSettings(deadzone = .09f), right = StickSettings(sensitivity = .8f, deadzone = .07f, curve = 1.55f), vibration = .55f)
            Tuning.FIRST_PERSON -> InputSettings(left = StickSettings(deadzone = .08f), right = StickSettings(sensitivity = .75f, deadzone = .06f, curve = 1.65f), vibration = .5f)
            Tuning.CLASSIC -> InputSettings(left = StickSettings(deadzone = .18f, curve = 1.2f), vibration = .5f)
            Tuning.TWIN_STICK -> InputSettings(left = StickSettings(deadzone = .08f), right = StickSettings(deadzone = .08f))
            Tuning.RACING -> InputSettings(left = StickSettings(deadzone = .06f, curve = 1.4f), analogTriggers = true, triggerDeadzone = .04f, triggerRange = .85f)
            Tuning.PLATFORMER, Tuning.STANDARD -> InputSettings()
        }

    fun layout(): ControllerLayout {
        val precise = tuning == Tuning.AIM || tuning == Tuning.FIRST_PERSON
        val controls = defaultLayout().elements.map { e -> when {
            e is StickElement && e.stick == Stick.RIGHT && precise -> e.copy(x = .65f, y = .65f, size = .22f)
            e is DpadElement && (tuning == Tuning.CLASSIC || tuning == Tuning.PLATFORMER) -> e.copy(x = .32f, y = .76f, size = .20f, deadzone = .18f)
            e is DpadElement && precise -> e.copy(size = .14f)
            e is StickElement && e.stick == Stick.LEFT && tuning == Tuning.SPORT -> e.copy(y = .60f, size = .19f)
            else -> e
        } }
        return defaults.applyTo(defaultLayout().copy(name = title, game = title, elements = controls))
    }
}

enum class Tuning(val summary: String) {
    STANDARD("Balanced Xbox layout"), SPORT("Fast movement · precise dribbling · skill-stick control"),
    ADVENTURE("Responsive movement · smooth camera"), AIM("Larger aim area · fine aim curve · full triggers"),
    FIRST_PERSON("Low aim deadzone · fine camera curve · full triggers"), CLASSIC("Larger D-pad · deliberate movement"),
    TWIN_STICK("Balanced twin sticks · low deadzones"), RACING("Progressive steering · slide triggers"),
    PLATFORMER("Larger D-pad · full movement range")
}

object GamePresets {
    private const val LEGACY = "Original PC/console editions may need a Steam Input keyboard/legacy template or an emulator mapping. This preset sends standard gamepad input."
    val catalog: List<GamePreset> = buildList {
        fun add(id: String, title: String, series: String, tuning: Tuning, note: String = "") = add(GamePreset(id, title, series, tuning, note))
        add("efootball", "eFootball", "Football", Tuning.SPORT, "Use the standard controller scheme. Slide RT/R2 upward to vary dash strength; full travel remains available for stunning kicks. Adjust assistance in the game.")
        add("pes2021", "PES 2021 / older PES", "Football", Tuning.SPORT)
        add("fc", "EA SPORTS FC / FIFA", "Football", Tuning.SPORT)
        val originals = listOf("Tomb Raider I", "Tomb Raider II", "Tomb Raider III", "Tomb Raider: The Last Revelation", "Tomb Raider: Chronicles", "Tomb Raider: The Angel of Darkness")
        originals.forEachIndexed { i, title -> add("tr-original-${i+1}", title, "Tomb Raider", Tuning.CLASSIC, LEGACY) }
        originals.forEachIndexed { i, title -> add("tr-remastered-${i+1}", "$title Remastered", "Tomb Raider", Tuning.CLASSIC, "Use tank or modern controls in the remastered collection; remap the profile to match your chosen scheme.") }
        listOf("Tomb Raider: Legend", "Tomb Raider: Anniversary", "Tomb Raider: Underworld").forEachIndexed { i, title -> add("tr-adventure-$i", title, "Tomb Raider", Tuning.ADVENTURE) }
        listOf("Tomb Raider (2013)", "Rise of the Tomb Raider", "Shadow of the Tomb Raider").forEachIndexed { i, title -> add("tr-survivor-$i", title, "Tomb Raider", Tuning.AIM) }
        listOf("Lara Croft and the Guardian of Light", "Lara Croft and the Temple of Osiris").forEachIndexed { i, title -> add("lara-coop-$i", title, "Tomb Raider", Tuning.TWIN_STICK) }
        add("lara-go", "Lara Croft GO", "Tomb Raider", Tuning.CLASSIC, "Requires a suitable Steam Input keyboard/mouse template for the PC edition.")
        listOf("Resident Evil (1996)", "Resident Evil 2 (1998)", "Resident Evil 3: Nemesis", "Resident Evil Survivor", "Resident Evil Code: Veronica", "Resident Evil Survivor 2", "Resident Evil: Dead Aim", "Resident Evil Outbreak", "Resident Evil Outbreak File #2", "Resident Evil Gaiden").forEachIndexed { i, title -> add("re-classic-$i", title, "Resident Evil", Tuning.CLASSIC, LEGACY) }
        listOf("Resident Evil HD Remaster", "Resident Evil 0 HD Remaster").forEachIndexed { i, title -> add("re-hd-$i", title, "Resident Evil", Tuning.CLASSIC) }
        listOf("Resident Evil 4 (2005)", "Resident Evil 5", "Resident Evil 6", "Resident Evil Revelations", "Resident Evil Revelations 2", "Resident Evil 2 (2019)", "Resident Evil 3 (2020)", "Resident Evil 4 (2023)", "Resident Evil: Operation Raccoon City", "Umbrella Corps").forEachIndexed { i, title -> add("re-action-$i", title, "Resident Evil", Tuning.AIM) }
        listOf("Resident Evil 7: Biohazard", "Resident Evil Village", "Resident Evil Requiem").forEachIndexed { i, title -> add("re-first-$i", title, "Resident Evil", Tuning.FIRST_PERSON) }
        listOf("Resident Evil: The Umbrella Chronicles", "Resident Evil: The Darkside Chronicles", "Resident Evil: The Mercenaries 3D").forEachIndexed { i, title -> add("re-console-$i", title, "Resident Evil", Tuning.AIM, LEGACY) }
        listOf("Resident Evil Resistance", "Resident Evil Re:Verse").forEachIndexed { i, title -> add("re-online-$i", title, "Resident Evil", Tuning.AIM, "Legacy online title: service availability is separate from controller support. Check the game's current online availability.") }
        add("steam-standard", "Steam · Standard", "Steam", Tuning.STANDARD)
        add("steam-racing", "Steam · Racing", "Steam", Tuning.RACING)
        add("steam-shooter", "Steam · Shooter", "Steam", Tuning.AIM)
        add("steam-platformer", "Steam · Platformer", "Steam", Tuning.PLATFORMER)
    }
    fun find(id: String) = catalog.firstOrNull { it.id == id }
    fun forGame(game: String) = catalog.firstOrNull { it.title.equals(game.trim(), ignoreCase = true) }

    /** Refresh only factory settings, preserving any edited input, layout and centre calibration. */
    fun refreshUntouchedInput(layout: ControllerLayout): ControllerLayout {
        val preset = forGame(layout.game) ?: return layout
        val legacyDpadZone = if (preset.tuning == Tuning.CLASSIC || preset.tuning == Tuning.PLATFORMER) .18f else .25f
        return layout.copy(
            input = if (layout.input == preset.legacyInput) preset.defaults.input else layout.input,
            elements = layout.elements.map { control ->
                when {
                    control is StickElement && control.touchpadSensitivity == 1f && control.touchpadDeadzoneCompensation == .28f ->
                        preset.defaults.tuneControl(control)
                    control is DpadElement && control.deadzone == legacyDpadZone && control.diagonals ->
                        preset.defaults.tuneControl(control)
                    else -> control
                }
            },
        )
    }
}
