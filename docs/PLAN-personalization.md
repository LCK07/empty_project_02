# Plan: profiles, favourites, watchlist, one Live TV tab, language filter

Status: approved 2026-10-16 (provider tags are prefixes like `DE | Action`; My List replaces the Guide tab; "Who's watching?" at launch with 2+ profiles).

## What exists today

| Area | Today | File |
|---|---|---|
| Tabs | Home · Series · Movies · Live TV · Guide · Search · Settings (7) | `ui/Navigation.kt` (`TopTab`), `ui/LumenRoot.kt` |
| Favourites | Room table `favourite(kind, itemId)` for series, movies, channels; heart on detail screens, long-press on channel tiles; "Favourites" row first on Live TV | `data/db/Entities.kt`, `data/LibraryRepository.kt`, `ui/live/LiveTab.kt` |
| Progress / watched | Room table `progress(key)`; "watched" = progress finished; Continue Watching + Up Next built from it; mirrored to Google TV Watch Next | `core/library/WatchProgress.kt`, `tv/WatchNextPublisher.kt` |
| Watchlist | none | – |
| Profiles | none; no login besides the Xtream account | – |
| Settings | DataStore, device-wide | `data/Settings.kt` |
| Language metadata | **none in Xtream list endpoints.** Only category names and titles, which providers usually tag (`DE | Action`, `EN - …`, `[FR]`, `GERMANY`) | `core/xtream/XtreamClient.kt` |
| DB migrations | schema v1, `fallbackToDestructiveMigration` (a schema change would wipe progress and favourites) | `data/db/LumenDatabase.kt` |

"Programm" (Guide) and "Live-TV" are the two tabs the brief asks to merge.

## Design

### Data layer (workstream 1, first)
- Room v2 with an explicit `Migration(1, 2)` (no destructive fallback for v1 → v2):
  - new `profile(id, name, avatar, contentLanguages, createdAt)`; migration inserts profile 1 "Me" (localised).
  - `favourite` and `progress` get `profileId` (part of the primary key); existing rows move to profile 1.
  - new `watchlist(profileId, kind, itemId, addedAt)`.
  - `category.language` and `movie.language` / `series.language` (nullable ISO code), filled by the import
    and by a one-off tagging pass after the upgrade (no re-import needed).
- `ProfileRepository`: list/create/rename/delete/select; active id in DataStore; deleting a profile deletes only
  its own rows; the last profile cannot be deleted.
- `LibraryRepository` reads the active profile (`flatMapLatest`), so every screen updates on a switch.
- Watch Next on the Google TV home screen shows the active profile only (cleared and republished on switch).
- Device-wide stays device-wide: Xtream account, parental PIN and locked categories, player settings, app language.

### Language filter (workstream 4)
- `core/LanguageTags.kt`: pure function `tag(categoryName, title) -> "de" | "en" | "fr" | … | null`,
  recognising provider prefixes/suffixes (`DE`, `GER`, `GERMAN`, `DEUTSCH`, `EN`, `ENG`, `UK`, `US`, `FR`, …)
  as whole tokens only — never guessing from the words of a title. Category tag first, title tag overrides.
- Filter options per profile: All · German · English · German + English (default for new and migrated profiles).
- Untagged titles are always shown; Settings says so. Applies to Home rows, Series, Movies, category pages,
  Search. Never to Live TV.

### Favourites and Watchlist (workstream 2)
- Heart (favourite) and "+ Watchlist" on series/movie detail; long-press menu on posters (add/remove both).
- Home: "Favourites" row right after the hero/Continue Watching, then "Watchlist", then the rest.
- New tab **My List** (replaces the Guide tab, so the count stays at 7): segmented control
  Favourites · Watchlist · Continue Watching, movies and series labelled, empty states.

### One Live TV tab (workstream 3)
- Remove the Guide tab. Live TV keeps hero (now playing) → Favourite channels → categories, and gains a
  **Guide** button/row that opens the guide as a full screen on top. The guide opens on a "Favourites"
  group when there are favourite channels. The remote's Guide key opens it directly.

### Profiles UI (workstream 2/5)
- Profile avatar (initial in a coloured circle) at the right end of the tab bar → profile switcher;
  Settings → Profiles: create, rename, pick colour, delete.
- "Who's watching?" screen at launch when there are 2+ profiles.
- Wording: "Profiles keep favourites and progress apart. They are not password-protected."

## Workstreams and order
1. Data layer + migration + tests (Robolectric migration test with a v1 database). **Gate for the rest.**
2. Favourites/Watchlist/My List/Home rows + profile switcher UI.
3. Live TV + Guide merge.
4. Language tagging + filter (core unit tests on real-world category names).
5. Integration: emulator run with two profiles, restart, upgrade from v1.0.0 with existing data; README/DECISIONS.

One Sonnet agent at a time on overlapping files (1 → 2 → 3); 4's core part can run in parallel.

## Acceptance criteria that depend on metadata
- Language filtering can only be as good as the provider's tags. Titles in untagged categories are shown
  (counted on the filter row, e.g. "312 without a language tag").
