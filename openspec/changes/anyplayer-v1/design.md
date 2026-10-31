## Architecture

Any Player is one Kotlin/Swing IntelliJ Platform plugin. Its `FileEditorProvider` opens media in ordinary editor tabs and inherits IDE splits, themes and lifecycle. The plugin depends on the common platform module, without an IDEA Java-plugin dependency.

`editor` owns file opening and Swing controls; `playback` owns VLC coordination, source-time presentation and settings; `images` handles still/animated images; `analysis` renders spectra/waveforms; `media` contains source parsing, probing, previews, native loading and owned processes.

## Playback and ownership

VLC 3.0.24 and vlcj 4.12.1 provide callback-rendered video/audio. The IDE supplies JNA; bundling another copy causes native bridge conflicts. Native callbacks dispatch player commands through supported vlcj mechanisms and UI changes to the EDT.

Each project has one audible playback owner. Opening media does not autoplay; starting another player pauses the prior owner. Returning to code preserves explicitly started audio. Closing an editor releases its player and owned analysis jobs.

`PlaybackClock` interpolates native time with a monotonic clock, freezes during pause/buffering and rejects stale seek notifications. A 16 ms presentation timer is separate from the 80 ms control tick. Native source time remains authoritative.

## Audio analysis

FFmpeg decodes the selected source audio separately from VLC output. This preserves audible playback while showing source data before mute, volume and playback effects. VLC track IDs are mapped to actual source streams rather than assumed equal to FFmpeg indices.

Full-file spectra use bounded `showspectrumpic` output. Live spectra use 15 fps `showspectrum` frames with decoded timestamps and bounded look-ahead. Seeks, source/track changes and disposal invalidate obsolete generations. Container starts and delayed audio normalize to the same source timeline.

Waveforms stream PCM into bounded per-channel envelope columns. Decoded image bounds remain separate from viewport bounds, allowing Follow to scroll through prefetched neighbors without replacing a valid graph during refill. Manual scrolling disables Follow.

## Images, animations and seeking

Still images use bounded Fit previews and asynchronous visible-region decoding for original-pixel inspection. Native codecs may internally decode complete frames. GIF/APNG/WebP animation uses composited RGBA frames, actual variable delays, bounded storage, seeking, frame stepping and repeat controls.

Timeline hover combines a cached coarse storyboard with an independently decoded four-second neighborhood sampled every 0.1 seconds. A stable five-frame strip supports precise selection. Actual source-frame stepping is separate from preview sampling.

Main-video scrubbing retains the last display-quality frame while one in-flight extraction follows the latest target, capped at 3840×2160. Hover thumbnails never replace the main canvas. Release restores the prior playing/paused state; Play after EOF restarts from zero.

## Sources and metadata

Automatic opening accepts regular local media files. Explicit local opening uses a path-free intent marker, allowing transport streams and extensionless files without intercepting TypeScript. Current VFS identity supplies physical paths; rename/move invalidates old sessions and restores bounded position/state after reopening.

Direct HTTP(S) media uses read-only virtual tabs. Seeking and full-file analysis follow actual source capabilities. Signed URL queries remain available for playback and are redacted from diagnostics. Website extraction/login flows are outside scope.

The packaged MediaInfo CLI supplies structured JSON properties and full text reports independently of playback. FFprobe supplies decoder/stream/timing information and is not presented as MediaInfo.

## Native distribution and resource boundaries

Compatibility starts at IntelliJ Platform 261. Gradle `nativeVariants` produces Windows/macOS/Linux packages for x86_64 and ARM64. Selection follows the IDE process architecture. Each installable ZIP includes VLC modules/dependencies, FFmpeg/ffprobe and MediaInfo, with no runtime download or separate system installation.

Preparation verifies pinned downloads, stages replacements and preserves the prior bundle on failure. macOS libraries are relocated and ad-hoc signed; Linux dependencies retain relative loader paths and the glibc 2.28 baseline. Matching native runners test extracted ZIPs before publication.

Tools receive argument arrays without shell interpolation. Output/diagnostics, caches and buffers are bounded; cancellation terminates only owned processes. Source media stays unchanged. Analysis failure leaves working playback available, and disabled tracks clear stale analysis.

## Validation and remaining work

Focused SDK tests cover editor behavior; native tests exercise real packaged tools with generated fixtures. Plugin Verifier establishes API compatibility separately from native portability. See [current validation](../../../docs/testing.md), [remaining tasks](tasks.md) and [release procedure](../../../docs/release.md).

Native codecs execute in the IDE process and can crash its JVM. Cross-platform native loading, wider product compatibility, physical output and 1080p/4K performance require their own checks before claims are expanded.
