## ADDED Requirements

### Requirement: Display real MediaInfo properties and reports
The MediaInfo view SHALL display available General, Video, Audio, and Text track fields from MediaInfo, with readable units and complete copyable values. It SHALL provide a complete text report and SHALL NOT substitute ffprobe output under the MediaInfo name.

#### Scenario: Inspect a multi-track video
- **WHEN** MediaInfo finishes processing a video with multiple audio and subtitle tracks
- **THEN** the view groups each track's fields and provides the complete untruncated report for copying

#### Scenario: A field is absent
- **WHEN** a source file has no value for an optional metadata field
- **THEN** the UI omits it or identifies it as unavailable instead of inventing a value or displaying a misleading zero

### Requirement: Metadata inspection is independent of playback
Metadata processing SHALL run outside the EDT and remain available when playback fails, provided the file remains readable and MediaInfo can inspect it. Closing/replacing a file SHALL cancel obsolete processing and prevent stale report updates.

#### Scenario: Playback codec is unsupported
- **WHEN** VLC cannot play a readable file but MediaInfo can inspect it
- **THEN** the user can still access its metadata without starting playback
