# Kizuna 1.0.0

- Rebrands droidOSS as Kizuna with a new original logo, Android launcher icons, Windows icon/header and README banner.
- Adds 58 searchable game presets for eFootball/PES/FC, Tomb Raider, Resident Evil and Steam genres. Profiles tune control geometry, deadzones, camera response and trigger behavior; compatibility notes identify legacy/emulator/service requirements.
- Expands saved profiles to 50 custom layouts plus Default and keeps old profile/QR imports. Existing joystick/touchpad tuning and compact editor remain.
- Adds automatic stable-release checks and verified downloads in Android and Windows, with native Android installation and a Windows installer/restart path.
- Adds stable/latest GitHub publication on versioned main-branch pushes or matching tags after all checks. Releases include signed APK, Windows x64 Setup EXE/portable ZIP, GPL source, update manifest and checksums. Published releases are never overwritten.
- Retains the Android UDP connection fix from 0.3.2, compatible v1 UDP/XInput protocol, four-controller sessions, Steam, rumble, console/demo modes and original attribution.

Kizuna's new Android application ID installs alongside droidOSS. Export/import old profiles before removing the old app. The supplied signed Kizuna APK and future CI builds must use the same private signing kit. Configure all four Android signing secrets before stable publication; unsigned APKs cannot enter a stable release.

Game presets are configurable starting profiles, not automatic game detection or guaranteed bindings. Older games may need Steam Input/emulator mappings. ViGEmBus remains required on Windows. Native installation, real LAN connectivity and gameplay require device validation; see the executed test report.

GPL-3.0-only. The original droidOSS and PadConnect authorship notices are retained. Corresponding source is included with each stable release.
