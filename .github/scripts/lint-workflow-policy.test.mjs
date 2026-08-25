import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const lintWorkflow = await readFile(new URL("../workflows/lint.yml", import.meta.url), "utf8");

test("Ktlint runs once through the app Gradle task", () => {
    const invocations = lintWorkflow.match(/^\s*run:.*:app:ktlintCheck/gm) ?? [];
    assert.equal(invocations.length, 1);
    assert.match(lintWorkflow, /\.\/gradlew :app:ktlintCheck[^\n]*--continue/);
    assert.doesNotMatch(lintWorkflow, /^\s*uses:\s*ScaCap\/action-ktlint/m);
    assert.doesNotMatch(lintWorkflow, /ktlint_version/);
});

test("Android Lint runs once through the app Gradle task", () => {
    const invocations = lintWorkflow.match(/^\s*run:.*:app:lintDebug/gm) ?? [];
    assert.equal(invocations.length, 1);
    assert.match(lintWorkflow, /\.\/gradlew :app:lintDebug[^\n]*--continue/);
    assert.doesNotMatch(lintWorkflow, /^\s*uses:\s*dvdandroid\/action-android-lint/m);
});

test("static analysis is read-only and retains failure evidence", () => {
    assert.doesNotMatch(lintWorkflow, /pull-requests:\s*write/);
    assert.doesNotMatch(lintWorkflow, /github-pr-review|reviewdog/i);
    assert.equal((lintWorkflow.match(/if: failure\(\)/g) ?? []).length, 2);
    assert.equal((lintWorkflow.match(/actions\/upload-artifact@[0-9a-f]{40}/g) ?? []).length, 2);
});
