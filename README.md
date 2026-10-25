# Calorie Tracker

A private, offline-first Android calorie tracker for you and a few friends. Log a meal by search, a typed or dictated sentence, a barcode scan, or a photo of a nutrition label. Every calorie number comes from a food database or the label itself, never from a language model's guess.

No accounts, no cloud, no analytics. Everything you log stays on your phone.

## Install

1. Open the [Releases](../../releases) page on your phone and download the latest `calorie-tracker-*.apk`.
2. Open the file. Android will ask you to allow installs from your browser or file manager; allow it once.
3. Updates are not automatic. Watch the releases page, or point an updater such as [Obtainium](https://github.com/ImranR98/Obtainium) at this repo.

Requires Android 8.0 (API 26) or newer.

## Get a USDA API key (one minute)

Food search uses USDA FoodData Central. The shared demo key allows only about 10 lookups an hour, so each person should use their own free key:

1. Go to <https://fdc.nal.usda.gov/api-key-signup> and sign up with your email.
2. Paste the key into **Settings > USDA API key**.

Foods you have used are cached on the phone, so repeat foods need no network.

## What it does

- **Log** by search, barcode scan, or label photo, or **Build a meal** from foods you already have (for example a chicken sandwich from chicken plus a bun). A built meal shows as one row with its total; tap it to see each food and its nutrition.
- **Day view** with a running total against your target and collapsible meal sections. Edit or delete any entry, move an entry or a built meal to the day before or after, or tap + on a past day to log food to it.
- **Reminders** that look at what you have logged when they fire ("430 kcal left to reach today's target").
- **Backup** to JSON (and CSV export of the log) from Settings. Android's own Google Drive backup is turned off, so this export is the only way data leaves the phone; use it to move to a new phone.

Phones differ in how aggressively they stop background apps. If reminders go quiet, follow the battery guidance in Settings (see also <https://dontkillmyapp.com>).

This app is a tracker, not medical advice. Choose a calorie target that suits you.

## Data and attribution

- USDA FoodData Central data is public domain.
- Barcode lookups use [Open Food Facts](https://world.openfoodfacts.org). Its data is available under the [Open Database License](https://opendatacommons.org/licenses/odbl/1-0/); thanks to its contributors.

## Building

```
./gradlew testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest   # needs a running emulator or device
```

The Gradle daemon uses JDK 25 (see `gradle/gradle-daemon-jvm.properties`).

### Releasing

Create a keystore once and keep it, and its passwords, out of the repo. Losing it means friends must uninstall (and lose their data) before they can update.

Add these repository secrets: `RELEASE_KEYSTORE_BASE64` (the keystore, base64-encoded), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Then push a tag:

```
git tag v1.0.0 && git push origin v1.0.0
```

The workflow builds the signed APK and attaches it to a GitHub release. A local signed build works the same way by setting those variables plus `RELEASE_STORE_FILE`.

## License

[MIT](LICENSE)
