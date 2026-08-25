#!/usr/bin/env bash

set -euo pipefail

missing=""
[ -z "$RELEASE_STORE_FILE_B64" ] && missing="$missing RELEASE_STORE_FILE_B64"
[ -z "$RELEASE_STORE_PASSWORD" ] && missing="$missing RELEASE_STORE_PASSWORD"
[ -z "$RELEASE_KEY_ALIAS" ] && missing="$missing RELEASE_KEY_ALIAS"
[ -z "$RELEASE_KEY_PASSWORD" ] && missing="$missing RELEASE_KEY_PASSWORD"
[ -z "$FIREBASE_SERVICE_ACCOUNT_JSON" ] && missing="$missing FIREBASE_SERVICE_ACCOUNT_JSON"

if [ -n "$missing" ]; then
    echo "::error::Missing required release secrets:$missing"
    exit 1
fi

umask 077
keystore_path="$RUNNER_TEMP/sms-forwarder-release.jks"
firebase_credentials_path="$RUNNER_TEMP/firebase-service-account.json"

{
    echo "keystore-path=$keystore_path"
    echo "firebase-credentials-path=$firebase_credentials_path"
} >> "$GITHUB_OUTPUT"

printf '%s' "$RELEASE_STORE_FILE_B64" | base64 --decode > "$keystore_path"
printf '%s' "$FIREBASE_SERVICE_ACCOUNT_JSON" > "$firebase_credentials_path"

test -s "$keystore_path"
test -s "$firebase_credentials_path"
keytool -list \
    -keystore "$keystore_path" \
    -storepass "$RELEASE_STORE_PASSWORD" \
    -alias "$RELEASE_KEY_ALIAS" \
    -keypass "$RELEASE_KEY_PASSWORD" \
    -noprompt >/dev/null
jq -e \
    'type == "object" and .type == "service_account" and
     (.client_email | type == "string") and (.private_key | type == "string")' \
    "$firebase_credentials_path" >/dev/null
