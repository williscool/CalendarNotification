#!/usr/bin/env bash
# Signs every .apk and .aab in a directory, writing <name>-signed.apk/.aab
# next to each input. Replaces r0adkll/sign-android-release, which is
# unmaintained (Node 12, deprecated set-output).
#
# Usage: sign_android_artifacts.sh <dir>
# Env:   SIGNING_KEY_BASE64, KEY_ALIAS, KEYSTORE_PASSWORD, KEY_PASSWORD,
#        BUILD_TOOLS_VERSION (ANDROID_HOME is set on GitHub runners)
#
# Passwords are passed to the tools by environment variable name, so they
# never appear on a command line.
set -euo pipefail

dir=$1
build_tools="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION"

keystore=$(mktemp)
trap 'rm -f "$keystore"' EXIT
base64 -d <<< "$SIGNING_KEY_BASE64" > "$keystore"

shopt -s nullglob
apks=("$dir"/*.apk)
aabs=("$dir"/*.aab)
if (( ${#apks[@]} + ${#aabs[@]} == 0 )); then
  echo "::error::No .apk or .aab files in $dir"
  exit 1
fi

for apk in "${apks[@]}"; do
  signed="${apk%.apk}-signed.apk"
  # Gradle already aligns; fail if it didn't (apksigner keeps alignment)
  "$build_tools/zipalign" -c 4 "$apk"
  "$build_tools/apksigner" sign \
    --ks "$keystore" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD \
    --out "$signed" "$apk"
  "$build_tools/apksigner" verify "$signed"
  echo "Signed $signed"
done

for aab in "${aabs[@]}"; do
  signed="${aab%.aab}-signed.aab"
  jarsigner -keystore "$keystore" \
    -storepass:env KEYSTORE_PASSWORD -keypass:env KEY_PASSWORD \
    -signedjar "$signed" "$aab" "$KEY_ALIAS"
  echo "Signed $signed"
done
