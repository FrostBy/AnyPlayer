## ADDED Requirements

### Requirement: Video and audio share complete transport controls
The player SHALL support play/pause, seek, current and total source time, volume/mute, and playback speed. The short-seek buttons SHALL visibly identify their ten-second step; animation frame controls SHALL retain frame-specific labels. Video SHALL preserve aspect ratio. Audio track and subtitle controls SHALL reflect available tracks and be disabled or hidden when inapplicable.

#### Scenario: Seek and change speed
- **WHEN** the user seeks to another source position and changes playback speed
- **THEN** playback resumes or remains paused according to its previous state, time displays follow the source timeline, and audio/video remain synchronized

#### Scenario: Click the video to play or pause
- **WHEN** the user single-clicks the video with the primary mouse button after loading
- **THEN** playback toggles through the same action as the Play/Pause button, including on the paused still surface after seeking; secondary clicks, image panning and transport controls do not trigger this action

#### Scenario: Select another audio track
- **WHEN** the user selects an available audio track
- **THEN** audible playback uses that track and analysis is notified of its source-stream identity

#### Scenario: Click or drag the timeline
- **WHEN** the user clicks the timeline track or drags its thumb
- **THEN** a click selects the pointer's source position, an active drag is not overwritten by timer updates, video shows a decoded still for the selected time before release, and release restores the previous playing/paused state

#### Scenario: Hover over the timeline
- **WHEN** the pointer hovers over a seekable video or animation timeline
- **THEN** a non-focusable thumbnail with the pointer's source time appears above the timeline without seeking the audible player; obsolete requests are cancelled and results cannot update a newer pointer position

#### Scenario: Reuse prepared previews
- **WHEN** a local video has a prepared preview strip
- **THEN** pointer movement reads ready thumbnails without launching a decoder per position, repeated views reuse the bounded cache, and source changes invalidate the strip

#### Scenario: Refine a local seek position
- **WHEN** the pointer pauses over the timeline or the user seeks to a new time
- **THEN** local decoding prepares neighboring images before and after that time independently of the coarse storyboard, reuses the active window during nearby movement, and replaces obsolete distant requests
- **AND** a five-frame strip shows 0.1-second samples with timestamps; hovering inspects without seeking, clicking seeks, and the strip anchor stays fixed during inspection
- **AND** approximate fallback images are marked, preview workers and memory remain bounded, and a short exit grace permits entering the strip without stealing keyboard focus

#### Scenario: Scrub without changing the video footprint
- **WHEN** playback switches to a lower-resolution still during dragging
- **THEN** its aspect-preserving fit and letterboxing match the main video viewport, including enlargement above the still's native pixel dimensions

#### Scenario: Keep main video quality during seek
- **WHEN** a new paused/scrubbed source frame is requested
- **THEN** the main viewport retains its last valid high-quality frame until a matching display-resolution still is available; tiny hover/storyboard thumbnails are never enlarged into the main video
- **AND** decoding coalesces to the latest requested target with one owned still request in flight, respects source/display dimensions up to4K, and does not restart a long debounce on every pointer movement

#### Scenario: Keyboard short seek and frame inspection
- **WHEN** the media context receives Left/Right or Ctrl+Left/Right
- **THEN** plain arrows match the visible step buttons and Ctrl arrows select adjacent decoded video/animation frame timestamps while paused, without replacing focused text/control navigation outside the timeline

#### Scenario: Play after the file ends
- **WHEN** the source reaches EOF and the user presses Play
- **THEN** playback starts again at zero, selected tracks are restored and source-time analysis restarts without stale frames from the end

#### Scenario: Show the terminal source position
- **WHEN** VLC signals EOF after its last reported timestamp falls short of the source duration
- **THEN** the seek thumb reaches its maximum, current time equals total time and analysis cursors indicate the end of the source

### Requirement: A project has one audible playback owner
Only one Any Player playback session per project SHALL be audible at a time. Explicitly starting another session SHALL pause the previous one. Merely opening or selecting a file SHALL NOT start it; switching to a code editor SHALL NOT stop an explicitly started background session.

#### Scenario: Start a second media file
- **WHEN** one file is playing and the user starts another file in the same project
- **THEN** the first pauses before the second becomes audible and retains its source position

### Requirement: Editor disposal releases playback resources
Closing a media editor SHALL stop its playback and release associated native surfaces, listeners, jobs, and owned processes. Reopening the file SHALL create a usable session. Async callbacks for disposed sessions SHALL NOT update another editor.

#### Scenario: Repeated open and close
- **WHEN** a playing media editor is closed and reopened repeatedly
- **THEN** no previous session remains audible, its resources are released, and the new session starts normally

### Requirement: Callback processing respects native and Swing threading
Native callback processing SHALL NOT block the EDT or reenter libVLC from its native event callback thread. UI updates SHALL occur on the EDT and all queued work SHALL obey the owning session lifetime.

#### Scenario: Playback ends while the editor closes
- **WHEN** a native end-of-media callback arrives during disposal
- **THEN** its work does not reenter a released player, repaint a disposed editor, or crash IDEA
