#!/usr/bin/env bash

set -euo pipefail

script_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
quality_checker="${script_directory}/repository-quality.sh"
fixture_root=$(mktemp -d /tmp/repository-quality-fixtures.XXXXXX)

cleanup() {
    rm -rf "$fixture_root"
}
trap cleanup EXIT

create_fixture_repository() {
    local fixture_name=$1
    local fixture_repository="${fixture_root}/${fixture_name}"

    mkdir -p "${fixture_repository}/.github/workflows"
    git -C "$fixture_repository" init -q

    printf '%s\n' \
        'name: Valid Fixture' \
        '' \
        'on:' \
        '  pull_request:' \
        '' \
        'jobs:' \
        '  fixture:' \
        '    runs-on: ubuntu-latest' \
        '    steps:' \
        '      - run: echo "fixture"' \
        > "${fixture_repository}/.github/workflows/valid.yml"
    printf '%s\n' \
        '#!/usr/bin/env bash' \
        'echo "fixture"' \
        > "${fixture_repository}/valid.sh"
    printf '%s\n' 'fixture repository' > "${fixture_repository}/README.md"

    git -C "$fixture_repository" add -- \
        .github/workflows/valid.yml \
        README.md \
        valid.sh

    printf '%s\n' "$fixture_repository"
}

expect_fixture_failure() {
    local fixture_name=$1
    local fixture_repository=$2
    local expected_message=$3
    local fixture_log="${fixture_root}/${fixture_name}.log"

    if bash "$quality_checker" "$fixture_repository" >"$fixture_log" 2>&1; then
        echo "Repository Quality fixture unexpectedly passed: $fixture_name" >&2
        return 1
    fi

    if ! grep -Fq "$expected_message" "$fixture_log"; then
        echo "Repository Quality fixture failed for the wrong reason: $fixture_name" >&2
        sed -n '1,160p' "$fixture_log" >&2
        return 1
    fi

    echo "Repository Quality fixture rejected as expected: $fixture_name"
}

baseline_repository=$(create_fixture_repository baseline)
bash "$quality_checker" "$baseline_repository"
echo "Repository Quality fixture passed as expected: valid baseline"

workflow_repository=$(create_fixture_repository invalid-workflow)
printf '%s\n' \
    'name: Invalid Workflow Fixture' \
    'on:' \
    '  pull_request:' \
    'jobs:' \
    '  invalid:' \
    '    runs-on: ubuntu-latest' \
    '    steps:' \
    '      - uses actions/checkout@v7' \
    > "${workflow_repository}/.github/workflows/invalid.yml"
git -C "$workflow_repository" add -- .github/workflows/invalid.yml
expect_fixture_failure \
    invalid-workflow \
    "$workflow_repository" \
    'Repository Quality: actionlint failed: .github/workflows/invalid.yml'

workflow_shell_repository=$(create_fixture_repository workflow-run-shellcheck)
printf '%s\n' \
    'name: Workflow Run ShellCheck Fixture' \
    'on:' \
    '  pull_request:' \
    'jobs:' \
    '  invalid-shell:' \
    '    runs-on: ubuntu-latest' \
    '    steps:' \
    "      - run: echo \$fixture_value" \
    > "${workflow_shell_repository}/.github/workflows/invalid-run-shell.yml"
git -C "$workflow_shell_repository" add -- .github/workflows/invalid-run-shell.yml
expect_fixture_failure \
    workflow-run-shellcheck \
    "$workflow_shell_repository" \
    'shellcheck reported issue in this script'

shell_repository=$(create_fixture_repository invalid-shell)
printf '%s\n' \
    '#!/usr/bin/env bash' \
    'if true; then' \
    '    echo "missing fi"' \
    > "${shell_repository}/invalid.sh"
git -C "$shell_repository" add -- invalid.sh
expect_fixture_failure \
    invalid-shell \
    "$shell_repository" \
    'Repository Quality: bash syntax failed: invalid.sh'

marker_repository=$(create_fixture_repository merge-marker)
printf '%s\n' \
    '<<<<<<< HEAD' \
    'left side' \
    '=======' \
    'right side' \
    '>>>>>>> fixture-branch' \
    > "${marker_repository}/README.md"
git -C "$marker_repository" add -- README.md
expect_fixture_failure \
    merge-marker \
    "$marker_repository" \
    'Repository Quality: merge conflict marker found'
