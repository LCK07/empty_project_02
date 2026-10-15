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
