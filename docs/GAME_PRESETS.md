# Game preset input and calibration

All 58 templates have explicit title-specific settings, shown below. Values are suggested Kizuna response settings, not measured engine deadzones or guaranteed optimal settings. Tune in-game sensitivity and control schemes for your edition. Centre X/Y remain **0** for every preset: centre calibration depends on the device, and game-specific offsets would create drift. Both joystick axes retain full travel at the rim.

New presets apply these settings immediately. Existing copies are recognized by the saved Game / Steam title. On loading a profile, input settings are upgraded only when the complete input block still matches its old factory values; any changed input or centre offset preserves that block. Stock touchpad and D-pad values are upgraded independently, retaining changed values and all positions, modes and mappings. Use **Input & calibration → Use game defaults** to apply the current game's defaults explicitly; it retains layout geometry, mappings and modes. Profiles with an edited game title can be matched by restoring the catalog title or created again from Game presets.

The stick columns use **deadzone / response curve**. Touchpad columns use **sensitivity / moving deadzone compensation** and apply when that control is switched to Touchpad. Left touchpad defaults are 1× and zero compensation. Trigger columns use **mode / deadzone / range**; analog output reaches full travel at deadzone + range, digital output reaches full travel on touch. All stick gains are 1×; curves shape fine movement without lowering maximum travel. Every template retains the full Xbox button set.

| Preset ID | Left stick | Right stick | Touchpad | D-pad zone / directions | Triggers | Vibration | Purpose |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `efootball` | 0.04 / 1.15 | 0.06 / 1.05 | 1.2× / 0.16 | 0.2 / 8 | Analog / 0.04 / 0.92 | 35% | Close dribbling and skill-stick flicks; slide RT for dash strength, reach the top for full kicks. |
| `pes2021` | 0.06 / 1.1 | 0.08 / 1.1 | 1.1× / 0.18 | 0.22 / 8 | Digital / 0 / 1 | 40% | Direct movement and controlled skill-stick flicks with full-press sprint modifiers. |
| `fc` | 0.05 / 1.08 | 0.05 / 1 | 1.3× / 0.14 | 0.18 / 8 | Analog / 0.03 / 0.94 | 38% | Quick skill-stick gestures and progressive sprint; enable Analog Sprint in games that offer it. |
| `tr-original-1` | 0.2 / 1.2 | 0.16 / 1.1 | 1× / 0.12 | 0.2 / 4 | Digital / 0 / 1 | 30% | Cardinal tank turns and deliberate grid-aligned jumps. |
| `tr-original-2` | 0.19 / 1.18 | 0.15 / 1.12 | 1.05× / 0.12 | 0.19 / 4 | Digital / 0 / 1 | 34% | Cardinal tank movement with a slightly earlier movement response. |
| `tr-original-3` | 0.18 / 1.16 | 0.14 / 1.14 | 1.1× / 0.14 | 0.18 / 4 | Digital / 0 / 1 | 36% | Cardinal tank turns and deliberate run/jump direction changes. |
| `tr-original-4` | 0.19 / 1.22 | 0.15 / 1.16 | 1× / 0.14 | 0.21 / 4 | Digital / 0 / 1 | 32% | Deliberate tank movement for ledges, ropes and grid-based platforming. |
| `tr-original-5` | 0.18 / 1.2 | 0.14 / 1.18 | 1.05× / 0.14 | 0.2 / 4 | Digital / 0 / 1 | 33% | Cardinal movement with controlled starts for classic traversal. |
| `tr-original-6` | 0.14 / 1.28 | 0.1 / 1.3 | 1.2× / 0.18 | 0.22 / 8 | Digital / 0 / 1 | 40% | Softer movement onset and smoother manual camera; match the edition or emulator mapping. |
| `tr-remastered-1` | 0.09 / 1.18 | 0.07 / 1.32 | 1.3× / 0.16 | 0.18 / 4 | Digital / 0 / 1 | 40% | Modern analog movement and camera with a cardinal D-pad for tank-control use. |
| `tr-remastered-2` | 0.08 / 1.16 | 0.065 / 1.3 | 1.35× / 0.17 | 0.17 / 4 | Digital / 0 / 1 | 42% | Responsive modern movement and camera; cardinal tank directions remain available. |
| `tr-remastered-3` | 0.075 / 1.14 | 0.06 / 1.28 | 1.4× / 0.18 | 0.16 / 4 | Digital / 0 / 1 | 44% | Quick modern traversal with a light camera curve and cardinal tank directions. |
| `tr-remastered-4` | 0.095 / 1.22 | 0.075 / 1.36 | 1.25× / 0.16 | 0.19 / 4 | Digital / 0 / 1 | 38% | Gentle modern movement onset for ledge and rope positioning; cardinal tank directions. |
| `tr-remastered-5` | 0.085 / 1.2 | 0.07 / 1.34 | 1.3× / 0.17 | 0.185 / 4 | Digital / 0 / 1 | 39% | Measured modern traversal and smooth camera; cardinal tank directions remain available. |
| `tr-remastered-6` | 0.1 / 1.26 | 0.08 / 1.38 | 1.2× / 0.18 | 0.21 / 8 | Digital / 0 / 1 | 43% | Gentle analog movement and camera for Angel of Darkness; eight-way D-pad available. |
| `tr-adventure-0` | 0.07 / 1.1 | 0.08 / 1.28 | 1.4× / 0.18 | 0.22 / 8 | Digital / 0 / 1 | 50% | Responsive traversal with a smooth exploration camera and full-press combat triggers. |
| `tr-adventure-1` | 0.08 / 1.2 | 0.075 / 1.38 | 1.25× / 0.16 | 0.2 / 8 | Digital / 0 / 1 | 45% | Gentler movement onset for platform positioning and a fine camera response. |
| `tr-adventure-2` | 0.065 / 1.14 | 0.065 / 1.24 | 1.5× / 0.2 | 0.21 / 8 | Digital / 0 / 1 | 52% | Fluid traversal and faster camera response for exploration and combat. |
| `tr-survivor-0` | 0.07 / 1.08 | 0.055 / 1.45 | 1.4× / 0.24 | 0.2 / 8 | Digital / 0 / 1 | 48% | Bow-friendly fine camera response with responsive movement and full aim/fire triggers. |
| `tr-survivor-1` | 0.065 / 1.1 | 0.05 / 1.5 | 1.35× / 0.22 | 0.19 / 8 | Digital / 0 / 1 | 46% | Fine bow adjustments and steady traversal; full aim/fire triggers. |
| `tr-survivor-2` | 0.06 / 1.14 | 0.045 / 1.55 | 1.25× / 0.2 | 0.18 / 8 | Digital / 0 / 1 | 42% | Gentle fine aiming and movement onset for stealth and traversal. |
| `lara-coop-0` | 0.055 / 1.02 | 0.05 / 1 | 1.2× / 0.1 | 0.18 / 8 | Digital / 0 / 1 | 50% | Near-linear independent movement and directional aiming for twin-stick play. |
| `lara-coop-1` | 0.05 / 1.04 | 0.045 / 1.02 | 1.25× / 0.1 | 0.17 / 8 | Digital / 0 / 1 | 48% | Quick twin-stick movement and aim with a small neutral region. |
| `lara-go` | 0.24 / 1 | 0.2 / 1 | 0.8× / 0.08 | 0.24 / 4 | Digital / 0 / 1 | 15% | Four distinct puzzle directions; requires a Steam Input keyboard/mouse template. |
| `re-classic-0` | 0.22 / 1.24 | 0.18 / 1.15 | 0.9× / 0.12 | 0.22 / 4 | Digital / 0 / 1 | 35% | Deliberate cardinal tank movement for fixed-camera rooms. |
| `re-classic-1` | 0.21 / 1.22 | 0.17 / 1.16 | 0.95× / 0.12 | 0.21 / 4 | Digital / 0 / 1 | 38% | Cardinal tank turns with a clear neutral zone for fixed-camera navigation. |
| `re-classic-2` | 0.18 / 1.16 | 0.16 / 1.12 | 1× / 0.14 | 0.19 / 4 | Digital / 0 / 1 | 42% | Quicker tank-movement onset for evasive movement; cardinal directions. |
| `re-classic-3` | 0.12 / 1.12 | 0.08 / 1.48 | 1.1× / 0.18 | 0.2 / 8 | Digital / 0 / 1 | 30% | Fine first-person aiming; use the original edition or emulator control mapping. |
| `re-classic-4` | 0.2 / 1.25 | 0.16 / 1.2 | 0.95× / 0.14 | 0.215 / 4 | Digital / 0 / 1 | 40% | Measured cardinal tank turns and full-press action inputs. |
| `re-classic-5` | 0.11 / 1.1 | 0.075 / 1.44 | 1.15× / 0.18 | 0.195 / 8 | Digital / 0 / 1 | 32% | Responsive movement and fine first-person aiming with emulator mappings. |
| `re-classic-6` | 0.1 / 1.12 | 0.06 / 1.6 | 1× / 0.16 | 0.18 / 8 | Digital / 0 / 1 | 28% | Fine aiming onset for the hybrid aiming interface; custom emulator mapping required. |
| `re-classic-7` | 0.19 / 1.24 | 0.15 / 1.18 | 1× / 0.14 | 0.205 / 4 | Digital / 0 / 1 | 36% | Measured fixed-camera tank movement with cardinal D-pad navigation. |
| `re-classic-8` | 0.185 / 1.22 | 0.145 / 1.2 | 1.05× / 0.14 | 0.2 / 4 | Digital / 0 / 1 | 37% | Slightly earlier movement onset while retaining deliberate cardinal tank turns. |
| `re-classic-9` | 0.16 / 1 | 0.2 / 1 | 0.75× / 0.08 | 0.18 / 4 | Digital / 0 / 1 | 15% | Four-way handheld navigation; emulator mappings must cover the battle timing controls. |
| `re-hd-0` | 0.12 / 1.22 | 0.12 / 1.2 | 1× / 0.14 | 0.2 / 4 | Digital / 0 / 1 | 42% | Gentle analog movement for the alternate scheme and cardinal D-pad for tank controls. |
| `re-hd-1` | 0.13 / 1.24 | 0.13 / 1.22 | 0.95× / 0.14 | 0.21 / 4 | Digital / 0 / 1 | 40% | Deliberate fixed-camera navigation with cardinal tank directions available. |
| `re-action-0` | 0.13 / 1.16 | 0.09 / 1.38 | 1.15× / 0.22 | 0.22 / 8 | Digital / 0 / 1 | 45% | Deliberate classic movement and fine camera input; use the selected 2005 control type. |
| `re-action-1` | 0.09 / 1.12 | 0.075 / 1.42 | 1.25× / 0.24 | 0.21 / 8 | Digital / 0 / 1 | 48% | Measured movement and fine aim response for the classic shoulder-camera scheme. |
| `re-action-2` | 0.055 / 1.04 | 0.055 / 1.26 | 1.6× / 0.22 | 0.18 / 8 | Digital / 0 / 1 | 52% | Quick movement and camera response for mobile combat and evasive actions. |
| `re-action-3` | 0.1 / 1.18 | 0.065 / 1.5 | 1.15× / 0.2 | 0.2 / 8 | Digital / 0 / 1 | 40% | Measured movement and gentle camera onset for close-range aiming. |
| `re-action-4` | 0.085 / 1.14 | 0.06 / 1.46 | 1.25× / 0.2 | 0.19 / 8 | Digital / 0 / 1 | 42% | Responsive movement with fine camera input for combat and partner play. |
| `re-action-5` | 0.08 / 1.16 | 0.05 / 1.58 | 1.1× / 0.18 | 0.18 / 8 | Digital / 0 / 1 | 36% | Gentle fine aiming for deliberate survival combat; full-press aim/fire triggers. |
| `re-action-6` | 0.065 / 1.08 | 0.045 / 1.4 | 1.4× / 0.2 | 0.17 / 8 | Digital / 0 / 1 | 44% | Faster movement and camera response for evasive action with fine aim retained. |
| `re-action-7` | 0.06 / 1.1 | 0.04 / 1.52 | 1.3× / 0.18 | 0.16 / 8 | Digital / 0 / 1 | 40% | Fine camera control and quick movement for modern shoulder-camera combat. |
| `re-action-8` | 0.05 / 1.03 | 0.05 / 1.22 | 1.65× / 0.22 | 0.18 / 8 | Digital / 0 / 1 | 50% | Fast movement and low-latency camera onset for squad action. |
| `re-action-9` | 0.04 / 1 | 0.04 / 1.18 | 1.8× / 0.16 | 0.16 / 8 | Digital / 0 / 1 | 30% | Fast directional movement and camera response for competitive action. |
| `re-first-0` | 0.08 / 1.16 | 0.045 / 1.68 | 1.1× / 0.18 | 0.19 / 8 | Digital / 0 / 1 | 32% | Gentle first-person camera onset for deliberate exploration and aiming. |
| `re-first-1` | 0.06 / 1.08 | 0.04 / 1.48 | 1.45× / 0.2 | 0.17 / 8 | Digital / 0 / 1 | 40% | Quicker first-person movement and camera response with fine aiming retained. |
| `re-first-2` | 0.07 / 1.12 | 0.05 / 1.55 | 1.25× / 0.18 | 0.18 / 8 | Digital / 0 / 1 | 35% | Conservative camera and movement defaults; adjust for the chosen camera mode and edition. |
| `re-console-0` | 0.16 / 1.1 | 0.05 / 1.6 | 1× / 0.16 | 0.2 / 8 | Digital / 0 / 1 | 30% | Fine cursor aiming for an emulator mapping; a stick is not a light-gun pointer. |
| `re-console-1` | 0.15 / 1.12 | 0.045 / 1.64 | 0.95× / 0.16 | 0.19 / 8 | Digital / 0 / 1 | 28% | Gentle cursor-aim response for an emulator mapping; pointer mapping still required. |
| `re-console-2` | 0.08 / 1.08 | 0.07 / 1.36 | 1.3× / 0.2 | 0.18 / 8 | Digital / 0 / 1 | 38% | Responsive movement and measured aiming with a suitable handheld emulator mapping. |
| `re-online-0` | 0.065 / 1.1 | 0.05 / 1.44 | 1.4× / 0.18 | 0.175 / 8 | Digital / 0 / 1 | 36% | Responsive movement and controlled aiming; online service availability is separate. |
| `re-online-1` | 0.045 / 1.02 | 0.04 / 1.24 | 1.7× / 0.16 | 0.155 / 8 | Digital / 0 / 1 | 32% | Fast movement and camera response; online service availability is separate. |
| `steam-standard` | 0.1 / 1.05 | 0.1 / 1.08 | 1.25× / 0.18 | 0.22 / 8 | Digital / 0 / 1 | 60% | Balanced movement and camera response for a standard Xbox layout. |
| `steam-racing` | 0.035 / 1.55 | 0.1 / 1.15 | 1× / 0.12 | 0.24 / 8 | Analog / 0.04 / 0.92 | 55% | Progressive steering near centre and full-range slide throttle/brake. |
| `steam-shooter` | 0.06 / 1.08 | 0.045 / 1.52 | 1.4× / 0.2 | 0.18 / 8 | Digital / 0 / 1 | 40% | Responsive movement and fine camera response with full-press aim/fire triggers. |
| `steam-platformer` | 0.08 / 1.02 | 0.12 / 1.1 | 1.1× / 0.12 | 0.16 / 8 | Digital / 0 / 1 | 45% | Direct movement, eight-way D-pad and full analog movement range for platforming. |

Open **Game profiles → Game presets** to select a title and save an editable copy. The store holds 50 custom profiles plus Default. Imports, exports and saved copies retain the input and control settings.

Legacy PC editions may need Steam Input keyboard/DirectInput mappings. Console-only entries need an appropriate emulator and mappings; light-gun and touch interfaces are not automatically reproduced by an Xbox stick. Online presets do not restore unavailable services. Requiem uses conservative, editable defaults for the selected camera mode rather than an assumed universal calibration.

Control requirements were checked against publisher documentation; the numeric response choices above are app-side starting points and have not been play-tested in every game:

- [Konami eFootball Xbox manual](https://img.konami.com/efootball/s/img/manual/efootball2023_xbox_en.pdf): variable dash strength, light stick dribbling and full-trigger stunning kicks.
- [EA FIFA PC controller settings](https://www.ea.com/able/resources/fifa/fifa-21/pc/controller-settings): Analog Sprint depends on the in-game option.
- [Tomb Raider classic and modern controls](https://www.tombraider.com/news/video-games/10-things-you-need-to-know-before-playing-classic-era-tomb-raider): tank movement versus modern analog controls.
- [Rise of the Tomb Raider manual](https://www.feralinteractive.com/en/manuals/riseofthetombraider/latest/linux/): dual-stick movement/camera and aiming controls.
- [Resident Evil 4 controls](https://game.capcom.com/manual/re4/en/steam/page/2/1): selectable control types; match the selected type in-game.
