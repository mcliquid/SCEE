import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import { mkdtempSync, readFileSync, symlinkSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { describe, it } from "node:test";

import {
  applyRefreshArtifactSafely,
  assertSafeArchiveMember,
  assertTarTypeAllowsExtract,
  inspectTarGzArchive,
  validateRefreshArtifact,
  validateRefreshTarBeforeExtract,
} from "./traffic-sign-artifact.mjs";

const hasTar = (() => {
  try {
    execFileSync("tar", ["--version"], { stdio: "pipe" });
    return true;
  } catch {
    return false;
  }
})();

function makeTarGz(dir, members) {
  const listFile = path.join(dir, "files.txt");
  writeFileSync(listFile, `${members.map((m) => m.rel).join("\n")}\n`, "utf8");
  for (const { rel, content } of members) {
    const abs = path.join(dir, rel);
    fs.mkdirSync(path.dirname(abs), { recursive: true });
    if (content !== undefined) {
      writeFileSync(abs, content, "utf8");
    }
  }
  const out = path.join(dir, "artifact.tar.gz");
  execFileSync("tar", ["--posix", "-czf", out, "--files-from", listFile], { cwd: dir });
  return out;
}

describe("assertSafeArchiveMember", () => {
  it("rejects traversal", () => {
    assert.throws(() => assertSafeArchiveMember("../.github/workflows/hack.yml"), /traversal/i);
    assert.throws(() => assertSafeArchiveMember("app/../../../etc/passwd"), /traversal|allowlist/i);
  });

  it("rejects workflow paths", () => {
    assert.throws(() => assertSafeArchiveMember(".github/scripts/evil.mjs"), /allowlist/i);
  });

  it("accepts catalogue paths", () => {
    assertSafeArchiveMember("app/src/commonMain/composeResources/files/traffic_signs/DE.json");
  });

  it("rejects absolute and Windows-drive paths", () => {
    assert.throws(() => assertSafeArchiveMember("/etc/passwd"), /Absolute archive member/i);
    assert.throws(() => assertSafeArchiveMember("C:/Windows/system.ini"), /Absolute archive member/i);
  });
});

describe("assertTarTypeAllowsExtract", () => {
  it("allows regular files only", () => {
    assertTarTypeAllowsExtract("-");
    assert.throws(() => assertTarTypeAllowsExtract("l"), /not allowed/i);
    assert.throws(() => assertTarTypeAllowsExtract("d"), /not allowed/i);
    assert.throws(() => assertTarTypeAllowsExtract("b"), /not allowed/i);
  });
});

describe("validateRefreshArtifact", () => {
  it("requires manifest and tar members to match exactly", () => {
    const manifest = ["app/build.gradle.kts", "tools/traffic-sign-parity/package.json"];
    validateRefreshArtifact(manifest, manifest);
    assert.throws(
      () => validateRefreshArtifact([...manifest, "evil"], manifest),
      /mismatch/,
    );
    assert.throws(() => validateRefreshArtifact(["app/build.gradle.kts", "app/build.gradle.kts"], manifest), /Duplicate/);
  });
});

(hasTar ? describe : describe.skip)("manipulated tar archives", () => {
  it("rejects symlink member in archive", () => {
    const dir = mkdtempSync(path.join(os.tmpdir(), "ts-art-"));
    const good = path.join(dir, "app/build.gradle.kts");
    fs.mkdirSync(path.dirname(good), { recursive: true });
    writeFileSync(good, "pin", "utf8");
    const link = path.join(dir, "app/src/commonMain/composeResources/files/traffic_signs/DE.json");
    fs.mkdirSync(path.dirname(link), { recursive: true });
    try {
      symlinkSync("/etc/passwd", link);
    } catch {
      return;
    }
    const tarPath = makeTarGz(dir, [
      { rel: "app/build.gradle.kts", content: "pin" },
      { rel: "app/src/commonMain/composeResources/files/traffic_signs/DE.json" },
    ]);
    const manifest = ["app/build.gradle.kts", "app/src/commonMain/composeResources/files/traffic_signs/DE.json"];
    assert.throws(() => validateRefreshTarBeforeExtract(tarPath, manifest), /Symlink|not allowed|regular file/i);
  });

  it("accepts good archive and apply copies regular files only", () => {
    const srcDir = mkdtempSync(path.join(os.tmpdir(), "ts-art-src-"));
    const repo = mkdtempSync(path.join(os.tmpdir(), "ts-art-repo-"));
    const rel = "tools/traffic-sign-parity/package.json";
    const tarPath = makeTarGz(srcDir, [{ rel, content: '{"name":"x"}' }]);
    const manifest = [rel];
    validateRefreshTarBeforeExtract(tarPath, manifest);
    applyRefreshArtifactSafely({ tarGzPath: tarPath, manifestPaths: manifest, repoRoot: repo });
    const dest = path.join(repo, rel);
    assert.equal(readFileSync(dest, "utf8"), '{"name":"x"}');
    assert.equal(inspectTarGzArchive(tarPath).entries.length, 1);
  });

  it("rejects duplicate tar entries", () => {
    const dir = mkdtempSync(path.join(os.tmpdir(), "ts-art-dup-"));
    const file = "app/build.gradle.kts";
    const abs = path.join(dir, file);
    fs.mkdirSync(path.dirname(abs), { recursive: true });
    writeFileSync(abs, "a", "utf8");
    const tar = path.join(dir, "d.tar.gz");
    execFileSync("tar", ["--posix", "-czf", tar, file, file], { cwd: dir });
    assert.throws(() => inspectTarGzArchive(tar), /Duplicate/i);
  });
});