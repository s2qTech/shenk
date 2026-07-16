"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.resolve(__dirname, "..");
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), "utf8");

test("native Android Package 0 keeps the accepted small module graph", () => {
  const settings = read("android-app/settings.gradle.kts");
  const requiredModules = [
    ':app',
    ':core:model-domain',
    ':core:data-sync',
    ':feature:timer-engine'
  ];

  for (const moduleName of requiredModules) {
    assert.ok(settings.includes(`include("${moduleName}")`), `missing ${moduleName}`);
  }
  assert.equal((settings.match(/include\(/g) || []).length, requiredModules.length);
});

test("Package 0 remains diagnostic-only and Contract v1 only", () => {
  const app = read("android-app/app/src/main/java/io/s2qtech/shenk/MainActivity.kt");
  const versions = read("android-app/gradle/libs.versions.toml");
  const fixture = JSON.parse(read("contracts/conformance/android-package0.json"));

  assert.match(app, /仅用于验证原生工程/);
  assert.match(versions, /agp = "9\.2\.1"/);
  assert.equal(fixture.synthetic, true);
  assert.equal(fixture.contractVersion, "1.0");
  assert.deepEqual(fixture.expectedDayPriority, [
    "training_logs",
    "effective_formal_plan",
    "local_fallback_suggestion"
  ]);
  assert.equal(fixture.routine.scene, "recovery");
  assert.equal(fixture.routine.role, "recovery");
  assert.equal(fixture.trainingLog.timerSessionId, fixture.timerSession.id);
});

test("Capacitor prototype is visibly frozen", () => {
  const readme = read("mobile/README.md");
  assert.match(readme, /验证原型（冻结）/);
  assert.match(readme, /不再接收生产功能/);
  assert.match(readme, /android-app\//);
});
