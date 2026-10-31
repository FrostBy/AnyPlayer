# Third-party notices and source inventory

Any Player's own code is licensed under [GNU GPLv3](LICENSE). Third-party components retain their own copyright and license terms.

| Component | Pinned version | License/source reference |
| --- | --- | --- |
| Waveform timeline adaptation | Audio Player1.2.1, Masaki Aida | MIT; [retained notice](src/main/resources/META-INF/licenses/audio-player-MIT.txt), [upstream](https://github.com/aida0710/jetbrains-audio-player-plugin/tree/v1.2.1) |
| vlcj |4.12.1 | GPLv3; [upstream](https://github.com/caprica/vlcj), exact Maven source artifact in the source inventory |
| vlcj-natives |4.12.0 | GPLv3; [upstream](https://github.com/caprica/vlcj-natives), exact Maven source artifact in the source inventory |
| VLC runtime | 3.0.24 | Upstream COPYING and per-component terms; [official source archive](https://download.videolan.org/pub/videolan/vlc/3.0.24/vlc-3.0.24.tar.xz). Linux packages also record RPM source identifiers. |
| FFmpeg/ffprobe | Per-platform versions in [dependencies.json](native/dependencies.json) | GPLv3 builds from Gyan, BtbN and Homebrew; exact core sources in [source-artifacts.json](native/source-artifacts.json), [Windows x64 configuration](native/build-info/windows-x86_64/ffmpeg-buildconf.txt). |
| MediaInfo CLI | Per-platform versions/inventories | BSD 2-Clause; [official sources](https://mediaarea.net/en/MediaInfo/Download/Source) and the upstream runtime LICENSE. |
| Bundled LIBCURL.DLL and other native dependencies | As supplied by upstream runtime packages | Their own notices/source correspondence must be reviewed with the native release materials |

The IDE supplies JNA and Kotlin runtime classes; the plugin does not redistribute a second JNA jar. Gradle/build/test dependencies are resolved by their standard repositories and retain their upstream terms.

## Collected source artifacts

[native/source-artifacts.json](native/source-artifacts.json) records exact URLs, computed SHA-256 values and sizes for collected FFmpeg, VLC, MediaInfo AllInclusive and vlcj source archives. The VLC source hash was also compared with its published upstream SHA-256. These source archives are cached locally under ignored `native/downloads/sources/` and can accompany a release separately from the application source archive.

The collected core-source bundle is **incomplete for bundled external libraries and build patches**. Exact correspondence remains open for Gyan/BtbN static libraries, VLC contrib dependencies, Homebrew/RPM changes and LIBCURL.DLL. The FFmpeg core snapshots alone do not cover every library compiled into those executables.

Publishing this repository's source does not include the native executables. Its preparation script downloads the pinned upstream packages for local builds. Keep the project license, adaptation notice and upstream notices when redistributing binaries; see the [release checklist](docs/release.md).
