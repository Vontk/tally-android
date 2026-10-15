#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
android_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/sdk}}"
signing_dir="${TALLY_SIGNING_DIR:-$HOME/.local/share/tally/signing}"
build_tools="$android_sdk/build-tools/36.1.0"
unsigned_apk="${TALLY_UNSIGNED_APK:-$project_dir/app/build/outputs/apk/release/app-release-unsigned.apk}"
output_dir="$project_dir/artifacts"
if [[ ! -f "$signing_dir/release.jks" || ! -f "$signing_dir/password" ]]; then
  echo "Release keystore and password file are required in TALLY_SIGNING_DIR." >&2
  exit 1
fi
if [[ ! -f "$unsigned_apk" ]]; then
  echo "Build the release APK first, or set TALLY_UNSIGNED_APK to the downloaded unsigned APK." >&2
  exit 1
fi
mkdir -p "$output_dir"
"$build_tools/zipalign" -f -P 16 4 "$unsigned_apk" "$output_dir/tally-aligned.apk"
"$build_tools/apksigner" sign --ks "$signing_dir/release.jks" --ks-key-alias tally --ks-pass "file:$signing_dir/password" --out "$output_dir/tally-1.2.0.apk" "$output_dir/tally-aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$output_dir/tally-1.2.0.apk"
# Anchor updates to the certificate from the published 1.1.2 APK.
expected_signer="e46414d0062502c4dc012368960579dc9506ad3528fa16abc980edc8183ec1c0"
actual_signer="$("$build_tools/apksigner" verify --print-certs "$output_dir/tally-1.2.0.apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
if [[ "$actual_signer" != "$expected_signer" ]]; then
  rm -f "$output_dir/tally-1.2.0.apk"
  echo "Signing certificate does not match the published app; refusing an incompatible update." >&2
  exit 1
fi
"$build_tools/zipalign" -c -P 16 4 "$output_dir/tally-1.2.0.apk"
rm "$output_dir/tally-aligned.apk"
(cd "$output_dir" && sha256sum tally-1.2.0.apk > SHA256SUMS)
