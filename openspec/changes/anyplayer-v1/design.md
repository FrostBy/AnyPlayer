## Context

The repository has Git metadata but no commits, build files, or application code. The owner approved a free public GitHub project and free Marketplace publication, with GPLv3 as the project license. The accepted scope includes video, audio, images, MediaInfo, and both spectrogram modes. Platform coverage follows IDEA's official supported desktop combinations, rather than an independently reduced list.

The planning deliverables are [visual direction and references](../../../docs/design/README.md), an [interactive design preview](../../../docs/design/index.html), normative capability specifications, and an implementation checklist. The browser preview is an annotated design artifact, not a web frontend for the plugin.

## Goals / Non-Goals

**Goals:** a self-contained local-media viewer, familiar IDEA controls, stable playback, source-track analysis, correct native packaging, and testable lifecycle behavior.

**Non-Goals:** media editing/transcoding exports, microphone/system-output capture, website video extraction/account-login flows, a separate desktop application, a JavaFX runtime, or a general-purpose interchangeable-backend framework. These are not required by the agreed viewer scope.

## Decisions

### 1. One Kotlin/Swing plugin with a custom file editor

Use `FileEditorProvider` and platform components, theme colors, action APIs, and disposal. Organize a single module into `editor`, `playback`, `images`, `analysis`, `information`, and native-loading packages. Do not add a web frontend, dependency injection framework, or internal event bus.

Keep transport in a stable bottom region. Preview, Spectrogram, and MediaInfo are views of the same selected file. Video can expose an inline collapsible analysis drawer; audio uses the analysis area as its main content; images replace transport with zoom controls. Narrow editors collapse secondary labels/actions without hiding playback or focus indicators. See the visual contract in `docs/design/README.md`.

The alternative, a dedicated tool window, separates media from its file and consumes the same space as Project/Terminal. A file editor also inherits IDEA split and detached-editor behavior.

### 2. libVLC 3 and vlcj 4 own playback

Use vlcj's `CallbackMediaPlayerComponent` / callback video surface as the common Swing path, including macOS. Select a compatible stable vlcj 4 and libVLC 3 pair in the first prototype, then lock exact dependency versions and native build manifests. JavaFX adds another UI runtime without the desired codec breadth; JCEF codec availability is not a dependable universal media contract.

Inspect vlcj's transitive JNA dependencies against the actual target IDE classloader. The prototype must establish one compatible native bridge configuration before packaging is frozen. Do not silently rely on an unspecified IDE-bundled JNA version or add multiple competing native bridges.

Each project allows one audible player at a time. Opening a file does not autoplay it. Starting another player pauses the previous one. Switching to a code tab does not interrupt explicitly started background audio. Closing the active media editor stops and releases its player. File position and selected tracks remain associated with that editor while it exists.

Never call back into libVLC on a native event callback thread. Use vlcj's supported dispatch mechanism for player commands; post Swing changes to the EDT. Player and analysis ownership follows editor/service `Disposable` lifetimes.

### 3. FFmpeg analyzes source audio separately

`libvlc_audio_set_callbacks` replaces VLC's audio output; it is not a passive PCM tap. VLC's internal visualization modules are not a public Java analysis API. Therefore VLC remains the only audible output and FFmpeg decodes only the selected source audio stream for analysis. The video is not decoded again for analysis.

- **File view:** `showspectrumpic` generates a bounded-resolution overview of the selected track. Its time axis supports cursor display and seeking.
- **Live view:** `showspectrum` emits bounded-size raw RGB frames, initially targeting 15 updates/second. Consume fixed-size frames asynchronously and associate them with FFmpeg PTS, for example from `showinfo`, rather than byte-arrival time.
- VLC source position is the master clock. Normalize stream/container timestamps to the same source timeline. Choose the appropriate available analysis frame for that position; do not use `-re` as an independent playback clock.
- Use a bounded look-ahead queue. If playback outruns analysis, show analysis catching up instead of an incorrectly positioned frame.
- Pause freezes presentation. Seek, track change, source replacement, and cancellation invalidate older generations. Restart analysis near the requested position with enough preroll for the FFT window; discard preroll output before presenting the new range.
- Speed changes select frames by source time. Analysis represents the original source track, before volume, mute, equalization, or time-stretch effects, not physical speaker output.
- Full-file analysis can run independently of playback. Avoid concurrent redundant jobs for the same editor; cache completed results under the IDE cache location with source identity, modification state, stream, and analysis parameters in the key.

The prototype must verify mapping between VLC track IDs and FFmpeg stream indices; do not assume they are the same number. Synchronization fixtures include a nonzero source start timestamp and a delayed audio stream. The initial acceptance target is no more than 200 ms visible source-time discrepancy without accumulating drift.

### 4. Still images and seekable animations

Use Java image facilities for still PNG/JPEG/BMP/GIF and the packaged FFmpeg fallback for additional still-image formats, including WebP. Respect orientation and transparency. Decode large images off the EDT, bound working buffers, and discard results for a closed/replaced editor. Fit and 100% are explicit actions; pan is available when the image exceeds the viewport.

Animated GIF, WebP, and APNG use a presentation timeline with play/pause, time seeking, previous/next frame while paused, speed, and repeat controls. Do not use an uncontrollable GIF image widget. Detect animation from the content rather than assuming every PNG/WebP/GIF is animated. Decode composited RGBA frames through the packaged FFmpeg, retain their timestamps/durations, and use a bounded frame cache. Presentation uses the real per-frame delays rather than a constant assumed FPS. After seeking, present the correct fully composited frame; decode necessary preceding frames asynchronously when the format needs them, showing seeking progress instead of stale content. Repeating follows file loop semantics by default and can be disabled/enabled by the user. Silent animation does not take over the project's audible playback owner.

Native build selection now requires actual GIF/APNG/animated-WebP demuxing and decoding probes. FFmpeg's changelog introduces Animated WebP decoding/demuxing in 9.0; older generic WebP support is insufficient. Pin a build with the required features for every supported target and prove alpha/disposal/variable-delay fixtures before claiming this capability. This reuses the selected native decoder rather than implementing image codecs or audio output in Kotlin.

### 5. Real MediaInfo reports

Run the packaged `mediainfo --Output=JSON` for structured General, Video, Audio, and Text track information; use MediaInfo's text output for a complete report. Keep report generation independent of whether playback succeeds. Optional or missing fields are omitted or clearly unavailable. Copy operations preserve the complete value/report. Do not label ffprobe output as MediaInfo.

### 6. Native dependencies travel with the plugin

Start compatibility at build 261 (IDEA 2026.1) and verify each supported release line before declaring it compatible. For the currently documented matrix, prepare Windows, macOS, and Linux on both x86_64 and arm64. Choose binaries by the IDE process architecture, including an x64 IDE running under OS translation.

Use the standard Gradle `nativeVariants` distribution mechanism. It enables all six targets; an empty file collection does not disable a target. Every advertised target must therefore contain its complete native runtime. Ship required VLC modules/shared dependencies and the FFmpeg and MediaInfo tools; preserve executable permissions and validate macOS native loading and the supported Linux libc baseline.

Keep versions, download origins, hashes, build configuration, and license/source notices in a reproducible dependency manifest. Resolve native artifacts during development/CI builds, not through a custom runtime downloader. Do not require end users to modify their JBR or system `PATH`.

### 7. Local execution and resource boundaries

Accept local filesystem media and explicitly entered HTTP(S) media URLs. Open Media accepts a URL or local path with a file picker, using a normal file editor even outside the project. URL resources use read-only virtual tabs and do not require project files. Validate the scheme/host/path, preserve signed queries for playback, and remove query secrets from diagnostics. Build file URIs safely for VLC and launch external tools with argument arrays, not a shell command string. Consume stdout and stderr without blocking, bound retained diagnostics and queues, handle nonzero exits, and support cancellation. Only terminate processes owned by the editor/plugin. Cache analysis under the IDE system directory and leave source media unchanged.

Streaming support follows actual source capabilities: a source without a known finite duration shows Live, non-seekable sources disable seeking and full-file overview, and absent tracks disable their analysis controls. A failure in network metadata or analysis must not be misrepresented as a different file or cached result. Direct media and supported playlists are in scope; extracting playable URLs from arbitrary website pages is not.

### 8. IntelliJ Platform product coverage

Use only the common platform module and public UI/file-editor APIs, without a dependency on the IDEA Java plugin. Validate advertised release lines in IDEA, PyCharm, WebStorm, GoLand, CLion, Rider and other targeted IntelliJ Platform IDEs. Native OS/architecture variants are shared where those products officially support them. Product compatibility and native portability are separate checks; successfully compiling against IDEA alone does not establish either.

The plugin remains responsive when a codec is unsupported, a file disappears, a dependency cannot load, or analysis fails. Playback and analysis errors are separate: analysis failure does not stop otherwise working audio/video.

## Review remediation: R1–R7

The owner accepted all seven confirmed findings from the five-agent review for planning. This section is the implementation plan, not permission to edit code. The report is [2026-10-31-full-plugin.md](../../../.codex/reviews/2026-10-31-full-plugin.md). The two unconfirmed animation/playback-owner hypotheses are excluded. No new dependencies or unrelated refactoring are needed.

### R1. Only claim automatic sources the editor can open

Keep explicit SOURCE-backed virtual tabs for remote media. Extension-based acceptance additionally requires a regular local VirtualFile, excluding directories and archive/non-local files. This prevents constructor-time local-path failures without implementing archive extraction or replacing the IDE's archive image viewer. Touch MediaEditorProvider and its SDK fixtures; verify local images/media and explicit remote tabs still work.

### R2. Preserve source pixels for image inspection

Retain the existing bounded preview for Fit. Keep original image geometry separately from the preview bitmap. At 100% and during zoom/pan, decode the visible source region off the EDT, using the existing FFmpeg fallback where ImageIO cannot supply it. Place the region using source coordinates, so a small preview is never represented as original pixels. Bound plugin-retained bitmaps and decoder output to the visible region/working budget; coalesce requests and discard obsolete results on pan, source replacement or disposal. Some native codecs still decode a complete source frame internally; this is not a promise of constant native memory for arbitrary image dimensions. If inspection cannot decode a region, report that failure instead of silently returning to a scaled preview labelled 100%.

This changes MediaEditor image loading/controls and ImageCanvas geometry/pan callbacks, plus focused image/SDK tests. A full original bitmap for every image was rejected because it replaces the current detail loss with unbounded IDE memory use. Verify an 8192×4096 fixture with source-pixel detail, region selection after panning, Fit/100% switching, transparency and unchanged video/still fit behavior.

### R3. Match cached native content to its verified archive

Key cached archives and extracted directories by dependency SHA-256. Mark extraction reusable only after successful completion. Build the complete runtime in a fresh staging directory; run existing required-file/decoder checks there before replacing the previous bundle and copying the matching manifest. Do not merge VLC modules across versions. On preparation failure keep the previous complete bundle usable; replace/clean only resolved paths inside this repository's intended native/downloads and target native directories.

Touch scripts/prepare-native.ps1 and focused offline PowerShell fixture checks, retaining the manifest schema and standard Gradle nativeVariants. Verify A→B dependency replacement with an existing A extraction, interrupted extraction, removal of obsolete modules, repeated same-hash reuse and preservation of the old complete bundle on failure. Use temporary fixture archives rather than downloading dependencies in regression tests.

### R4 and R5. Stream a bounded waveform envelope

Replace showwavespic accumulation with incremental PCM consumption from the existing owned FFmpeg process. Preserve the current source-time normalization, leading silence, selected stream and pre-volume semantics. Aggregate absolute sample values/counts into bounded per-channel columns, then render the existing BufferedImage presentation and square-root amplitude scale. Retain at most a fixed read buffer and the column accumulators, independent of duration; do not retain all PCM or resample away peaks merely to reduce memory.

Use at most 1600 columns, reducing the count when a selected interval contains fewer samples. Empty/tail intervals must produce defined silence or an actionable error, not a division by zero. Keep cancellation, timeouts, nonzero-exit handling and process cleanup through ToolProcess. Version the waveform cache key so old images cannot hide the corrected generation. Touch WaveformAnalysis, the necessary ToolProcess reading boundary and MediaEditor cache variant; keep WaveformView's public image contract.

Verify bounded accumulator storage on long input, channel separation, 8 kHz/100 ms windows, source offsets and delayed audio, cancellation/error cleanup and playback remaining available. Existing stereo and leading-silence native fixtures must continue passing. No new generic process framework is part of this change.

### R6. Resolve local media through current VFS identity

Do not persist a local Path in SOURCE on a physical VirtualFile; reserve explicit SOURCE for virtual/remote tabs. Resolve local media from the current VirtualFile when creating/reloading its session, including tabs carrying legacy SOURCE user data. Subscribe to relevant name/move changes through the platform VFS lifecycle, including parent-directory moves. On a changed path invalidate old generations/processes/caches through existing session cleanup and reinitialize using the current path. Retain the bounded source position and previous paused/playing state only after the matching replacement session is ready; closed editors do not restart work.

Touch OpenMediaAction, MediaEditor source/session ownership and focused SDK VFS tests. Verify explicit-open→close→rename/move→tree reopen, parent-directory move, active-session source refresh, stale-result rejection, disposal and unchanged explicit remote tabs. A getter alone is insufficient because old decoder handles/jobs would remain attached to the prior source.

### R7. Clear analysis when no track is selected

After invalidating workers, clear both spectrum views and completed overview state before returning for a missing/disabled/unmapped selected stream. Show a no-selected-audio message and allow a valid track to regenerate analysis. Reuse existing generation guards; preserve the different mute behavior, which intentionally leaves source analysis active. Touch MediaEditor and the SDK track-selection regression. Check live and full-file modes, pending old results and switching back to a valid track.

### Validation and delivery

Run the focused regressions for each finding, then the established JUnit/SDK and native suites once on the final source. Run the offline native-preparation fixture check, strict OpenSpec validation, Windows x64 packaging and Plugin Verifier for the current Platform261 target. Inspect the package for the expected tools, notices and absence of duplicate JNA; provide a local archive and checksum. Other OS/IDE verification, publication, commits and the two unconfirmed review candidates remain outside this remediation plan.

## Astra follow-up C1–C6

The owner explicitly requested implementation after reviewing the separate Astra report. Fix only its six confirmed findings/maintenance issues; unconfirmed animation/ownership hypotheses remain excluded.

- C1: retain a Boolean explicit-open marker on local VirtualFile objects while SOURCE remains reserved for virtual media. Resolve current physical paths through VFS as before. The production media-file preparation helper and SDK regression exercise local transport-stream/extensionless opening without intercepting ordinary TypeScript.
- C2: remove TimelinePreview's unreachable fallback decoder, LRU, bucket/request generation and worker timer. Reuse only PreviewStoryboard/LocalPreviewWindow; exercise latest-pointer display and selection in the actual indexed UI test. Existing native coverage checks local-window replacement/caching.
- C3: the optional mediaFixture regression requires finite seekable video with audio, derives expected seek/time-range bounds from native duration and explicitly skips unsupported fixture capabilities. Preserve generated coverage for other media; do not require the owner's27-second clip.
- C4: SDK tests use a manually managed standard JUnit TemporaryFolder per test and a class-level PlayerSettings snapshot/reset/restore. Wait for owned native release during temp cleanup; do not delete external mediaFixture inputs.
- C5: compare the generated native fixture's SHA-256 before and after thumbnail extraction, replacing the same-path post-operation size comparison.
- C6: the owner explicitly approved replacing the stale scaffold instructions in AGENTS.md with concise actual Kotlin/Gradle/source/test/release conventions.

Validate SDK/native suites against a generated shorter video, repeat the optional fixture regression on the owner's video and verify a non-video fixture is skipped as documented. Build/verify the next local Windows archive and retain existing screenshot provenance as dev.11; this maintenance change does not change its visual layouts. No commits/publication.

## Smooth analysis presentation and quality-preserving seeking

The owner approved correcting visibly stepped graphs/Follow and the thumbnail-to-HD main-video transition before release.

- PlaybackClock interpolates native source-time anchors with a monotonic clock at the selected playback rate. Small corrections adjust forward progression; discontinuities resynchronize. Pause/buffering freeze, seeks reject stale notifications until acknowledged or timed out, and extrapolation without updates is bounded. This is a presentation clock, not a separate audible playback engine.
- Keep the existing80ms control/notification tick. A separate16ms presentation timer updates cursors, slider and fractional bitmap placement; unchanged paused views do not repaint unnecessarily. FFT data remains15fps with its original decoded PTS and bounded queues; smooth scrolling does not require doubling analysis work.
- WaveformView separates decoded image time bounds from visible time bounds. Preload one viewport span on either side, using at least one second of padding per side and a fixed33,600-column ceiling, preserving approximately1600 columns per visible range. Reuse existing cache/process ownership; adjust cache version. Keep valid bitmaps during refill and swap uncovered distant ranges only when data is ready. Failures retain usable display, expose diagnostics and stop automatic retries until user interaction.
- Main-video still extraction no longer consumes208×117 preview-cache images or waits for180ms of pointer silence. One in-flight decode coalesces changing targets; only a matching target replaces the displayed frame. Size output for the source and physical viewport, capped at3840×2160 with a corresponding bounded PNG byte budget. The old valid frame remains visible during decode; native playing frames take over after accepted source-time updates.

Validate deterministic source-clock behavior, fractional waveform coordinates, uninterrupted buffered Follow, last-target HD scrubbing, existing pause/frame-step/EOF behavior and the complete SDK/native suite. No new media dependencies or changes to the unconfirmed animation/ownership audit hypotheses.

## Risks / Trade-offs

- **Native decoder inside the IDE process can crash the JVM** → validate lifecycle/callback discipline in the first prototype; native integration failures block release. A helper-process player is a separate decision only if the selected path demonstrably fails.
- **Callback rendering copies frames and can cost CPU** → measure representative 1080p/4K fixtures with HiDPI and analysis enabled before promising performance; avoid repainting every native callback on the EDT.
- **Live analysis repeats audio decoding** → cap graph dimensions, update rate, queue length, and background work; stop it when not requested.
- **Native binaries differ in available codecs and dependent libraries** → test actual packaged archives on the complete supported matrix, not just artifact filenames or Plugin Verifier.
- **New IDEA releases can change native bridge visibility** → verify JNA/module dependencies for each declared release; do not claim future compatibility without checks.

## Migration Plan

There is no existing implementation to migrate. Deliver in order: approved design contract, playback/live-analysis technical prototype, complete native packaging, media UI, analysis/metadata completion, and verified distribution packages. Keep the initial release explicitly bounded to verified IDE releases and platforms. A failed release can be withdrawn/replaced without modifying the user's source media.

## Open Questions

Product scope, license direction, both analysis modes, and platform policy are settled. Implementation gates still need measured answers: compatible pinned native/JNA builds, callback rendering cost, PTS/track mapping accuracy, and actual native execution across the IDEA matrix. These are tasks below, not already validated claims.

## Primary Sources

- [JetBrains UI guidelines](https://plugins.jetbrains.com/docs/intellij/ui-guidelines-welcome.html)
- [IDEA supported installations](https://www.jetbrains.com/help/idea/installation-guide.html)
- [Native variants, since build 261](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#native-variants)
- [Disposable ownership](https://plugins.jetbrains.com/docs/intellij/disposers.html)
- [vlcj direct rendering](https://capricasoftware.co.uk/tutorials/vlcj/4/direct-rendering)
- [vlcj callback threading](https://capricasoftware.co.uk/tutorials/vlcj/4/thread-model)
- [LibVLC 3 audio callbacks](https://videolan.videolan.me/vlc-3.0/group__libvlc__media__player.html)
- [FFmpeg filters](https://ffmpeg.org/ffmpeg-filters.html#showspectrum)
- [FFmpeg changelog: animated WebP in 9.0](https://github.com/FFmpeg/FFmpeg/blob/master/Changelog)
- [MediaInfo](https://mediaarea.net/en/MediaInfo)
- [Official VLC Windows ARM64 artifacts](https://ftp.videolan.org/vlc/last/winarm64/)
