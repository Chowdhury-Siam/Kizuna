# Kizuna 1.0.6

- Restores touchpad input at and beyond its edges. Swiping outward into the edge band continues turning in the swipe direction while held; inward movement responds without lifting, and lift/cancellation releases the stick. Landing alone at an edge stays neutral.
- Doubles the actual swipe-velocity gain at each saved sensitivity, retains the 0.25×–16× range, and reduces the movement threshold from 0.006 to 0.002 control radii so slow aiming does not drop out. Idle time no longer dilutes the first swipe after a pause.
- Filters small edge jitter, retains event-based timing and independent pointer ownership, and caps camera output to the existing XInput range. Ordinary joystick tuning, mappings and saved profile values are preserved.
- Extends JVM/UDP and Android touch regressions for held edges/corners, reversals, slow-swipe strength, neighboring buttons and release. Physical button semantics now reflect real held input, allowing the neighbor test to check the actual state.
- Android/Windows version 1.0.6, Android code 13. Signing, stable-release gates, caching, four-player transport, Steam and GPL-3.0-only licensing are retained. See TEST_REPORT.md for executed checks and blocked Android validation.

---

# Kizuna 1.0.5

- Pauses touchpad camera input outside the visible pad rectangle while keeping pointer ownership until lift. Re-entry rebases the gesture and avoids a jump or unintended neighboring-button press.
- Calculates swipe velocity from pointer event timestamps, keeping idle timing separate. Batched Android delivery no longer changes the apparent swipe speed.
- Expands saved touchpad sensitivity from 0.25×–4× to 0.25×–16× in the editor, profile settings and import/store sanitization. Existing gain values remain unchanged. Maximum turn speed at full XInput travel remains controlled by the game's camera settings.
- Adds five JVM regression cases and one Android edge-interaction case; existing persistence/editor tests now cover values above 4×.
- Preserves the existing UDP/XInput protocol, four controllers, Steam, D-pad/editor improvements, signing identity, cached CI and immutable releases.

See [TEST_REPORT.md](TEST_REPORT.md) for checks actually executed and pending Android/gameplay validation.

---

# Kizuna 1.0.4

- Fixes the Android compilation error in the compact editor sliders by using the Material 3 SliderState overload for custom thumb/track content. Profile values remain synchronized after control selection, undo and reset.
- Prepares cached SDK package ownership before restoration on hosted runners and uses fresh SDK cache keys, avoiding the repeated tar mode/timestamp permission errors in the supplied logs.
- Retains touchpad camera scaling/compensation, D-pad/editor improvements, signed APK and actual Windows Setup packaging, all required tests, GPL-3.0-only, UDP/XInput/four-player/Steam behavior, and immutable published releases.

See [TEST_REPORT.md](TEST_REPORT.md) for the uploaded run's results and checks actually performed on this correction.

---

# Kizuna 1.0.3

- Corrects weak touchpad-to-stick output: relative swipe velocity is scaled for camera control instead of emitting only one frame's tiny displacement.
- Adds saved Game deadzone compensation (default 28%, adjustable 0–60%) alongside touchpad sensitivity. Moving output can cross game-side neutral thresholds; landing, idle and release remain neutral. Small finger jitter is filtered and intentional slow travel accumulates.
- Adds regression test sources for slow-swipe camera output, jitter, compensation/neutral behavior, and an actual production UDP transport path carrying right-stick camera values then neutral. Existing persistence and editor tests now cover the saved compensation value.
- Keeps the same UDP/XInput fields, four players, Steam path, joystick behavior, D-pad/editor improvements, retained signing identity, cached CI and immutable releases.

Tomb Raider (2013) gameplay and the new Kotlin tests still require hosted/device validation; see [TEST_REPORT.md](TEST_REPORT.md). This release does not assume a particular game's exact deadzone.

---

# Kizuna 1.0.2

- Replaces the undersized D-pad cross with larger, separated arrow faces and immediate direction highlights that match the transmitted button bits. Arrow faces snap to cardinal directions; diagonal gaps work in 8-way mode.
- Adds saved D-pad 4-way/8-way mode and radial center deadzone settings. Sliding beyond the pad releases it; legacy edge layouts fit all four arrows on screen without rewriting saved coordinates. New layouts use a larger, raised D-pad.
- Compacts the layout inspector: slim color-consistent sliders, small thumbs, visibility in the header, and Duplicate/Reset under Arrange. Slider and palette touch targets remain at least 48 dp.
- Adds seven JVM regression cases and two Android interaction tests covering direction faces/gaps, input mixing, persistence, bounds, sliding/release and saved options. Execution status is in the test report.
- Retains cached CI, automatic saved-key signing, immutable releases, GPL-3.0-only, the existing UDP/XInput protocol, four-player support, Steam and Windows installer packaging.

---

# Kizuna 1.0.1

- Keeps Save reachable on narrow phones by separating editor actions into two compact rows and wrapping profile action buttons. The game title field now has a Done action to dismiss the keyboard.
- Repairs Android integration tests: exercises TalkBack hold through its semantic action, checks Save bounds at 320 dp, verifies saved touchpad/profile settings, and binds the UDP test peer to the same IPv4 loopback address as its client. Neutral disconnect checks now cover triggers and all stick axes as well as buttons.
- Adds Android SDK, emulator SDK/clean boot snapshot and Linux/Windows NuGet caching, plus Gradle task-output caching. All tests and stable-release gates remain required.
- Preserves automatic retained APK signing, Windows Setup/portable packaging, GPL-3.0-only, four-controller UDP/XInput and Steam compatibility. Existing signing keys and published releases are not replaced.

See [TEST_REPORT.md](TEST_REPORT.md) for checks actually executed and the remaining hosted Android build/emulator verification.

---

# Kizuna 1.0.0

- Rebrands droidOSS as Kizuna with a new original logo, Android launcher icons, Windows icon/header and README banner.
- Adds 58 searchable game presets for eFootball/PES/FC, Tomb Raider, Resident Evil and Steam genres. Profiles tune control geometry, deadzones, camera response and trigger behavior; compatibility notes identify legacy/emulator/service requirements.
- Expands saved profiles to 50 custom layouts plus Default and keeps old profile/QR imports. Existing joystick/touchpad tuning and compact editor remain.
- Adds automatic stable-release checks and verified downloads in Android and Windows, with native Android installation and a Windows installer/restart path.
- Adds stable/latest GitHub publication on versioned main-branch pushes or matching tags after all checks. Releases include signed APK, Windows x64 Setup EXE/portable ZIP, GPL source, update manifest and checksums. Published releases are never overwritten.
- Retains the Android UDP connection fix from 0.3.2, compatible v1 UDP/XInput protocol, four-controller sessions, Steam, rumble, console/demo modes and original attribution.

Kizuna's new Android application ID installs alongside droidOSS. Export/import old profiles before removing the old app. The supplied signed Kizuna APK and future CI builds must use the same private signing kit. Use the Auralis-style **Set up Android signing** workflow once to save a retained identity automatically, or keep/import your existing key. Stable publishing requires that retained identity; temporary Preview builds never enter a stable release.

Game presets are configurable starting profiles, not automatic game detection or guaranteed bindings. Older games may need Steam Input/emulator mappings. ViGEmBus remains required on Windows. Native installation, real LAN connectivity and gameplay require device validation; see the executed test report.

GPL-3.0-only. The original droidOSS and PadConnect authorship notices are retained. Corresponding source is included with each stable release.
