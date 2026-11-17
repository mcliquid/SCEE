import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  assertOnlySha256PinChange,
  readPins,
  tarballUrl,
} from "./pin-traffic-signs-sha256.mjs";

describe("readPins", () => {
  it("parses version and sha256 from build.gradle.kts pins", () => {
    const source = `
val trafficSignsVersion = "0.8.0"
val trafficSignsSha256 = "abcdef0123456789"
`.trim();
    assert.deepEqual(readPins(source), { version: "0.8.0", sha256: "abcdef0123456789" });
  });

  it("throws when pins are missing", () => {
    assert.throws(() => readPins('val trafficSignsVersion = "1.0.0"'), /Could not parse/);
  });
});

describe("tarballUrl", () => {
  it("targets the scoped npm package tarball for the version", () => {
    assert.equal(
      tarballUrl("0.9.1"),
      "https://registry.npmjs.org/@osm-traffic-signs/converter/-/converter-0.9.1.tgz",
    );
  });
});

describe("assertOnlySha256PinChange", () => {
  it("accepts identical content", () => {
    assertOnlySha256PinChange("a\nb\n", "a\nb\n");
  });

  it("accepts a single trafficSignsSha256 line change", () => {
    const before = `val trafficSignsVersion = "0.8.0"\nval trafficSignsSha256 = "old"\n`;
    const after = `val trafficSignsVersion = "0.8.0"\nval trafficSignsSha256 = "new"\n`;
    assertOnlySha256PinChange(before, after);
  });

  it("rejects changes to trafficSignsVersion", () => {
    const before = `val trafficSignsVersion = "0.8.0"\nval trafficSignsSha256 = "x"\n`;
    const after = `val trafficSignsVersion = "0.9.0"\nval trafficSignsSha256 = "x"\n`;
    assert.throws(() => assertOnlySha256PinChange(before, after), /trafficSignsSha256/);
  });

  it("rejects multi-line diffs", () => {
    const before = "line1\nline2\n";
    const after = "line1\nchanged\nextra\n";
    assert.throws(() => assertOnlySha256PinChange(before, after), /add or remove lines/);
  });
});