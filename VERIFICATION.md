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
