# Decisions

Where the brief and the platform disagree, Lumen picks the closest behaviour that keeps the
Apple TV feel. Each decision is recorded here.

## Build

- **AGP 9 with built-in Kotlin.** The Android Gradle plugin 9 compiles Kotlin itself, so the
  app module applies only the Compose, serialization and KSP plugins. Versions are the newest
  stable releases that were at least two weeks old when the project started (2026-10-15).
- **ABIs.** The APK carries `armeabi-v7a` (Fire TV sticks run a 32-bit user space),
  `arm64-v8a` (most Google TV devices) and `x86_64` (the emulator). The FFmpeg audio decoders
  are the bulk of the size.
- **Release signing** falls back to the debug key when no keystore secrets are configured,
  so every CI run still produces an installable release APK.

## Core

- **XMLTV window.** A programme that ends exactly when the import window opens does not
  overlap it and is skipped (the draft kept it, and its own test expected it to be skipped).
- **Streaming catalogue import.** Movie, series and channel lists are decoded element by
  element (`decodeToSequence`) and stored in batches of 500, so a 50 000-title account never
  sits in memory at once. Lists keyed by index are still accepted. Lenient parsing would
  accept an HTML error page silently, so a streamed body must start with `[` or `{`.

## Screens and remote

- **Tabs switch on Select**, as in the approved mockup (tvOS switches on focus). Moving along the
  tab bar does not reload content until a tab is chosen.
- **Text entry.** A field is a row; Select opens the system keyboard for it and the keyboard's
  Next/Done moves to the next row. The keyboard is hidden a moment before the field is removed:
  removing both in one frame left Gboard on Android TV attached and swallowing the D-pad.
- **Playback started outside a detail screen** (Home, Search, Continue Watching) puts the series or
  movie detail underneath the player, so Back from the player lands somewhere relevant.
- **Early stream ends.** A VOD stream that ends more than 30 s before its duration is treated as a
  dropped connection: the player reconnects at the same position (up to three times) and the
  title is not marked as watched.
- **Up Next** appears 30 s before the end of an episode with a countdown; at the end the next
  episode starts. Back (or Watch Credits) dismisses it.
- **Zapping.** Up/down change channel within the channel's category (the same lists as the strip
  and the guide); up goes to the previous channel in the list, down to the next, as in the
  mockup. Channel Up/Down keys go to the higher/lower channel.
- **Live preview.** The Live TV hero plays the last channel muted after a short pause while it is
  on screen. It shares the one player, so it stops before anything else plays (one connection).
- **Live reconnects.** A live stream that fails reconnects up to three times before an error shows.
