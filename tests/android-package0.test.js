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

test("native Android keeps the accepted small module graph", () => {
  const settings = read("android-app/settings.gradle.kts");
  const requiredModules = [
    ":app",
    ":core:model-domain",
    ":core:data-sync",
    ":feature:timer-engine"
  ];

  for (const moduleName of requiredModules) {
    assert.ok(settings.includes(`include("${moduleName}")`), `missing ${moduleName}`);
  }
  assert.equal((settings.match(/include\(/g) || []).length, requiredModules.length);
});

test("Package 2 provides the local-first foundation without feature UI", () => {
  const versions = read("android-app/gradle/libs.versions.toml");
  const repository = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/LocalFirstRepository.kt");
  const store = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/LocalStore.kt");
  const sync = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/CloudSync.kt");
  const secrets = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/DeviceConfiguration.kt");
  const backup = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/BusinessBackup.kt");

  assert.match(versions, /room-runtime/);
  assert.match(versions, /work-runtime-ktx/);
  assert.match(versions, /datastore-preferences/);
  assert.match(repository, /database\.withTransaction/);
  assert.match(repository, /queueInTransaction/);
  assert.match(store, /tableName = "outbox"/);
  assert.match(store, /tableName = "sync_conflicts"/);
  assert.match(sync, /NetworkType\.CONNECTED/);
  assert.match(secrets, /AndroidKeyStore/);
  assert.match(backup, /shenk_business_backup\/v1/);
  assert.doesNotMatch(backup, /SHENK_TOKEN|TIMER_TOKEN|AI_PROVIDER_KEY/);

  const appSources = walkFiles(path.join(root, "android-app/app/src/main"))
    .filter((file) => file.endsWith(".kt"))
    .map((file) => fs.readFileSync(file, "utf8"))
    .join("\n");
  assert.doesNotMatch(appSources, /TodayScreen|CheckInScreen|TrainingScreen|TimerScreen/);
});

test("native CI gates emulator tests behind Package 2 verification", () => {
  const workflow = read(".github/workflows/android-native.yml");
  const instrumentationScript = read("android-app/ci/run-instrumentation.sh");

  assert.match(workflow, /needs: verify/);
  assert.match(workflow, /package2Check/);
  assert.match(workflow, /timeout-minutes: 30/);
  assert.match(workflow, /api-level: 34/);
  assert.match(workflow, /script: \.\/ci\/run-instrumentation\.sh/);
  assert.match(instrumentationScript, /timeout --signal=TERM 18m/);
  assert.match(instrumentationScript, /--no-parallel :core:data-sync:connectedDebugAndroidTest/);
  assert.match(instrumentationScript, /--no-parallel :app:connectedDebugAndroidTest/);
  assert.doesNotMatch(instrumentationScript, /gradlew connectedDebugAndroidTest/);
});

test("instrumentation tests expose void JUnit methods", () => {
  const instrumentationSources = walkFiles(path.join(root, "android-app"))
    .filter((file) => file.includes(`${path.sep}src${path.sep}androidTest${path.sep}`) && file.endsWith(".kt"));

  for (const file of instrumentationSources) {
    const source = fs.readFileSync(file, "utf8");
    assert.doesNotMatch(
      source,
      /@Test\s+fun\s+\w+\s*\([^)]*\)\s*=\s*runBlocking/,
      `${path.relative(root, file)} must use a block body so JUnit receives a void test method`,
    );
  }
});

test("Package 1 contract fixture remains sanitized and explicit", () => {
  const fixture = JSON.parse(read("contracts/conformance/android-package1-v2.json"));
  assert.equal(fixture.synthetic, true);
  assert.equal(fixture.contractVersion, "2.0");
  assert.ok(fixture.supportedContractVersions.includes("1.0"));
  assert.ok(fixture.supportedContractVersions.includes("2.0"));
  assert.equal(fixture.routine.scene, "recovery");
  assert.equal(fixture.routine.role, "recovery");
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
    if (text.length > 0 && !text.endsWith("\n")) violations.push(`${relative}: missing final newline`);
  }

  assert.deepEqual(violations, []);
});

test("Capacitor prototype remains visibly frozen", () => {
  const readme = read("mobile/README.md");
  const workflow = read(".github/workflows/mobile-android.yml");
  assert.match(readme, /android-app\//);
  assert.doesNotMatch(workflow, /assembleDebug/);
  assert.match(workflow, /pnpm run test/);
  assert.match(workflow, /pnpm run android:sync/);
});
