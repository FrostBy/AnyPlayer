## 1. Implemented

- [x] 1.1 Define the product scope, visual studies and seven capability specifications.
- [x] 1.2 Implement the Kotlin/Swing file editor with IDE theme components and disposal.
- [x] 1.3 Implement VLC video/audio playback, transport, track selection and project playback ownership.
- [x] 1.4 Support local paths, direct HTTP(S) URLs and explicit extensionless/transport-stream opening.
- [x] 1.5 Implement still-image Fit, original-pixel regions, zoom/pan and seekable GIF/APNG/WebP animation.
- [x] 1.6 Implement source-aligned Live/Full file spectrograms, waveform zoom/Follow and separate channels.
- [x] 1.7 Add MediaInfo properties/report copying and persistent playback/view preferences.
- [x] 1.8 Implement timeline hover refinement, display-quality scrub frames, frame stepping and EOF replay.
- [x] 1.9 Add no-audio states, contextual shortcuts, compact controls and pointer cursors.
- [x] 1.10 Apply confirmed review fixes for source routing, image inspection, waveform retention, native preparation, VFS refresh, cancellation and test isolation.
- [x] 1.11 Add smooth source-clock presentation and buffered analysis scrolling.
- [x] 1.12 Capture real sandbox screenshots with original media and embed them in README.
- [x] 1.13 Run 52 SDK/unit and 15 native checks; verify the Windows x64 0.1.0 package against IU-261.22158.277.
- [x] 1.14 Publish GPLv3 source and the Windows x64 package in the public GitHub 0.1.0 release.
- [x] 1.15 Configure native-runner builds, installation-ZIP validation and native integration checks for all six OS/CPU targets.

## 2. Release work in progress

- [x] 2.1 Complete native loading and media tests for Windows ARM64 and both macOS/Linux architectures; attach successful ZIPs to the same release.
- [ ] 2.2 Record per-platform dependency/build inventories and finish exact corresponding-source coverage for bundled external libraries.
- [ ] 2.3 Verify clean-machine installation and supported OS/libc baselines for every advertised target.
- [ ] 2.4 Validate other advertised IntelliJ Platform IDEs, including PyCharm, WebStorm, GoLand, CLion and Rider.
- [ ] 2.5 Run physical audio, light/dark/HiDPI and repeated lifecycle checks across the supported platform matrix.
- [ ] 2.6 Benchmark representative 1080p/4K CPU and memory behavior; expand container/codec timing and animation-composition fixtures.
- [ ] 2.7 Owner uploads validated archives, listing fields and screenshots to JetBrains Marketplace under Romanovsky.

See [testing.md](../../../docs/testing.md) for checks and limitations, and [release.md](../../../docs/release.md) for publication steps.
