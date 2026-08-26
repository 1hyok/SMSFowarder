import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const workflowDirectory = new URL("../workflows/", import.meta.url);
const gateWorkflow = "pr-validation.yml";
const validationWorkflows = [
    "lint.yml",
    "unit-test.yml",
    "repository-quality.yml",
    "mock-cleanup-check.yml",
];

function readWorkflow(name) {
    return readFile(new URL(name, workflowDirectory), "utf8");
}

function jobNames(source) {
    const jobsSection = source.slice(source.indexOf("\njobs:\n"));
    return [...jobsSection.matchAll(/^ {2}([A-Za-z][\w-]*):$/gm)].map((match) => match[1]);
}

function needsOf(source, job) {
    const pattern = new RegExp(`^ {2}${job}:$[\\s\\S]*?^ {4}needs:\\s*\\[([^\\]]*)\\]`, "m");
    const match = pattern.exec(source);
    return match ? match[1].split(",").map((entry) => entry.trim()) : [];
}

test("pull request validation has one orchestrated entry point", async () => {
    const gate = await readWorkflow(gateWorkflow);
    assert.match(gate, /^ {2}pull_request:\n {4}branches: \[master\]$/m);

    for (const workflow of validationWorkflows) {
        assert.doesNotMatch(
            await readWorkflow(workflow),
            /^ {2}pull_request:/m,
            `${workflow} must not start a duplicate pull request run`,
        );
    }
});

test("every validation workflow is reusable", async () => {
    for (const workflow of validationWorkflows) {
        assert.match(await readWorkflow(workflow), /^ {2}workflow_call:$/m);
    }
});

test("ci-gate aggregates every reusable validation", async () => {
    const gate = await readWorkflow(gateWorkflow);
    const jobs = jobNames(gate);
    const validationJobs = jobs.filter((job) => job !== "ci-gate");

    assert.ok(jobs.includes("ci-gate"));
    assert.equal(validationJobs.length, validationWorkflows.length);
    assert.deepEqual(needsOf(gate, "ci-gate").sort(), [...validationJobs].sort());

    for (const workflow of validationWorkflows) {
        assert.ok(gate.includes(`uses: ./.github/workflows/${workflow}`));
    }
});

test("ci-gate evaluates failures, cancellations, and skips", async () => {
    const gate = await readWorkflow(gateWorkflow);

    assert.match(gate, /^ {4}if: always\(\)$/m);
    assert.match(gate, /toJSON\(needs\)/);
    assert.match(gate, /\.value\.result != "success"/);
    assert.match(gate, /exit 1/);
});

test("stale pull request runs are cancelled", async () => {
    const gate = await readWorkflow(gateWorkflow);
    assert.match(
        gate,
        /^concurrency:\n {2}group: pr-validation-\$\{\{ github\.event\.pull_request\.number \|\| github\.ref \}\}\n {2}cancel-in-progress: true$/m,
    );
});

test("repository-only guards also run on master pushes", async () => {
    for (const workflow of ["repository-quality.yml", "mock-cleanup-check.yml"]) {
        assert.match(
            await readWorkflow(workflow),
            /^ {2}push:\n {4}branches: \[master\]$/m,
        );
    }
});
