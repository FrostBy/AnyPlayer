## ADDED Requirements

### Requirement: Cover the officially supported IDEA desktop platforms
Every declared compatible IDEA release SHALL support the same official desktop OS/CPU combinations as that IDEA release. The initial release policy SHALL cover Windows, macOS, and Linux on x86_64 and arm64 for verified IDEA releases starting with 2026.1. Compatibility claims SHALL be tied to completed checks, not presumed future releases.

#### Scenario: Install on a supported ARM64 IDEA
- **WHEN** Any Player is installed into an officially supported ARM64 IDEA distribution
- **THEN** it loads matching ARM64 playback, analysis, and metadata binaries and exposes the full agreed feature set

### Requirement: Native selection follows the IDE process
Native selection SHALL use the running IDE's OS and process architecture. All advertised native variants SHALL contain their complete required binaries, VLC modules, and shared dependencies. An empty native file collection SHALL NOT be treated as a supported target.

#### Scenario: x64 IDEA on an ARM64 host
- **WHEN** IDEA runs as an x64 process under supported OS translation
- **THEN** the plugin selects x64 native dependencies rather than mixing them with host ARM64 libraries

### Requirement: Installation is self-contained
After plugin installation, supported local playback, images, spectrograms, and MediaInfo SHALL work without separately installed VLC, FFmpeg, MediaInfo, custom JBR changes, or runtime downloads. Native tools SHALL retain required executable permissions and load on the supported OS/libc baseline.

#### Scenario: Clean-machine installation
- **WHEN** a user installs the target plugin archive into a clean compatible IDEA without system media utilities
- **THEN** playback, full-file/live analysis, and metadata inspection work using packaged dependencies

### Requirement: Native processes handle local media safely
The plugin SHALL leave source media unchanged, invoke tools with argument arrays, consume process output asynchronously, bound retained diagnostics, and terminate only processes it owns. Unavailable or mismatched native libraries SHALL produce a contextual error without a shell fallback or silent third-party download.

#### Scenario: Paths contain Unicode and shell metacharacters
- **WHEN** media has a local path containing spaces, Unicode, quotes, or shell metacharacters
- **THEN** it is treated as a file path rather than executable shell syntax and the source remains unchanged

### Requirement: Distribution is reproducible and verified
Release artifacts SHALL record exact native/dependency versions, origins, checksums, and required license/source notices. The release gate SHALL include Plugin Verifier, packaged native execution, and actual playback/analysis/metadata smoke checks for each advertised target. Plugin Verifier alone SHALL NOT establish native compatibility.

Native preparation SHALL reuse extracted content only when it matches the verified archive identity and completed extraction. Updating a dependency SHALL replace the complete prepared bundle without retaining obsolete modules or mislabelling previous binaries with a new manifest. Failed preparation SHALL leave the previous complete bundle usable.

#### Scenario: One target fails native execution
- **WHEN** an advertised target cannot load its packaged dependencies or fails its media smoke checks
- **THEN** the full-platform release remains blocked until that target works rather than publishing an empty or silently degraded variant

#### Scenario: Upgrade with an existing extraction cache
- **WHEN** a dependency archive/hash changes while extraction from the previous version remains cached
- **THEN** preparation uses the new verified content, removes obsolete bundle modules and writes the matching manifest; a repeated unchanged-hash run can reuse only completed matching extraction

#### Scenario: Interrupted extraction or bundle validation
- **WHEN** extraction or validation fails before a replacement runtime is ready
- **THEN** incomplete extraction is not reusable and the previous complete runtime/manifest remain usable
