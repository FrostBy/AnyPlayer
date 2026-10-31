## ADDED Requirements

### Requirement: Animated images have a playback timeline
GIF, animated WebP, and APNG SHALL support play/pause, current and total time, seeking, playback speed, and repeat controls. The system SHALL detect animation from file contents, distinguish still variants of the same extensions, and open animations paused. Silent animation SHALL NOT interrupt separately playing project audio.

#### Scenario: Open an animated WebP
- **WHEN** an animated WebP is opened
- **THEN** it displays an animation timeline and paused frame with applicable controls, without audio/subtitle/spectrogram actions

#### Scenario: Still WebP
- **WHEN** a WebP contains only a still image
- **THEN** it opens with image inspection controls rather than an artificial playback duration

### Requirement: Frame timing and composition remain accurate
Animation presentation SHALL respect frame timestamps and variable delays, alpha transparency, blend/disposal operations, and encoded repeat semantics. Seeking SHALL resolve the corresponding fully composed frame. A user repeat override SHALL be available and disabling repeat SHALL stop at the end of one cycle.

#### Scenario: Variable-delay seek
- **WHEN** the user seeks into an animation whose frames have unequal durations
- **THEN** the frame whose display interval contains the selected source time appears, with correct composition and transparency

#### Scenario: Disable an infinite repeat
- **WHEN** the source repeats indefinitely and the user disables repeat
- **THEN** playback reaches the end of the current cycle and pauses instead of restarting automatically

### Requirement: Paused animations support frame stepping
The editor SHALL provide previous/next frame actions while paused, show the current frame ordinal when available, and set source position to the stepped frame's timestamp. Stepping SHALL NOT approximate variable-delay animation as a constant FPS stream.

#### Scenario: Inspect the next frame
- **WHEN** the user pauses an animation and selects Next frame
- **THEN** the next composited frame appears and the source time updates without resuming playback

### Requirement: Animation decoding is bounded and cancellable
Decoding/indexing/seeking SHALL run outside the EDT, use bounded frame buffers/cache, and cancel obsolete work when seeking again or closing the editor. When a requested frame is not ready the editor SHALL show progress rather than an incorrectly positioned old frame. Full animation support SHALL be checked against the actual packaged decoder build on every supported target.

#### Scenario: Close during backward seek
- **WHEN** decoding preceding frames is needed for a backward seek and the editor is closed
- **THEN** its owned decode work stops, buffers are released, and no result updates another editor
