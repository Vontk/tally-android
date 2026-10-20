# Release verification — 1.2.1

The counter no longer stretches vertically at its scroll boundaries; scrolling remains available when content exceeds the screen. General buttons, menus and selection surfaces use the neutral light/dark theme instead of inheriting the selected tally's color. The counting circle, tally markers and interpolated header accent retain their chosen color.

No database, backup, tally-record or signing-identity changes.

# Release verification — 1.2.0

Title and color move with each tally page. The header accent interpolates during swipes. Add and edit/reorder controls replace the selector, the color popup offers 20 defaults, and the app and launcher icon default to off-white on charcoal. Example-data controls and the circle's redundant label are removed.

Positive tallies build streaks on days with records. Negative tallies count completed days without records; adding a record breaks the streak and undo recalculates it. Type, creation date and order persist in Room and backups. The version-1 database is migrated without deleting records or custom colors.

## Automated validation

[PR checks and build artifacts](https://github.com/Vontk/tally-android/pull/1/checks) record unit tests, lint, optimized release APK/AAB compilation and Android 8/API 26 device tests. Device coverage includes Room schema migration, tally persistence and undo, backup/restore, isolation, history navigation and persisted negative type/order. Visual testing is excluded by request. Room's Gradle plugin separates variant schema generation to avoid concurrent writes.

## Signing and publication

The original release key is on the owner's home PC and is unavailable in this environment. An installable update requires that key. The signing script checks the resulting APK against the SHA-256 signer certificate from the published 1.1.2 APK:

`e46414d0062502c4dc012368960579dc9506ad3528fa16abc980edc8183ec1c0`

When the key is available, build the release or download the unsigned APK from the release draft/CI artifact, then run:

```bash
TALLY_UNSIGNED_APK=/absolute/path/app-release-unsigned.apk ./scripts/sign-release.sh
```

The default key location is `~/.local/share/tally/signing/release.jks` with its password in the sibling `password` file; `TALLY_SIGNING_DIR` can select another directory. The script verifies signatures, signer identity and 16 KB ZIP alignment, and produces `artifacts/tally-1.2.0.apk` and `SHA256SUMS`. Upload those signed files to the draft release before publishing it as the latest version.

## Historical verification for 1.1.2

# Release verification — 1.1.2

Adaptive launcher icon with a dark background and Android themed-icon support; circular clipping for tally tap feedback. README shortened and native design screenshots included.

Release APK compilation, signing, source revision, archive alignment and published checksum are checked for publication. Automated tests were not rerun. Motorola physical-device appearance has not been checked locally.

## Historical verification for 1.1.1

# Release verification — 1.1.1

Native subject paging update. Final release compilation, APK signatures, source revision, 16 KB archive alignment and uploaded checksum are checked for publication. Automated test suites were not rerun for this update.

## Historical verification for 1.1.0

# Release verification — 1.1.0

Design update dated 2026-10-12, based on the supplied revised handoff. Compiled with Android 17 SDK 37.2, target API 37, minimum API 26. Uses the original signing key and database schema.

This update's release compilation, signing, archive alignment and published checksum are recorded in its GitHub release. Automated unit/device test suites were not rerun for this design update. The existing history/preview test's navigation selectors were adjusted for the new Settings entry point; no new tests were added.

The earlier Android 17 emulator renderer limitation remains unresolved. No physical-device validation is claimed.

## Historical verification for 1.0.1

# Release verification — 1.0.1

Verified locally on 2026-10-10. The final release is compiled against Android 17 SDK 37.2, targets API 37, and supports API 26 onward.

## Results

| Check | Result |
| --- | --- |
| Calendar/streak/validation and color contrast unit tests | 8 passed |
| Android lint | No issues found |
| Debug APK, optimized release APK, and app bundle compilation | Passed |
| Android 8 / API 26 database and Compose UI tests | 4 passed |
| Signed release APK installation and launch on API 26 | Passed |
| Signed 1.0.1 update preserves existing subjects and tallies | Passed |
| Custom black accent in light and dark themes | Visually checked |
| Signed release keyboard counting, tap, undo, theme switching, and subject isolation | Passed |
| Real document-picker export, canceled restore, and confirmed restore | Passed |
| Native Days, Hours, and Month navigation | Passed |
| Phone, 150% font scaling, and tablet sidebar layouts | Visually checked |
| APK v2/v3 signatures | Verified with apksigner |
| APK archive 16 KB alignment | Verified with zipalign |
| ARM64 native-library ELF segment alignment | Both bundled libraries use 16 KB alignment |

The universal signed APK is approximately **1.5 MiB**, below the 10 MB target. The exact size and SHA-256 checksum are attached to the GitHub release. No network permission is requested.

## Android 17 emulator limitation

Both Android 17 base and quarterly emulator images repeatedly crashed their system renderer with `mapper.ranchu / SurfaceFlinger: Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`. Multiple software-renderer settings and three-button navigation were tried. Two instrumentation tests completed before an emulator system crash aborted the full suite. This is recorded as an incomplete Android 17 device-suite result, not a passing full run. Physical-device validation has not been performed.

The app compiles for Android 17; emulator logs did not identify a Tally application exception. API 26 completed the full database/UI suite and signed-APK functional checks.

## Reproduce

```bash
./gradlew testDebugUnitTest lint assembleDebug assembleRelease bundleRelease
# Boot a supported emulator or connect a device, then:
./gradlew connectedDebugAndroidTest
./scripts/sign-release.sh
```

GitHub Actions repeats unit tests, lint, release compilation, and device tests on API 26. Release signing remains local; signing credentials are excluded from Git and CI artifacts.

The workflow is published. Hosted Actions results could not be retrieved with the available account permissions; the results above are local verification.
