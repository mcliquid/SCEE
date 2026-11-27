/**
 * Guards and allowlists for the Renovate traffic-sign coordinated refresh workflow.
 * Imported from the PR base SHA (trusted); do not duplicate security-sensitive logic in untrusted paths.
 */
import fs from "node:fs";
import path from "node:path";

import { readPins } from "./pin-traffic-signs-sha256.mjs";

/** Paths a bot commit may touch (POSIX, repo-relative). */
export const BOT_COMMIT_ALLOWLIST = [
  "app/build.gradle.kts",
  "app/src/commonMain/composeResources/files/traffic_signs/",
  "app/traffic-signs-update/history/",
  "tools/traffic-sign-parity/package.json",
  "tools/traffic-sign-parity/package-lock.json",
  "tools/traffic-sign-parity/reference.jsonl",
];

/** Paths that must not appear in a Renovate traffic-sign PR (except after a completed bot refresh). */
export const BOT_COMMIT_DENYLIST_PREFIXES = [
  "app/src/commonTest/",
  ".github/workflows/",
  ".github/scripts/",
];

/**
 * @param {string} filePath repo-relative POSIX path
 */
export function isBotCommitPathAllowed(filePath) {
  const norm = filePath.replaceAll("\\", "/");
  if (BOT_COMMIT_DENYLIST_PREFIXES.some((p) => norm.startsWith(p))) {
    return false;
  }
  if (norm === BOT_COMMIT_ALLOWLIST[0]) {
    return true;
  }
  return BOT_COMMIT_ALLOWLIST.slice(1).some(
    (prefix) => norm === prefix || norm.startsWith(prefix),
  );
}

/**
 * @param {string[]} changedPaths repo-relative paths (added/modified), excluding deletions handled separately
 */
export function assertBotCommitAllowlist(changedPaths) {
  /** @type {string[]} */
  const bad = [];
  for (const p of changedPaths) {
    if (!isBotCommitPathAllowed(p)) {
      bad.push(p);
    }
  }
  if (bad.length > 0) {
    throw new Error(
      `Refresh produced changes outside the bot allowlist:\n${bad.map((l) => `  ${l}`).join("\n")}`,
    );
  }
}

/**
 * @param {string} gradleSource
 */
export function readTrafficSignVersion(gradleSource) {
  return readPins(gradleSource).version;
}

/**
 * @param {string} baseGradle
 * @param {string} headGradle
 */
export function trafficSignVersionChanged(baseGradle, headGradle) {
  return readTrafficSignVersion(baseGradle) !== readTrafficSignVersion(headGradle);
}

/**
 * Whether the coordinated refresh pipeline should run (Gradle pin + catalogues + parity).
 * After the bot commit, the PR already contains allowlisted catalogue/parity paths while
 * `trafficSignsVersion` still differs from SCEE-mc — skip re-running the heavy pipeline.
 *
 * @param {string} baseGradle
 * @param {string} headGradle
 * @param {string[]} prChangedFiles paths from the PR file list (GitHub API)
 */
export function shouldRunCoordinatedCatalogueRefresh(baseGradle, headGradle, prChangedFiles) {
  if (!trafficSignVersionChanged(baseGradle, headGradle)) {
    return false;
  }
  const norm = prChangedFiles.map((f) => f.replaceAll("\\", "/"));
  if (norm.length === 0) {
    return false;
  }
  const onlyGradleBump = norm.length === 1 && norm[0] === "app/build.gradle.kts";
  if (onlyGradleBump) {
    return true;
  }
  const hasRefreshOutputs = norm.some(
    (f) => f !== "app/build.gradle.kts" && isBotCommitPathAllowed(f),
  );
  if (hasRefreshOutputs) {
    return false;
  }
  return true;
}

/**
 * Renovate opens with only the version bump in build.gradle.kts (SHA may be stale).
 * @param {string[]} prChangedFiles paths from the PR file list (github API)
 */
export function assertRenovateInitialPrFiles(prChangedFiles) {
  const norm = prChangedFiles.map((f) => f.replaceAll("\\", "/"));
  const unexpected = norm.filter(
    (f) => f !== "app/build.gradle.kts" && !isBotCommitPathAllowed(f),
  );
  if (unexpected.length > 0) {
    throw new Error(
      `Unexpected files in Renovate traffic-sign PR before refresh:\n${unexpected.map((l) => `  ${l}`).join("\n")}`,
    );
  }
}

/**
 * @param {string} reportMarkdown full report file
 * @returns {{ needsReview: boolean, reasons: string[] }}
 */
export function parseReportNeedsReview(reportMarkdown) {
  const marker = "## Needs a manual review";
  const idx = reportMarkdown.indexOf(marker);
  if (idx === -1) {
    return { needsReview: false, reasons: [] };
  }
  const after = reportMarkdown.slice(idx + marker.length);
  const end = after.search(/\n## /);
  const section = end === -1 ? after : after.slice(0, end);
  const reasons = section
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l.startsWith("- "))
    .map((l) => l.slice(2).trim())
    .filter(Boolean);
  return { needsReview: reasons.length > 0, reasons };
}

/**
 * @param {string} reportMarkdown
 */
export function parseReportHasCatalogueFileChanges(reportMarkdown) {
  const m = reportMarkdown.match(/^- files: (\d+) added, (\d+) changed, (\d+) removed/m);
  if (!m) {
    return true;
  }
  return Number(m[1]) + Number(m[2]) + Number(m[3]) > 0;
}

/** Gradle lifecycle line from updateTrafficSigns when nothing would change. */
export function dryRunIndicatesIdentical(dryLogText) {
  return dryLogText.includes("identical to the existing files");
}

/**
 * @param {string} dryLogText
 * @param {string} reportMarkdown
 */
export function shouldApplyCatalogueUpdate(dryLogText, reportMarkdown) {
  if (dryRunIndicatesIdentical(dryLogText)) {
    return false;
  }
  return parseReportHasCatalogueFileChanges(reportMarkdown);
}

/**
 * Find newest traffic-signs-update-*.md in report dir.
 * @param {string} reportDir
 */
export function findLatestUpdateReport(reportDir) {
  if (!fs.existsSync(reportDir)) {
    return null;
  }
  const files = fs
    .readdirSync(reportDir)
    .filter((f) => /^traffic-signs-update-.+\.md$/.test(f))
    .map((f) => ({ f, mtime: fs.statSync(path.join(reportDir, f)).mtimeMs }))
    .sort((a, b) => b.mtime - a.mtime);
  return files.length > 0 ? path.join(reportDir, files[0].f) : null;
}