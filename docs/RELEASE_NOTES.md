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
