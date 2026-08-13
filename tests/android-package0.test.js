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

test("native Android uses the accepted current-stable platform baseline", () => {
  const appBuild = read("android-app/app/build.gradle.kts");
  const syncBuild = read("android-app/core/data-sync/build.gradle.kts");
  const domainBuild = read("android-app/core/model-domain/build.gradle.kts");
  const timerBuild = read("android-app/feature/timer-engine/build.gradle.kts");

  assert.match(appBuild, /compileSdk = 36/);
  assert.match(appBuild, /minSdk = 36/);
  assert.match(appBuild, /targetSdk = 36/);
  assert.match(syncBuild, /minSdk = 36/);

  for (const moduleBuild of [appBuild, syncBuild, domainBuild, timerBuild]) {
    assert.match(moduleBuild, /jvmToolchain\(25\)/);
    assert.match(moduleBuild, /JvmTarget\.JVM_17/);
  }
});

test("Package 5 keeps local-first records and adds the native routine and timer path", () => {
  const manifest = read("android-app/app/src/main/AndroidManifest.xml");
  const versions = read("android-app/gradle/libs.versions.toml");
  const repository = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/LocalFirstRepository.kt");
  const store = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/LocalStore.kt");
  const sync = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/CloudSync.kt");
  const secrets = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/DeviceConfiguration.kt");
  const backup = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/BusinessBackup.kt");
  const todayRepository = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/TodayRecordRepository.kt");
  const cloudConnection = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/CloudConnectionManager.kt");
  const todayScreen = read("android-app/app/src/main/java/io/s2qtech/shenk/TodayScreen.kt");
  const checkinSheet = read("android-app/app/src/main/java/io/s2qtech/shenk/CheckInSheets.kt");
  const reminders = read("android-app/app/src/main/java/io/s2qtech/shenk/ReminderSettings.kt");
  const calendarRepository = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/CalendarRecordRepository.kt");
  const calendarScreen = read("android-app/app/src/main/java/io/s2qtech/shenk/CalendarScreen.kt");
  const dataScreen = read("android-app/app/src/main/java/io/s2qtech/shenk/DataScreen.kt");
  const routineModels = read("android-app/core/model-domain/src/main/kotlin/io/s2qtech/shenk/model/RoutineModels.kt");
  const timerRepositories = read("android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/TimerRepositories.kt");
  const timerRuntime = read("android-app/feature/timer-engine/src/main/kotlin/io/s2qtech/shenk/timer/TimerRuntime.kt");
  const trainingScreen = read("android-app/app/src/main/java/io/s2qtech/shenk/TrainingScreen.kt");
  const timerPlatform = read("android-app/app/src/main/java/io/s2qtech/shenk/NativeTimerPlatform.kt");

  assert.match(manifest, /android\.permission\.INTERNET/);
  assert.match(manifest, /android\.permission\.ACCESS_NETWORK_STATE/);
  assert.match(manifest, /android\.intent\.action\.TTS_SERVICE/);
  assert.match(manifest, /android:icon="@mipmap\/ic_launcher"/);
  assert.match(manifest, /android:roundIcon="@mipmap\/ic_launcher_round"/);
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
  assert.match(todayRepository, /persistBatchAndEnqueue/);
  assert.match(cloudConnection, /withContext\(Dispatchers\.IO\)/);
  assert.match(cloudConnection, /CloudConnectionFailure\.INVALID_MIGRATION_CODE/);
  assert.match(todayScreen, /TodayRoute/);
  assert.match(checkinSheet, /MorningCheckInSheet/);
  assert.match(checkinSheet, /PreWorkoutSheet/);
  assert.match(reminders, /MissingMorningWorker/);
  assert.match(calendarRepository, /observeMonth/);
  assert.match(calendarRepository, /persistAndEnqueue\(outgoing, SharedEntityOwner\.RECORD\)/);
  assert.match(calendarRepository, /future|knownKeys|TRAINING_LOG_KEYS/);
  assert.match(calendarScreen, /RecordEditPolicy/);
  assert.match(calendarScreen, /TrainingLogEditorSheet/);
  assert.match(dataScreen, /TrendCanvas/);
  assert.match(routineModels, /requiredEnum<RoutineScene>\("scene"\)/);
  assert.match(routineModels, /requiredEnum<RoutineRole>\("role"\)/);
  assert.match(timerRepositories, /SharedEntityOwner\.TIMER/);
  assert.match(timerRuntime, /restoreTimerSnapshot/);
  assert.match(timerRuntime, /execution\.sideSeconds/);
  assert.match(trainingScreen, /RoutineLibraryScreen/);
  assert.match(trainingScreen, /PostWorkoutSheet/);
  assert.match(trainingScreen, /NextActionStrip/);
  assert.match(timerPlatform, /AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK/);
});

test("launcher and splash artwork follow the system light and dark theme", () => {
  const launcher = read("android-app/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml");
  const roundLauncher = read("android-app/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml");
  const styles = read("android-app/app/src/main/res/values/styles.xml");
  const darkColors = read("android-app/app/src/main/res/values-night/colors.xml");
  const lightArtwork = path.join(root, "android-app/app/src/main/res/drawable-nodpi/ic_shenk_launcher.png");
  const darkArtwork = path.join(root, "android-app/app/src/main/res/drawable-night-nodpi/ic_shenk_launcher.png");
  const lightSplash = path.join(root, "android-app/app/src/main/res/drawable-nodpi/ic_shenk_splash.png");
  const darkSplash = path.join(root, "android-app/app/src/main/res/drawable-night-nodpi/ic_shenk_splash.png");

  for (const adaptiveIcon of [launcher, roundLauncher]) {
    assert.match(adaptiveIcon, /background android:drawable="@drawable\/ic_shenk_launcher"/);
    assert.match(adaptiveIcon, /foreground android:drawable="@android:color\/transparent"/);
    assert.match(adaptiveIcon, /monochrome android:drawable="@drawable\/ic_shenk_monochrome_bitmap"/);
  }
  assert.ok(fs.statSync(lightArtwork).size > 0);
  assert.ok(fs.statSync(darkArtwork).size > 0);
  assert.ok(fs.statSync(lightSplash).size > 0);
  assert.ok(fs.statSync(darkSplash).size > 0);
  assert.match(styles, /windowSplashScreenAnimatedIcon">@drawable\/ic_shenk_splash/);
  assert.match(styles, /windowSplashScreenBackground">@color\/ic_launcher_background/);
  assert.match(darkColors, /<color name="ic_launcher_background">#021227<\/color>/);
});

test("Package 6 keeps advanced AI exchange provider-neutral and clipboard-only", () => {
  const manifest = read("android-app/app/src/main/AndroidManifest.xml");
  const mainActivity = read("android-app/app/src/main/java/io/s2qtech/shenk/MainActivity.kt");
  const planningScreen = read("android-app/app/src/main/java/io/s2qtech/shenk/PlanningScreen.kt");
  const webApp = read("src/app.js");
  const constraints = read("docs/development-constraints.md");
  const guardrails = JSON.parse(read("governance/guardrails.json"));

  assert.match(planningScreen, /copy-weekly-feedback/);
  assert.match(planningScreen, /clipboard\.setText/);
  assert.match(planningScreen, /plan-patch-input/);
  assert.doesNotMatch(planningScreen, /Intent\.ACTION_SEND|share-weekly-feedback|分享到 ChatGPT/);
  assert.doesNotMatch(manifest, /android\.intent\.action\.SEND/);
  assert.doesNotMatch(mainActivity, /Intent\.ACTION_SEND|Intent\.EXTRA_TEXT/);
  assert.doesNotMatch(webApp, /<section class="settings-block mcp-connect-block">/);
  assert.match(constraints, /第一期高级 AI 协作只允许规范化摘要复制/);
  assert.ok(guardrails.requiredInvariants.some((item) => item.id === "G-AI-003"));
});

test("native CI gates emulator tests behind Package 8 foundation verification", () => {
  const workflow = read(".github/workflows/android-native.yml");
  const instrumentationScript = read("android-app/ci/run-instrumentation.sh");

  assert.match(workflow, /needs: verify/);
  assert.match(workflow, /package8FoundationCheck/);
  assert.match(workflow, /timeout-minutes: 30/);
  assert.match(workflow, /java-version: "25"/);
  assert.match(workflow, /api-level: 36/);
  assert.match(workflow, /Build instrumentation APKs before emulator boot/);
  assert.match(workflow, /Enable KVM for Android emulator/);
  assert.match(workflow, /udevadm trigger --name-match=kvm/);
  assert.match(workflow, /target: default/);
  assert.match(workflow, /ram-size: 2048M/);
  assert.doesNotMatch(workflow, /profile: pixel_6/);
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
