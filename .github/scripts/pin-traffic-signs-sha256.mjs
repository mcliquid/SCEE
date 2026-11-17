// Sync app/build.gradle.kts trafficSignsSha256 with the npm tarball for trafficSignsVersion.
// Used after Renovate bumps @osm-traffic-signs/converter (registry dist.integrity is sha512, not this pin).
import crypto from "node:crypto";
import fs from "node:fs";
import https from "node:https";
import path from "node:path";
import { fileURLToPath } from "node:url";

const MAX_REDIRECTS = 5;

function gradlePathLabel(gradlePath) {
  return gradlePath || process.env.GRADLE_PATH || "app/build.gradle.kts";
}

/** @param {string} source */
export function readPins(source, gradlePath = gradlePathLabel()) {
  const version = source.match(/val trafficSignsVersion = "([^"]+)"/)?.[1];
  const sha256 = source.match(/val trafficSignsSha256 = "([^"]+)"/)?.[1];
  if (!version || !sha256) {
    throw new Error(`Could not parse traffic sign pins in ${gradlePath}`);
  }
  return { version, sha256 };
}

/** @param {string} version */
export function tarballUrl(version) {
  return `https://registry.npmjs.org/@osm-traffic-signs/converter/-/converter-${version}.tgz`;
}

/**
 * @param {string} url
 * @param {number} redirectsLeft
 * @returns {Promise<Buffer>}
 */
export function fetchBuffer(url, redirectsLeft = MAX_REDIRECTS) {
  if (!url.startsWith("https://")) {
    return Promise.reject(new Error(`Refusing non-HTTPS URL: ${url}`));
  }
  return new Promise((resolve, reject) => {
    https
      .get(url, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          if (redirectsLeft <= 0) {
            reject(new Error(`Too many redirects for ${url}`));
            return;
          }
          fetchBuffer(res.headers.location, redirectsLeft - 1).then(resolve, reject);
          return;
        }
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => {
          if (res.statusCode !== 200) {
            reject(new Error(`HTTP ${res.statusCode} for ${url}`));
            return;
          }
          resolve(Buffer.concat(chunks));
        });
        res.on("error", reject);
      })
      .on("error", reject);
  });
}

/** @param {string} version */
export async function tarballSha256(version) {
  const tarball = await fetchBuffer(tarballUrl(version));
  return crypto.createHash("sha256").update(tarball).digest("hex");
}

/**
 * @param {string} before
 * @param {string} after
 */
export function assertOnlySha256PinChange(before, after) {
  if (before === after) {
    return;
  }
  const beforeLines = before.split(/\r?\n/);
  const afterLines = after.split(/\r?\n/);
  if (beforeLines.length !== afterLines.length) {
    throw new Error("Gradle pin update must not add or remove lines");
  }
  /** @type {Array<{ line: number, before: string, after: string }>} */
  const changed = [];
  for (let i = 0; i < beforeLines.length; i++) {
    if (beforeLines[i] !== afterLines[i]) {
      changed.push({ line: i + 1, before: beforeLines[i], after: afterLines[i] });
    }
  }
  if (changed.length !== 1) {
    throw new Error(
      `Gradle pin update must change only trafficSignsSha256 (got ${changed.length} line diff(s))`,
    );
  }
  const pin = /^val trafficSignsSha256 = "/;
  if (!pin.test(changed[0].before) || !pin.test(changed[0].after)) {
    throw new Error(
      `Gradle pin update changed line ${changed[0].line} which is not trafficSignsSha256`,
    );
  }
}

/**
 * @param {string} gradlePath
 * @returns {Promise<boolean>} true when file was updated
 */
export async function syncSha256Pin(gradlePath = gradlePathLabel()) {
  const gradle = fs.readFileSync(gradlePath, "utf8");
  const pins = readPins(gradle, gradlePath);
  const expected = await tarballSha256(pins.version);

  if (expected === pins.sha256) {
    console.log(`trafficSignsSha256 already matches @osm-traffic-signs/converter@${pins.version}`);
    return false;
  }

  const updated = gradle.replace(
    /val trafficSignsSha256 = "[^"]+"/,
    `val trafficSignsSha256 = "${expected}"`,
  );
  assertOnlySha256PinChange(gradle, updated);
  fs.writeFileSync(gradlePath, updated);
  console.log(`Updated trafficSignsSha256 for ${pins.version}: ${expected}`);
  return true;
}

async function main() {
  const gradlePath = gradlePathLabel();
  await syncSha256Pin(gradlePath);
}

const isMain =
  process.argv[1] &&
  path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url));

if (isMain) {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}