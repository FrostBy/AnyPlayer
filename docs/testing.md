# Testing

## Release 0.1.0

The release code passed **53 JUnit/IntelliJ SDK checks and 15 native integration checks** on Windows x64. Plugin Verifier reports **Compatible** with IntelliJ IDEA 2026.1, build `IU-261.22158.277`. Real sandbox screenshots are in [screenshots/](screenshots/README.md).

All six OS/CPU packages passed 15 native integration checks each. See the final [Windows x64 validation](https://github.com/FrostBy/AnyPlayer/actions/runs/41203247548) and the [other five platform jobs](https://github.com/FrostBy/AnyPlayer/actions/runs/41202859683). Each job validates Plugin ID `me.romanovsky.anyplayer`, display name, vendor, class namespace, platform dependencies and executable permissions, then exercises the extracted installation ZIP. Packages, checksums and exact per-platform build provenance are in [release assets](https://github.com/FrostBy/AnyPlayer/releases/tag/v0.1.0).

## Existing archive API compatibility

The unchanged Windows x64 ZIP from [source run 41203247548](https://github.com/FrostBy/AnyPlayer/actions/runs/41203247548) was checked with Plugin Verifier 1.411. All six installers have the same recorded application `.class` tree; class-file major 65 requires Java 21.

```text
Original ZIP SHA-256: 9cb9810c69af30c797f9de0e4b6cf10618375b42f4c4b27e1457155744da99aa
Class-tree SHA-256:  971829544e1ab77f347bd2d0407a1fe490803acd4f35da8d433fadc4c080e7f4
```

Results come from the [eight-version run](https://github.com/FrostBy/AnyPlayer/actions/runs/41206906836) and the [first-2025.2/263-RC boundary run](https://github.com/FrostBy/AnyPlayer/actions/runs/41208031558), which checked two SDKs in parallel on one runner. The API probe uses `-ignore-os-arch`; it is separate from the normal probe.

| IDEA version | Build | API-probe result |
| --- | --- | --- |
| 2024.1.7 | 241.19416.15 | 13 compatibility problems, including Java virtual-thread and SDK references |
| 2024.2.6 | 242.26775.15 | 3 problems: `PluginId.Companion` class/field and the two-argument browse-folder listener |
| 2024.3.7.1 | 243.28141.41 | 2 problems: `PluginId.Companion` class/field |
| 2025.1.7.2 | 251.29188.72 | 2 problems: `PluginId.Companion` class/field |
| 2025.2, first release | 252.23892.409 | Compatible |
| 2025.2.6.4 | 252.28539.149 | Compatible |
| 2025.3.6.2 | 253.33813.99 | Compatible |
| 2026.1.5.1 | 261.27258.136 | Compatible |
| 2026.2.4 | 262.11879.100 | Compatible; internal API usage: `PluginManagerCore.getPlugin` |
| 2026.3 RC | 263.7307.112 | Compatible; the same internal API usage and deprecated `FileEditorManager.openFile(VirtualFile, boolean)` |

The first tested API-compatible release is **2025.2**; the latest checked build is **263.7307.112 RC**. These results do not establish untested patches, future versions or native playback on those SDKs. The original archive used `since-build=261` / `until-build=261.*`. The compatibility rebuild preserves the tested application bytecode and native payload but declares only `since-build=261`, without either upper-build attribute. The native-variant minimum remains 261.

Normal probes reported two missing OS/architecture modules on every tested SDK, including 261. Those constraint-resolution reports do not establish actual installation or loading success/failure.

[TeamCity 399252](https://intellij-plugins-performance.teamcity.com/buildConfiguration/PluginPlatformTests_ExternalPluginsCheckerTests/399252?guest=1) passed `installPluginTest` and `testTrialWidgetPresence`, but its [idea.log archive](https://intellij-plugins-performance.teamcity.com/guestAuth/app/rest/builds/id:399252/artifacts/content/install-plugin-test/with-plugin/logs-134822.zip) records rejection of the Linux ARM64 plugin on an x86_64 host; that success does not prove plugin loading or media playback on RC.

## Running checks

Use JDK 21+ and prepare the matching [native runtime](../native/README.md) first:

```powershell
.\gradlew.bat test nativeTest
.\gradlew.bat buildPluginVariants_windows_x86_64 verifyPlugin
powershell -NoProfile -File scripts/test-prepare-native.ps1
openspec validate anyplayer-v1 --strict
```

On macOS/Linux, use `./gradlew` and the corresponding variant task. Linux native tests need a display, such as `xvfb-run -a ./gradlew nativeTest` in CI.

Append `'-PlocalIdePath=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1'` to reuse an installed SDK. To exercise an extracted plugin, pass `-PnativeRoot=<installation>/AnyPlayer/native` to `nativeTest`.

SDK tests accept `-PmediaFixture=<local video>` for an additional finite, seekable video with audio. Missing or unsupported optional input prints a diagnostic; JUnit3 records that method as successful, so it must not be counted as an exercised media fixture. Generated fixtures provide independent coverage.

## Coverage

- Source parsing, URL-secret redaction, Unicode paths, explicit transport-stream/extensionless opening and normal TypeScript editor routing.
- Real VLC callback frames, play/pause, seeking, speed, tracks, EOF replay and ownership/disposal.
- Timeline clicks/drags, display-quality paused frames, hover-only previews, cached local refinement and adjacent-frame navigation.
- Source-time alignment, delayed audio, stable buffered spectrogram edges, centered live spectrograms, full-file analysis, waveform zoom/Follow and stereo isolation.
- Variable-delay GIF/APNG/WebP, still-image transparency, bounded Fit previews and original-pixel region inspection.
- HTTP Range input, MediaInfo, settings persistence, no-audio controls, pointer cursors and compact layouts.
- Stale-result rejection, bounded waveform retention, process failure/cancellation and source immutability.
- Native preparation upgrades, interrupted extraction, cache reuse, stale-module removal and failure preservation using offline temporary archives.

## Manual smoke test

Install the matching ZIP through **Settings > Plugins > Install Plugin from Disk** and restart.

1. Play local video/audio; seek, pause, resume, replay after EOF, change speed/volume and select tracks.
2. Inspect Live/Full file spectra and Waveform. Mute must leave source analysis intact. Check Follow, zoom and separate channels.
3. Start another media tab, return to code, then close the active media tab. Confirm playback ownership and cleanup.
4. Open still/animated images; test Fit, original pixels, pan, frame stepping and repeat.
5. Open a direct HTTP(S) URL and an external local path; copy MediaInfo properties/report and inspect failure/retry states.
6. Check narrow splits, light/dark themes, HiDPI, keyboard focus and repeated open/close cycles.

## Limits

Native automated tests use dummy audio output; they do not establish physical speaker behavior. Sandbox screenshots document the Windows UI. Automated checks do not constitute manual acceptance on every OS, display scale or JetBrains product.

Dedicated 1080p/4K CPU/RSS benchmarks, the wider IDE-product matrix and equivalent nonzero-start timing fixtures for MKV/TS remain open. Rendering targets approximately 60 Hz; FFT generation remains 15 fps. Native codecs may internally decode full image frames even when plugin-owned region buffers are bounded.

Some optional IDE dependency downloads returned HTTP 451 during local verification; those responses were recorded without bypassing them. Reports are generated under `build/reports/tests/` and `build/reports/pluginVerifier/`.
