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

## Polish (milestone 8)

- **Baseline Profile.** The `baselineprofile` module drives a scripted TV session against the fake
  server. Generating the profile needs an emulator or TV (`./gradlew :app:generateReleaseBaselineProfile`
  with `tools/testserver` running). The committed profile was generated on the Android TV emulator (API 36).
- **Accessibility.** Every `tvClickable` element now merges its descendants into one TalkBack node;
  icon-only controls (round buttons, icon tabs, PIN delete, search keys) carry an explicit label;
  decorative icons next to text have no description; the LIVE badge always has text. Buttons and tabs
  use a minimum height instead of a fixed one, so they grow with the system font scale. Checked on the
  emulator at font scale 1.3 in German (Settings, Home): nothing clipped, and the remote keeps working after
  the activity is recreated by a font-scale or language change.
- **German copy** was compared key by key with the English file (no missing keys; `app_name` and the
  language names are intentionally untranslated). Du-form throughout. Checked on the emulator: tab bar, Home and Settings fit without truncation.
- **Performance review.** Rows and grids are lazy; category/search/recent queries are `LIMIT`ed;
  Coil sizes each request from the layout (tiles decode at tile size), uses RGB_565 where possible,
  a 15 % memory cache and a 250 MB disk cache. The one unpaged read is the full channel list used when the
  live player cannot resolve its category list. **No real Fire TV was available:** cold start, 60 fps
  focus movement and channel-change timings are unverified on device.
- **Entering the tab bar** returns to the tab that last had focus there (Compose `focusRestorer`), not
  always the selected tab as on tvOS. Redirecting in `onEnter` lost focus on the emulator, so it stays.

## Personalization (profiles, favourites, watchlist, one Live TV tab, language filter)

Plan: `docs/PLAN-personalization.md`.

- **Database v2** with an explicit `Migration(1, 2)` instead of the destructive fallback. Existing favourites
  and progress move into the first profile ("Me" / "Ich"). Covered by a Robolectric test that opens a v1
  database, and checked on the emulator by upgrading over a v1.0 install with data.
- **What is per profile:** favourites, watchlist, watch progress, content languages, and Watch Next on the
  Google TV home screen (cleared and republished on a switch). **Device-wide:** the Xtream account,
  parental PIN and locked categories, player settings and app language. The last profile cannot be deleted.
- **My List replaces the Guide tab**, so the tab bar keeps seven entries. The guide is a full screen over
  Live TV; tuning from it replaces it with the player. It opens on the favourite channels when there are any.
- **Remote Guide key:** the Android TV emulator (API 36) does not deliver `KEYCODE_GUIDE` to apps, so this
  path is untested; check it on a real TV.
- **Language tags** come only from category names and titles (Xtream list endpoints have no language
  field). Whole tokens only (`DE`, `GER`, `DEUTSCH`, `EN`, `ENG`, `[FR]` …), never words of a title, so
  "It Follows" is not Italian. A title's own tag overrides its category's. Existing libraries are tagged once
  in the background after the upgrade (no re-import). The filter applies to Home rows, Series, Movies,
  category rows and Search; never to Live TV, My List, Continue Watching or Favourites.
- **Detail buttons** may run wider than the text column so a label like "Auf der Merkliste" is never cut off.
- **Emulator testing:** if the D-pad stops reaching the app after text entry, Gboard is stuck
  (`dumpsys input_method` shows the input view as shown while its window is hidden). Force-stop
  `com.google.android.inputmethod.latin`; it is not an app bug.
- **Channel favourites** can be set from the heart button in the Live TV hero, by holding Select on a channel
  tile (Home, Live TV, the live player's strip) or on a row in the guide (where Select alone still tunes, on
  release). Favourite channels show a small heart in the guide and lead My List → Favourites.
- **Long press** also counts when Android flags the key event as a long press (`KeyEvent.isLongPress`), not only
  after 550 ms of repeats, so `adb shell input keyevent --longpress` and remotes with their own timing work.
