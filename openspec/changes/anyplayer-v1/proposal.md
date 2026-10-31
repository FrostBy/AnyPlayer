## Why

Developers need to inspect video, audio, images and media metadata without leaving their JetBrains IDE. Any Player provides a self-contained native media backend and an editor integrated with the IDE.

## What Changes

- Introduce a native-looking file editor for local media, using the existing IDEA editor area.
- Open media using an HTTP(S) URL or local path, including files outside the project and capability-aware network streams.
- Target compatible JetBrains IDEs on IntelliJ Platform, not IntelliJ IDEA alone.
- Play video and audio with transport controls, seeking, volume, playback speed, track selection, and subtitles.
- Display images with fit, original-size, zoom, and pan controls.
- Treat GIF, animated WebP, and APNG as seekable animations with play/pause, source timing, frame stepping, and repeat controls.
- Provide full-file and live spectrograms for the selected source audio track.
- Display structured MediaInfo fields and a complete copyable report.
- Bundle native dependencies for every desktop platform officially supported by the targeted IDEA releases; require no separate VLC, FFmpeg, or MediaInfo installation.
- Publish public source under GPLv3 and prepare free JetBrains Marketplace distributions.
- Establish annotated visual references and interactive design mockups before implementing the plugin.

## Capabilities

### New Capabilities

- `media-editor`: File opening, native IDEA presentation, image viewing, keyboard access, and editor state.
- `media-sources`: HTTP(S) URLs, local paths, source validation, privacy, and stream capabilities.
- `media-playback`: Video/audio playback, track selection, and playback resource ownership.
- `animated-image-playback`: Seekable GIF/WebP/APNG animation, variable frame timing, composition, and repeat behavior.
- `audio-analysis`: Full-file and live source-track spectrograms synchronized with playback.
- `media-information`: Structured properties and complete MediaInfo reports.
- `native-distribution`: Self-contained native packaging, compatibility, and release verification.

### Modified Capabilities

None; these specifications define the initial plugin capabilities.

## Impact

The implementation is one Kotlin/Gradle plugin with IntelliJ Platform SDK integration and packaged libVLC/FFmpeg/MediaInfo binaries. Tests cover native runtime, UI, lifecycle and platform integration. Design references live in `docs/design/`; current validation and outstanding distribution work are recorded in `docs/testing.md` and `tasks.md`.
