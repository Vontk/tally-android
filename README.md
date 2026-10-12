# Tally

A small, native Android app for making a moment count. Version 1.1.0 implements the revised phone design supplied in `tally-design-updated.zip`. The original handoff is preserved in [docs/design](docs/design/tally-design-updated.zip).

## Install

Download `tally-1.1.1.apk` from this repository's **Releases** page. Android 8.0 or newer is required. Allow installation from the app used to open the APK when Android prompts you.

## Features

- Multiple subjects, editable names, six accent presets, and custom RGB/hex colors.
- Swipe left for the next subject or right for the previous one on Counter; selection is saved, and each page shows its own totals and records.
- One tap per moment, undo, today/weekly/all-time totals, streaks, and recent moments.
- Daily history, exact-minute timeline, timestamp list, date picker, and monthly heatmaps with a day-list fallback on small screens or large text.
- Confirmed deletion of individual tallies or entire subjects; deleting the final subject leaves an intentional empty state.
- Compact labelled Settings/Edit controls, subject sheets, persistent preview banner, and explicit Dark/Light appearance.
- Dark and light appearance, phone/tablet layouts, haptics, and accessible controls.
- Example data that remains separate from your saved moments.
- Offline storage and JSON backup/restore through Android's document picker.

The app uses native Kotlin and Jetpack Compose screens, Room for records, and DataStore for preferences. It requests no network or storage permissions and includes no accounts, advertising, or analytics. Android automatic backup is disabled; export a backup before uninstalling or clearing app data.

## Build

Use JDK 21, the Android SDK, and the checked-in Gradle wrapper:

```bash
sdkmanager 'platforms;android-37.2' 'build-tools;36.1.0'
# Point Android Studio to your SDK, set ANDROID_HOME, or create local.properties.
./gradlew testDebugUnitTest lint assembleDebug assembleRelease bundleRelease
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.
Unsigned app bundle: `app/build/outputs/bundle/release/app-release.aab`.

Pinned core versions: AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, Compose BOM 2026.09.00, Room 2.8.5, DataStore 1.2.1. Minimum API 26; target API 37; compile SDK 37.2. Release builds enable code and resource shrinking.

## Sign a release

Use the original signing key for every update. The initial key is stored locally in `~/.local/share/tally/signing/`, with restricted file permissions, outside Git. Back up this directory securely: losing the key prevents installing future updates over the existing app.

```bash
./gradlew assembleRelease
# TALLY_SIGNING_DIR can override the default directory.
./scripts/sign-release.sh
```

The directory must contain `release.jks` (alias `tally`) and a `password` file containing its password. The script produces `artifacts/tally-1.1.1.apk` and `artifacts/SHA256SUMS`. Never commit or upload the key/password to a release or artifact.

## Data and backup

Every tally is stored as a UTC timestamp. History groups moments in the device's current timezone, so changing timezone can change which day a moment appears under. Last 7 days means today plus six earlier calendar days. A streak can continue from yesterday until a tally is recorded today. Undo removes the latest inserted record for the selected subject, even after a clock change.

**Settings → Export backup** creates a versioned `tally-android` JSON file containing all subjects, tallies, and preferences. **Restore backup** validates the file, then asks before replacing existing records. Invalid files leave your data unchanged. Restore offers an export-first action. Empty backups are supported when there are no subjects or tallies. Import is limited to 20 MB. Website data is not imported.

## Verification

```bash
./gradlew testDebugUnitTest lint
# With an Android emulator or device connected:
./gradlew connectedDebugAndroidTest
```

Tests cover calendar boundaries, daylight-saving time, timezone changes, streaks, preview data, subject isolation, insertion-order undo, rapid tapping, recreation, backup validation and round trips, and history navigation. GitHub Actions builds unsigned artifacts and runs device tests on API 26. See [VERIFICATION.md](VERIFICATION.md) for local release results.
