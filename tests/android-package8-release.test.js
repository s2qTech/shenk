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

  assert.match(properties, /SHENK_VERSION_CODE=11/);
  assert.match(properties, /SHENK_VERSION_NAME=1\.0\.0-rc\.1/);
  assert.match(appBuild, /SHENK_REQUIRE_RELEASE_SIGNING/);
  assert.match(appBuild, /Release keystore must be stored outside the repository/);
  assert.match(appBuild, /isDebuggable = false/);
  assert.match(ignore, /\*\.jks/);
  assert.match(ignore, /\*\.keystore/);
  assert.match(ignore, /release-signing\.properties/);
  assert.doesNotMatch(example, /BEGIN PRIVATE KEY|api[_-]?key\s*=\s*[A-Za-z0-9_-]{20,}/i);
});

test("P8.8 release candidate is signed, reproducible, and installed without bypassing identity checks", () => {
  const rootBuild = read("android-app/build.gradle.kts");
  const appBuild = read("android-app/app/build.gradle.kts");
  const buildScript = read("android-app/ci/build-private-release.ps1");
  const deviceScript = read("android-app/ci/verify-private-release-device.ps1");

  assert.match(rootBuild, /tasks\.register\("package8ReleaseCandidateCheck"\)/);
  assert.match(rootBuild, /:app:verifySignedReleaseConfiguration/);
  assert.match(appBuild, /SHENK_REQUIRE_RELEASE_SIGNING=true/);
  assert.match(buildScript, /status --porcelain/);
  assert.match(buildScript, /package8ReleaseCandidateCheck/);
  assert.match(buildScript, /Signer #1 certificate SHA-256 digest/);
  assert.match(buildScript, /sourceRevision/);
  assert.match(buildScript, /rollbackRevision/);
  assert.match(deviceScript, /Signing certificate mismatch/);
  assert.match(deviceScript, /install -r/);
  assert.match(deviceScript, /firstInstallTime/);
  assert.doesNotMatch(deviceScript, /adb uninstall|pm clear/);
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

test("P8.5 enforces contrast, scalable controls, TalkBack alternatives, and reduced motion", () => {
  const theme = read("android-app/app/src/main/java/io/s2qtech/shenk/ShenkTheme.kt");
  const contrastTest = read("android-app/app/src/test/java/io/s2qtech/shenk/ShenkThemeContrastTest.kt");
  const app = read("android-app/app/src/main/java/io/s2qtech/shenk/ShenkApp.kt");
  const activity = read("android-app/app/src/main/java/io/s2qtech/shenk/MainActivity.kt");
  const today = read("android-app/app/src/main/java/io/s2qtech/shenk/TodayScreen.kt");
  const calendar = read("android-app/app/src/main/java/io/s2qtech/shenk/CalendarScreen.kt");
  const training = read("android-app/app/src/main/java/io/s2qtech/shenk/TrainingScreen.kt");
  const deviceTest = read("android-app/app/src/androidTest/java/io/s2qtech/shenk/AccessibilityContractInstrumentedTest.kt");

  assert.match(theme, /ShenkLightColors/);
  assert.match(theme, /ShenkDarkColors/);
  assert.match(contrastTest, /ratio >= 4\.5/);
  assert.match(app, /CustomAccessibilityAction\("转到日历"\)/);
  assert.match(app, /CustomAccessibilityAction\("转到今天"\)/);
  assert.match(app, /CustomAccessibilityAction\("转到训练"\)/);
  assert.match(activity, /ValueAnimator\.areAnimatorsEnabled\(\)/);
  assert.match(today, /fontScale >= 1\.3f/);
  assert.match(today, /heightIn\(min = 52\.dp\)/);
  assert.match(calendar, /clearAndSetSemantics/);
  assert.match(training, /CustomAccessibilityAction\("删除\$\{routine\.title\}"\)/);
  assert.match(deviceTest, /rootInActiveWindow/);
});

test("P8.6 keeps backup restore additive, secret-free, and reachable through SAF", () => {
  const backup = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/BusinessBackup.kt");
  const repository = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/LocalFirstRepository.kt");
  const today = read("android-app/app/src/main/java/io/s2qtech/shenk/TodayScreen.kt");
  const settings = read("android-app/app/src/main/java/io/s2qtech/shenk/AppSettingsSheet.kt");

  assert.match(backup, /shenk_business_backup\/v1/);
  assert.match(backup, /MAX_BACKUP_BYTES/);
  assert.match(backup, /EntityOwnership\.knownEntities/);
  assert.match(backup, /duplicate record/);
  assert.match(backup, /profileaccesskey/);
  assert.match(backup, /authorization/);
  assert.match(repository, /Backup restore is a merge, never an implicit replace/);
  assert.match(repository, /skippedExisting/);
  assert.match(today, /ActivityResultContracts\.CreateDocument\("application\/json"\)/);
  assert.match(today, /ActivityResultContracts\.OpenDocument\(\)/);
  assert.match(settings, /数据备份/);
  assert.match(settings, /绝不覆盖本机现有修改/);
});

test("P8.6 aligns encrypted migration parameters and rolls back partial local replacement", () => {
  const androidCrypto = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/SyncProfileCrypto.kt");
  const webCrypto = read("src/sync-profile-core.js");
  const connection = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/CloudConnectionManager.kt");
  const keystoreTest = read("android-app/core/data-sync/src/androidTest/kotlin/io/s2qtech/shenk/sync/KeystoreSecretStoreInstrumentedTest.kt");

  assert.match(androidCrypto, /shenk_sync_profile\/v1/);
  assert.match(webCrypto, /shenk_sync_profile\/v1/);
  assert.match(androidCrypto, /ITERATIONS = 210_000/);
  assert.match(webCrypto, /210000/);
  assert.match(androidCrypto, /SALT_BYTES = 16/);
  assert.match(androidCrypto, /IV_BYTES = 12/);
  assert.match(webCrypto, /new Uint8Array\(16\)/);
  assert.match(webCrypto, /new Uint8Array\(12\)/);
  assert.match(connection, /preferences\.setApiBase\(""\)/);
  assert.match(connection, /previousShenkToken/);
  assert.match(connection, /previousTimerToken/);
  assert.match(keystoreTest, /shenk_device_preferences_test_/);
  assert.doesNotMatch(keystoreTest, /DevicePreferencesStore\(context\)/);
});

test("P8.6 migration and Worker tests generate transient access codes at runtime", () => {
  const sources = [
    read("tests/sync-profile-core.test.js"),
    read("tests/sync-transfer.test.js"),
    read("tests/worker-security.test.js")
  ];
  const literalCredential = /(migrationCode|profileAccessKey|transferCode)\s*=\s*["'][A-Za-z0-9_-]{20,}["']/;

  sources.forEach((source) => assert.doesNotMatch(source, literalCredential));
});
