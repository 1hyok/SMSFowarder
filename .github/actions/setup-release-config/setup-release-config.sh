#!/usr/bin/env bash

set -euo pipefail

missing=""
[ -z "$RELEASE_STORE_FILE_B64" ] && missing="$missing RELEASE_STORE_FILE_B64"
[ -z "$RELEASE_STORE_PASSWORD" ] && missing="$missing RELEASE_STORE_PASSWORD"
[ -z "$RELEASE_KEY_ALIAS" ] && missing="$missing RELEASE_KEY_ALIAS"
[ -z "$RELEASE_KEY_PASSWORD" ] && missing="$missing RELEASE_KEY_PASSWORD"

if [ -n "$missing" ]; then
    echo "::error::Missing required release secrets:$missing"
    exit 1
fi

umask 077
keystore_path="$RUNNER_TEMP/sms-forwarder-release.jks"

echo "keystore-path=$keystore_path" >> "$GITHUB_OUTPUT"

printf '%s' "$RELEASE_STORE_FILE_B64" | base64 --decode > "$keystore_path"

test -s "$keystore_path"
keytool -list \
    -keystore "$keystore_path" \
    -storepass "$RELEASE_STORE_PASSWORD" \
    -alias "$RELEASE_KEY_ALIAS" \
    -keypass "$RELEASE_KEY_PASSWORD" \
    -noprompt >/dev/null
