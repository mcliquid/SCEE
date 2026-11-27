#!/usr/bin/env node
/**
 * Apply a validated refresh tar to the current repo (commit job only; script from trusted base SHA).
 * Usage: node apply-traffic-sign-artifact.mjs <tar.gz> <manifest.txt> [repoRoot]
 */
import fs from "node:fs";
import path from "node:path";

import { applyRefreshArtifactSafely } from "./traffic-sign-artifact.mjs";

const tarGzPath = process.argv[2];
const manifestPath = process.argv[3];
const repoRoot = process.argv[4] ? path.resolve(process.argv[4]) : process.cwd();

if (!tarGzPath || !manifestPath) {
  console.error("Usage: node apply-traffic-sign-artifact.mjs <tar.gz> <manifest.txt> [repoRoot]");
  process.exit(2);
}

const manifestPaths = fs
  .readFileSync(manifestPath, "utf8")
  .split(/\r?\n/)
  .map((l) => l.trim())
  .filter(Boolean);

applyRefreshArtifactSafely({ tarGzPath: path.resolve(tarGzPath), manifestPaths, repoRoot });
console.log(`Applied ${manifestPaths.length} path(s) from refresh artifact.`);