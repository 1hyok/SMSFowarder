import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
    inspectDatabase,
    parseDatabaseLocations,
    parseExtensions,
    renderEvidence,
    selectSourceEntries,
} from "./verify-codeql-database.mjs";

test("parses canonical CodeQL database locations", () => {
    assert.deepEqual(parseDatabaseLocations('{"java":"/tmp/db-java"}'), {
        java: "/tmp/db-java",
    });
    assert.throws(() => parseDatabaseLocations("[]"), /must be a JSON object/);
    assert.throws(() => parseDatabaseLocations('{"java":""}'), /location for java is empty/);
});

test("normalizes and validates source extensions", () => {
    assert.deepEqual(parseExtensions(".kt, .yml,.kt"), [".kt", ".yml"]);
    assert.throws(() => parseExtensions("kt"), /Invalid CODEQL_SOURCE_EXTENSIONS/);
});

test("selects only required source paths with matching extensions", () => {
    assert.deepEqual(
        selectSourceEntries(
            [
                "home/runner/work/repo/app/src/main/Main.kt",
                "home/runner/work/repo/app/src/test/MainTest.kt",
                "home/runner/work/repo/app/src/main/res/layout.xml",
                "home\\runner\\work\\repo\\app\\src\\main\\Other.KT",
            ],
            [".kt"],
            "app/src/main/",
        ),
        [
            "home/runner/work/repo/app/src/main/Main.kt",
            "home/runner/work/repo/app/src/main/Other.KT",
        ],
    );
});

test("inspects an uncompressed CodeQL source archive", async (context) => {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), "codeql-database-"));
    context.after(() => fs.rm(root, { recursive: true, force: true }));
    const database = path.join(root, "db-java");
    await fs.mkdir(path.join(database, "src/home/runner/work/repo/app/src/main"), {
        recursive: true,
    });
    await fs.writeFile(path.join(database, "codeql-database.yml"), "primaryLanguage: java\n");
    await fs.writeFile(
        path.join(database, "src/home/runner/work/repo/app/src/main/Main.kt"),
        "class Main\n",
    );

    const inspection = await inspectDatabase({
        locations: { java: database },
        databaseLanguage: "java",
        extensions: [".kt"],
        requiredPathFragment: "app/src/main/",
    });

    assert.equal(inspection.archive, "src/");
    assert.equal(inspection.totalArchiveEntries, 1);
    assert.deepEqual(inspection.matchedSources, [
        "home/runner/work/repo/app/src/main/Main.kt",
    ]);
});

test("fails when the requested canonical database is absent", async () => {
    await assert.rejects(
        inspectDatabase({
            locations: { actions: "/tmp/db-actions" },
            databaseLanguage: "java",
            extensions: [".kt"],
            requiredPathFragment: "app/src/main/",
        }),
        /no canonical 'java' entry/,
    );
});

test("renders build mode and database source evidence", () => {
    const summary = renderEvidence({
        displayLanguage: "java-kotlin",
        buildMode: "manual",
        inspection: {
            archive: "src.zip",
            databaseLanguage: "java",
            matchedSources: ["workspace/app/src/main/Main.kt"],
            totalArchiveEntries: 321,
        },
    });

    assert.match(summary, /CodeQL database evidence: java-kotlin/);
    assert.match(summary, /Build mode: `manual`/);
    assert.match(summary, /Canonical database: `java`/);
    assert.match(summary, /\*\*1\*\* \.kt files/);
});
