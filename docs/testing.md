# Development validation

## Windows x64 baseline, 2026-10-31

Target: IntelliJ IDEA 2026.1, build IU-261.22158.277, bundled JBR. Native dependencies are pinned in `native/dependencies.json`.

## Dev.13 smooth graphs and sharp main-video seek frames

The full run passed **52 JUnit/SDK checks and15 native checks**, zero failures, with the owner's trailer. Plugin Verifier reports dev.13 **Compatible** with IU-261.22158.277. Optional IDE dependency HTTP451 responses remain recorded rather than bypassed.

- PlaybackClock has deterministic tests for16ms advancement between native events, pause, speed changes, non-reversing small corrections, stale pre-seek event rejection/acknowledgement and bounded extrapolation. Native events remain authoritative; the16ms render timer is separate from the80ms control tick. Unchanged paused graphics do not repaint repeatedly. This targets about60Hz presentation; no real-device FPS benchmark is claimed.
- The live spectrum uses fractional image placement while its decoded data remains15fps with existing PTS/queue bounds. Existing centered-cursor and source-timing fixtures pass.
- A waveform paint regression checks buffered source-time image mapping as its viewport slides. The SDK regression verifies multiple small Follow advances inside the same ready bitmap, no null image or regeneration during those advances, retention during a distant seek, and manual-scroll opt-out. Neighboring data is prefetched with bounded columns/read buffers; stream/channel changes still invalidate obsolete data. Existing zoom, stereo, short8kHz and delayed-audio cases pass.
- A1280×720 red/blue clip verifies every observed main still remains at least640 pixels wide throughout a drag and the final blue source frame is correct. The208×117 hover cache is never assigned to the main canvas. Single-request extraction coalesces targets and sizes output from source/physical viewport up to3840×2160. In the synthetic run the final frame was already ready at release; this is not a cold-seek latency guarantee for arbitrary media.
- Existing Ctrl-frame, replay after EOF, source/VFS refresh, no-audio controls, actual owner-trailer analysis, animation and HTTP Range tests remain passing.

The Windows x64 archive and SHA-256 are local artifacts. Screenshots remain accurately labelled dev.11. Main-IDE subjective smoothness, physical output and other platforms were not manually revalidated. The GitHub release-readiness discussion is still pending: this fix does not initialize/push the repository or complete native corresponding-source release materials.

## Dev.12 Astra follow-up C1–C6

The full run passed **45 JUnit/SDK checks and 15 native checks**, zero failures, using a generated10-second H.264/AAC fixture for the optional mediaFixture regression. The same optional regression also passed with the owner's27-second trailer. Focused subsequent runs verify the final missing-input/non-video guards log that the optional check was not run, without failing JUnit3. These non-applicable runs are not counted as exercised media validation; JUnit3 records the methods as successful rather than runner-level skips.

- C1: the actual Open Media source-preparation helper marks explicit local intent without retaining an obsolete Path. SDK checks decode a local transport stream and extensionless copy through the media provider and confirm ordinary TypeScript remains declined.
- C2: TimelinePreview now uses only the actual storyboard/local-window pipeline. Its unreachable fallback decoder, independent cache, request buckets and worker timer were removed. The real indexed UI test checks latest-hover presentation without seeking, alongside the existing native window/cache/replacement coverage.
- C3: fixture seeks and waveform boundaries derive from actual native duration. Required finite video/audio/seek capabilities are explicit; no13.5/27-second constants remain in that test. Missing/non-applicable inputs are logged without relying on JUnit4 Assume behavior inside the JUnit3 SDK runner.
- C4: every SDK method uses standard TemporaryFolder ownership and common PlayerSettings snapshot/reset/restore. Temporary deletion waits for asynchronous native release. The full run covers physical VFS/JAR, image and audio/video cases; external mediaFixture inputs are never owned or deleted by this cleanup.
- C5: thumbnail extraction compares the generated source's SHA-256 before and after both decodes, not two post-operation sizes of the same path.
- C6: AGENTS.md was updated under the owner's explicit approval to the actual source layout, Gradle/native/SDK commands and review/publication boundaries;362 words.

The Windows x64 archive builds successfully; Plugin Verifier reports dev.12 **Compatible** with IU-261.22158.277. Strict OpenSpec validation and archive tools/JNA/icons/GPL/MIT checks pass. Screenshot assets remain accurately labelled dev.11 because this maintenance change does not alter their layouts. No commits/publication or fixes for unconfirmed runtime hypotheses were performed. Main-IDE GUI/sound and other OS/IDE targets were not rechecked.

## Dev.11 no-audio state and pointer cursors

Two targeted SDK tests passed: a generated silent MP4 verifies disabled Spectrogram/Waveform tabs with explanatory tooltips, disabled main/overflow volume and mute, enabled-tab hand versus disabled-tab default cursor, spectrum and scrollbar hand cursors. The owner's audio/video trailer regression still verifies playback, both spectra, waveform and narrow controls. Dev.11 builds successfully; Plugin Verifier reports **Compatible** with IU-261.22158.277. Native decoder implementations were unchanged and the full native suite was not repeated for this UI-only addition.

The reported trailer-compare-03-mop.mp4 was inspected read-only: one H.264 video stream,2880×1080,6.533008 seconds, no audio stream. Disabling audio-analysis actions is correct for this file; dev.11 explains why.

After persistent Computer Use app access became available, the actual sandbox IDE was operated with original generated media. Four unedited1386×993 window screenshots cover video with inline Live spectrum, Full file audio spectrum, separate-channel waveform and image viewing. UI opening, range selection, seek, channel controls and tab navigation were exercised; playback remained muted. See [screenshots and provenance](screenshots/README.md). This is sandbox visual evidence, not manual validation of the user's installed main IDEA or physical sound.

The separate read-only Astra audit of dev.10 is recorded in `.codex/reviews/2026-10-31-astra-smells.md`. C1–C6 await owner review and were not included in these user-requested UI changes.

## Dev.10 approved review fixes R1–R7

The final full run passed **43 JUnit/SDK checks and15 native checks** with the owner's trailer. A subsequent focused SDK check also passed for paused/playing session restoration across VFS path refresh: **44 distinct JUnit/SDK checks and15 native checks** across these runs,59 total. Plugin Verifier reports dev.10 **Compatible** with IU-261.22158.277. Optional IDE dependency downloads still return HTTP451; those responses were not bypassed.

- R1/R6: an actual temporary JAR resource and physical local files verify automatic-provider rejection of archive entries/directories, acceptance of valid local media, legacy SOURCE handling, active-image rename, parent-directory rename and close/reopen. A separate SDK fixture publishes VFS path changes between two real audio sources and verifies restored paused time, then restored playing time/state with actual VLC. This separates session restoration from Windows file-sharing restrictions; active-audio physical rename was not claimed as manually verified.
- R2: an8192×4096 PNG verifies a bounded4096-pixel Fit preview, original one-source-pixel detail at100%, region movement after pan and returning from Fit to100%. Region output is capped at16,777,216 pixels; native codec internal source-frame memory is not asserted constant. Existing video fit/scrub and image lifecycle checks continue passing.
- R3: offline PowerShell fixture archives exercise interrupted extraction, A→B upgrade, removal of obsolete modules, completed same-hash reuse and preservation of the prior manifest/runtime after a broken replacement. Fixtures passed after the final script change. The real pinned Windows native runtime was prepared successfully with the revised script using existing verified downloads.
- R4/R5: waveform uses a1024-frame PCM read buffer and at most1600 per-channel columns. Unit checks cover short8kHz ranges, a two-hour declared sample span with bounded accumulators, silence and malformed PCM. Native fixtures cover120 seconds of actual input,8kHz/100ms zoom, stereo isolation and existing delayed-audio timing. This proves bounded plugin retention by construction/tests; it is not an actual two-hour RSS benchmark.
- ToolProcess reader failures and cancellation both terminate the owned live FFmpeg process. Existing thumbnail, HTTP Range, animation, spectrogram and JNA checks pass against the same runtime.
- R7: SDK track menu actions verify Audio Disable clears both spectrum views for Live and Full file, then valid reselection regenerates analysis. Existing mute/source-analysis semantics are preserved.

No commits or publication were performed. The two unconfirmed review candidates remain excluded. Manual main-IDE GUI/speaker checks and the wider OS/IDE matrix remain release gates.

## Dev.9 click-to-toggle video

The targeted SDK scrub/frame-step regression passed with primary mouse press/release/click events dispatched to the actual VLC callback surface and the paused still canvas. It verifies start, pause, resume from the still and return to the native surface, then pause again. Secondary clicks do not toggle playback; video surfaces use the hand cursor. Both surfaces call the existing Play/Pause action, preserving its EOF restart and playback coordination behavior. Strict OpenSpec validation passes. The Windows x64 archive builds successfully and Plugin Verifier reports dev.9 **Compatible** with IU-261.22158.277.

## Dev.8 local preview refinement

The full run passed **38 JUnit/SDK checks and 13 native integration checks**, with the owner's trailer supplied through `-PmediaFixture`. Plugin Verifier reports dev.8 **Compatible** with IU-261.22158.277; strict OpenSpec validation passes. The verifier still records HTTP 451 for optional IDE dependencies.

- On the owner's 27-second trailer, an independent cold request at 13.5 seconds produced its first approximate preview in **353 ms** and refined neighbors across the four-second window in **684 ms**. These decoder timings exclude the 140 ms hover dwell and are measurements on this machine, not latency guarantees. Cached images remain available during refinement.
- The local decoder has a separate queue from the global storyboard, first attempts a quick keyframe, then samples the bounded neighborhood every 0.1 seconds. Nearby movement reuses the active request and images; a distant request cancels obsolete work. Each viewer retains at most 128 local frames. Requests stay bounded near EOF; disposal cancels owned processes and network requests have timeouts.
- A deterministic red/blue clip verifies actual images before and after the scene cut. SDK events verify that hovering the five-frame strip does not seek, clicking selects the displayed timestamp, the anchor stays fixed, and the slider-to-strip exit grace is cancelled on entry. The component fits within 480 logical pixels; its offscreen capture is `build/reports/ui/timeline-preview.png`.
- Native checks verify both sides of a requested time, warm image identity during repeated nearby requests, replacement of distant work, and refined neighbors over a local HTTP Range endpoint without an extension. Existing scrub-fit, source-frame stepping, EOF replay and analysis checks remain passing.

The strip is a 0.1-second sample grid, not every source frame. `Ctrl+Left` / `Ctrl+Right` retain actual source-frame inspection. The installed main IDE window and physical speaker output remain manually unverified; automated checks use the real SDK and bundled native tools.

## Dev.7 preview performance and transport UX

The change passed **37 JUnit/SDK checks and 12 native integration checks** across the full run and targeted repeats. Plugin Verifier reports dev.7 **Compatible** with IU-261.22158.277.

- A single FFmpeg pass prepared 54 thumbnails for the owner's 27-second desktop trailer in **1.41 seconds** on this machine. This is a measured cold preparation, not a guarantee for arbitrary media or hardware.
- Local strips use up to 160 aspect-preserving 208×117 thumbnails. Ready images serve hover directly from memory; local PNG strips are reused across viewer instances and invalidated by path, size, mtime, stream, clock origin and duration. Disk cleanup targets 256 MiB / 4096 images, excluding active producers. Concurrent views serialize preparation for the same cache key.
- A warm-cache regression supplies a nonexistent FFmpeg executable to the second viewer while the first prepares the strip. It still loads all cached images; 10,000 warmed lookups complete in under the one-second test bound.
- HTTP previews request bounded groups of eight one-second samples rather than automatically reading the entire URL. The local HTTP Range fixture verifies distant groups and session closure. URL query strings are not used as persisted cache metadata.
- Scrubbing presents a prepared representative image immediately where available and coalesces exact still extraction until the pointer settles or is released. The video-fit canvas allows upscaling and uses black letterboxing, so a 1280×720 or smaller decoded still does not shrink the main video footprint. A pixel test verifies a low-resolution stop frame fills a larger viewport; ordinary image inspection retains its original 100% limit.
- SDK checks cover compact transport/options at 480 pixels, volume preference updates, cancel/resume of live analysis, waveform following and manual-pan opt-out. Go-to-time parsing checks valid formats, invalid fields and source bounds. Focus remains in the media context and existing text/control navigation is preserved.

Research found two established approaches: prepared thumbnail metadata/strips in [Video.js](https://videojs.org/docs/framework/html/reference/components/thumbnail), and a long-lived silent thumbnail decoder with coalesced fast/exact seeking in [thumbfast](https://github.com/po5/thumbfast/blob/master/thumbfast.lua). This implementation uses prepared strips with the already bundled FFmpeg; it does not add mpv or another runtime.

Cold preparation still takes work, and network ranges can incur network/decode latency. The actual IDE window and physical speaker output remain manually unverified because Computer Use app access was rejected.

## Earlier validation

Dev.6 implementation passed the full **35 JUnit/SDK checks and 11 native integration checks**. Coverage includes hover without seeking, cancellation of outdated thumbnail requests, bounded in-memory thumbnail reuse, actual decoded frames at different source positions, bounded video-frame timestamp inspection, pixel checks of the main still frame before drag release, and adjacent-frame keyboard navigation on a deterministic 4 FPS fixture. The owner's trailer remains an external fixture.

The dev.6 Windows x64 archive built successfully. Plugin Verifier reports **Compatible** against IU-261.22158.277. Archive checks confirm the new preview/frame-inspection classes, complete native tools, plugin icons and license notices, with no duplicate JNA jars.

Hover thumbnails use quarter-second samples, up to 320×180, with a 32-entry per-viewer memory cache. The main paused/scrubbed frame uses a separate cancellable FFmpeg request capped at 1280×720; VLC remains the only audible player. Native paused seeking alone did not reliably refresh its callback surface in testing, so the correct decoded still remains visible until playback produces a time update. Preview failures leave playback available.

The ignored-file fix now traverses the current viewer's displayed notification area, including nested Swing wrappers, rather than relying on `EditorComposite` lookup. A fixture with the SDK editor's CENTER/NORTH layout verifies actual timer-driven attachment of the close and opt-out actions. It checks unrelated notices and global ignore settings remain unchanged. The opt-out preference survives platform XML serialization. `DumbAware` marks the viewer as independent of indexing; enabled buttons/dropdowns use hand cursors.

Version `0.1.0-dev.5`: all **8 targeted SDK editor checks** pass, including the owner's trailer fixture. A new assertion first reproduced the EOF thumb falling short of its maximum. The corrected presentation uses the full source duration in the terminal state, consistently updating the thumb, time counter and analysis cursors. The same test verifies replay from zero. Video/audio step buttons show `−10` / `+10` with second-specific tooltips; animation labels remain frame-specific. Both step buttons pass the 480-pixel hit-target check. Plugin Verifier reports dev.5 **Compatible** with IU-261.22158.277. Other native decoding checks were not repeated for this presentation-only change.

Version `0.1.0-dev.4`: **31 JUnit/SDK checks and 9 native integration checks** passed, zero failures. The SDK run supplied `-PmediaFixture=<local trailer path>`; without this property, the owner-supplied fixture test is skipped. A targeted regression was repeated after correcting initial visibility of the restored inline spectrum selector. The platform-specific ZIP includes VLC, FFmpeg/ffprobe and MediaInfo. No system VLC or media-tool discovery is required.

Tests cover source parsing/redaction, metadata formatting, cache invalidation, variable animation timing, platform file/action registration, editor loading/disposal, real VLC callback frames and transport, Unicode paths, multiple audio tracks, HTTP Range input without a filename extension, live/full-file spectrograms and GIF/APNG/WebP decoding. The delayed-audio MP4 fixture checks actual spectral energy against the VLC movie timeline, including leading silence.

Additional checks exercise persistent settings through platform XML serialization, clamping invalid saved preferences, waveform coordinate/zoom/pan behavior and cache separation. A real SDK audio editor opens the preferred Waveform tab, renders its waveform and zooms it. Stereo fixtures verify channel separation and viewport regeneration; the delayed-audio MP4 also checks leading silence in the waveform.

Plugin Verifier reports are under `build/reports/pluginVerifier/`. Some optional bundled IDE dependencies could not be fetched because the vendor returned HTTP 451. That response was recorded rather than bypassed. The previously flagged browse-dialog API has been replaced with its current overload.

Plugin Verifier reports `0.1.0-dev.4` **Compatible** with IDEA IU-261.22158.277, with no deprecated API usages reported.

## Transport and analysis regressions

- The old 80 ms UI timer overwrote the seek thumb during an active drag. A regression first reproduced that failure, then verified holding a new position across timer ticks, releasing it and reaching the target on VLC's paused source clock.
- Actual Swing mouse press/drag/release events check direct track-click positioning and one seek commit on release. The reported trailer is also used to verify pointer seeking, first-view control hit targets at 480 pixels, volume-slider interaction/preferences and a native playback-speed change before switching tabs.
- The track/subtitle popup is prepared before transport is enabled. Refresh tracks explicitly reloads dynamic track information.
- A generated clip reaches the visible Ended state, then Play restarts it from the beginning while source analysis remains attached. EOF restart reopens the source, restores selected track IDs and clears old live-analysis presentation.
- A timed-tone fixture verifies actual decoded audio ahead of the centered cursor and correct history/silence near EOF. An offscreen paint test checks the central cursor and empty, unavailable future area.
- The current English `.ignore` notice receives close and opt-out actions only inside the media viewer. Tests cover wrapped notification discovery, dismissal, repeated provider updates and preservation of unrelated notices.

The installed IDEA window could not be accessed through Computer Use because the tool rejected app access. SDK tests cover initial Swing event handling and native commands, not a manual acceptance run in that window. The SDK audio backend returned `-1` for its volume query, so automated checks verify slider events and saved volume rather than claiming a physical gain measurement.

## Installed-IDE JNA regression

The owner reported `Unable to locate JNA native support library` in dev.2. Its ZIP bundled upstream JNA while the IDE already loaded its own native dispatcher. A separate JVM with isolated IDE/plugin classloaders and the actual `jna.nosys=true` / `jna.noclasspath=true` launcher flags reproduced the exact error with dev.2.

The plugin now excludes transitive JNA jars and shares the IDE-provided classes and dispatcher. The same isolated-loader check succeeds, and an SDK regression checks both shared classes and absence of duplicate JNA jars. Both test tasks use the SDK's native boot path and launcher restrictions; no IDE configuration or global JVM properties outside the test processes are changed.

The reported **trailer-en-desktop.mp4** (27 seconds, H.264/AAC, 48 kHz stereo) was opened through the real SDK media editor. Playback initialization, live spectrogram, full-file overview and waveform all became ready. The existing file is read as an external fixture and is not copied into the repository or modified.

## Owner smoke test

Install the **platform-specific** ZIP from `build/distributions/`, restart, then:

1. Open a local video, start playback, seek, change speed, mute and select tracks. Confirm audible sound and actual rendering.
2. Enable both spectrogram modes; mute must not alter the source analysis. Open another media file and check that starting it pauses the previous audible session.
3. Return to code while audio plays, then close the media tab and confirm playback stops.
4. Open GIF, animated WebP and APNG; pause, step frames, seek and switch repeat mode. Inspect transparent images and zoom/pan.
5. Try a direct HTTP(S) media URL and a path outside the project. Inspect MediaInfo and copy a report. Check a damaged or missing file and retry.
6. Check light/dark themes, keyboard focus, narrow split editors, HiDPI and repeated open/close cycles.
7. Select Waveform, zoom around the playhead, scroll and drag to seek. Toggle Separate channels on stereo input. Change playback/view preferences, reopen the editor and restart the IDE to inspect persistence.

## Remaining limits

Native automated playback tests use dummy audio output; physical speakers and desktop rendering have not been accepted visually. Computer Use access to the sandbox Java window was rejected by the tool despite owner authorization; SDK component tests do not replace that visual check.

Only Windows x64 has a prepared/tested native bundle. Other JetBrains products, Windows ARM64, both macOS and Linux architectures, clean-machine installation and 1080p/4K performance remain unchecked. Nonzero-start timing is calibrated on MP4; equivalent container-clock checks for MKV/TS remain open.

Large still images are currently decoded to at most 4096 pixels per dimension, and animation frames to 1280. Consequently 100% zoom does not yet guarantee original pixels for larger inputs. The browser UI studies have separate validation and are not evidence of native-plugin layout acceptance.
