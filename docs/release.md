# Owner-managed release

## Platform-specific archives

JetBrains Marketplace supports separate OS/architecture plugin updates for IDE2026.1+. Each update declares its target through plugin.xml dependencies; the filename alone is insufficient. The current Windows archive has `com.intellij.modules.os.windows` and `com.intellij.modules.arch.x86_64`, and version `0.1.0-windows-x86_64`.

Upload one platform archive in the initial Plugin file field. Add other prepared/verified platform archives through Upload Update on the same plugin page; Marketplace selects the matching update for each IDE's OS/architecture. Do not upload the generic Java-only ZIP or empty platform variants. A universal archive is not required.

The Gradle configuration already uses nativeVariants2.19.0. Only Windowsx64 is presently prepared and tested; do not run publishPlugin while the other five configured variants are empty. Marketplace publication remains manual by the owner.

Source: [official OS/architecture-specific version guidance](https://plugins.jetbrains.com/docs/marketplace/os-architecture-specific-plugin-versions.html).

## Agreed order

1. Build a platform-specific development ZIP and test it locally.
2. After owner acceptance, upload the source project to **https://github.com/FrostBy/AnyPlayer**. No repository creation, commit or push is implicit in this checklist.
3. Prepare the public license/source materials and Marketplace page fields.
4. The owner uploads the release archive to Marketplace manually under [Romanovsky](https://plugins.jetbrains.com/vendor/Romanovsky).

No automatic publication workflow or publication credentials are configured.

## Public release gates

- Complete the remaining checks in [testing.md](testing.md) and the [OpenSpec tasks](../openspec/changes/anyplayer-v1/tasks.md). Advertise only products/platforms actually verified.
- Keep the project GPLv3 `LICENSE` and dependency notices. Assemble the exact corresponding sources/build information for redistributed native dependencies and their included libraries before distributing a public binary.
- Create the approved GitHub release/tag and attach the matching platform archives and SHA-256 values. Native archives must contain the complete runtime, not just the Java plugin.
- Capture screenshots from the actual plugin; do not present the browser design studies as product screenshots.

## Screenshots and Marketplace media

Keep real IDE captures in `docs/screenshots/` and reference those files from the repository README. Capture the same clean sandbox project/window for video, audio analysis and image inspection, using original demonstration media rather than private project files. Keep the IDE chrome, default theme and consistent image dimensions; do not include desktop/browser windows or personal information. JetBrains recommends at least1200×760 pixels.

README images are repository documentation. To display the plugin-page gallery, the owner must upload the [prepared screenshots](screenshots/README.md) separately in **Media** in the Marketplace plugin admin panel after the initial plugin upload. This does not happen automatically when a README or plugin ZIP changes. Keep publication manual as agreed.

Source: [JetBrains Marketplace listing guidance, Media](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html#media).

## Marketplace fields to finish after testing

| Field | Agreed value or pending work |
| --- | --- |
| Name | Any Player |
| Plugin ID | `dev.anyplayer` |
| Vendor | Romanovsky |
| License | GNU GPLv3; link to the uploaded repository license |
| Source code | `https://github.com/FrostBy/AnyPlayer` after upload |
| Price | Free |
| Description | Start with “A video player, audio player and image viewer for your IDE.” Describe verified formats, animations, both spectrogram modes, MediaInfo and direct URLs. |
| Tags | Select relevant tags actually offered by Marketplace at publication time; confirm the available list rather than inventing tag names. |
| Versions/platforms | Match tested IDE build lines and the native archive being uploaded. |

Source repository: https://github.com/FrostBy/AnyPlayer. The release installer version and archive contents must match its public tag; development build counters are not public release versions.
