## Current delivery status

Current Windows x64 release ZIP: `AnyPlayer-0.1.0-windows-x86_64.zip`. Release code passed 52 JUnit/SDK and 15 native checks; the 0.1.0 package was rebuilt and Plugin Verifier reports compatibility with IDEA2026.1. Other platform packages are being prepared and are not claimed as verified. Source: https://github.com/FrostBy/AnyPlayer. See docs/testing.md for validation history.

Dev.7 replaces per-pointer thumbnail decoding with prepared local preview strips and session-only HTTP ranges, fixes the video/still fit mismatch, and implements the owner's approved UX recommendations: compact overflow, buffering/seeking/error status, volume feedback, contextual shortcuts/tooltips, analysis cancellation/resume, Go to time and waveform Follow with manual-pan opt-out.

Dev.6 added timestamped hover thumbnails, main-frame scrubbing with playback-state restoration, Left/Right aliases and Ctrl+Left/Right frame inspection. It also fixed notification discovery through displayed Swing wrappers, added persistent ignored-notice opt-out, set hand cursors on enabled controls and marked the viewer as index-independent.

Dev.4 covers direct pointer seeking, timer-safe dragging, Play after EOF, centered live analysis with upcoming audio, immediately available track menus, clearer spectrum/speed labels and dismissal of the current English ignored-file notice in this viewer. The owner withdrew the mobile-trailer frame report; no game-project source or media was changed.

Owner-approved additions from Audio Player research: persistent playback/view preferences and an interactive waveform with range zoom, scrolling and separate channels. Adapted timeline code retains Masaki Aida's MIT notice. The plugin has original light/dark SVG branding and matching media-file icons.

The owner tests the archive, then separately authorizes uploading sources to `https://github.com/FrostBy/AnyPlayer`. Marketplace upload is manual by the owner under Romanovsky. License/source links, tags and screenshots are finalized after testing; no automatic publication is configured.

## 1. Planning and visual contract

- [x] 1.1 Record agreed viewer scope, GPLv3 direction, source-track spectrogram semantics, and IDEA-derived platform policy.
- [x] 1.2 Complete annotated moodboard, video/audio/image/animation/MediaInfo mockups, light/dark/narrow layouts, and loading/error states; verify the design preview.
- [x] 1.3 Validate this OpenSpec change.
- [x] 1.4 Owner approved implementation on 2026-10-31. Subsequent scope additions: HTTP(S) URLs/local paths and all compatible IntelliJ Platform JetBrains IDEs.

## 2. Technical prototype

- [x] 2.1 Create one Kotlin IntelliJ Platform Gradle project with wrapper and an IDEA 2026.1 development sandbox.
- [x] 2.2 Pin a compatible vlcj 4/libVLC 3/JNA configuration and prove callback-rendered MP4/audio playback inside IDEA.
- [ ] 2.3 Verify play/pause, seek, speed, resize, HiDPI, close/reopen, native callback dispatch, and resource disposal.
- [x] 2.4 Prototype FFmpeg live spectrogram frames without changing VLC's audible output; prove VLC track-to-FFmpeg stream mapping.
- [ ] 2.5 Measure source-time alignment on tones/impulses, delayed streams, seek, pause, and speed changes; meet the 200 ms initial target without drift.
- [ ] 2.6 Record rendering/analysis CPU and memory behavior on representative 1080p and 4K fixtures; resolve native lifecycle failures before expanding the UI.
- [ ] 2.7 Prove GIF/APNG/animated-WebP decode, variable frame timing, alpha/disposal composition, seeking and paused frame stepping using the actual chosen FFmpeg build.

## 3. Native platform packages

- [ ] 3.1 Establish the verified IDEA release/OS/architecture matrix and exact compatible native build manifest with origins and checksums.
- [ ] 3.2 Package complete VLC runtimes, FFmpeg, and MediaInfo through nativeVariants for every supported target, preserving dependent libraries and permissions.
- [ ] 3.3 Prove native loading on Windows, macOS, and Linux, both architectures, including process/host-architecture distinction and the supported libc baseline.
- [ ] 3.4 Verify a clean-machine installation without system media utilities, runtime downloads, or JBR modifications.

## 4. Media editor

- [ ] 4.1 Implement the approved FileEditorProvider layouts using platform components, theme tokens, icons, and disposal.
- [ ] 4.2 Add playback transport, track/subtitle selection, one-audible-session-per-project behavior, and background playback while coding.
- [ ] 4.3 Add still PNG/JPEG/BMP/GIF/WebP viewing, orientation/transparency, fit/100%/zoom, and pan.
- [ ] 4.4 Add focus-scoped keyboard controls, accessible labels, light/dark/HiDPI support, and usable 480-pixel split-editor layout.
- [ ] 4.5 Implement loading, unsupported/damaged-file, missing-native, disappeared-file, and retry/diagnostic states.
- [ ] 4.6 Add seekable GIF/WebP/APNG animation with variable-delay presentation, bounded frames, previous/next frame, speed and repeat controls; preserve independently playing audio.
- [x] 4.7 Add Open Media for HTTP(S) URLs and local paths outside the project, with capability-aware controls for streams and sanitized diagnostics.

## 5. Analysis and information

- [x] 5.1 Implement cancellable full-file spectrogram generation with axes, intensity legend, playback cursor, and click-to-seek.
- [x] 5.2 Implement bounded live-analysis queues, PTS-based source-time presentation, preroll, and generation invalidation for seek/track/source changes.
- [ ] 5.3 Verify analysis failures do not stop working playback and analysis cannot remain active after editor disposal.
- [x] 5.4 Add source-aware cache invalidation and bounded working/output resources under the IDE system cache.
- [x] 5.5 Implement structured MediaInfo tracks, missing-field handling, full text report, and complete-value/report copying independently of playback.

## 6. Verification and release preparation

- [x] 6.1 Add generated/licensed media fixtures and focused tests for timing, track mapping, lifecycle, metadata parsing, and cache invalidation.
- [ ] 6.2 Test packaged H.264/AAC, HEVC, VP9/Opus, MP3/WAV/FLAC, still images, variable-delay/blended GIF/APNG/WebP animations, corrupted files, Unicode/special-character paths, and absent audio tracks.
- [ ] 6.3 Run actual media smoke checks for every advertised target; verify sound, rendering, seeking, both spectrograms, metadata, and repeated editor disposal.
- [ ] 6.4 Configure GitHub Actions build/test/native-archive checks and Plugin Verifier against each declared IDEA release line.
- [ ] 6.7 Verify compatibility in IntelliJ IDEA, PyCharm, WebStorm, GoLand, CLion, Rider and other advertised IntelliJ Platform products without Java-plugin dependencies.
- [ ] 6.5 Prepare GPLv3 project licensing, dependency notices/source obligations, README, platform/format matrix, screenshots, and free Marketplace listing text.
- [ ] 6.6 Build and verify final platform archives; publish GitHub/Marketplace artifacts only on the owner's explicit publication command.

## 7. Approved review findings

- [x] 7.1 R1: restrict automatic provider acceptance to regular local files; retain explicit virtual sources and add JAR/directory/local/remote SDK regressions.
- [x] 7.2 R2: separate source geometry from the bounded Fit preview; add cancellable visible-region decoding for true100%/zoom/pan and an8192×4096 source-detail regression, preserving video fit and image transparency.
- [x] 7.3 R3: key native archives/extraction by SHA-256, stage a fresh complete bundle and preserve the old bundle on failure; verify upgrades, incomplete extraction, stale-module removal and reuse with offline temporary fixtures.
- [x] 7.4 R4/R5: stream source-aligned PCM into bounded waveform columns, handle intervals with fewer samples than output columns, preserve channel/timing behavior and version the waveform cache; test long input,8kHz/100ms, stereo, delayed audio, cancellation and failures.
- [x] 7.5 R6: resolve physical sources from current VirtualFile paths, handle legacy local SOURCE and scoped VFS move/name events, invalidate old sessions/results and restore position/state after reopening; add close/rename/reopen, parent move and active-session SDK regressions.
- [x] 7.6 R7: clear live/full spectrum state when selected audio is disabled/unmapped, reject stale results and regenerate after valid reselection; verify mute remains independent.
- [x] 7.7 Run focused regressions followed by the established JUnit/SDK and native suites; run the offline native fixture check and strict OpenSpec validation. Record actual results and remaining platform/manual limits.
- [x] 7.8 Build the next local Windows x64 archive, verify compatibility and contents/JNA/notices, and produce its SHA-256. No commit, push or publication is included.

## 8. Subsequent owner UI and screenshot requests

- [x] 8.1 Explain disabled audio-analysis controls for files without audio, disable volume/mute in main and overflow controls, and add enabled/disabled tab and other interaction cursors; verify with targeted SDK regressions.
- [x] 8.2 Capture real video/audio/image UI in a clean sandbox using original demonstration media, verify consistent dimensions and privacy, embed the files in README and document separate manual Marketplace Media upload.
- [x] 8.3 Build dev.11 for Windows x64, run Plugin Verifier and save SHA-256; leave Astra C1–C6 unchanged pending review approval.

## 9. Owner-approved Astra follow-up

- [x] 9.1 C1: preserve explicit local media selection through a path-free marker; verify production source preparation and playback of transport-stream/extensionless files while ordinary TypeScript is declined.
- [x] 9.2 C2: remove the unreachable preview decoder/cache and exercise latest-hover behavior through the actual indexed path.
- [x] 9.3 C3: derive optional-fixture positions from actual duration and state required video/audio/seek capabilities explicitly.
- [x] 9.4 C4: centralize SDK settings isolation and standard temporary-fixture ownership/cleanup after native release.
- [x] 9.5 C5: replace the tautological native file-size check with a pre/post SHA-256 assertion.
- [x] 9.6 C6: replace stale AGENTS.md scaffold text with the current contributor workflow under the owner's explicit approval.
- [x] 9.7 Validate focused/full SDK/native checks, short/owner/unsupported optional fixtures, strict OpenSpec and the local Windows archive/Verifier/checksum. Do not publish or fix unconfirmed audit hypotheses.

## 10. Owner-approved smooth graphs and sharp seek frames

- [x] 10.1 Add a bounded native-synchronized presentation clock and16ms graph/cursor rendering, preserving pause/buffer/seek/rate/EOF transitions; test monotonic interpolation and stale-seek rejection.
- [x] 10.2 Separate waveform image/view ranges, prefetch bounded neighboring data and retain a valid display during Follow/refill; test source-coordinate painting, buffered reuse, distant seeks and manual-pan opt-out.
- [x] 10.3 Remove tiny cached main-video stills and the per-pointer180ms debounce; coalesce one display-resolution still request to the latest target, capped at4K; test that no thumbnail replaces the HD canvas and the final frame is correct.
- [x] 10.4 Run final SDK/native regressions and strict OpenSpec, verify/package dev.13 for Windowsx64 and save its checksum. Record actual results and remaining manual/platform limits.
