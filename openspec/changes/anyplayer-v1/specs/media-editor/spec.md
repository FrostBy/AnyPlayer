## ADDED Requirements

### Requirement: Media opens in an IDEA file editor
Any Player SHALL provide a media file editor for supported local video, audio, and image files. Opening media SHALL NOT start playback automatically. The initial supported fixture set SHALL include H.264/AAC MP4, HEVC MKV/MP4, VP9/Opus WebM, MP3, WAV, FLAC, still PNG/JPEG/BMP/GIF/WebP, and animated GIF/WebP/APNG; container extensions alone SHALL NOT be treated as proof of codec support.

#### Scenario: Open local video
- **WHEN** the user opens a supported local video using Any Player
- **THEN** its preview and transport controls appear in the editor area in a paused state

#### Scenario: Do not claim unsupported virtual resources automatically
- **WHEN** a supported media extension belongs to a directory, an archive entry or another non-local VirtualFile without an explicit media SOURCE
- **THEN** the automatic provider declines it before constructing a media editor, while readable local files and explicit remote-media tabs remain accepted

#### Scenario: Unsupported or damaged media
- **WHEN** media cannot be decoded
- **THEN** the editor displays a contextual error and copyable diagnostics without freezing IDEA or claiming successful playback

### Requirement: The interface follows IDEA conventions
The editor SHALL use IDEA theme colors, fonts, icons/actions, focus behavior, and scaling. It SHALL expose Preview, Spectrogram, Waveform, and MediaInfo views for applicable media, keep transport accessible for audio/video, and permit inline analysis without covering the video. The functional layout SHALL remain usable in a 480 logical-pixel-wide editor and at 1280 by 720 desktop resolution with ordinary IDE chrome.

#### Scenario: Narrow split editor
- **WHEN** the media editor is narrowed to 480 logical pixels
- **THEN** primary playback/seek controls remain reachable, secondary actions move to overflow as needed, and controls do not overlap or become clipped

#### Scenario: Audio features are unavailable for a silent video
- **WHEN** a video has no audio streams
- **THEN** Spectrogram, Waveform, volume and mute controls are disabled with an explanation that the file has no audio tracks, including overflow audio controls

#### Scenario: Pointer cursors identify available interactions
- **WHEN** the pointer is over an enabled tab, playback button/menu, slider, analysis graph or scrollbar
- **THEN** its available interaction uses a hand cursor; disabled tabs and controls use the default cursor

#### Scenario: Inspect a precise source time
- **WHEN** the user clicks current time or uses Ctrl+G in a seekable media context
- **THEN** Go to time validates seconds or colon-separated source times, rejects out-of-range input and seeks without changing the previous playing/paused state

#### Scenario: Theme changes
- **WHEN** the active IDEA theme changes between light and dark
- **THEN** panels, text, controls, focus indicators, and plot labels remain readable without restarting playback

### Requirement: Image controls match image content
For supported still images the editor SHALL provide fit, 100%, zoom, and pan behavior and respect image orientation and transparency. Still images SHALL NOT display playback controls. Animated images SHALL instead expose the seekable-animation controls defined in `animated-image-playback`, without irrelevant volume, audio-track, subtitle, or spectrogram controls.

#### Scenario: Inspect a large transparent image
- **WHEN** the user selects 100% and drags an image larger than the viewport
- **THEN** the image pans without changing the source file and transparency remains distinguishable from the viewer background

#### Scenario: Inspect original pixels beyond the Fit preview limit
- **WHEN** an 8192 by 4096 image exceeds the bounded Fit preview dimensions and the user selects 100% or pans its viewport
- **THEN** visible detail is decoded from original source pixels at the requested scale, with bounded plugin-retained bitmaps/output and asynchronous stale-result rejection
- **AND** the viewer never labels an enlarged reduced preview as original-resolution 100%; inspection failures are explicit

### Requirement: Keyboard actions respect editor focus
Primary controls SHALL have accessible names, visible focus, and keyboard access. Playback shortcuts SHALL act only in the media editor context and SHALL NOT intercept typing in code editors or text fields.

#### Scenario: Resume coding while audio plays
- **WHEN** the user focuses a code editor and presses Space
- **THEN** IDEA receives the normal code-editor action and Any Player does not toggle playback

### Requirement: Loading and failures remain actionable
The editor SHALL distinguish loading, unsupported media, unavailable native runtime, and analysis failure. Retry and diagnostic actions SHALL be available where meaningful; stale frames from previously selected files SHALL NOT be shown as current results.

#### Scenario: Native runtime cannot load
- **WHEN** the packaged media runtime fails to load
- **THEN** playback controls are disabled and the editor explains the failure with retry/copy-diagnostics actions without instructing the user to install an unrelated runtime

#### Scenario: Dismiss an ignored-file editing notice
- **WHEN** the `.ignore` plugin shows its English ignored-file editing notice above an Any Player viewer
- **THEN** the notice has a close button and a Don't show again action; close lasts for this viewer's lifetime, opt-out persists for Any Player, and unrelated notices and global ignore settings remain unchanged

### Requirement: Playback and view preferences persist
The plugin SHALL persist volume, mute, playback speed, animation repeat choice, analysis visibility/range, preferred audio analysis view and separate-channel choice through the platform settings service. Source URLs, credentials and file paths SHALL NOT be stored in those preferences. Invalid saved control values SHALL fall back to valid defaults or bounds. Opening a source SHALL remain paused.

#### Scenario: Reopen an audio editor
- **WHEN** the user selects Waveform, adjusts volume and speed, then opens another audio editor
- **THEN** the saved controls and preferred audio view are restored without starting playback
