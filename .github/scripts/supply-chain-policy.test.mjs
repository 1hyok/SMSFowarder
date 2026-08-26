import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readdir, readFile } from "node:fs/promises";
import test from "node:test";

const workflowDirectory = new URL("../workflows/", import.meta.url);

async function sourcesIn(directory, fileName) {
    const names = await readdir(directory, { withFileTypes: true });
    const sources = [];
    for (const entry of names) {
        if (entry.isFile() && /\.ya?ml$/.test(entry.name)) {
            sources.push([entry.name, await readFile(new URL(entry.name, directory), "utf8")]);
        } else if (entry.isDirectory() && fileName) {
            const source = await readFile(new URL(`${entry.name}/${fileName}`, directory), "utf8");
            sources.push([entry.name, source]);
        }
    }
    return sources;
}

function actionReferences(source) {
    return [...source.matchAll(/^\s*(?:-\s+)?uses:\s*(\S+)/gm)].map((match) => match[1]);
}

function isPinnedAction(reference) {
    return reference.startsWith("./") || /@[0-9a-f]{40}$/.test(reference);
}

function invokesGradleWrapper(source) {
    return /(?:^|\s)\.\/gradlew(?:\s|$)/m.test(source);
}

test("every external action uses an immutable commit SHA with a version comment", async () => {
    const workflows = await sourcesIn(workflowDirectory);
    const composites = await sourcesIn(new URL("../actions/", import.meta.url), "action.yml");

    for (const [name, source] of [...workflows, ...composites]) {
        for (const reference of actionReferences(source)) {
            assert.ok(isPinnedAction(reference), `${name} uses a floating action: ${reference}`);
        }
        for (const line of source.split("\n")) {
            const match = /^\s*(?:-\s+)?uses:\s*(\S+@[0-9a-f]{40})(.*)$/.exec(line);
            if (match) {
                assert.match(match[2], /^\s+#\s+v?\d/, `${name} does not document ${match[1]}`);
            }
        }
    }
});

test("every Gradle wrapper workflow validates the wrapper through pinned setup-gradle", async () => {
    for (const [name, source] of await sourcesIn(workflowDirectory)) {
        if (!invokesGradleWrapper(source)) continue;

        assert.match(
            source,
            /gradle\/actions\/setup-gradle@[0-9a-f]{40} # v\d+\.\d+\.\d+/,
            `${name} does not use pinned setup-gradle`,
        );
        assert.match(source, /cache-provider:\s*basic/);
        assert.match(source, /validate-wrappers:\s*true/);
        assert.doesNotMatch(source, /^\s+cache:\s*['"]?gradle['"]?\s*$/m);
        assert.doesNotMatch(source, /uses:\s*actions\/cache@/);
    }
});

test("the privileged dependency graph bridge never executes pull request code", async () => {
    const source = await readFile(
        new URL("../workflows/dependency-submission-upload.yml", import.meta.url),
        "utf8",
    );

    assert.match(source, /workflow_run:/);
    assert.match(source, /github\.event\.workflow_run\.event == 'pull_request'/);
    assert.match(source, /github\.event\.workflow_run\.conclusion == 'success'/);
    assert.match(source, /actions:\s*read/);
    assert.match(source, /contents:\s*write/);
    assert.match(source, /dependency-graph:\s*download-and-submit/);
    assert.match(source, /cache-disabled:\s*true/);
    assert.doesNotMatch(source, /actions\/checkout@/);
    assert.doesNotMatch(source, /^\s+-?\s*run:/m);
});

test("dependency graph generation and review fail closed", async () => {
    const submission = await readFile(
        new URL("../workflows/dependency-submission.yml", import.meta.url),
        "utf8",
    );
    const review = await readFile(
        new URL("../workflows/dependency-review.yml", import.meta.url),
        "utf8",
    );

    assert.equal((submission.match(/dependency-graph-continue-on-failure:\s*false/g) ?? []).length, 2);
    assert.equal((submission.match(/validate-wrappers:\s*true/g) ?? []).length, 2);
    assert.equal((submission.match(/cache-read-only:\s*true/g) ?? []).length, 2);
    assert.match(submission, /dependency-graph:\s*generate-and-submit/);
    assert.match(submission, /dependency-graph:\s*generate-and-upload/);
    assert.match(review, /fail-on-severity:\s*high/);
    assert.match(review, /license-check:\s*true/);
    assert.doesNotMatch(review, /pull_request_target:/);
});

test("release credentials are reachable only from trusted master pushes", async () => {
    const source = await readFile(
        new URL("../workflows/release-distribution.yml", import.meta.url),
        "utf8",
    );

    assert.match(source, /^ {2}push:\n {4}branches: \[master\]$/m);
    assert.doesNotMatch(source, /workflow_dispatch:/);
    assert.doesNotMatch(source, /pull_request(?:_target)?:/);
    assert.match(source, /environment:\s*release-distribution/);
    assert.match(source, /FIREBASE_APP_DISTRIBUTION_ENABLED == 'true'/);
    assert.match(source, /persist-credentials:\s*false/);
    assert.match(source, /id-token:\s*write/);
    assert.match(
        source,
        /google-github-actions\/auth@7c6bc770dae815cd3e89ee6cdf493a5fab2cc093/,
    );
    assert.match(
        source,
        /workload_identity_provider:\s*projects\/805084536668\/locations\/global\/workloadIdentityPools\/github-actions\/providers\/smsforwarder/,
    );
    assert.match(
        source,
        /service_account:\s*github-app-distribution@smsforwarder-1hyok\.iam\.gserviceaccount\.com/,
    );
    assert.doesNotMatch(source, /FIREBASE_SERVICE_ACCOUNT_JSON|credentials_json:/);

    const attestationIndex = source.indexOf("Attest the signed release APK");
    const authenticationIndex = source.indexOf("Authenticate to Google Cloud");
    const uploadIndex = source.indexOf("Upload the attested APK");
    assert.ok(attestationIndex < authenticationIndex);
    assert.ok(authenticationIndex < uploadIndex);
});

test("Dependabot updates Actions and Gradle without automatic merging", async () => {
    const source = await readFile(new URL("../dependabot.yml", import.meta.url), "utf8");

    assert.match(source, /package-ecosystem:\s*github-actions/);
    assert.match(source, /package-ecosystem:\s*gradle/);
    assert.doesNotMatch(source, /auto-merge|automerge/i);
});

test("the Gradle wrapper remains on the reviewed distribution and wrapper checksums", async () => {
    const properties = await readFile(
        new URL("../../gradle/wrapper/gradle-wrapper.properties", import.meta.url),
        "utf8",
    );
    const wrapperJar = await readFile(
        new URL("../../gradle/wrapper/gradle-wrapper.jar", import.meta.url),
    );

    assert.match(
        properties,
        /^distributionUrl=https\\:\/\/services\.gradle\.org\/distributions\/gradle-9\.4\.1-bin\.zip$/m,
    );
    assert.match(
        properties,
        /^distributionSha256Sum=2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb$/m,
    );
    assert.match(properties, /^validateDistributionUrl=true$/m);
    assert.equal(
        createHash("sha256").update(wrapperJar).digest("hex"),
        "e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637",
    );
});

test("the local ktlint hook verifies its binary and preserves unrelated hooks", async () => {
    const hook = await readFile(new URL("../../git-hooks/pre-commit", import.meta.url), "utf8");
    const build = await readFile(new URL("../../build.gradle.kts", import.meta.url), "utf8");

    assert.match(
        hook,
        /KTLINT_SHA256="a3fd620207d5c40da6ca789b95e7f823c54e854b7fade7f613e91096a3706d75"/,
    );
    assert.match(hook, /github\.com\/ktlint\/ktlint\/releases\/download/);
    assert.match(hook, /ktlint_sha256/);
    assert.doesNotMatch(build, /rm -f .*pre-push/);
});
