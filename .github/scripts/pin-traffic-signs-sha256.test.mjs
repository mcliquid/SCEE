import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { readPins, tarballUrl } from "./pin-traffic-signs-sha256.mjs";

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