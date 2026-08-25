#!/usr/bin/env bash

set -euo pipefail

remove_runner_temp_file() {
    path="$1"
    [ -n "$path" ] || return 0
    case "$path" in
        "$RUNNER_TEMP"/*) rm -f -- "$path" ;;
        *)
            echo "::error::Refusing to remove a path outside RUNNER_TEMP"
            return 1
            ;;
    esac
}

remove_runner_temp_file "${KEYSTORE_PATH:-$RUNNER_TEMP/sms-forwarder-release.jks}"
remove_runner_temp_file \
    "${FIREBASE_CREDENTIALS_PATH:-$RUNNER_TEMP/firebase-service-account.json}"
