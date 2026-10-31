# Repository Guidelines

## Project Structure & Module Organization

Any Player is a Kotlin/Swing media plugin for JetBrains IDEs.

Plugin ID/package namespace: `me.romanovsky.anyplayer`. Keep the descriptor, loader, registrations and archive checks aligned.

- `src/main/kotlin/me/romanovsky/anyplayer/` contains `editor`, `playback`, `media`, `images` and `analysis` modules. Plugin registration, icons and notices are in `src/main/resources/`.
- `src/test/kotlin/` contains JUnit, IntelliJ SDK and native integration checks.
- `native/dependencies.json` pins runtime origins/hashes; `scripts/prepare-native.ps1` prepares ignored platform bundles.
- `docs/` holds validation, release instructions and actual sandbox screenshots. `openspec/changes/anyplayer-v1/` records requirements and tasks; `.serena/` is tooling configuration.

## Build, Test, and Development Commands

Use JDK 21+ and the Gradle wrapper. The README documents prerequisites and the installed-IDE override `-PlocalIdePath=...`.

- `powershell -File scripts/prepare-native.ps1`: prepare the Windows native runtime; requires 7-Zip.
- `.\gradlew.bat test nativeTest`: run SDK/unit and native suites separately.
- `.\gradlew.bat runIde`: launch the isolated development IDE.
- `.\gradlew.bat buildPluginVariants_windows_x86_64 verifyPlugin`: package the prepared target and check platform compatibility. The generic ZIP has no native runtime.
- `openspec validate anyplayer-v1 --strict`: validate specifications.

## Coding Style & Naming Conventions

Use UTF-8, `.editorconfig`, Kotlin official style and four-space Kotlin indentation. Types use UpperCamelCase; functions/properties use lowerCamelCase. No standalone formatter/linter task is configured. Keep decoding off the EDT, reject obsolete background results and release owned processes. Reuse existing module helpers before adding abstractions.

## Testing Guidelines

Name test classes `*Test`; SDK tests extend BasePlatformTestCase. `test` excludes NativeMediaTest, which runs through `nativeTest`. Use focused behavior regressions and generated media; restore shared settings and clean temporary files after native release. Optional `mediaFixture` requires a finite seekable video with audio. No coverage threshold is configured. Record actual checks and explicit limitations.

## Commit & Pull Request Guidelines

No historical commit convention is established. Use concise imperative English subjects, for example `Fix explicit media opening`.

Keep pull requests focused. Describe the change, link a relevant issue when available, and record validation results. Include screenshots for visible interface changes. Do not add generated attribution or `Co-Authored-By` trailers.

## Security & Configuration

Keep credentials, private media and machine-specific secrets out of shared files. Preserve GPLv3 and dependency notices, including the MIT waveform attribution. Use IDE-provided JNA; never bundle a second copy. Other OS/IDE targets remain release gates until tested. Read pending review state before edits; commits, pushes and publication require explicit instructions.
