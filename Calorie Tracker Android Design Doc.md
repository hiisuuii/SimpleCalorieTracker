# Calorie Tracker: Android Design Doc

Oct 24, 2026 · @haven

## Overview

A private Android calorie tracker, built with Kotlin and Jetpack Compose, that turns a barcode scan, a label photo, a typed item or a spoken sentence like "chicken breast with rice and broccoli" into a calorie count. It is for you and a few friends, shared through GitHub rather than an app store.

**Goals**

- Log a meal in under 15 seconds by any of four input methods.
- Take every calorie number from a database or the label itself, never from an LLM's guess.
- Keep a permanent day-by-day history and show progress against a daily calorie target.
- Send configurable reminders ("430 kcal left to hit your target") tied to your usual meal times.
- Work offline for everything except looking up new foods.

**Non-goals**

- Detailed macro or micronutrient tracking (macros are stored when available, but not a focus).
- iOS, web, accounts, cloud sync or social features.
- Play Store release, ads or monetization.
- Retention settings: all data is kept forever (a year of logs is about 2 MB).

## Features

Version 1 covers six areas. Items marked *later* ship after the core loop works.

| Area | What it does | Phase |
| --- | --- | --- |
| Manual entry | Search foods, pick a portion, log it; quick-add a raw calorie number; create custom foods and recipes | 1 |
| Day log and target | Per-day list of entries grouped by meal, running total vs. daily target, edit and delete any entry | 1 |
| Favorites and recents | One-tap re-logging of common meals and foods | 1 |
| Typed or dictated sentence | Free text split into items, clarifying questions for missing quantities, USDA match | 2 |
| Barcode scan | CameraX plus ML Kit barcode scanning, Open Food Facts lookup | 3 |
| Reminders | Lunch, evening and custom nudges using today's running total | 4 |
| Label scan | Photo of a nutrition facts panel, on-device OCR, parsed into a custom food | 5 |
| On-device LLM parsing | Optional upgrade for the sentence parser on capable phones | later |
| History browser | Calendar or list of past days with totals; CSV/JSON export and import | 1 (basic), 4 (export) |

**Settings**

- Daily calorie target and units (g/oz, kcal).
- Day rollover hour (default 4 am, so a 12:30 am snack counts toward the previous day).
- Typical breakfast, lunch and dinner times, used to tag meals and schedule reminders.
- Reminder times and on/off per reminder.
- USDA API key (each user pastes their own free key, see Distribution).

## Architecture

The app is three layers (UI, logic, data) with camera input and the reminder worker attached at the edges. Only the logic layer and the databases ever decide a calorie number.

&#91;embedded content: app architecture · 4 layers\]

Scans and photos enter through the camera layer, the logic layer turns them into foods and amounts, and repositories are the only code that touches the database, settings or network.

**Layers**

- **UI:** Compose screens, each with one `ViewModel` that exposes a `StateFlow`. No business rules here.
- **Logic:** plain Kotlin with no Android imports, so it is fast to unit test: parser, matcher, portion conversion, label parser, day-boundary and reminder rules.
- **Data:** repositories wrap Room, DataStore and the two HTTP APIs. The day screen and the reminder worker read the same daily-total query.
- **Platform edges:** CameraX and ML Kit return plain values (a barcode string, OCR text) that go straight to the logic layer. The worker is injected with the same repositories through Hilt.
- **Modules:** one Gradle module at first, split into packages `ui`, `domain`, `data` and `platform`. Split into modules only if build times become a problem.

**Path of a typed sentence**

1. Parse the text into items with quantities and states.
2. Ask only about what is missing (amount, raw or cooked).
3. Match each item: alias, then local foods, then USDA.
4. Convert the amount to grams and scale the per-100 g calories.
5. Show the confirmation screen; you can change the match or the amount.
6. Save the entry with a snapshot of its calories and update the day total.

## Tech stack and dependencies

Standard Jetpack plus a short list of well-supported libraries. Pin versions in a Gradle version catalog (`libs.versions.toml`) and use the latest stable release of each when you start.

| Concern | Choice | Why |
| --- | --- | --- |
| Language, UI | Kotlin, Jetpack Compose with Material 3, Navigation Compose | Your stated stack |
| Async | Kotlin coroutines and Flow | Room and DataStore already expose Flow; the UI observes state directly |
| Architecture | MVVM with a repository layer, `StateFlow` UI state in `ViewModel`s | Standard, testable, minimal ceremony |
| Dependency injection | Hilt | Official, works with ViewModel and WorkManager out of the box (Koin is fine if you prefer less annotation processing) |
| Database | Room (with KSP) | Typed SQLite, Flow queries, migrations, in-memory testing |
| Settings | Jetpack DataStore (Preferences) | Target, rollover hour, meal times, reminder config, API key |
| Background work | WorkManager | Reminders that read today's total at fire time; survives reboots |
| Networking | Retrofit, OkHttp, kotlinx.serialization | USDA and Open Food Facts are plain JSON REST APIs; OkHttp adds an on-disk response cache |
| Camera | CameraX | Preview and image capture for label photos |
| Barcode | ML Kit Barcode Scanning (bundled model), or the ML Kit code scanner | The code scanner gives a ready-made scan screen with no camera permission; use CameraX analysis only if you want a custom UI |
| Label OCR | ML Kit Text Recognition (Latin, bundled) | On-device, free, works offline; works on every phone you listed |
| Permissions | Standard Activity Result API: `ActivityResultContracts` | Camera (label scan) and POST\_NOTIFICATIONS (Android 13+) |
| Images | Coil (optional) | Only if you show Open Food Facts product thumbnails |
| Later: local LLM | MediaPipe LLM Inference or llama.cpp | Optional natural-language upgrade, one interface so it can be swapped in |

**Build settings**

- minSdk 26, targetSdk current. Pixel 6 and Galaxy S24 are both well above this.
- Release builds with R8 minification and a debug/release split so you can sideload test builds.
- No Google Play Services dependency beyond ML Kit; no Firebase, no analytics, no crash reporter.

## Data model

Four Room tables hold everything. A food row doubles as the API cache and as your custom foods, and each log entry copies its calories at log time so history never changes retroactively.

| Table | Key columns | Notes |
| --- | --- | --- |
| `log_entry` | id, logged\_at (UTC ms), tz\_id, log\_date (ISO date), meal, name, grams, kcal, protein\_g, carbs\_g, fat\_g, source, food\_id (nullable) | Calories and macros are a snapshot. `source` is manual, text, barcode, label or quick-add. Index on `log_date`. |
| `food` | id, name, brand, source (usda, off, custom), external\_id, barcode (indexed), kcal\_per\_100g, protein, carbs, fat per 100g, is\_favorite, last\_used\_at | Every food ever fetched or created. Search checks here first, so repeat foods need no network. |
| `portion` | food\_id, label ("1 cup", "1 medium breast"), grams | USDA household measures and custom serving sizes. |
| `alias` | phrase (normalized, unique), food\_id, default\_grams, default\_portion\_id | Your shortcuts: "my rice" resolves instantly to one food and one amount. |

Saved meals (the "usual breakfast" favorites) are two small extra tables, `saved_meal` and `saved_meal_item`, each item pointing at a food and a gram amount. Settings live in DataStore, not Room.

**Decisions**

- **Snapshot nutrition per entry.** `food_id` is kept only so you can tap through to the source food. Editing or refreshing a food never rewrites past days.
- **Day boundary.** `log_date` is computed at write time as the local date of `logged_at` minus the rollover hour (default 4 am), using the stored `tz_id`. Changing the rollover setting affects new entries only, so old days stay stable.
- **Per-100 g storage.** Everything converts to grams first, then scales by `kcal_per_100g / 100`. Quick-add calories bypass this: a `log_entry` with no `food_id` and `grams` null.
- **Daily total** is a single `SUM(kcal) GROUP BY log_date` query exposed as a Flow, so the UI and the reminder worker read the same number.
- **Migrations.** Export Room schemas to the repo and write a migration for every version bump, since friends will install updates over existing data.
- **Size.** At about 10 entries a day, a year is roughly 2 MB. No pruning job is needed.

## Food data sources and matching

Use two free databases and cache everything you fetch, so the app gets faster and more offline-capable the longer you use it.

| Source | Best for | Access | Caveats |
| --- | --- | --- | --- |
| [USDA FoodData Central](https://fdc.nal.usda.gov/api-guide) | Generic foods and ingredients (Foundation, SR Legacy), prepared dishes (Survey/FNDDS), packaged foods (Branded, searchable by UPC) | Free REST API, key from api.data.gov, public domain data | Default limit has been about 1,000 requests/hour per key (confirm in the API guide); each user needs their own key |
| [Open Food Facts](https://world.openfoodfacts.org/data) | Barcode lookups worldwide | Free REST API, no key, send a descriptive User-Agent | Crowdsourced, so some products lack calories or have wrong units; validate before logging |
| Custom foods | Homemade recipes, restaurant items, label scans | Local only | You are the data source; verify numbers once |

**Barcode lookup order**

1. Local `food` table by `barcode`.
2. Open Food Facts by barcode.
3. USDA Branded search by UPC.
4. Not found: offer a label scan or manual entry, then save the result against that barcode so the next scan is instant.

**Text matching order** (for each parsed item)

1. `alias` table: an exact or normalized-phrase hit resolves with no questions.
2. Local `food` search (SQLite FTS) over cached and custom foods, boosted by recency and favorites.
3. USDA search restricted to Foundation, SR Legacy and Survey, returning the top 5.
4. Rank candidates by word overlap, matching cooking state (raw, cooked, roasted) and data-type preference; if the top score is clearly ahead, pre-select it, otherwise show the top 3 as tappable chips.
5. When you confirm a match, save the alias and your quantity so the same phrase never needs matching again.

**Accuracy rules**

- **Raw vs. cooked** is the biggest error source (dry rice is about 360 kcal per 100 g, cooked about 130). Always carry a `state` field and ask when the item is a grain, pasta, meat or legume and the state is missing.
- **Unit conversion** uses the food's USDA portions ("1 cup", "1 medium") first, then a small built-in table for generic units (tsp, tbsp, cup, oz, lb, g). An unknown unit triggers a question rather than a guess.
- **Label and barcode data beat database data.** If a package gives calories per serving, use that over any generic match.

## Input methods

All four methods end the same way: a confirmation screen showing the matched food, amount and calories, which you can correct before saving.

**Manual entry**

Search box over local foods first, then USDA on demand. Pick a portion or type grams. Recents and favorites show before you type. A "quick add" button logs a bare calorie number with a label.

**Typed or dictated sentence**

The keyboard's built-in dictation fills the same text box, so there is no separate voice feature. A `MealParser` interface keeps the parser swappable:

```kotlin
data class ParsedItem(
    val name: String,
    val state: String?,      // raw, cooked, grilled...
    val quantity: Double?,
    val unit: String?,       // g, cup, slice, piece...
)

interface MealParser {
    suspend fun parse(text: String): List<ParsedItem>
}
```

- **`RuleBasedParser` (ships first):** splits on commas, "and", "with" and "plus"; reads leading numbers, fractions and words like "a", "two", "half"; recognizes unit words and cooking-state words; treats the rest as the food name.
- **Clarifying questions** come from fixed templates, driven by what is missing: "How much rice?" with gram and cup chips, "Cooked or raw?", "How many chicken breasts?". Answers are applied, then matching runs.
- **`LlmParser` (later, optional):** same interface, implemented with an on-device model that returns structured JSON. The app picks it only on phones that can run it and falls back to the rule-based parser on any failure. The LLM never outputs calories.

**Barcode scan**

The ML Kit code scanner returns EAN/UPC strings. Normalize them first (a 12-digit UPC-A and its 13-digit EAN form with a leading zero are the same product). Look up by the order in the previous section, then ask for servings eaten, since barcode data comes per serving or per 100 g.

**Nutrition label scan**

CameraX captures a photo, ML Kit Text Recognition runs on-device, and a parser pulls out serving size (including the gram weight in parentheses), calories, and optionally fat, carbs and protein. Rules that keep it trustworthy:

- Always show the extracted numbers in editable fields next to the photo before saving; never log straight from OCR.
- Take the "per serving" column when a panel shows both per serving and per container.
- Sanity check: calories should be within about 20% of 4 × protein + 4 × carbs + 9 × fat. Warn if not, since that usually means a misread digit.
- Save the result as a custom food (linked to the barcode if one was scanned), then discard the photo.

## Reminders and background work

A reminder computes its message when it fires, not when it is scheduled, so the "calories left" number always reflects what you have logged so far.

**How it works**

1. Each enabled reminder (for example "midday check" and "evening check") is stored as a time of day in DataStore, defaulting from your lunch and dinner times.
2. Scheduling code enqueues a one-time `WorkManager` job for the next occurrence of each reminder, and re-enqueues the following day's job at the end of every run. This avoids drift, and rescheduling on boot or app update is automatic with WorkManager.
3. When the worker runs, it reads today's total and target through the same repository the UI uses, picks a message by rules, and posts a notification. If you are on target or already logged enough, it posts nothing.
4. Tapping the notification opens the quick-add screen.

**Message rules** (all thresholds configurable)

- Midday: if logged calories are below a set fraction of the target pro-rated to that time (for example under 30% by 1 pm), nudge: "You've logged 500 kcal. Aim for about 900 by now."
- Evening: if calories remain, say how many: "430 kcal left to reach today's target."
- Over target: no nagging by default; an optional "you're over by X" is off unless you enable it.
- Respect the day rollover hour when deciding what "today" means.

**Pitfalls to design around**

- **Notification permission.** Android 13 and newer need a runtime `POST_NOTIFICATIONS` grant. Ask when the user turns on their first reminder, not at first launch.
- **Exact timing.** WorkManager is not exact and can run a few minutes to an hour late under battery saver. For a "around 8:30 pm" nudge that is fine; avoid exact alarms and their extra permission.
- **Aggressive battery management.** OnePlus and some other brands kill background work for apps they consider idle, so reminders can silently stop. Add a settings screen that detects this, explains it, and links to the app's battery settings (see [dontkillmyapp.com](https://dontkillmyapp.com) for per-brand instructions). Test on your friend's OnePlus early.
- **Notification channel.** Create one channel for reminders so users can silence it from system settings.

## Testing strategy

Put the logic that decides calories in plain Kotlin classes with no Android dependencies, and test those heavily; the UI layer stays thin.

| Layer | What to test | Tools |
| --- | --- | --- |
| Sentence parser | Table of phrases to expected items ("2 eggs and a slice of toast", "half a cup of rice", "chicken, rice, broccoli") | JUnit, parameterized tests |
| Matcher and ranking | Candidate scoring, raw vs. cooked preference, alias hits | JUnit with fixture lists |
| Unit conversion and totals | Portion math, rounding, quick-add, per-100 g scaling | JUnit |
| Day boundary | 11:59 pm, 12:30 am, 3:59 am and 4:00 am entries, time-zone changes, rollover edits | JUnit with a fake clock |
| Label OCR parser | Real label text dumps (two-column panels, "Calories 230", OCR typos); the calorie sanity check | JUnit with saved OCR output |
| API clients | Real USDA and Open Food Facts responses saved as fixtures; missing-calorie products | MockWebServer |
| Database | DAO queries, daily-total SUM, migrations | Room in-memory DB, migration test helper |
| Reminder rules | Pure function from (target, logged, time, settings) to message or nothing | JUnit |
| UI flows | Log a meal, edit an entry, change the target | Compose UI tests with fake repositories |

**Manual checks before each release**

- Run the full flow on a Pixel 6, a Galaxy S24 and the OnePlus: log, scan, reminder fires, reboot, reminder still fires.
- Photograph 15 to 20 real nutrition labels (glare, curved cans, small print) and track how many parse correctly without edits. This number is the real quality bar for label scanning.

## Milestones

Build in this order. Each phase ends with something you can use daily, and each depends only on the ones before it.

| Phase | Deliverable | Done when |
| --- | --- | --- |
| 0. Skeleton | Gradle project, version catalog, Hilt, Room, DataStore, navigation, CI build that produces an APK | The app launches and a trivial entry saves to the database |
| 1. Core log | USDA search and cache, manual entry, portions, quick-add, custom foods, day log, daily target, day rollover, edit and delete, recents and favorites, basic history list | You can replace your current calorie app for a week |
| 2. Sentence entry | `RuleBasedParser`, clarifying questions, matcher with ranking, aliases | "Chicken breast with rice and broccoli" logs correctly in under 15 seconds after the first time |
| 3. Barcodes | Code scanner, Open Food Facts and USDA Branded lookup, save-to-barcode memory | 9 of 10 common grocery items scan to a usable result |
| 4. Reminders and export | WorkManager reminders, settings screen, battery-optimization guidance, CSV/JSON export and import | Reminders fire correctly on all three test phones after a reboot |
| 5. Label scan | CameraX capture, OCR, label parser, confirm screen with sanity check | 80% of your test labels parse with no edits |
| 6. Share | Signed release APK, GitHub release, README with USDA key instructions | A friend installs from the release page and logs a meal unaided |
| Later | `LlmParser` on capable phones, saved-meal editing polish, home-screen widget | Only if you actually want it |

Phases 3 to 5 are independent of each other, so reorder them to match what you miss most.

## Risks and decisions

The biggest risks are data quality and reminder reliability, not code complexity.

| Risk | Effect | Mitigation |
| --- | --- | --- |
| Wrong database match (wrong food, raw vs. cooked) | Calories off by 2x or more | Show the match on every confirmation screen, ask about state, save corrections as aliases |
| Open Food Facts gaps and errors | Missing or implausible calories | Validate against the macro sanity check; fall back to USDA Branded, then label scan |
| OCR misreads digits | Wrong calories saved | Always require the confirm screen; sanity check; show the photo beside the fields |
| OEM battery management kills reminders | Silent missed reminders | In-app detection and guidance, early testing on the OnePlus |
| USDA rate limit or outage | Search fails | Local-first lookups, response caching, clear offline messaging |
| Schema changes break friends' data | Lost history | Exported schemas, migration tests, export/import as backup |
| Calorie target set poorly | Bad guidance for weight goals | Let the user enter any number; show a note that the app is a tracker, not medical advice |

**Decisions**

- **Calorie target:** a typed number. No calculator.
- **Reminders:** same-day nudges only. No weekly summary.
- **History:** each phone is fully independent. No shared or synced data.
- **Macros:** stored when available and shown behind a tap, not on the main day screen.
- **License:** MIT.

## Distribution

Ship a signed APK through GitHub Releases and let each friend sideload it.

- **Signing.** Create one release keystore, keep it and its passwords out of the repo (back them up somewhere safe), and use the same key for every release. Lose it and friends must uninstall, losing their data, before they can update.
- **Releases.** A GitHub Actions workflow builds and attaches the signed APK when you push a version tag. Friends enable "install unknown apps" for their browser or file manager once.
- **Updates.** Sideloaded apps do not update themselves. Friends can watch the releases page by hand, or use an open-source updater such as Obtainium that tracks GitHub releases.
- **USDA API key.** Do not commit a key to a public repo. Each person gets their own free key from api.data.gov (a minute's work) and pastes it into Settings on first launch; the README walks through it. Until then, the app can fall back to the shared `DEMO_KEY`, which has very low limits.
- **Attribution.** USDA data is public domain. Open Food Facts data is under the Open Database License and requires credit, so add an About screen that names it and links back.
- **No tracking.** The app collects nothing; all entries stay on the device, which is worth saying in the README.

* **License.** The repo uses the MIT license; include the license file and the Open Food Facts credit in the README and About screen.
