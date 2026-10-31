# Testing

## Release 0.1.0

The release code passed **53 JUnit/IntelliJ SDK checks and 15 native integration checks** on Windows x64. Plugin Verifier reports **Compatible** with IntelliJ IDEA 2026.1, build `IU-261.22158.277`. Real sandbox screenshots are in [screenshots/](screenshots/README.md).

All six OS/CPU packages passed 15 native integration checks each. See the final [Windows x64 validation](https://github.com/FrostBy/AnyPlayer/actions/runs/41203247548) and the [other five platform jobs](https://github.com/FrostBy/AnyPlayer/actions/runs/41202859683). Each job validates Plugin ID `me.romanovsky.anyplayer`, display name, vendor, class namespace, platform dependencies and executable permissions, then exercises the extracted installation ZIP. Packages, checksums and exact per-platform build provenance are in [release assets](https://github.com/FrostBy/AnyPlayer/releases/tag/v0.1.0).

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

Install the matching ZIP through **Settings в†’ Plugins в†’ Install Plugin from Disk** and restart.

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
