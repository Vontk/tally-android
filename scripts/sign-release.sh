#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
android_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/sdk}}"
signing_dir="${TALLY_SIGNING_DIR:-$HOME/.local/share/tally/signing}"
build_tools="$android_sdk/build-tools/36.1.0"
unsigned_apk="$project_dir/app/build/outputs/apk/release/app-release-unsigned.apk"
output_dir="$project_dir/artifacts"
if [[ ! -f "$signing_dir/release.jks" || ! -f "$signing_dir/password" ]]; then
  echo "Release keystore and password file are required in TALLY_SIGNING_DIR." >&2
  exit 1
fi
mkdir -p "$output_dir"
"$build_tools/zipalign" -f -P 16 4 "$unsigned_apk" "$output_dir/tally-aligned.apk"
"$build_tools/apksigner" sign --ks "$signing_dir/release.jks" --ks-key-alias tally --ks-pass "file:$signing_dir/password" --out "$output_dir/tally-1.0.0.apk" "$output_dir/tally-aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$output_dir/tally-1.0.0.apk"
rm "$output_dir/tally-aligned.apk"
(cd "$output_dir" && sha256sum tally-1.0.0.apk > SHA256SUMS)
