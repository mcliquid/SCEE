/**
 * Safe packaging and application of refresh artifacts (traversal, type, symlink guards).
 */
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import { assertBotCommitAllowlist, isBotCommitPathAllowed } from "./traffic-sign-renovate-lib.mjs";

/** @returns {boolean} */
function isGnuTar() {
  try {
    return execFileSync("tar", ["--version"], { encoding: "utf8" }).includes("GNU tar");
  } catch {
    return false;
  }
}

/** @param {string} member tar member path */
export function assertSafeArchiveMember(member) {
  const norm = member.replaceAll("\\", "/").replace(/^\.\/+/, "");
  if (!norm || norm === "." || norm === "..") {
    throw new Error(`Unsafe archive member: ${member}`);
  }
  if (path.posix.isAbsolute(norm) || norm.startsWith("/") || /^[A-Za-z]:\//.test(norm)) {
    throw new Error(`Absolute archive member rejected: ${member}`);
  }
  if (norm.split("/").some((seg) => seg === "..")) {
    throw new Error(`Path traversal in archive member: ${member}`);
  }
  if (!isBotCommitPathAllowed(norm)) {
    throw new Error(`Archive member outside bot allowlist: ${member}`);
  }
}

/**
 * @param {string} typeChar first character of GNU `tar -tv` mode field
 */
export function assertTarTypeAllowsExtract(typeChar) {
  if (typeChar !== "-") {
    throw new Error(
      `Archive member type not allowed (want regular file): type=${JSON.stringify(typeChar)}`,
    );
  }
}

/**
 * Parse one line of GNU `tar -tvf` output.
 * @param {string} line
 * @returns {{ typeChar: string, memberPath: string } | null}
 */
export function parseTarTvLine(line) {
  const trimmed = line.trimEnd();
  if (!trimmed) {
    return null;
  }
  const typeChar = trimmed[0];
  const arrow = trimmed.indexOf(" -> ");
  if (arrow !== -1) {
    throw new Error(`Symlink or link entry in archive listing: ${trimmed}`);
  }
  const lastSpace = trimmed.lastIndexOf(" ");
  if (lastSpace <= 0) {
    throw new Error(`Unparseable tar -tv line: ${trimmed}`);
  }
  const memberPath = trimmed.slice(lastSpace + 1).replace(/^\.\/+/, "");
  return { typeChar, memberPath };
}

/**
 * @param {string} tarGzPath
 * @returns {{ members: string[], entries: { typeChar: string, memberPath: string }[] }}
 */
export function inspectTarGzArchive(tarGzPath) {
  const listing = execFileSync("tar", ["-tvf", tarGzPath], { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  const membersRaw = execFileSync("tar", ["-tzf", tarGzPath], { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  const membersLines = membersRaw
    .split("\n")
    .map((l) => l.trim().replace(/^\.\/+/, ""))
    .filter(Boolean);
  if (membersLines.length !== new Set(membersLines).size) {
    throw new Error("Duplicate paths in tar archive");
  }
  const entries = [];
  for (const line of listing.split("\n")) {
    const parsed = parseTarTvLine(line);
    if (parsed) {
      entries.push(parsed);
    }
  }
  const members = entries.map((e) => e.memberPath.replaceAll("\\", "/"));
  if (members.length !== membersLines.length) {
    throw new Error("tar -tv and tar -tz member counts differ");
  }
  const tzSorted = [...membersLines].map((m) => m.replaceAll("\\", "/")).sort();
  const tvSorted = [...members].sort();
  for (let i = 0; i < tzSorted.length; i++) {
    if (tzSorted[i] !== tvSorted[i]) {
      throw new Error(`tar listing mismatch at ${i}: ${tzSorted[i]} vs ${tvSorted[i]}`);
    }
  }
  return { members: membersLines.map((m) => m.replaceAll("\\", "/")), entries };
}

/**
 * @param {string[]} members paths from tar
 * @param {string[]} manifest paths written at package time
 */
export function validateRefreshArtifact(members, manifest) {
  if (members.length !== new Set(members).size) {
    throw new Error("Duplicate archive member paths");
  }
  const man = [...new Set(manifest.map((p) => p.replaceAll("\\", "/")).filter(Boolean))].sort();
  const list = [...new Set(members.map((p) => p.replaceAll("\\", "/")).filter(Boolean))].sort();
  if (man.length !== list.length) {
    throw new Error(`Artifact manifest mismatch: manifest ${man.length} paths, tar ${list.length} members`);
  }
  for (let i = 0; i < man.length; i++) {
    if (man[i] !== list[i]) {
      throw new Error(`Artifact manifest mismatch at index ${i}: ${man[i]} vs ${list[i]}`);
    }
  }
  for (const m of list) {
    assertSafeArchiveMember(m);
  }
  assertBotCommitAllowlist(list);
}

/**
 * @param {string} tarGzPath
 * @param {string[]} manifest
 */
export function validateRefreshTarBeforeExtract(tarGzPath, manifest) {
  const { members, entries } = inspectTarGzArchive(tarGzPath);
  for (const e of entries) {
    assertTarTypeAllowsExtract(e.typeChar);
    assertSafeArchiveMember(e.memberPath);
  }
  validateRefreshArtifact(members, manifest);
}

/**
 * Refuse if any path component under repoRoot is an existing symlink.
 * @param {string} repoRoot absolute
 * @param {string} relPosix repo-relative
 */
export function assertTargetPathNotSymlink(repoRoot, relPosix) {
  const parts = relPosix.replaceAll("\\", "/").split("/").filter(Boolean);
  let cur = path.resolve(repoRoot);
  const rootResolved = cur;
  for (const part of parts) {
    cur = path.join(cur, part);
    if (!fs.existsSync(cur)) {
      return;
    }
    const st = fs.lstatSync(cur);
    if (st.isSymbolicLink()) {
      throw new Error(`Refusing to write through symlink in repo: ${relPosix}`);
    }
    const resolved = path.resolve(cur);
    if (!resolved.startsWith(rootResolved + path.sep) && resolved !== rootResolved) {
      throw new Error(`Refusing path outside repo root: ${relPosix}`);
    }
  }
}

/**
 * Extract to an isolated directory, verify regular files, copy into repoRoot.
 * @param {{ tarGzPath: string, manifestPaths: string[], repoRoot: string }} opts
 */
export function applyRefreshArtifactSafely({ tarGzPath, manifestPaths, repoRoot }) {
  const manifest = manifestPaths.map((p) => p.replaceAll("\\", "/")).filter(Boolean);
  validateRefreshTarBeforeExtract(tarGzPath, manifest);

  const extractRoot = fs.mkdtempSync(path.join(os.tmpdir(), "traffic-sign-refresh-"));
  try {
    const manifestFile = path.join(extractRoot, "manifest.txt");
    fs.writeFileSync(manifestFile, `${manifest.join("\n")}\n`, "utf8");
    const extractArgs = ["-xzf", tarGzPath, "-C", extractRoot];
    if (isGnuTar()) {
      extractArgs.push("--no-same-owner", "--no-same-permissions", "--anchored", "--no-wildcards");
    }
    extractArgs.push("--files-from", manifestFile);
    execFileSync("tar", extractArgs, { stdio: ["ignore", "pipe", "pipe"] });

    const repoAbs = path.resolve(repoRoot);
    for (const rel of manifest) {
      assertTargetPathNotSymlink(repoAbs, rel);
      const src = path.join(extractRoot, rel);
      const st = fs.lstatSync(src);
      if (st.isSymbolicLink()) {
        throw new Error(`Extracted member is a symlink: ${rel}`);
      }
      if (!st.isFile()) {
        throw new Error(`Extracted member is not a regular file: ${rel}`);
      }
      const dest = path.join(repoAbs, rel);
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.copyFileSync(src, dest);
    }
  } finally {
    fs.rmSync(extractRoot, { recursive: true, force: true });
  }
}