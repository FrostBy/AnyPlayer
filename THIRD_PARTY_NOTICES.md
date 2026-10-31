# Third-party notices and source inventory

Any Player's own code is licensed under [GNU GPLv3](LICENSE). Third-party components retain their own copyright and license terms.

| Component | Pinned version | License/source reference |
| --- | --- | --- |
| Waveform timeline adaptation | Audio Player1.2.1, Masaki Aida | MIT; [retained notice](src/main/resources/META-INF/licenses/audio-player-MIT.txt), [upstream](https://github.com/aida0710/jetbrains-audio-player-plugin/tree/v1.2.1) |
| vlcj |4.12.1 | GPLv3; [upstream](https://github.com/caprica/vlcj), exact Maven source artifact in the source inventory |
| vlcj-natives |4.12.0 | GPLv3; [upstream](https://github.com/caprica/vlcj-natives), exact Maven source artifact in the source inventory |
| VLC runtime |3.0.24 Windows x64 | Retains the upstream distribution's COPYING and per-component terms; [official source archive](https://download.videolan.org/pub/videolan/vlc/3.0.24/vlc-3.0.24.tar.xz) |
| FFmpeg/ffprobe |2026-10-25,46f23be8b1976ed4c8ec85584daabcf0e57a7b9e | GPLv3 static Gyan essentials build; [exact FFmpeg source](https://github.com/FFmpeg/FFmpeg/tree/46f23be8b1976ed4c8ec85584daabcf0e57a7b9e), [recorded configuration](native/build-info/windows-x86_64/ffmpeg-buildconf.txt) |
| MediaInfo CLI |26.10 | BSD2-Clause; [official sources](https://mediaarea.net/en/MediaInfo/Download/Source) and the upstream runtime LICENSE |
| Bundled LIBCURL.DLL and other native dependencies | As supplied by upstream runtime packages | Their own notices/source correspondence must be reviewed with the native release materials |

The IDE supplies JNA and Kotlin runtime classes; the plugin does not redistribute a second JNA jar. Gradle/build/test dependencies are resolved by their standard repositories and retain their upstream terms.

## Collected source artifacts

[native/source-artifacts.json](native/source-artifacts.json) records exact URLs, computed SHA-256 values and sizes for collected FFmpeg, VLC, MediaInfo AllInclusive and vlcj source archives. The VLC source hash was also compared with its published upstream SHA-256. These source archives are cached locally under ignored `native/downloads/sources/` and can accompany a release separately from the application source archive.

This inventory is **not a claim of complete corresponding-source coverage for the prebuilt native runtime**. Exact external-library/build correspondence for the Gyan static FFmpeg build, VLC Windows contrib dependencies and the bundled LIBCURL.DLL remains to be established. The FFmpeg source snapshot alone does not establish the sources of every library compiled into that executable. Keep that status visible when reviewing binary publication; do not label the partial source bundle complete.

Publishing this repository's source does not include the native executables. Its preparation script downloads the pinned upstream packages for local builds. Keep the project license, adaptation notice and upstream notices when redistributing binaries; see the [release checklist](docs/release.md).
