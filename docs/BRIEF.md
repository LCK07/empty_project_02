# Build "Lumen": an Apple-TV-quality IPTV player for Google TV and Fire TV

You are building a complete, installable Android TV app from an empty GitHub
repository. Read this whole brief and the files in `reference/` before writing
code. Work in milestones, commit and push after each one, and keep the app
installable at every milestone.

## 1. Goal

A personal IPTV player that looks and behaves as if Apple designed it for
Apple TV, running on Android TV devices. It plays the owner's own Xtream Codes
subscription (series, movies, live TV with a programme guide). It ships with
no content, playlists or provider of its own.

- **Owner's devices:** an XGIMI MoGo laser projector running Google TV, and an
  Amazon Fire TV 4K stick from 2024 (Fire OS 8, Android 11 base; the remote has
  a D-pad, Back, Home, Menu, play/pause, rewind/forward and voice, but **no
  channel up/down buttons**). Treat the Fire TV stick as the performance floor.
- **Installation:** sideloaded APK (no store). Must install and run on both.
- **Languages:** English and German, following the device language, with an
  override in Settings.
- **Working name:** "Lumen" (package `com.lck07.lumen`). Keep names easy to change.
- **Repository:** `https://github.com/LCK07/empty_project_02` (public, empty,
  default branch `main`). Everything goes here.

## 2. Reference material (in `reference/`)

| File | What it is | How to use it |
|---|---|---|
| `tv-mockup.html` | Clickable 1920 × 1080 mockup the owner approved ("pretty great looking"). Open it in a browser; arrow keys, Enter, Esc, Space, PgUp/PgDn, G. | **The visual and interaction target.** Match its layout, hierarchy, focus behaviour, copy and both languages. Artwork and data in it are placeholders. |
| `apple-hig-web/` | A design system distilled from Apple's Human Interface Guidelines (colour values incl. dark and increased contrast, type, motion, accessibility, components, writing rules). Start with `SKILL.md`, then `DESIGN.md`. | The rulebook. It is written for the web, so translate it to Compose for TV using section 4 of this brief, which adds the tvOS-specific rules. |
| `core-draft/` | A first draft of the pure-Kotlin `core` module: Xtream API client, XMLTV streaming parser, guide index, continue-watching logic, plus JUnit tests and fixtures. It compiles; its tests have **not** been run yet. | Start from it: copy it into the repo, run the tests, fix what fails, then build on it. Keep `core` free of Android dependencies so it stays unit-testable on the JVM. |

## 3. Technology

Use current stable versions of everything (check Google Maven and Maven
Central; avoid anything released in the last two weeks). Pin versions in a
Gradle version catalog.

- **Kotlin** and **Jetpack Compose for TV** (`androidx.tv:tv-material`,
  `tv-foundation` where needed) for all UI. No Leanback, no WebView UI.
- **Media3 ExoPlayer** for playback: HLS and MPEG-TS (Xtream live streams are
  usually `.ts`, sometimes `.m3u8`), MP4/MKV for VOD. Add the Media3 FFmpeg audio
  decoder extension, or a pragmatic fallback, so AC3/E-AC3/DTS audio still plays
  on devices without hardware decoders. Use hardware video decoding.
- **OkHttp** + **kotlinx.serialization** (lenient JSON; see section 6).
- **Room** (with KSP) for the cached catalogue, the guide and watch progress.
  **WorkManager** for the background guide import. **DataStore** for settings.
- **Coil** for images, with sensible memory and disk limits for 1–2 GB RAM devices.
- **androidx.tvprovider** to publish Continue Watching to the Google TV home
  screen's "Watch Next" row (Fire TV ignores it gracefully).
- Simple manual dependency injection (an app container) is fine; Hilt is optional.
- `minSdk` 28 or higher is fine for both devices; `targetSdk` current.
- Allow cleartext HTTP (most Xtream servers are plain HTTP).

**Modules:** `core` (JVM: models, Xtream client, XMLTV parser, guide index,
continue-watching rules) and `app` (Android: UI, player, persistence, workers).

## 4. Design: Apple tvOS rules, translated to Android TV

The mockup is laid out in tvOS points on a 1920 × 1080 canvas. Android TV at
1080p is 960 × 540 dp, so **every tvOS point value halves in dp**. Build a
`design` package of tokens (colours, type, spacing, radii, motion, focus) and
use only those tokens in screens.

**Canvas and layout**
- Safe area: 60 pt top and bottom, 80 pt left and right → **30 dp / 40 dp**.
  Artwork and backdrops run edge to edge; text and controls stay inside.
- Grid gutter 40 pt → 20 dp. Tiles in a row share one size. Partly visible
  tiles at the right edge show that a row scrolls.
- Dark appearance only (tvOS media apps may be dark-only). Black background,
  generous full-bleed artwork, a bottom-up and left-to-right darkening scrim
  so text stays legible.

**Type** (tvOS text styles → dp/sp). Font: **Inter** (bundled; SF Pro may not
be used on Android), tabular figures for times, durations and channel numbers.

| Style | tvOS pt | sp | Weight |
|---|---|---|---|
| Title 1 | 76 | 38 | Medium; hero titles bolder and tighter, as in the mockup |
| Title 2 | 57 | 28.5 | Medium |
| Title 3 | 48 | 24 | Medium |
| Headline | 38 | 19 | Medium |
| Callout | 31 | 15.5 | Medium |
| Body | 29 | 14.5 | Medium |
| Caption 1 | 25 | 12.5 | Medium |
| Caption 2 | 23 | 11.5 | Medium (minimum size anywhere) |

**Colour** (from `apple-hig-web/tokens.css`): primary text white;
secondary `rgba(235,235,245,0.6–0.68)`; tertiary `0.3–0.42`; fills
`rgba(255,255,255,0.14 / 0.22)`; live red `#FF4245`; accent blue `#0091FF`
(used sparingly, e.g. a "new" badge, the text caret). Focused buttons are white
with black text, as on Apple TV.

**Materials:** the tab bar, player overlays, banners and channel strip are
"glass": translucent dark fill plus blur (`RenderEffect` on Android 12+). On
devices without blur (the Fire TV's Android 11 base), fall back to a darker,
nearly opaque fill. Never put glass in the content layer.

**Focus is the interface** (tvOS focus and selection):
- The focused tile scales to about 1.08–1.1, lifts with a deep soft shadow, and
  gets a soft specular sheen from the top; it briefly leans 3–4° in the
  direction focus moved, then settles. Unfocused tiles stay calm.
- Buttons and tab items turn white with black text, scale about 1.06–1.08.
- Five states per focusable item: normal, focused, pressed (instant shrink to
  0.96 on press), selected (e.g. filled heart, current tab), unavailable.
- All focus motion uses springs (damping ≈ 0.8–1.0, response ≈ 0.3–0.4 s) and
  is interruptible when a direction is held. Respect the system's
  remove-animations setting.
- Each row remembers its last focused item; entering the row again returns there.
  At the end of a row, give a small "bump" rather than wrapping.
- Never move focus by itself, except when content the user acted on changes.
- Titles under posters appear only for the focused poster (tvOS posters).

**Remote behaviour** (tvOS remotes guidance, adapted to D-pad remotes):
- Select activates. **Back always goes to the parent screen**; on a top-level
  tab, Back first moves focus to the tab bar, and a second Back leaves the app.
- Play/Pause always plays and pauses media.
- **Live TV:** while watching full screen, D-pad up/down changes channel (the
  Fire TV remote has no channel keys); Channel Up/Down keys also work where they
  exist; Select or left/right opens the channel strip; up or Back closes it.
  Channel changes show the info banner instantly while the stream loads.
- **VOD:** left/right while the transport bar is focused seeks ±10 s (hold to
  scrub faster); rewind/fast-forward media keys seek too.
- A Guide/EPG key, where present, opens the Guide; in the Guide, page keys page.

**Writing:** buttons and segments in Title Case ("Start Over", "Watch Live"),
sentence case for descriptions and messages, errors say how to fix the problem.
German copy as in the mockup (Start, Serien, Filme, Live-TV, Programm, Suchen,
Einstellungen, Fortsetzen, Von vorne, Folgen, Weiterschauen, Staffel, Folge…).

## 5. Screens and behaviour

The app opens on **Home showing the series the owner watched last**, not on
Live TV. Follow the mockup for each screen.

1. **First launch / Add Account.** Server address, username, password. Typing
   with a remote is slow, so also show a QR code: the app runs a tiny local
   HTTP server (e.g. Ktor or NanoHTTPD, LAN only, one-time token in the URL)
   serving a form the owner fills in on their phone. Validate by logging in;
   show specific errors (wrong password, account expired, server unreachable).
   Store credentials encrypted (Android Keystore). Then import the catalogue
   with visible progress, and start the guide import in the background.
2. **Tab bar** (glass capsule at the top, centred): Home · Series · Movies ·
   Live TV · Guide · Search (icon) · Settings (icon). It slides away when focus
   moves down into content and returns when focus comes back to the top.
3. **Home.**
   - Full-bleed hero for the most recently watched series: eyebrow "Continue
     Watching", the series title, "Season 2, Episode 5: Title", a progress bar
     with minutes left, and **Resume** (focused on launch, one press plays),
     **Start Over**, **Episodes**. If the last episode was finished, the hero
     offers the next episode instead. If nothing has been watched yet, feature a
     recently added series with Play.
   - Rows: Continue Watching (other series and films in progress), Up Next for
     the hero series, Favourite Channels (live tiles), New Episodes, Recently
     Added Movies.
4. **Series** tab: rows of 2:3 posters (Your Series, Recently Updated, then the
   provider's categories). **Series detail**: full-bleed backdrop, title,
   year · genre · seasons · age rating, plot, Resume S2 E5 / Play, Start Over,
   Favourite (heart); a season segmented control; a row of episode tiles with
   watched ticks and progress.
5. **Movies** tab and **movie detail**: the same patterns for films.
6. **Player (VOD).** Opens straight into full screen. Resumes automatically at
   the saved position, without asking. If loading takes more than 2 s, show a
   black screen with a centred spinner only. A glass transport overlay appears
   on any key and hides after about 4 s: title, "Season 2, Episode 5: Title",
   a scrubber with elapsed/remaining time, buttons for Subtitles, Audio and Next
   Episode. Near the end of an episode, offer the next one. Save progress every
   few seconds and on exit. Back hides the overlay, a second Back returns to the
   series or movie detail (a contextually relevant screen). Keep the screen awake.
7. **Live TV** tab: a hero playing or showing the last channel with Watch Live
   and Guide buttons; rows for Favourites and each category. Every channel tile
   shows the logo, a red LIVE badge, the current programme and its progress.
8. **Live player.** Instant info banner on every channel change (number, logo,
   now, next, progress). Channel strip (content footer) over the darkened bottom
   of the picture, using the same categories as the guide, the current channel
   marked. Long-press Select on a tile adds or removes a favourite.
9. **Guide.** Time across the top in 30-minute steps, channels down the side
   (number, logo, name), programme cells sized by duration, a red "now" line,
   the current programme highlighted. Top area: a small live preview of the
   current channel and details of the focused programme. Opens on "now" for the
   current channel. Selecting a current programme tunes to it. Page through
   channels and time quickly. Favourites as a group.
10. **Search.** Apple-style single-row keyboard (a–z, space, delete), results
    updating as you type across series, movies and channels; voice search via the
    remote where the platform supports it.
11. **Settings.** Account (server, status, expiry date), Language (System,
    English, Deutsch), Guide refresh (time of day), Parental lock (PIN per
    category), Player (preferred audio and subtitle language, buffer size,
    live stream format TS or HLS), About (version, licences).

**Also:** favourites for series, movies and channels; restore the last screen
on relaunch; handle no network and expired accounts with an in-app message (no
alert at launch); never block the UI while loading — show cached data first.

## 6. Xtream Codes API

All calls are `GET {base}/player_api.php?username=U&password=P[&action=…]`.

| Purpose | Action / URL |
|---|---|
| Login and account info | no action → `{user_info:{auth, status, exp_date, max_connections, active_cons}, server_info:{timezone,…}}` |
| Categories | `get_live_categories`, `get_vod_categories`, `get_series_categories` |
| Live channels | `get_live_streams[&category_id=]` → `num, name, stream_id, stream_icon, epg_channel_id, category_id, tv_archive, tv_archive_duration` |
| Movies | `get_vod_streams`; details `get_vod_info&vod_id=` → `info{plot, cast, director, genre, releasedate, duration_secs, backdrop_path[]}, movie_data{container_extension}` |
| Series | `get_series`; details `get_series_info&series_id=` → `info{…}, episodes{"1":[{id, episode_num, title, container_extension, info{duration_secs, plot, movie_image}, season}]}` |
| Now/next | `get_short_epg&stream_id=&limit=` (titles and descriptions base64-encoded) |
| Full guide | `{base}/xmltv.php?username=U&password=P` (XMLTV, often 50–200 MB) |
| Stream URLs | `{base}/live/U/P/{stream_id}.ts` (or `.m3u8`), `{base}/movie/U/P/{id}.{ext}`, `{base}/series/U/P/{episode_id}.{ext}` |

**Quirks to handle** (the draft already does most of this): numbers arrive as
strings or numbers, empty strings and `null` mean "missing", lists sometimes
arrive as objects keyed by index, `episodes` is keyed by season, ratings may use
a decimal comma, episode titles often repeat the series name and "S02E05",
credentials must be URL-escaped in paths, and `auth: 0` means a wrong login.
Parse the XMLTV guide as a stream (XmlPullParser), keep only the next 2–3 days
and the channels the account has, store it in Room, and refresh it daily in a
WorkManager job. Map guide channels by `epg_channel_id`. Respect the account's
max connections (one stream at a time; stop the preview stream before full
screen playback).

## 7. Quality bar

- **Performance on the Fire TV stick:** cold start to an interactive Home in
  under 2 s with cached data; 60 fps focus movement in rows; channel change to
  first frame in about 1–2 s on a good stream; no memory leaks when browsing
  thousands of channels and titles (lazy rows and grids, paged queries, image
  downsampling). Use Baseline Profiles.
- **Accessibility:** TalkBack labels on every focusable item, content
  descriptions for icons, system font scaling must not break layouts, never rely
  on colour alone (the LIVE badge has text).
- **Robustness:** no crash on bad server data; offline mode with cached
  catalogue; clear error states with a Retry action.
- **Tests:** JVM unit tests for everything in `core` (API parsing, guide
  parsing, continue-watching rules), plus a few Compose UI tests for focus and
  navigation (Back behaviour, row focus memory, Home opening on Resume).
- **Privacy:** credentials only in encrypted storage; never logged; no analytics.

## 8. Build, CI and delivery

- Gradle wrapper, version catalog, Kotlin DSL. `./gradlew test` and
  `./gradlew assembleDebug` must pass from a clean checkout.
- **GitHub Actions** workflow on every push: run unit tests, build the debug APK
  and a release APK signed with a keystore stored in repository secrets
  (document the secret names and how to create the keystore; fall back to
  debug signing when the secrets are absent). Upload both APKs as workflow
  artifacts; on a `v*` tag, attach them to a GitHub Release.
- Write `README.md` with: what the app is, screenshots, how to build, and
  **step-by-step sideloading** for both devices:
  - Fire TV: enable Developer options and Install unknown apps (Downloader app),
    or `adb connect <ip>:5555` then `adb install -r app-release.apk`.
  - Google TV (XGIMI): enable Developer options and USB/network debugging,
    then `adb connect` and `adb install`, or a file-transfer app.
- Include an Android TV banner (320 × 180 dp) and launcher icons, and declare
  the `LEANBACK_LAUNCHER` category, `android.software.leanback` (required=false),
  touchscreen not required.

## 9. Milestones (commit and push after each)

1. Project skeleton: Gradle setup, `core` from the draft with passing tests,
   empty `app` that installs and shows a black screen with the tab bar; CI green.
2. Design tokens, focus system and components (tab bar, buttons, poster,
   episode and channel tiles, progress, badges, segmented control, glass) on a
   sample-data Home that matches the mockup.
3. Account setup (form and QR), catalogue import, Room cache, real Home, Series,
   Movies and detail screens.
4. VOD player with resume, transport overlay, audio/subtitle selection, next episode.
5. Live TV tab, live player, info banner, channel strip, favourites.
6. Guide import worker and the Guide screen.
7. Search, Settings (language, parental lock, player options), Watch Next
   integration, relaunch restore.
8. Polish: performance on the Fire TV stick, Baseline Profiles, accessibility
   pass, German copy review, README with screenshots, a tagged release.

## 10. Definition of done

- The owner can sideload one APK on both devices, log in with Xtream
  credentials (typed or via the phone QR form), and land on Home showing the
  series they watched last, ready to resume with one press.
- Series, movies, live TV and the guide all work with the remote as described,
  in English and German.
- The app looks and moves like the mockup and follows the rules in section 4.
- CI is green, tests pass, and the README explains building and installing.

When a requirement here conflicts with what the platform allows, choose the
closest behaviour that keeps the Apple TV feel, note the decision in
`docs/DECISIONS.md`, and continue rather than stopping to ask.
