# Product screenshots

These are unedited window captures of the actual Any Player dev.11 plugin in IntelliJ IDEA 2026.1, Windows x64, default dark theme. All four images are 1386×993 pixels and use the same clean `AnyPlayer-Demo` sandbox project. They contain no personal project files or desktop/browser windows. Existing browser design studies are separate and are not product screenshots.

| File | What it shows |
| --- | --- |
| [video.jpg](video.jpg) | Paused video at about 8 seconds, transport controls and an inline live source spectrogram. |
| [audio-spectrogram.jpg](audio-spectrogram.jpg) | Full-file spectrum of a 24-second synthetic frequency sweep and reference tones. |
| [audio-waveform.jpg](audio-waveform.jpg) | Separate stereo waveform channels, source-time cursor and Follow controls. |
| [image.jpg](image.jpg) | Image Fit presentation, original-size/zoom controls and inactive audio-analysis tabs. |

## Media provenance

The illustration comes from the repository's original [coast.svg](../design/assets/coast.svg), rasterized to PNG. The video adds a slow pan and generated audio; the standalone WAV uses generated sine-wave sweeps, reference tones and a changing amplitude envelope. No user game/trailer footage, third-party photographs or music were used. Original demonstration media follows the repository license; the IDE interface is shown to document plugin behavior.

Working media/project files were generated under `build/showcase/AnyPlayer-Demo/`. Only final screenshots belong here. Capture used the documented Computer Use API to save the window's original JPEG bytes without recompression, cropping, retouching or UI compositing. Playback was muted; these images are not a physical speaker-output test.

## Marketplace

The repository README displays these images on GitHub. For the Marketplace gallery, upload the same files separately under **Media** in the plugin admin panel after the initial plugin upload. README changes and ZIP contents do not automatically populate that gallery. Suggested order: video, audio spectrum, waveform, image. Captions can use the descriptions above.

JetBrains recommends real IDE screenshots, consistent aspect ratios, legible content, the default theme and no personal information, with a minimum recommended size of 1200×760. See [official Marketplace listing guidance](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html#media). Upload remains the owner's manual step; no external publication was performed.
