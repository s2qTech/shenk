"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.resolve(__dirname, "..");
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), "utf8");

test("Package 8 centralizes release versioning and keeps private signing external", () => {
  const properties = read("android-app/gradle.properties");
  const appBuild = read("android-app/app/build.gradle.kts");
  const ignore = read(".gitignore");
  const example = read("android-app/release-signing.properties.example");

  assert.match(properties, /SHENK_VERSION_CODE=10/);
  assert.match(properties, /SHENK_VERSION_NAME=0\.8\.2-package8-dev/);
  assert.match(appBuild, /SHENK_REQUIRE_RELEASE_SIGNING/);
  assert.match(appBuild, /Release keystore must be stored outside the repository/);
  assert.match(appBuild, /isDebuggable = false/);
  assert.match(ignore, /\*\.jks/);
  assert.match(ignore, /\*\.keystore/);
  assert.match(ignore, /release-signing\.properties/);
  assert.doesNotMatch(example, /BEGIN PRIVATE KEY|api[_-]?key\s*=\s*[A-Za-z0-9_-]{20,}/i);
});

test("Package 8 release foundation is an explicit CI gate", () => {
  const rootBuild = read("android-app/build.gradle.kts");
  const workflow = read(".github/workflows/android-native.yml");

  assert.match(rootBuild, /tasks\.register\("nativeCheck"\)/);
  assert.match(rootBuild, /tasks\.register\("package8FoundationCheck"\)/);
  assert.match(rootBuild, /:app:verifyReleaseConfiguration/);
  assert.match(rootBuild, /:app:lintRelease/);
  assert.match(rootBuild, /:app:assembleRelease/);
  assert.match(workflow, /package8FoundationCheck/);
  assert.doesNotMatch(workflow, /shenk-package5-debug/);
});

test("Package 8 private release and rollback decisions are documented", () => {
  const packageDoc = read("docs/android-package8-hardening-release.md");
  const adr = read("docs/adr/0009-private-release-and-update.md");

  assert.match(packageDoc, /P8\.0/);
  assert.match(packageDoc, /P8\.8/);
  assert.match(packageDoc, /8 \/ 9/);
  assert.match(adr, /authenticated Worker/i);
  assert.match(adr, /once per 24 hours/i);
  assert.match(adr, /versionCode/i);
  assert.match(adr, /SHA-256/i);
  assert.match(adr, /no silent install/i);
});

test("P8.2 updater is foreground-only, authenticated, verified, and user-confirmed", () => {
  const manager = read("android-app/app/src/main/java/io/s2qtech/shenk/AppUpdateManager.kt");
  const activity = read("android-app/app/src/main/java/io/s2qtech/shenk/MainActivity.kt");
  const manifest = read("android-app/app/src/main/AndroidManifest.xml");
  const worker = read("cloudflare/worker.js");

  assert.match(activity, /postFrameCallback/);
  assert.match(manager, /TimeUnit\.HOURS\.toMillis\(24\)/);
  assert.match(manager, /header\("Authorization", "Bearer \$token"\)/);
  assert.match(manager, /update_sha256_mismatch/);
  assert.match(manager, /update_application_id_mismatch/);
  assert.match(manager, /update_version_mismatch/);
  assert.match(manager, /update_signing_certificate_mismatch/);
  assert.match(manager, /Intent\.ACTION_VIEW/);
  assert.match(manifest, /REQUEST_INSTALL_PACKAGES/);
  assert.match(worker, /\/api\/android\/update\/metadata/);
  assert.match(worker, /\/api\/android\/update\/apk/);
  assert.match(worker, /forbidden_android_update_role/);
});
