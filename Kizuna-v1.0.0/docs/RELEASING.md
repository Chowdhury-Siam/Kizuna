# Build and publish Kizuna

## Repository and version

The intended repository is `Chowdhury-Siam/Kizuna`. Publish the supplied source to your own repository when ready. This work does not create that repository or push to droidOSS upstream.

GitHub Actions embeds `${{ github.repository }}` into both apps. Local builds default to `Chowdhury-Siam/Kizuna`; override with Android `-PreleaseRepository=owner/repo` or Windows `-p:ReleaseRepository=owner/repo`. Installed apps retain the update channel embedded when they were built.

Keep `versionName` in `androidapp3/app/build.gradle.kts` and `<Version>` in `server/DroidOSS.App/DroidOSS.App.csproj` identical. Stable releases require `major.minor.patch`. Increase Android `versionCode` for every app update. Current version is **1.0.0**, code **7**. `python tools/release.py` validates and prints these values.

## Android signing

Every published stable APK must reuse the **same permanent signing key**. No CI run generates a temporary replacement key. Pull-request jobs do not receive secrets; unsigned APKs are allowed only as non-release build artifacts.

Under **Repository → Settings → Secrets and variables → Actions**, add these repository **secrets**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the complete JKS keystore |
| `ANDROID_STORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing alias, for example `kizuna` |
| `ANDROID_KEY_PASSWORD` | Key password |

Use the private signing kit supplied with the local signed APK if you want CI releases to update that exact installation. The kit contains a keystore and four text files named after the secrets. Paste each file's complete contents into its matching secret. Keep this kit backed up privately; never upload it into source, Actions artifacts or GitHub Releases.

For a new signing identity instead, run with JDK/keytool installed:

```bash
python tools/create-signing-key.py --output /a/private/path/kizuna-signing
```

The output path must be outside this repository. The command refuses existing directories. A different key cannot update installations signed by the supplied kit.

For local signed builds, set `ANDROID_KEYSTORE_PATH`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`, then run `./gradlew assembleRelease`. Existing `local.properties` signing fields are still accepted. Without a key, local release builds remain unsigned.

The workflow restores the keystore to a private runner temporary file, signs during the release build, verifies the APK with `apksigner`, and removes the keystore in an `always()` cleanup step. Missing or partially configured secrets stop stable publication.

## Stable release automation

The workflow `.github/workflows/checks.yml` runs on pull requests, `main`, `feature/**`, `v*` tags and manual dispatch.

1. Bump both app versions and Android version code.
2. Push the change to `main`, or push the exact matching version tag (for example `v1.0.0`). Manual dispatch can publish only from the repository's default branch.
3. All protocol, Android, emulator UI and Windows jobs must pass.
4. Actions downloads the APK and Windows packages from that same run, generates source, `update.json` and `SHA256SUMS.txt`, and publishes one **stable/latest** release.

The built-in `GITHUB_TOKEN` receives `contents: write` only in the release job. Ensure repository Actions policy permits that permission. No personal token is required for ordinary release creation.

Published assets:

- `Kizuna-vX.Y.Z-android.apk` — signed release APK.
- `Kizuna-vX.Y.Z-Setup.exe` — Windows x64 per-user Inno Setup installer.
- `Kizuna-vX.Y.Z-win-x64.zip` — portable Windows app and bundled .NET/license notices.
- `Kizuna-vX.Y.Z-source.zip` — corresponding GPL source.
- `update.json` — version, Android version code, repository, expected package names, sizes and SHA-256 hashes.
- `SHA256SUMS.txt` — hashes of the complete release files.

Existing releases are skipped, never edited, deleted or overwritten. A rerun cannot replace published files. Increment the version for a correction. Consider enabling **Settings → General → Releases → Release immutability** for GitHub-enforced locking too.

## Windows installer

The workflow compiles `tools/installers/kizuna.iss` with Inno Setup 6 on `windows-latest`. The installer is per-user, bundles .NET, retains settings, adds uninstall support, and optionally creates a desktop shortcut. ViGEmBus remains a separate driver installation.

`ReleaseClient` downloads the Setup EXE verified against the stable manifest. **Install and restart** verifies it again, starts Setup in silent update mode and gracefully shuts down the server. Setup relaunches Kizuna after completing an update. The user is warned before disconnecting active players. Unsigned Windows binaries can produce operating-system reputation prompts; no Authenticode certificate is configured by this project.

## Update behavior

Updates are opt-out startup checks, followed by explicit download/install actions. Android checks at most every six hours automatically; manual checks bypass the interval. No polling or downloads occur in the background while playing.

Both clients reject drafts, prereleases, version mismatches, unexpected repository/asset URLs, invalid names, sizes or SHA-256 hashes. Downloads are bounded to 512 MiB, written to partial files and promoted only after verification. Android additionally checks APK package ID, version and signing certificate before installation. Its installer permission prompt is followed by the system installation UI; silent installation is not promised.

The update manifest must be attached to the same release as the packages. Until the first stable release is published, the apps show that no stable release is available. A 404 or rate limit is a retryable error, not a claim that the app is current.

## Validation

```bash
python -m unittest discover -s tools -p 'test_*.py' -v
dotnet test server/DroidOSS.Tests -c Release
cd androidapp3
./gradlew testDebugUnitTest lintDebug assembleRelease assembleDebugAndroidTest
# Requires an emulator or device:
./gradlew connectedDebugAndroidTest
```

The shared fixtures in `tests/fixtures` exercise the Python publisher plus Kotlin and C# update validators. Tests use local fixture bytes; they do not publish a real release. See `docs/TEST_REPORT.md` for executed results and remaining physical-device checks.
