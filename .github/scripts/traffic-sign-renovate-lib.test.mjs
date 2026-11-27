import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  assertBotCommitAllowlist,
  assertRenovateInitialPrFiles,
  isBotCommitPathAllowed,
  parseReportHasCatalogueFileChanges,
  parseReportNeedsReview,
  shouldApplyCatalogueUpdate,
  shouldRunCoordinatedCatalogueRefresh,
  dryRunIndicatesIdentical,
  trafficSignVersionChanged,
} from "./traffic-sign-renovate-lib.mjs";

describe("isBotCommitPathAllowed", () => {
  it("allows gradle pin and catalogue tree", () => {
    assert.equal(isBotCommitPathAllowed("app/build.gradle.kts"), true);
    assert.equal(
      isBotCommitPathAllowed("app/src/commonMain/composeResources/files/traffic_signs/DE.json"),
      true,
    );
    assert.equal(
      isBotCommitPathAllowed("app/traffic-signs-update/history/0.8.0-to-0.8.1.md"),
      true,
    );
    assert.equal(isBotCommitPathAllowed("tools/traffic-sign-parity/reference.jsonl"), true);
  });

  it("denies commonTest and workflow changes", () => {
    assert.equal(
      isBotCommitPathAllowed(
        "app/src/commonTest/kotlin/de/westnordost/streetcomplete/osm/traffic_sign/TrafficSignCatalogueTest.kt",
      ),
      false,
    );
    assert.equal(isBotCommitPathAllowed(".github/workflows/unit-test.yml"), false);
  });
});

describe("assertBotCommitAllowlist", () => {
  it("accepts only allowlisted paths", () => {
    assertBotCommitAllowlist([
      "app/build.gradle.kts",
      "app/src/commonMain/composeResources/files/traffic_signs/VERSION",
    ]);
  });

  it("rejects foreign paths", () => {
    assert.throws(
      () => assertBotCommitAllowlist(["gradle/libs.versions.toml"]),
      /outside the bot allowlist/,
    );
  });
});

describe("shouldRunCoordinatedCatalogueRefresh", () => {
  const base = `val trafficSignsVersion = "0.8.0"\nval trafficSignsSha256 = "a"`;
  const head = `val trafficSignsVersion = "0.8.1"\nval trafficSignsSha256 = "b"`;

  it("skips NSI-only when version unchanged", () => {
    assert.equal(
      shouldRunCoordinatedCatalogueRefresh(base, base, ["app/build.gradle.kts"]),
      false,
    );
  });

  it("runs on initial Renovate PR (only build.gradle.kts)", () => {
    assert.equal(
      shouldRunCoordinatedCatalogueRefresh(base, head, ["app/build.gradle.kts"]),
      true,
    );
  });

  it("skips after bot refresh despite base still behind SCEE-mc", () => {
    assert.equal(
      shouldRunCoordinatedCatalogueRefresh(base, head, [
        "app/build.gradle.kts",
        "app/src/commonMain/composeResources/files/traffic_signs/DE.json",
        "tools/traffic-sign-parity/reference.jsonl",
      ]),
      false,
    );
  });
});

describe("trafficSignVersionChanged", () => {
  const base = `val trafficSignsVersion = "0.8.0"\nval trafficSignsSha256 = "a"`;
  const head = `val trafficSignsVersion = "0.8.1"\nval trafficSignsSha256 = "b"`;
  it("detects version bump", () => {
    assert.equal(trafficSignVersionChanged(base, head), true);
  });
  it("is false when only sha would change", () => {
    const head2 = `val trafficSignsVersion = "0.8.0"\nval trafficSignsSha256 = "b"`;
    assert.equal(trafficSignVersionChanged(base, head2), false);
  });
});

describe("assertRenovateInitialPrFiles", () => {
  it("allows only build.gradle.kts", () => {
    assertRenovateInitialPrFiles(["app/build.gradle.kts"]);
  });

  it("allows completed refresh file set", () => {
    assertRenovateInitialPrFiles([
      "app/build.gradle.kts",
      "tools/traffic-sign-parity/package.json",
      "app/src/commonMain/composeResources/files/traffic_signs/DE.json",
    ]);
  });

  it("rejects commonTest in PR", () => {
    assert.throws(
      () =>
        assertRenovateInitialPrFiles([
          "app/build.gradle.kts",
          "app/src/commonTest/kotlin/foo/Test.kt",
        ]),
      /Unexpected files/,
    );
  });

  it("rejects workflow or script paths on initial Renovate PR", () => {
    assert.throws(
      () => assertRenovateInitialPrFiles(["app/build.gradle.kts", ".github/workflows/evil.yml"]),
      /Unexpected files/,
    );
  });
});

describe("parseReportNeedsReview", () => {
  it("returns empty when section missing", () => {
    assert.deepEqual(parseReportNeedsReview("# no review\n"), {
      needsReview: false,
      reasons: [],
    });
  });

  it("parses bullet reasons", () => {
    const md = `## Needs a manual review
- DE: rule sign: 250
- DE: 1 signs were removed: foo

## Import per country
`;
    const r = parseReportNeedsReview(md);
    assert.equal(r.needsReview, true);
    assert.equal(r.reasons.length, 2);
  });
});

describe("shouldApplyCatalogueUpdate", () => {
  it("skips apply when dry-run log says identical even if stale report lists changes", () => {
    const dry = "Traffic sign catalogues 0.8.1 -> 0.8.1 (identical to the existing files)\n";
    const report = "- files: 3 added, 11 changed, 0 removed\n";
    assert.equal(shouldApplyCatalogueUpdate(dry, report), false);
  });

  it("applies when dry-run shows changes", () => {
    const dry = "0.8.0 -> 0.8.1: DE 293/293\n";
    const report = "- files: 3 added, 0 changed, 0 removed\n";
    assert.equal(shouldApplyCatalogueUpdate(dry, report), true);
  });
});

describe("dryRunIndicatesIdentical", () => {
  it("detects identical phrase", () => {
    assert.equal(dryRunIndicatesIdentical("(identical to the existing files)"), true);
  });
});

describe("parseReportHasCatalogueFileChanges", () => {
  it("detects no file changes", () => {
    const md = "# u\n- files: 0 added, 0 changed, 0 removed\n";
    assert.equal(parseReportHasCatalogueFileChanges(md), false);
  });

  it("detects catalogue diff", () => {
    const md = "# u\n- files: 3 added, 11 changed, 0 removed\n";
    assert.equal(parseReportHasCatalogueFileChanges(md), true);
  });
});