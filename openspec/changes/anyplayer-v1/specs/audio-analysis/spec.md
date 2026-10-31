## ADDED Requirements

### Requirement: Full-file spectrograms inspect the selected source track
The editor SHALL generate a full-file spectrogram for the selected audio track, with time and frequency axes and a labeled intensity scale. It SHALL show a playback cursor and allow seeking by time. Analysis SHALL be cancellable, run outside the EDT, and use bounded output dimensions and working queues.

#### Scenario: Navigate from the overview
- **WHEN** the user clicks a time position in the completed full-file spectrogram
- **THEN** playback seeks to the corresponding source time while preserving its paused/playing state

#### Scenario: Cancel a long analysis
- **WHEN** the user cancels generation or closes the editor
- **THEN** its owned analysis process stops and subsequent output is not applied to the editor

### Requirement: Live spectrograms follow playback source time
Live analysis SHALL display the selected source audio track around the current VLC source position, with the cursor centered, past audio on the left and decoded upcoming audio on the right. For seekable sources it SHALL decode ahead by half the visible window, including bounded silence after EOF. For sources that cannot supply future data, the unavailable right-hand portion SHALL remain empty. Its time alignment SHALL use decoded timestamps rather than data-arrival time. On deterministic timing fixtures it SHALL remain within 200 ms of source playback position without accumulating drift.

#### Scenario: Inspect upcoming audio
- **WHEN** a seekable source contains silence at the cursor and a tone later in the visible window
- **THEN** the tone appears to the right of the central cursor at its correct source time

#### Scenario: Known tone at a known timestamp
- **WHEN** a fixture with timed silence, tones, and impulses is played
- **THEN** the live spectrogram features, file overview, and player cursor correspond within the accepted source-time tolerance

#### Scenario: Smooth presentation between playback events
- **WHEN** playback continues between native source-time notifications
- **THEN** graph/cursor presentation interpolates from the authoritative source clock at the selected rate, targets approximately60Hz repainting, freezes on pause/buffering and resynchronizes on seek/EOF without treating stale pre-seek events as the new position

#### Scenario: Nonzero stream start and delayed audio
- **WHEN** a container has a nonzero start timestamp or an audio-stream offset
- **THEN** normalized analysis timestamps align with the corresponding VLC source positions

### Requirement: Analysis state changes invalidate obsolete results
Pause SHALL freeze live presentation. Seek, source replacement, and audio-track changes SHALL invalidate older analysis generations. Speed changes SHALL follow the player's source-time progression. If analysis is not ready, the UI SHALL indicate that state instead of presenting an incorrectly positioned frame.

#### Scenario: Rapid backward and forward seek
- **WHEN** the user seeks several times before preceding jobs finish
- **THEN** only results for the latest source range and selected track can update the live graph

#### Scenario: Change playback speed
- **WHEN** speed changes between 0.5x, 1x, and 2x
- **THEN** the graph follows the actual source position without adopting an independent playback clock

#### Scenario: Disable or fail to map the selected audio track
- **WHEN** the user selects Audio Disable or a track cannot be mapped to an analysis source stream
- **THEN** both live and full-file graphs clear the prior track's frame/overview, reject pending old-generation output and show that no selected audio stream is available
- **AND** choosing a valid track regenerates its analysis; ordinary mute continues to preserve source analysis

### Requirement: Source analysis does not replace audible output
VLC SHALL remain the only audible output. Spectrograms SHALL represent the original selected track before player volume, mute, or playback effects. Analysis SHALL NOT capture microphone/system output or install audio callbacks that replace VLC's output.

#### Scenario: Mute playback
- **WHEN** the user mutes a playing file
- **THEN** audible output stops while the source-track spectrogram continues to show its original spectral content

### Requirement: Analysis failures are isolated from playback
Analysis SHALL be requested only for media with an audio stream and SHALL NOT stop successful playback when it fails. Owned decoder processes and cached output SHALL be bounded and released or cancelled according to editor lifetime.

#### Scenario: Audio analysis exits with an error
- **WHEN** FFmpeg fails during live analysis of a file that VLC can play
- **THEN** the player continues normally and the analysis panel offers a clear error with retry/diagnostic actions

### Requirement: Interactive source waveform
For a seekable source with a selected audio stream, the editor SHALL offer a waveform view with a source-time ruler, playback cursor, click/drag seeking, playhead-anchored zoom, horizontal scrolling and optional separate-channel presentation. Changing the range or stream SHALL invalidate obsolete work. Generation SHALL run off the EDT, use bounded output/cache resources, and stop when the editor closes or the view is left. Failures SHALL leave playback available.

Waveform working memory SHALL remain bounded independently of source duration; generation SHALL aggregate streamed samples rather than retain the complete decoded PCM range. Valid short intervals SHALL remain displayable when they contain fewer audio samples than the preferred output width.

#### Scenario: Inspect one channel in a shorter range
- **WHEN** the user enables separate channels and zooms a stereo waveform
- **THEN** the selected source interval is regenerated with channels separated and seeking maps to that interval's absolute source time

#### Scenario: Follow or manually explore the waveform
- **WHEN** Follow is enabled and playback leaves the visible waveform range
- **THEN** the range moves to include the playhead, and manual scrolling disables following rather than competing with the user's navigation

#### Scenario: Follow slides through prepared waveform data
- **WHEN** a zoomed waveform follows ongoing playback
- **THEN** the viewport moves continuously through a prepared source-time buffer, neighboring ranges are prefetched and valid displayed data is retained while replacement work completes
- **AND** a distant uncached range keeps the previous valid display with a preparing indication until its source-aligned replacement is ready; track/source changes still invalidate mismatched data

#### Scenario: Stop expensive analysis
- **WHEN** the user chooses Cancel analysis and then Resume analysis
- **THEN** owned analysis work stops without disabling playback, and the selected analysis mode can be requested again

#### Scenario: Preserve leading silence
- **WHEN** a source contains leading empty movie edits or delayed audio
- **THEN** waveform content retains the corresponding gap on the player's source timeline

#### Scenario: Full-range long audio
- **WHEN** a long audio file is inspected in Waveform Fit without a cached image
- **THEN** retained PCM buffers and envelope accumulators remain bounded independently of duration, selected channels retain their source timing and cancellation stops the owned process

#### Scenario: Zoom into a low-rate source
- **WHEN** an 8 kHz source is zoomed to a 100 ms interval
- **THEN** a valid waveform is rendered without requiring 1600 input samples, and its cursor/seek coordinates still represent that absolute source interval
