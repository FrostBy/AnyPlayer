# Any Player visual direction

Status: interactive design study and implementation contract, not a running IDEA plugin. Open [index.html](index.html) directly in a browser; there is no build, server, or package installation. Reference images need internet access; editor mockups are local and self-contained.

## Design intent

The media file occupies an ordinary IDEA editor tab. The surrounding Project/tool-window/editor/status-bar structure remains familiar. Content receives most of the space, transport stays predictable, and detailed controls appear where they are relevant.

The preview includes video, audio, images, seekable animation, MediaInfo, light/dark themes, a code/media split, and loading/failure states. The outer study controls select example layouts; they are not part of the shipping plugin. Media, signal, metadata, and transport timing are illustrative.

## Annotated moodboard

Use the **Moodboard** tab in the preview to compare the references and the adaptation notes.

| Source | Borrowed principle | Adaptation for Any Player |
| --- | --- | --- |
| [JetBrains UI overview](https://plugins.jetbrains.com/docs/intellij/ui-overview.html) | Existing editor frame, compact toolbars, contextual actions | Fit the file editor into IDEA's Project/split-editor layout. |
| [JetBrains UI guidelines](https://plugins.jetbrains.com/docs/intellij/ui-guidelines-welcome.html) | Theme, typography, icons, focus and spacing | Use platform components and theme roles in the implemented plugin. |
| [IINA](https://iina.io/) | Clear primary transport and grouped secondary controls | Keep transport in a fixed bottom region that does not obscure media. |
| [Audacity Spectrogram View](https://manual.audacityteam.org/man/spectrogram_view.html) | Readable axes, meaningful intensity colors, time navigation | Give source time, frequency and dB explicit labels and keep the playback cursor consistent. |

Images in the board are linked to the authors' public sites and retain their original ownership. They are reference material, not shipped plugin assets. The [coastal artwork](assets/coast.svg) is an original vector illustration made for these mockups, not an extracted frame from a third-party movie. The synthetic plot is not measured media data.

## Layout contract

### Video

Preview fills the content area while preserving aspect ratio. A fixed transport region exposes current time, seek, duration, play/pause, short seeks, volume and speed. Tracks/subtitles live in contextual options. The inline analysis drawer can be collapsed; its controls do not cover the video. Spectrogram switches to a full-size analysis view. MediaInfo opens a dedicated properties view for the same file.

### Audio

The spectrogram becomes the main content. File/Live switches the source-time range while transport stays in the same bottom region. A compact header identifies the source track and audio properties. The graph includes source-time ticks, frequency ticks and an intensity legend. Full-file click-to-seek and live cursor behavior share the player's source timeline.

### Images

The content area uses a restrained transparency checkerboard. A bottom inspection toolbar shows dimensions and fit/100%/zoom/pan controls. Playback controls and Spectrogram are inapplicable to still images. Production 100% must correspond to actual image pixels and IDEA scaling.

### Animations

GIF, animated WebP, and APNG receive a playback timeline with millisecond readouts, play/pause, previous/next frame, speed, and repeat controls. Frame stepping leaves playback paused and updates the source time. The browser study uses a twelve-frame illustrative sequence with unequal frame delays; it demonstrates control behavior rather than decoding a real GIF. Audio/subtitle/spectrogram actions are absent. Real decoding must preserve frame delays, alpha/blend/disposal composition and the source's repeat semantics, with bounded asynchronous frame storage.

### MediaInfo

Properties are grouped as General, Video, Audio and Text/Image where applicable. Align labels and values, retain units, wrap long values, and provide the complete report separately. Metadata remains available when playback is unsupported but the file is readable. The study uses sample values rather than an actual decoder result.

## Visual roles

The final plugin uses the active IDEA theme's colors/fonts/icons, rather than hardcoded copies of these preview values.

| Role | Dark study | Light study | Production source |
| --- | --- | --- | --- |
| Editor surface | `#1E1F22` | `#FFFFFF` | IDEA editor/panel theme roles |
| Tool panels | `#2B2D30` | `#F7F8FA` | Platform panel/toolbar roles |
| Text | `#DFE1E5` | `#25272D` | Platform label foreground |
| Secondary text | `#9DA0A8` | `#656B76` | Platform secondary text role |
| Selection/focus | `#548AF7` | `#285CC4` | Platform selection/focus roles |
| Separators | `#393B40` | `#D8DBE0` | Platform border/separator roles |

The preview uses Inter when installed and Segoe UI/system fallbacks. The plugin uses the IDE font system. Primary UI labels are approximately 12–14 logical pixels, with secondary data below that only where readable. Production scaling follows the SDK, including user font/zoom settings. Keep text sentence case, controls compact, separators quiet, and visible focus distinct. The signal intensity ramp retains its meaning across UI themes.

## States and interaction

- Opening a file does not autoplay it. Starting another media file pauses the previous project playback owner; switching back to code does not unexpectedly interrupt background audio.
- Space/seek shortcuts apply in the focused media editor context, not in code or text-entry controls. Tooltips/accessibility names explain icon-only actions.
- Loading keeps the layout stable and exposes cancellation. Unsupported media and native-engine errors offer retry/diagnostics. An analysis error leaves working playback available.
- A video without audio has no usable spectrogram action. Still images use inspection controls; animations use silent playback and frame controls.
- At 480 logical pixels, secondary labels/actions yield to primary controls. Inspect the study's Split editor mode, including light theme. The real release must also verify 1280×720 with normal IDE chrome, HiDPI, keyboard navigation and long localized/file names.
- Source-track analysis is before mute/volume/effects; label the graph accordingly. Never present stale analysis after source/track/seek changes.

## Validation

The native viewer uses cached coarse previews followed by a denser local window, then a stable five-frame strip for selection. The first quick keyframe may be approximate; the refined strip samples every 0.1 seconds. Keyboard frame stepping uses actual source timestamps independently of this sampling grid.

References: YouTube's [precise seeking help](https://support.google.com/youtube/answer/12825599?co=GENIE.Platform%3DDesktop&hl=en) describes selecting a thumbnail in a row; its [2012 player article](https://blog.youtube/news-and-events/looking-ahead-in-youtube-player/) documents historical temporal zoom and surrounding scenes. Autodesk's [Swifter research](https://www.research.autodesk.com/app/uploads/2023/03/swifter-improved-online-video.pdf_rec46kl91LgCROmKf.pdf) studies multi-frame navigation. [thumbfast](https://github.com/po5/thumbfast/blob/master/thumbfast.lua) demonstrates fast preview seeks followed by refinement. These inform interaction and scheduling; they do not establish that YouTube clients decode neighboring frames locally.

The browser self-check is [preview.check.js](preview.check.js). It exercises view changes, range selection, seek, play/pause, image controls, failure recovery, audio-free video, theme/split states, focus scoping and moodboard loading. It does not execute native tools or test real decoding. See [validation.md](validation.md) for measured browser checks and captured layouts.

The normative behavior is in [the OpenSpec change](../../openspec/changes/anyplayer-v1/proposal.md). Some behavior, including background playback ownership and native disposal, requires the later IDEA prototype and is not simulated fully by this browser study.
