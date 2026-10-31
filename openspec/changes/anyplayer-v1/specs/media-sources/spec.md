## ADDED Requirements

### Requirement: Open media by URL or local path
The plugin SHALL provide an Open Media action with one field accepting an HTTP(S) URL or local file path, plus a local file chooser. It SHALL open readable files outside the current project and network resources in the media editor without creating or modifying project files. Input validation SHALL reject unsupported URI schemes, malformed URLs and unreadable local paths with an actionable inline error.

#### Scenario: Local file outside the project
- **WHEN** the user supplies an existing local media path outside the project
- **THEN** it opens in Any Player without copying it into the project or changing its contents

#### Scenario: Explicit local media without a recognized extension
- **WHEN** Open Media is used for a valid local transport stream with a .ts suffix or media without a filename extension
- **THEN** an explicit-open marker selects Any Player while the current physical path is resolved from its VirtualFile; ordinary TypeScript files without that marker retain their normal editor

#### Scenario: Direct media URL without an extension
- **WHEN** the user supplies an HTTP(S) media endpoint with no filename extension
- **THEN** the editor inspects the source content and uses the appropriate supported media presentation rather than relying on the URL suffix

#### Scenario: Reopen a renamed or moved local source
- **WHEN** a local source opened through Open Media is closed, renamed or moved in the IDE and opened again from the project tree
- **THEN** the editor resolves the current VirtualFile path, including a moved parent directory, without retaining an obsolete local Path in explicit SOURCE data

#### Scenario: Move the source of an active editor
- **WHEN** a local source path changes while its media editor remains open
- **THEN** the editor invalidates old decoding/analysis work, reinitializes against the current path and restores the bounded source position and previous playing/paused state only after the new session is ready
- **AND** old-source results cannot update the replacement session and closed editors do not restart work

### Requirement: Streaming controls reflect source capabilities
Sources without a known finite duration SHALL show a Live duration state. Non-seekable inputs SHALL disable seek and full-file spectrogram actions. Available playback, live analysis and information SHALL remain usable independently according to each backend's actual source capabilities.

#### Scenario: Live network input
- **WHEN** a supported live media stream has no finite duration or seeking capability
- **THEN** playback is available while the UI identifies it as Live and does not offer a misleading finite seek bar or whole-file overview

### Requirement: Paths and URLs retain meaning without exposing secrets
The system SHALL pass paths and URLs as data arguments, preserve encoded paths and signed queries needed to access a resource, and remove embedded URL query credentials/tokens from diagnostic output. Source files SHALL remain unchanged. Private media addresses SHALL NOT be written into project configuration or cache filenames in plaintext.

#### Scenario: Signed URL fails
- **WHEN** a media URL containing an access token fails to open
- **THEN** the request retains the required token while copyable diagnostics omit it
