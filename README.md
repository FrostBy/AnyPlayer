# Any Player

A free, open-source video player, audio player and image viewer for JetBrains IDEs. Publisher: [Romanovsky](https://plugins.jetbrains.com/vendor/Romanovsky).

![Video playback with a live source-audio spectrogram](docs/screenshots/video.jpg)

<details>
<summary>Audio analysis and image viewing</summary>

![Full-file audio spectrogram](docs/screenshots/audio-spectrogram.jpg)

![Separate stereo waveform channels](docs/screenshots/audio-waveform.jpg)

![Image Fit and zoom controls](docs/screenshots/image.jpg)

</details>

## Installation

Download a matching ZIP from [Any Player 0.1.0](https://github.com/FrostBy/AnyPlayer/releases/tag/v0.1.0). The plugin targets **IntelliJ Platform 2026.1 (261)**. Plugin ID: `me.romanovsky.anyplayer`.

| Operating system | Available architectures |
| --- | --- |
| Windows | x86_64, ARM64 |
| macOS | Intel x86_64, Apple Silicon ARM64 |
| Linux | x86_64, ARM64 |

Choose the architecture of the running IDE. For example, use `AnyPlayer-0.1.0-windows-x86_64.zip` for a Windows x64 IDE. Each ZIP includes its media runtime; no separate VLC, FFmpeg or MediaInfo installation is required.

Remove an earlier development installation before switching to this Plugin ID. Open **Settings → Plugins → gear menu → Install Plugin from Disk**, select the ZIP and restart. The generic Java-only build is not an installation package.

## Features

- Video/audio playback with seeking, speed, volume, audio/subtitle tracks and background audio while coding.
- Local files and direct HTTP(S) media URLs through **Tools → Open Media…**; external paths, transport streams and extensionless files are supported explicitly.
- Still images with Fit, original-pixel inspection, zoom, pan and transparency. GIF/APNG/animated WebP have seeking, frame stepping and repeat controls.
- Live and full-file source-track spectrograms, plus waveform zoom, Follow and separate channels. Analysis stays independent of mute/volume.
- MediaInfo properties, full reports and copying. Playback/view preferences persist across IDE restarts.

## Controls

Click the video to play/pause. Click or drag the timeline to seek; hovering shows timestamped frame previews and nearby thumbnails. Dragging updates the paused main frame, then restores the previous playback state. Play after EOF restarts from zero.

| Action | Shortcut/control |
| --- | --- |
| Play/pause | Space or video click |
| Seek backward/forward | Left/Right or −10/+10 buttons |
| Inspect adjacent frames | Ctrl+Left/Ctrl+Right |
| Go to time | Ctrl+G or current-time label |

Shortcuts apply in the media editor context. Animations use frame steps. Narrow editors put secondary controls in `⋯`; files without audio disable audio-analysis controls with an explanation.

## Development

Use JDK 21+ and the Gradle wrapper. Prepare the matching [native runtime](native/README.md) first. On Windows x64:

```powershell
powershell -File scripts/prepare-native.ps1
.\gradlew.bat test nativeTest
.\gradlew.bat buildPluginVariants_windows_x86_64 verifyPlugin
.\gradlew.bat runIde
```

On macOS/Linux, use `./gradlew` and the corresponding variant task. The [native CI workflow](.github/workflows/native-packages.yml) tests extracted installation ZIPs on matching OS/CPU runners.

- [Testing and remaining validation](docs/testing.md)
- [Release and manual Marketplace upload](docs/release.md)
- [Real screenshots and media provenance](docs/screenshots/README.md)
- [Architecture](openspec/changes/anyplayer-v1/design.md) and [contributor guidelines](AGENTS.md)
- [Browser design references](docs/design/README.md)

## License

Any Player uses [GNU GPLv3](LICENSE). Dependency notices and core-source inventories are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), including the remaining external-library/build correspondence.

Waveform interaction adapts code from Masaki Aida's [Audio Player 1.2.1](https://github.com/aida0710/jetbrains-audio-player-plugin/tree/v1.2.1) under its [retained MIT license](src/main/resources/META-INF/licenses/audio-player-MIT.txt).
