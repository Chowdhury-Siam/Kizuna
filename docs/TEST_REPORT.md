# Kizuna 1.0.3 — validation report

## Touchpad camera output correction (2026-11-01)

Version **1.0.3**, Android code **10**. The reported case is Tomb Raider (2013), with the right joystick changed to a touchpad for camera rotation. Source inspection found a plausible input-strength cause: a relative move of 0.02 control radii per 16 ms previously produced only about 655/32767 on RX, with no game-side deadzone compensation. Microsoft's [XInput guide](https://learn.microsoft.com/en-us/windows/win32/xinput/getting-started-with-xinput) gives a recommended right-stick deadzone of 8689 and notes games may choose their own values. **Tomb Raider's exact threshold has not been measured here.**

The touchpad now scales swipe velocity using a 160 ms reference instead of a single 16 ms frame. A saved per-stick Game deadzone compensation setting defaults to 28% and can be adjusted from 0–60%, independently of sensitivity. Nonzero motion maps radially above this floor; zero, landing and idle stay exactly neutral. Very small position jitter is accumulated until it crosses a 0.006-radius movement threshold. Output direction, full-range limits, idle timeout, touch-release behavior and existing UDP right-stick fields are retained. Joystick mode and its tuning path are unchanged.

The new setting is editable in both the layout inspector and profile input settings, sanitized on import/store loading, and retained in JSON/QR exports. Old files use the default compensation when touchpad mode is chosen. Existing profile sensitivity and output-stick assignments are preserved.

Added four JVM regression test sources: ordinary slow-swipe output above the recommended XInput threshold; jitter/accumulated motion; configurable compensation with exact neutral output; and production UDP transport carrying mixed touchpad RX/RY then neutral without changing LX/LY. Existing frame-rate, persistence, import/sanitization and Android editor tests were extended or retained. These new Kotlin tests have **not run locally**.

| Validation actually executed | Result |
| --- | --- |
| Python signing/protocol/release suite | **23 passed**, zero failures/skips, 3.454 seconds |
| Both workflows, actionlint 1.7.7 | Passed |
| Version validator | Passed: Android/Windows **1.0.3**, Android code **10** |
| Whitespace diff check | No whitespace errors |
| Gradle JVM tests, lint and instrumentation APK build | Attempted; wrapper download blocked by `Network is unreachable` before compilation |
| New/changed Kotlin tests, Android emulator tests | **Not compiled/executed locally**; fresh hosted CI required |
| Tomb Raider (2013) camera response, physical device, upgrade, multiplayer | **Not tested** for this source version |
| New APK / Windows installer | **Not built** for this version |

The generic deadzone explanation is an inference from source output and the XInput reference, not proof of Tomb Raider's runtime behavior. Start manual validation at Output stick Right, Touchpad mode, sensitivity 1× and compensation 28%; use Windows controller diagnostics and then verify slow camera/aim swipes in the game. No repository push, secret mutation, workflow dispatch or release publication was performed. Historical results below do not establish that this source version passes Android checks.

---

## D-pad comfort and inspector update (2026-11-01)

Version **1.0.2**, Android code **9**. Source changes address the supplied phone screenshot:

- D-pad arrow faces previously occupied only 62% of their nominal radius. New separated arrow faces extend to 96%, with square hit bounds that include their corners and diagonal gaps. Drawn faces and direction selection share geometry constants. Arrow faces select one cardinal direction; gaps can select diagonals. A radial neutral zone and optional 4-way mode replace independent X/Y thresholds.
- Saved D-pad mode/deadzone use the existing layout store, JSON and QR paths. Old profiles default to 8-way. New layouts have a larger D-pad at a higher position; old saved coordinates/sizes are not rewritten. Placement fits the D-pad inside the viewport so an old bottom-edge layout does not lose its lower arrow.
- Sliding beyond a 20% tolerance releases the D-pad instead of retaining a direction anywhere on screen. Direction feedback uses the same output-bit mapping, without spring interpolation or whole-pad press shrinking. Accessibility reports current directions and can switch a held direction directly.
- Inspector visibility moves into the header; its redundant subtitle and fixed Duplicate footer are removed. Duplicate and Reset remain under Arrange. Sliders use a 4 dp neutral-colored track and 18 dp thumb, retaining at least 48 dp interaction height. Five palette choices retain 48 dp touch targets.

Added **7 JVM regression cases** (5 input/persistence and 2 geometry cases) plus **2 Android interaction tests** for drag/diagonal/off-pad release and saved direction/deadzone options. These are included in the existing required CI tasks; none are disabled or marked optional.

Validation actually executed for this source update:

| Check | Result |
| --- | --- |
| Python signing/protocol/release suite | **23 passed**, zero failures/skips, 4.268 seconds |
| Both workflow files, actionlint 1.7.7 | Passed |
| Matching Android/Windows version validator | Passed: **1.0.2**, Android code **9** |
| Whitespace diff check | No whitespace errors |
| `testDebugUnitTest lintDebug assembleDebugAndroidTest` | Attempted; Gradle distribution download failed with `Network is unreachable` before compilation |
| New Kotlin JVM tests and changed Android source | **Not compiled/executed locally**; fresh hosted build required |
| Android emulator tests and actual rendered UI | **Not run/rendered locally**; no post-change screenshot is claimed |
| Physical thumb comfort, controller/multiplayer gameplay, upgrade | **Not tested** for this version |
| New APK or Windows installer | **Not built** for this version |

Earlier executed totals below are historical. They do not establish that this version's new Kotlin/UI tests pass. Cached CI, signing helpers, protocol transport and immutable-release gates remain unchanged. No upstream push, hosted workflow dispatch, secret mutation or release publication was performed.

---

## Android integration fixes and CI caching (2026-11-01)

The user-provided hosted run compiled Android debug/test APKs and executed all eight instrumentation tests: four passed and four failed. That run is failure evidence for **1.0.0**, not a passing result for this update. Deprecation/native-strip warnings did not stop compilation; the test assertions stopped the job.

Source changes in **1.0.1**, Android code **8**:

- The editor's single-row minimum action widths could place Save beyond the phone's bounds after a control was selected. Narrow layouts now put undo/redo/control actions on a second compact row; wide layouts keep one row. The touchpad save test uses a 320 dp viewport and checks the full Save button bounds before physically tapping it and checking the saved mode/sensitivity.
- The profile footer similarly used an unbounded Row. It now wraps its buttons with FlowRow. The title field has a Done IME action that clears focus; the profile test completes editing, verifies the full Save button bounds at 320 dp, then physically taps and checks the saved title/input. Callback waits now report a missing save directly rather than failing with a null dereference.
- The hold/release accessibility test now invokes `SemanticsActions.OnClick`, as TalkBack does. Its former `performClick()` injects a normal touch on Android, which releases after tapping; it does not exercise the explicit accessibility hold action. Production gamepad touch handling is unchanged.
- The UDP integration peer now binds `127.0.0.1`, matching its client. `getLoopbackAddress()` can select IPv6, which is incompatible with that IPv4-only client on such devices. The production transport and wire format are unchanged. The disconnect assertion additionally checks neutral triggers and stick axes.

Workflow caching now covers Gradle dependency/wrapper/task outputs, Linux/Windows NuGet packages, the added Android build SDK, emulator/system image and a clean AVD boot snapshot. SDK and snapshot saves occur before tests, and the test run does not save over the clean snapshot. AVD keys include runner image and native emulator/system-image fingerprints. All original jobs and the retained-signature gate still block stable publication on failure. No signing material or configuration-cache state is explicitly cached. See [the caching guide](RELEASING.md#ci-caching-and-diagnostics).

Validation actually executed for this update:

| Check | Result |
| --- | --- |
| Existing Python signing/protocol/release suite | **23 passed**, zero failures/skips, 4.350 seconds |
| actionlint 1.7.7, both workflows | Passed |
| Bash syntax and workflow wiring inspection | Passed: cache preparation order, snapshot reuse, wrapper chmod, all required release jobs retained |
| Version validator | Passed: Android/Windows **1.0.1**, Android code **8**, tag **v1.0.1** |
| Android Gradle tests/lint/test-APK build | Attempted; wrapper download failed with `Network is unreachable` before Gradle could start |
| Updated Android instrumentation tests | **Not executed locally**; requires a fresh hosted run |
| New APK/Setup EXE, physical upgrade, gameplay | **Not built or tested** for this update |
| Hosted cache hit/speed measurements | **Not measured**; first run must populate caches |

No GitHub push, workflow dispatch, signing-secret mutation or release publication was performed. Historical test totals below describe earlier source snapshots and are not a substitute for rerunning the changed Android tests.

---

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
