# Releasing Any Player

Source: [FrostBy/AnyPlayer](https://github.com/FrostBy/AnyPlayer). Current release: [0.1.0](https://github.com/FrostBy/AnyPlayer/releases/tag/v0.1.0).

## Platform packages

Use one GitHub release with separate installation ZIPs for Windows, macOS and Linux, each on x86_64 and ARM64. Only attach packages that passed their target's native checks. Do not distribute the generic Java-only ZIP as an installer.

Run the manually dispatched [Native packages workflow](../.github/workflows/native-packages.yml). Successful jobs provide a ZIP, SHA-256 and separate diagnostics. The workflow validates the installed archive on its matching native runner; it does not publish releases or Marketplace updates.

Keep the public version, release title and archive version consistent. Record exact source/build commits in `BUILD-INFO.json` when platform builds follow the initial release tag, and supply their matching source archive. Do not silently move a published tag.

## GitHub release checklist

1. Run the checks in [testing.md](testing.md), including Plugin Verifier for the declared IDE line and native tests for each attached platform.
2. Inspect `plugin.xml`, native tools/libraries, executable permissions, icons, licenses and the absence of duplicate JNA jars.
   Verify Plugin ID `me.romanovsky.anyplayer`, display name `Any Player`, vendor `Romanovsky` and extension class namespaces inside every packaged JAR.
3. Attach the platform ZIPs, project sources, dependency source materials and `SHA256SUMS.txt` to the approved release/tag.
4. Write concise release notes describing shipped behavior, completed platform checks and concrete remaining limits.
5. Preserve dependency notices and exact source/build inventories. The collected core-source bundle is incomplete for some external libraries; see [third-party notices](../THIRD_PARTY_NOTICES.md).

## Marketplace upload

Publication is performed manually by the owner under [Romanovsky](https://plugins.jetbrains.com/vendor/Romanovsky).

JetBrains supports OS/CPU-specific updates for IDE 2026.1+. Upload one matching ZIP in the initial **Plugin file** field, then add the others through **Upload Update** on the same plugin page. Each archive must declare `com.intellij.modules.os.<os>` and `com.intellij.modules.arch.<arch>` in its descriptor. The filename alone does not select the platform. A universal ZIP is not required. See [JetBrains platform-specific version guidance](https://plugins.jetbrains.com/docs/marketplace/os-architecture-specific-plugin-versions.html).

Do not run `publishPlugin`: all six variants are configured, and Marketplace publication is owner-managed.

A listing already created with a different Plugin ID does not change identity when GitHub files are replaced. Resolve that listing with Marketplace before uploading this identity's archives. Remove an earlier development plugin from the IDE before installing this Plugin ID.

| Field | Value |
| --- | --- |
| Name | Any Player |
| Plugin ID | `me.romanovsky.anyplayer` |
| Vendor | Romanovsky |
| Price | Free |
| License | GNU GPLv3 |
| Source | `https://github.com/FrostBy/AnyPlayer` |
| Documentation | `https://github.com/FrostBy/AnyPlayer#readme` |
| Bugtracker | `https://github.com/FrostBy/AnyPlayer/issues` |
| Description opening | A video player, audio player and image viewer for your IDE. |
| Platforms | Only the OS/CPU packages and IDE release lines actually validated. |
| Tags | Relevant tags offered by Marketplace when uploading. |

## Screenshots

Upload the four prepared [real IDE screenshots](screenshots/README.md) separately under **Media**. The README and plugin ZIP do not populate the Marketplace gallery automatically. Suggested order: video, audio spectrum, waveform, image. Browser mockups in `docs/design/` are design references.
