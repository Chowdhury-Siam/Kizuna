# Kizuna 1.0.0 — validation report

## Gradle wrapper permission fix (2026-11-01)

Both Android jobs now run `chmod +x androidapp3/gradlew` immediately after checkout. This handles ZIP/browser uploads that record the wrapper without its executable bit and prevents exit 126 before Gradle starts. Each job prepares its own checkout. APK signing, SDK setup and stable-release rules are unchanged; application version remains **1.0.0**, code **7**.

Validation executed for this update:

- Reproduced `Permission denied` with the actual wrapper set to mode `0644`, then ran each job's exact permission step and verified executable access was restored.
- Both workflows passed actionlint 1.7.7.
- All **23 existing Python tests passed**, with no failures or skips (4.713 seconds).
- Running the actual `./gradlew --version` successfully launched the wrapper and Java, then failed to download Gradle 9.5.0 because outbound network access was unavailable. No Gradle build or emulator tests passed in this update; hosted CI still needs a new run.

No app source or signing behavior changed.


## Automatic signing workflow update (2026-11-01)

This CI-only update adopts Auralis’s retained signing flow. **23 Python tests passed** (16 signing/setup regressions plus the 7 existing protocol/release tests), with no failures or skips. Native JDK checks generated and restored PKCS12 keys, signed a probe archive, rejected incorrect store/key passwords and aliases, and verified Java Properties escaping. GitHub secret operations were mocked: tests covered preserving existing identities and legacy names, partial-secret refusal, setup event/branch/token guards, one bundled secret write, reuse on a second run, a secret appearing during setup, and redacted errors. Private-kit import preserved the test keystore bytes and refused an overwrite.

Both `checks.yml` and the new `android-signing.yml` passed local actionlint. Setup saves one `ANDROID_SIGNING_IDENTITY` using a one-time repository Secrets token and dispatches a fresh stable build. Missing keys now produce an isolated release-mode Preview APK and skip stable publication, while partial/invalid saved credentials fail without regeneration. Existing complete legacy keys retain priority. The earlier SDK setup fix and ViGEmBus README instructions remain.

Only CI helpers, workflows, tests and documentation changed; app version **1.0.0**, code **7**, remain unchanged. No Android/.NET rebuild or real APK upgrade was run for this update. No signing secret was read or written on GitHub, no workflow was dispatched, and no release was created or changed by this work. Hosted end-to-end signing/publication still needs the owner’s one-time setup and CI run.

The original app-build report below is historical; its four-secret setup instructions are superseded by [the current signing guide](RELEASING.md#android-signing).

---

## Original app validation — rebrand, releases, updates and presets

Date: 2026-11-01 (Asia/Dhaka)
Branch: `feature/kizuna-rebrand-releases`
Change base: `ac9f41d` (droidOSS 0.3.2 connection fix).

## Implemented

- Original mint/coral K logo as editable SVG, README banner, Android launcher/home icons and Windows application/header icon. Public branding is Kizuna; original copyrights and GPL-3.0-only license remain.
- README rewritten using the structure of the current Yutaka README: banner/badges, downloads, setup, features, update behavior, development, publishing, troubleshooting and attribution. Detailed signing/release and game-preset guides are included.
- 58 searchable game presets covering eFootball/PES/FC, Tomb Raider classics/remasters/modern games, Resident Evil titles through Requiem plus selected spin-offs, and Steam genres. Presets adjust stick response, control geometry, trigger behavior and vibration; they retain the full controller. Legacy/emulator/online compatibility notes are shown. Profile capacity increased to 50 custom layouts plus Default, with longer game names and old JSON/QR import compatibility.
- Automatic stable-release checks in Android and Windows; download progress, retryable errors, opt-out automatic checking, verified packages and install actions. Android validates the archive's app ID, version and signer as well as manifest integrity, then uses the native installer. Windows downloads the Setup EXE, verifies it again before launch, closes the server gracefully and uses the installer's update/restart path.
- One GitHub Actions workflow checks protocol, Android JVM/lint/UI and Windows paths, builds signed APK and x64 Windows installer/portable packages, generates corresponding source and update metadata/checksums, and creates stable/latest releases on versioned main/tag pushes or explicit default-branch dispatch. Missing signing secrets stop stable publication. Existing releases are skipped without replacement. Existing tags pointing to another commit are rejected before publication.
- A new permanent signing key was generated for the supplied signed APK. Its owner-only backup and four GitHub secret files are supplied separately from public source/builds. The kit must be retained and used by future Actions releases.

## Validation actually executed

| Check | Result |
| --- | --- |
| Android JVM tests | **71 passed**, 0 failures, errors or skipped |
| Existing phone-transport UDP tests | Passed, including four simultaneous production clients, fifth-client refusal, legacy handshake, rumble, neutral disconnect, spoofed source rejection and reconnect |
| New preset tests | Passed: all 58 templates preserve full controls, sane neutral input, distinct tuning and JSON round-trip; 50 profiles persist |
| New Kotlin updater tests | Passed: shared manifest, numeric stable versions, downgrade/code checks, draft/prerelease and wrong URL/repository/schema rejection, tampered/truncated files |
| Android clean Gradle build | **Passed**: `clean testDebugUnitTest lintDebug assembleRelease assembleDebugAndroidTest` |
| Android lint | **0 errors, 27 warnings and 1 informational note** |
| Android instrumentation suite | **8 test cases compiled; not executed**, including searchable preset selection |
| Signed release APK | Built and verified with `aapt` and `apksigner`; v2 signature valid |
| APK identity | `io.github.chowdhurysiam.kizuna`, version **1.0.0**, code **7**, label Kizuna; launchable activity resolves correctly |
| Signing kit | Base64 secret decodes to the exact keystore used for the release APK |
| Embedded Android/Windows update repository | Verified as `Chowdhury-Siam/Kizuna` |
| .NET Core/protocol/server/updater tests | **155 passed**, 0 failed or skipped |
| Windows x64 WPF publish | **Passed**, self-contained single-file publish; PE machine verified as AMD64 (`0x8664`) |
| Python protocol/release tests | **7 passed** |
| Shared publisher/client manifest contract | Python-generated fixture matches the manifest consumed by Kotlin and C# tests |
| GitHub workflow `actionlint` | **Passed** |
| Git whitespace and unchanged GPL license | **Passed** |
| Banner/logo visual inspection | PNG rendering inspected; editable SVG assets retained |

Toolchain: Temurin JDK 21.0.9, Gradle 9.5.0, Android SDK 37.0/build-tools 36.0.0, .NET SDK 10.0.403. The full Gradle run completed in 10m16s. Existing and nonfatal new lint findings include dependency/API suggestions, legacy unused color resources, identical round launcher images, Compose modifier ordering and KTX style suggestions; the exact report is included. No lint errors were suppressed.

APK signing certificate SHA-256: `4cc8567d019456d305690f89ced221a4829b2e4ea744cb955fba439e4426bc09`.

## Installation and migration

The new Android application ID allows Kizuna to coexist with droidOSS and the earlier connection test app. Export/import the profiles you want before removing either old installation; Kizuna cannot read another app's private data. Old layout/QR format identifiers remain compatible, while new exported filenames end in `.kizuna.json`.

The supplied Kizuna APK is a signed **release**, not the earlier debug/preview distribution. Keep the separately supplied private signing kit. Add its four named text files as the matching GitHub Actions repository secrets before publishing; the same key is required to update this installed APK. No secret material is included in the public source ZIP, Git history or Windows package.

The Windows portable ZIP contains `Kizuna.exe`, bundled .NET and license notices. The Setup EXE is produced by the configured Windows Actions job; it was not compiled on this Linux host. The old Windows settings path is retained for compatibility. Normal releases use the actual GitHub repository injected by Actions; locally supplied builds target `Chowdhury-Siam/Kizuna` by default.

## Limits

No source was pushed, no hosted workflow was run, and no GitHub release/tag was created or changed. Stable publication and live update checks require the source repository and its first successful signed release. The local workflow lint check is not a claim that remote CI passed.

No Android emulator/device UI execution, native Windows installer install/update/uninstall, real APK upgrade, physical phone LAN/firewall connectivity, Steam gameplay, ViGEmBus hardware or commercial game gameplay was verified here. Update validators/download integrity were tested with local fixtures; live GitHub download/install paths remain device/CI acceptance items. Preset tuning is a configurable starting point, not an empirical game-performance certification. Console-only and legacy titles may need emulator or Steam Input mappings; online templates do not restore services.

The input/session protocol, XInput backend and Steam launch URI remain unchanged. Existing compatibility and multiplayer tests passed. Manual acceptance steps, including a real signed Android data-preserving upgrade and Windows silent update/restart, are in `docs/ACCEPTANCE.md`.

## Evidence

The source ZIP includes JUnit/TRX results, Gradle/WPF/Python logs, lint output, APK metadata/signature verification and workflow lint evidence, together with a complete Git bundle. Restore with:

```bash
git clone -b feature/kizuna-rebrand-releases git-history/Kizuna.bundle Kizuna
```
