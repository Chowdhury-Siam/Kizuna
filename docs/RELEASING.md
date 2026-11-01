# Build and publish Kizuna

## Repository and version

The intended repository is `Chowdhury-Siam/Kizuna`. Publish the supplied source to your own repository when ready. This work does not create that repository or push to droidOSS upstream.

GitHub Actions embeds `${{ github.repository }}` into both apps. Local builds default to `Chowdhury-Siam/Kizuna`; override with Android `-PreleaseRepository=owner/repo` or Windows `-p:ReleaseRepository=owner/repo`. Installed apps retain the update channel embedded when they were built.

Keep `versionName` in `androidapp3/app/build.gradle.kts` and `<Version>` in `server/DroidOSS.App/DroidOSS.App.csproj` identical. Stable releases require `major.minor.patch`. Increase Android `versionCode` for every app update. Current version is **1.0.0**, code **7**. `python tools/release.py` validates and prints these values.

## Android signing

Kizuna now uses the same **one-time automatic signing setup as Auralis**. You do not have to generate a keystore or enter four signing values manually. Setup creates a key once, stores the complete identity in the repository secret `ANDROID_SIGNING_IDENTITY`, then starts a fresh signed stable build. Every later trusted build reuses that identity.

> [!IMPORTANT]
> Already using the earlier supplied Kizuna APK? [Import its existing signing kit](#existing-installs-and-the-supplied-private-signing-kit) before generating a new identity to keep update compatibility.

### First-time automatic setup

1. Create a **fine-grained GitHub personal access token** scoped to this Kizuna repository with **Repository permissions → Secrets → Read and write**. This permission is needed only to save the signing identity; the normal build token cannot manage repository secrets. See [GitHub’s repository secret API permissions](https://docs.github.com/en/rest/actions/secrets#create-or-update-a-repository-secret).
2. Under **Repository → Settings → Secrets and variables → Actions**, add that token as a repository **secret** named `ANDROID_SIGNING_SETUP_TOKEN`.
3. Open **Actions → Set up Android signing → Run workflow**, choosing **main**.
4. The workflow preserves any existing complete signing identity, or generates a new key if none exists. It saves all key credentials as one private secret and queues **Build and publish stable** on main with publishing enabled. A new run is necessary because secrets are read when a workflow is queued.
5. After setup succeeds, you can remove `ANDROID_SIGNING_SETUP_TOKEN`. Future builds need only the saved identity; the setup token is not used for routine signing or release creation.

Keep `ANDROID_SIGNING_IDENTITY` intact. Setup runs are serialized; an existing identity or complete legacy key is never deliberately regenerated or replaced. An incomplete legacy configuration is an error, not permission to generate another key. Key material is masked, kept out of caches/artifacts/source and removed from the runner after building. No key is committed or published.

### Existing installs and the supplied private signing kit

To update the previously supplied signed Kizuna APK, keep its original key. **A newly generated key cannot update an APK signed by a different key.** If you have not yet saved that key in GitHub, import it before running automatic setup:

```bash
# Run from the project folder; both paths below are outside the repository.
python tools/android_signing.py --import-kit ../Kizuna-private-signing-kit.zip --output ../ANDROID_SIGNING_IDENTITY
```

Paste the output file's complete JSON contents into the repository secret `ANDROID_SIGNING_IDENTITY`, then run **Build and publish stable** with publishing enabled. Keep the file and kit private. The importer preserves the original keystore bytes and refuses to overwrite an output file or save it inside the repository. It does not generate a new key.

Existing four-secret setups are still supported and take priority, preserving the previous signing identity:

| Legacy secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the complete keystore |
| `ANDROID_STORE_PASSWORD` | Keystore password; Auralis's `ANDROID_KEYSTORE_PASSWORD` name is also accepted |
| `ANDROID_KEY_ALIAS` | Signing alias |
| `ANDROID_KEY_PASSWORD` | Key password |

If no saved identity or legacy key is present, CI builds a release-mode **Kizuna Preview** APK named `Kizuna-vX.Y.Z-android-development.apk`. It uses a temporary development signature and the separate application ID `io.github.chowdhurysiam.kizuna.preview`; it cannot overwrite the stable app. Stable publishing is skipped until signing setup is complete. Preview updates are disabled, and subsequent Preview builds may use different temporary keys. Pull requests never receive signing secrets.

### Local builds

Normal local Gradle behavior is unchanged: set `ANDROID_KEYSTORE_PATH`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`, or use private `local.properties` signing fields, then run `./gradlew assembleRelease`. Without a key, a direct local release build remains unsigned. `tools/create-signing-key.py` remains an optional manual alternative for a new identity; do not use it to replace the key of an existing installation.

The Actions helper validates the saved store, alias and private-key password with native JDK signing tools, writes private Gradle signing properties, and verifies the built APK with `apksigner`. Its temporary files are removed in an `always()` cleanup step. APK verification, matching platform checks, corresponding source, update manifests and checksums still gate stable publication.

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
