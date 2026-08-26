#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

export function parseDatabaseLocations(rawLocations) {
    let locations;
    try {
        locations = JSON.parse(rawLocations);
    } catch (error) {
        throw new Error(`CODEQL_DATABASE_LOCATIONS is not valid JSON: ${error.message}`);
    }
    if (!locations || typeof locations !== "object" || Array.isArray(locations)) {
        throw new Error("CODEQL_DATABASE_LOCATIONS must be a JSON object");
    }
    for (const [language, location] of Object.entries(locations)) {
        if (typeof location !== "string" || location.length === 0) {
            throw new Error(`CodeQL database location for ${language} is empty`);
        }
    }
    return locations;
}

export function parseExtensions(rawExtensions) {
    const extensions = rawExtensions
        .split(",")
        .map((extension) => extension.trim().toLowerCase())
        .filter(Boolean);
    if (extensions.length === 0 || extensions.some((extension) => !/^\.[a-z0-9]+$/.test(extension))) {
        throw new Error(`Invalid CODEQL_SOURCE_EXTENSIONS: ${rawExtensions}`);
    }
    return [...new Set(extensions)];
}

function normalizeEntry(entry) {
    return entry.replaceAll("\\", "/").replace(/^\.\//, "");
}

export function selectSourceEntries(entries, extensions, requiredPathFragment) {
    const normalizedFragment = normalizeEntry(requiredPathFragment).toLowerCase();
    return entries
        .map(normalizeEntry)
        .filter((entry) => !entry.endsWith("/"))
        .filter((entry) => extensions.some((extension) => entry.toLowerCase().endsWith(extension)))
        .filter((entry) => entry.toLowerCase().includes(normalizedFragment))
        .sort();
}

async function walk(directory, root = directory, entries = []) {
    for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
        const absolute = path.join(directory, entry.name);
        if (entry.isDirectory()) {
            await walk(absolute, root, entries);
        } else if (entry.isFile()) {
            entries.push(path.relative(root, absolute));
        }
    }
    return entries;
}

async function sourceArchiveEntries(databasePath) {
    const zipPath = path.join(databasePath, "src.zip");
    try {
        await fs.access(zipPath);
        const output = execFileSync("unzip", ["-Z1", zipPath], {
            encoding: "utf8",
            maxBuffer: 32 * 1024 * 1024,
        });
        return {
            archive: "src.zip",
            entries: output.split(/\r?\n/).filter(Boolean),
        };
    } catch (error) {
        if (error?.code !== "ENOENT") {
            throw error;
        }
    }

    const sourceDirectory = path.join(databasePath, "src");
    try {
        const stat = await fs.stat(sourceDirectory);
        if (!stat.isDirectory()) {
            throw new Error(`${sourceDirectory} is not a directory`);
        }
        return { archive: "src/", entries: await walk(sourceDirectory) };
    } catch (error) {
        if (error?.code !== "ENOENT") {
            throw error;
        }
    }

    const topLevelEntries = (await fs.readdir(databasePath)).sort().join(", ");
    throw new Error(
        `CodeQL database has no src.zip or src/ source archive. Top-level entries: ${topLevelEntries}`,
    );
}

export async function inspectDatabase({
    locations,
    databaseLanguage,
    extensions,
    requiredPathFragment,
}) {
    const databasePath = locations[databaseLanguage];
    if (!databasePath) {
        throw new Error(
            `CodeQL database output has no canonical '${databaseLanguage}' entry: ${Object.keys(locations).join(", ")}`,
        );
    }
    if (!path.isAbsolute(databasePath)) {
        throw new Error(`CodeQL database location must be absolute: ${databasePath}`);
    }
    const databaseStat = await fs.stat(databasePath);
    if (!databaseStat.isDirectory()) {
        throw new Error(`CodeQL database location is not a directory: ${databasePath}`);
    }
    const metadataPath = path.join(databasePath, "codeql-database.yml");
    if ((await fs.stat(metadataPath)).size === 0) {
        throw new Error("codeql-database.yml is empty");
    }

    const sourceArchive = await sourceArchiveEntries(databasePath);
    const matchedSources = selectSourceEntries(
        sourceArchive.entries,
        extensions,
        requiredPathFragment,
    );
    if (matchedSources.length === 0) {
        throw new Error(
            `CodeQL '${databaseLanguage}' database contains no ${extensions.join("/")} source under ${requiredPathFragment}`,
        );
    }
    return {
        archive: sourceArchive.archive,
        databaseLanguage,
        matchedSources,
        totalArchiveEntries: sourceArchive.entries.length,
    };
}

export function renderEvidence({ displayLanguage, buildMode, inspection }) {
    const extensions = [...new Set(inspection.matchedSources.map((source) => path.extname(source)))];
    return [
        `### CodeQL database evidence: ${displayLanguage}`,
        "",
        `- Build mode: \`${buildMode}\``,
        `- Canonical database: \`${inspection.databaseLanguage}\``,
        `- Source archive: \`${inspection.archive}\` (${inspection.totalArchiveEntries} total entries)`,
        `- Required compiled/analyzed sources: **${inspection.matchedSources.length}** ${extensions.join("/")} files`,
        "",
    ].join("\n");
}

async function main() {
    const rawLocations = process.env.CODEQL_DATABASE_LOCATIONS;
    const databaseLanguage = process.env.CODEQL_DATABASE_LANGUAGE;
    const rawExtensions = process.env.CODEQL_SOURCE_EXTENSIONS;
    const requiredPathFragment = process.env.CODEQL_REQUIRED_PATH_FRAGMENT;
    const displayLanguage = process.env.CODEQL_DISPLAY_LANGUAGE;
    const buildMode = process.env.CODEQL_BUILD_MODE;
    if (
        !rawLocations ||
        !databaseLanguage ||
        !rawExtensions ||
        !requiredPathFragment ||
        !displayLanguage ||
        !buildMode
    ) {
        throw new Error(
            "CODEQL_DATABASE_LOCATIONS, CODEQL_DATABASE_LANGUAGE, CODEQL_SOURCE_EXTENSIONS, CODEQL_REQUIRED_PATH_FRAGMENT, CODEQL_DISPLAY_LANGUAGE, and CODEQL_BUILD_MODE are required",
        );
    }

    const inspection = await inspectDatabase({
        locations: parseDatabaseLocations(rawLocations),
        databaseLanguage,
        extensions: parseExtensions(rawExtensions),
        requiredPathFragment,
    });
    const evidence = renderEvidence({ displayLanguage, buildMode, inspection });
    console.log(evidence.trimEnd());
    console.log("Sample analyzed sources:");
    for (const source of inspection.matchedSources.slice(0, 5)) {
        console.log(`- ${source}`);
    }
    if (process.env.GITHUB_STEP_SUMMARY) {
        await fs.appendFile(process.env.GITHUB_STEP_SUMMARY, evidence, "utf8");
    }
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : "";
if (import.meta.url === invokedPath) {
    main().catch((error) => {
        console.error(error instanceof Error ? error.stack : error);
        process.exitCode = 1;
    });
}
