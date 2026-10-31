# Native runtime bundles

Runtime files are generated under `<os>-<arch>/native/` and are not committed. Plugin variants copy that directory into the plugin root, so the installed layout is `native/bin/` for CLI tools and `native/vlc/` for the playback engine.

The dependency lock is [dependencies.json](dependencies.json). VLC and FFmpeg hashes are checked against the vendors' published SHA-256 values. The MediaInfo hash is pinned from its versioned upstream HTTPS download; it is not claimed to be a separately signed checksum.

On Windows, run `powershell -File scripts/prepare-native.ps1 -Target windows-x86_64` (or `windows-arm64`) from the repository root. Development extraction of FFmpeg requires 7-Zip. The installed plugin does not require any external tool installation or runtime download.

On a matching macOS or Linux host, run `bash scripts/prepare-posix-native.sh mac-arm64`, substituting `mac-x86_64`, `linux-x86_64` or `linux-arm64` as appropriate. macOS preparation uses Homebrew and relocates/signs the copied libraries. Linux preparation uses Docker with Rocky Linux 8 to preserve the glibc 2.28 baseline; bundled non-glibc dependencies use relative runtime paths.

Archive/extraction caches are keyed by their verified SHA-256 and completed extraction markers. Preparation validates a fresh staged bundle before replacing the prior runtime, so dependency upgrades do not retain old VLC modules. Failed preparation preserves the previous complete bundle. `powershell -NoProfile -File scripts/test-prepare-native.ps1` runs offline temporary-archive regressions for upgrades, interrupted extraction, reuse and failure preservation; it requires Windows PowerShell and 7-Zip. Its repository override is for isolated fixtures.

The selected FFmpeg snapshot is pinned because the stable Gyan package available at bootstrap did not include animated WebP decoding. The preparation step probes GIF, APNG and `webp_anim` decoders. Other supported platform bundles must satisfy the same feature tests; an empty directory is not a working native variant.

Preparing/building a platform artifact is not proof it runs on that platform. The OpenSpec native smoke-test tasks remain the release gate. No artifacts are published automatically by the preparation script.

The manually dispatched [Native packages workflow](../.github/workflows/native-packages.yml) prepares each target on its own OS/CPU runner, builds its platform ZIP, verifies the descriptor and executable permissions, and runs `nativeTest` against the extracted ZIP. Only successful jobs upload installable artifacts. Diagnostic artifacts retain dependency inventories and test reports. GitHub release attachments and Marketplace uploads remain separate actions; a CI artifact alone is not a published release.
