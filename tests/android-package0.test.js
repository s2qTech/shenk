"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.resolve(__dirname, "..");
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), "utf8");

const walkFiles = (directory) => fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
  const entryPath = path.join(directory, entry.name);
  if (entry.isDirectory()) {
    if (entry.name === "build" || entry.name === ".gradle") return [];
    return walkFiles(entryPath);
  }
  return [entryPath];
});

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
  assert.doesNotMatch(versions, /org\.jetbrains\.kotlin\.android/);
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

test("native Kotlin sources keep the repository formatting baseline", () => {
  const sourceFiles = walkFiles(path.join(root, "android-app"))
    .filter((file) => file.endsWith(".kt") || file.endsWith(".kts"));
  const violations = [];

  for (const file of sourceFiles) {
    const text = fs.readFileSync(file, "utf8");
    const relative = path.relative(root, file);
    for (const [index, line] of text.split(/\r?\n/).entries()) {
      if (line.includes("\t")) violations.push(`${relative}:${index + 1}: tab`);
      if (line !== line.trimEnd()) violations.push(`${relative}:${index + 1}: trailing whitespace`);
    }
    if (text.length > 0 && !text.endsWith("\n")) {
      violations.push(`${relative}: missing final newline`);
    }
  }

  assert.deepEqual(violations, []);
});

test("Capacitor prototype is visibly frozen", () => {
  const readme = read("mobile/README.md");
  const workflow = read(".github/workflows/mobile-android.yml");
  assert.match(readme, /验证原型（冻结）/);
  assert.match(readme, /不再接收生产功能/);
  assert.match(readme, /android-app\//);
  assert.doesNotMatch(workflow, /assembleDebug/);
  assert.match(workflow, /pnpm run test/);
  assert.match(workflow, /pnpm run android:sync/);
});
