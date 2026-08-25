import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import test from "node:test";

const workflowDirectory = new URL("../workflows/", import.meta.url);

async function workflows() {
    const names = (await readdir(workflowDirectory)).filter((name) => name.endsWith(".yml"));
    return Promise.all(
        names.map(async (name) => [name, await readFile(new URL(name, workflowDirectory), "utf8")]),
    );
}

function withoutComments(source) {
    return source
        .split("\n")
        .filter((line) => !/^\s*#/.test(line))
        .join("\n");
}

test("build-cache-warm is the only explicit Gradle cache writer", async () => {
    const writers = (await workflows())
        .filter(([, source]) => /uses:\s*gradle\/actions\/setup-gradle@/.test(source))
        .filter(([, source]) => /cache-read-only:\s*false/.test(withoutComments(source)))
        .map(([name]) => name);

    assert.deepEqual(writers, ["build-cache-warm.yml"]);
});

test("every other setup-gradle workflow is read-only", async () => {
    for (const [name, source] of await workflows()) {
        if (name === "build-cache-warm.yml" || !/uses:\s*gradle\/actions\/setup-gradle@/.test(source)) {
            continue;
        }
        assert.match(source, /cache-read-only:\s*true/, `${name} must not write the shared cache`);
        assert.doesNotMatch(source, /cache-read-only:\s*\$\{\{/);
    }
});

test("the warming workflow runs on master and is never cancelled", async () => {
    const source = await readFile(new URL("build-cache-warm.yml", workflowDirectory), "utf8");

    assert.match(source, /^ {2}push:\n {4}branches: \[master\]$/m);
    assert.match(source, /^ {4}if: github\.ref == 'refs\/heads\/master'$/m);
    assert.match(source, /cancel-in-progress:\s*false/);
    assert.match(source, /--build-cache/);
});

test("the warming workflow covers pull request Gradle tasks", async () => {
    const warm = await readFile(new URL("build-cache-warm.yml", workflowDirectory), "utf8");
    const lint = await readFile(new URL("lint.yml", workflowDirectory), "utf8");
    const unitTest = await readFile(new URL("unit-test.yml", workflowDirectory), "utf8");

    for (const task of [":app:ktlintCheck", ":app:lintDebug"]) {
        assert.ok(lint.includes(task));
        assert.ok(warm.includes(task));
    }
    for (const task of [
        ":app:testDebugUnitTest",
        ":app:koverXmlReportDebug",
        ":app:compileDebugAndroidTestKotlin",
    ]) {
        assert.ok(unitTest.includes(task));
        assert.ok(warm.includes(task));
    }
});

test("CodeQL observes compiler execution instead of build-cache hits", async () => {
    const source = await readFile(new URL("codeql.yml", workflowDirectory), "utf8");

    assert.doesNotMatch(withoutComments(source), /--build-cache/);
    assert.match(source, /cache-read-only:\s*true/);
    assert.match(source, /:app:compileDebugKotlin/);
});
