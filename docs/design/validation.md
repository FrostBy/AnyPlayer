# Design validation

Validated on 2026-10-31. This record covers the browser design artifact and OpenSpec documents, not a compiled IntelliJ plugin or native media decoding.

## Checks performed

| Check | Result |
| --- | --- |
| `openspec validate anyplayer-v1 --strict` | Passed; six capability specifications |
| `node --check docs/design/preview.js` | Passed |
| `node --check docs/design/preview.check.js` | Passed |
| Browser interaction self-check, 1440×1000 | 40 assertions passed |
| Browser interaction self-check, 1280×720 | 40 assertions passed |
| Forced 480 logical-pixel editor, light theme | All five media/information layouts keep toolbar and bottom controls inside their regions |
| Moodboard images | Four images decoded successfully from the original public sources |
| Visual inspection | Dark/light, video, both audio plot ranges, image, animation, MediaInfo split view, native failure and reference board inspected |

The interaction function in [preview.check.js](preview.check.js) is evaluated in the browser against [index.html](index.html). It tests actual DOM actions, not native codecs. The checks cover paused opening; play/pause and seek; analysis collapse and File/Live selection; properties/report availability; preserved transport during metadata inspection; loading, unsupported/native/analysis failures and recovery; absent audio; source-size image zoom; animation seeking/variable delays/frame stepping/repeat; focus-scoped keyboard behavior; light theme; split layout; clipping and horizontal overflow; and moodboard navigation.

## Captured screens

All images below depict the authored design preview. They are not screenshots of an installed Any Player plugin.

| Screen | Capture |
| --- | --- |
| Video, dark | [video-dark.png](screenshots/video-dark.png) |
| Live audio analysis, dark | [audio-dark.png](screenshots/audio-dark.png) |
| Full-file audio overview, dark | [audio-file-dark.png](screenshots/audio-file-dark.png) |
| Image inspection, light | [image-light.png](screenshots/image-light.png) |
| Seekable animation, dark | [animation-dark.png](screenshots/animation-dark.png) |
| MediaInfo with code split, light | [mediainfo-light-split.png](screenshots/mediainfo-light-split.png) |
| Video with code split, light | [video-light-split.png](screenshots/video-light-split.png) |
| Unavailable native media engine, light | [native-error-light.png](screenshots/native-error-light.png) |

Screens were exported with an isolated temporary headless Chrome profile. The annotated reference board remains available in the preview's Moodboard tab; its third-party reference images are linked from their original sources rather than copied into this repository.

## Implementation validation remains a separate gate

The browser study does not establish real playback, callback threading, source-track/PTS mapping, animated-image decoding/composition, native/JNA compatibility, or resource/performance behavior. These are explicitly unchecked implementation tasks in [the OpenSpec checklist](../../openspec/changes/anyplayer-v1/tasks.md), including actual execution on all supported IDEA desktop platforms. The synthetic plot and twelve-frame animation illustrate UI behavior only.
