# Release verification — 1.0.0

Verified locally on 2026-10-10. The final release is compiled against Android 17 SDK 37.2, targets API 37, and supports API 26 onward.

## Results

| Check | Result |
| --- | --- |
| Calendar/streak/validation unit tests | 6 passed |
| Android lint | No issues found |
| Debug APK, optimized release APK, and app bundle compilation | Passed |
| Android 8 / API 26 database and Compose UI tests | 4 passed |
| Signed release APK installation and launch on API 26 | Passed |
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
