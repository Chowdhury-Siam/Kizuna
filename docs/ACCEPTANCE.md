# Acceptance checks

Automated results are supplied separately in TEST_REPORT.md. This checklist describes device checks that must not be reported as passed without executing them.

## Android

- Install using the same persistent signing key as the previous install; verify old layouts, active selection and host are retained.
- Test system/light/dark themes, large text, TalkBack, landscape, a narrow phone and a tablet. Verify buttons and dialogs remain reachable.
- Create, rename, copy and switch a game profile. Exercise every editor action, undo/redo after drag/resize, reset, hidden-control recovery, overlap/layer hit tests, save, relaunch and cancel.
- Export/import through file and QR, including a version 1 layout. Confirm mappings, color, visibility, game title and input settings round-trip.
- Check both sticks at centre, deadzone boundary, gentle diagonal and rim; tune curves/sensitivity/offsets. Check analog trigger 0/128/255, digital trigger 255 and release to zero.
- Hold two controls mapped to A and release one; the other must stay held. Test at least six simultaneous touches, slide-off, navigation interrupts and backgrounding.
- Switch each stick between Joystick and Touchpad. Confirm no jump on contact, correct swipe direction, steady output at different frame rates, stop on idle/lift/cancel, independent sensitivity from 0.25× to 4×, and save/QR/file migration. Inspect the compact editor with large fonts in portrait and landscape; every control must remain visible in the preview.
- Confirm vibration strength at zero and full, local haptics and game rumble.

## Connection regression (0.3.2)

- When a PC appears in discovery, connect to its announced input port. Confirm both the current metadata handshake and a legacy four-byte HELLO server work.
- Check setup-error messages separately from handshake timeouts. Discovery uses UDP 27501 and controller input uses UDP 27500 by default.
- Test the separately labelled Connection Test APK alongside the existing app, then import a profile if needed. Normal builds must retain the original package ID.

## Windows, Steam and multiplayer

- Install ViGEmBus, launch the dashboard and inspect joy.cpl. Test driver-missing, port-in-use and runtime-error screens, retry, console and demo modes.
- Connect four real phones (mix old/new clients). Confirm independent buttons, sticks and triggers in joy.cpl and a local multiplayer game; the fifth must get a full/occupied error.
- Pick P1–P4 and Auto before connecting. Occupied choices never displace a player. Logical app slots can differ from global XInput indices when other controllers exist.
- Confirm friendly names, occupancy, handshake RTT, Windows packet rates/input ages and diagnostics. These are measured statistics; RTT is not an input-to-game latency benchmark.
- Disconnect one phone, reconnect it, restart the server and roam/recover Wi-Fi. Verify input is neutral during reconnect and stale clients eventually release slots.
- Check Steam Big Picture and two Steam games, including analog racing input. Test Steam Input enabled and disabled according to each game's requirements.
- Exercise Windows system/light/dark choices, keyboard navigation and display scaling. Inspect the visualizer and log with all four players.

## Release

- Run PR checks and emulator instrumentation on the actual hosted workflow.
- Test the no-secrets Preview build, consolidated saved identity and complete legacy signing configuration; partial secrets must fail without key replacement.
- Confirm Android/server versions agree with the tag, SHA256SUMS match, source and GPL are included, and a second publication of the same release is skipped without touching its tag or assets.

## Kizuna branding, presets and updates

- Confirm launcher label/icon, home logo, Windows title/icon and README banner use Kizuna. Keep original copyright notices and GPL intact.
- Install Kizuna beside droidOSS. Export an old touchpad layout, import it, and verify mappings, sensitivity, profile name and game title. Do not remove the old app before copying needed profiles.
- Search Game presets for eFootball, each Tomb Raider trilogy and Resident Evil Requiem. Create a copy, tune it, save/relaunch, and confirm all controls still work. Exercise a legacy game with the indicated Steam Input/emulator mapping.
- Install a Kizuna release signed with the permanent kit, then publish a newer signed version. Check automatic/manual discovery, download progress, permission denial/retry and Android's native installer. Confirm profiles/settings survive the real upgrade.
- Reject a wrong-signing-key APK, a wrong application ID, a truncated package, a mismatched checksum and a prerelease. Check missing release, offline and rate-limit messages.
- On Windows, compile/run Setup on x64, test normal install/uninstall and silent update/restart. Verify custom settings persist and active players receive a disconnect confirmation. Check the portable-to-installed transition.
- Push a new app version on the actual repository after completing automatic signing setup or importing the existing identity. Confirm the signed APK, Setup EXE, portable ZIP, source, manifest and checksums all come from one successful run. A version tag pointing to another commit must fail before publication.

## Automatic signing setup

- Run **Set up Android signing** manually from main with a repository-scoped Secrets read/write token. Confirm it saves one identity and queues a new stable build; remove the setup token and confirm ordinary builds still sign.
- Run setup again with the token and confirm no key changes. Incomplete existing secrets must fail without replacement. Concurrent setup runs must serialize.
- Check that a missing identity produces only a separate Preview APK and skips stable publication. A PR must not receive either production identity or legacy signing secrets.
- Import the earlier private signing kit to preserve the supplied APK's signing identity, then exercise a real signed upgrade and confirm profiles/settings survive.
