const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');

test('calendar observes and opens the review for the selected date', () => {
  const calendar = read('android-app/app/src/main/java/io/s2qtech/shenk/CalendarScreen.kt');

  assert.match(calendar, /dailyReviewRepository\.observe\(date\)/);
  assert.match(calendar, /DailyReviewSheet\(\s*date = date,/);
  assert.match(calendar, /canReview = !date\.isAfter\(today\)/);
});

test('calendar day overview combines guidance, body metrics, and a compact daily review', () => {
  const calendar = read('android-app/app/src/main/java/io/s2qtech/shenk/CalendarScreen.kt');
  const repository = read('android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/CalendarRecordRepository.kt');

  assert.match(calendar, /GuidanceSummary\(/);
  assert.match(calendar, /details\.bodyMetrics\.forEach \{ metric -> DailyMetricValue\(metric\) \}/);
  assert.match(repository, /records\.observeActive\("body_metrics"\)/);
  assert.match(repository, /val bodyMetrics: List<DailyMetric>/);
  assert.doesNotMatch(calendar, /details\.actualLogs\.forEachIndexed/);
  assert.match(calendar, /if \(canEdit && details\.actualLogs\.isEmpty\(\)\)/);
  assert.match(calendar, /CalendarReviewSummary\(/);
  assert.match(calendar, /Text\(\s*review\.conclusion,/);
  assert.doesNotMatch(calendar, /review\.actions\.take\(1\)/);
  assert.doesNotMatch(calendar, /review\.conclusion,[\s\S]{0,200}maxLines/);
});

test('daily review sheet is date aware and supports returning to date details', () => {
  const sheet = read('android-app/app/src/main/java/io/s2qtech/shenk/DailyReviewSheet.kt');

  assert.match(sheet, /val isToday = date == LocalDate\.now\(\)/);
  assert.match(sheet, /val reviewLabel = if \(isToday\) "今日简评" else "当日简评"/);
  assert.match(sheet, /onBack: \(\(\) -> Unit\)\? = null/);
  assert.match(sheet, /Text\("返回日期详情"\)/);
  assert.match(sheet, /Text\("今日评价"/);
  assert.match(sheet, /Text\("复盘分析"/);
  assert.match(sheet, /Text\("后续修正"/);
  assert.match(sheet, /复盘当天执行，指出问题并给出后续修正/);
});

test('a completed daily review hides stale generation and retry state', () => {
  const sheet = read('android-app/app/src/main/java/io/s2qtech/shenk/DailyReviewSheet.kt');
  const today = read('android-app/app/src/main/java/io/s2qtech/shenk/TodayScreen.kt');
  const store = read('android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/LocalStore.kt');

  assert.match(sheet, /if \(state\.review == null && generating\)/);
  assert.match(sheet, /"PENDING", "RUNNING", "AWAITING_SERVER"/);
  assert.match(today, /"PENDING", "RUNNING", "AWAITING_SERVER"/);
  assert.match(store, /state IN \('PENDING', 'RETRY', 'AWAITING_SERVER'\)/);
  assert.match(sheet, /else if \(state\.review == null && state\.jobState in setOf\("RETRY", "FAILED"\)\)/);
  assert.match(sheet, /else if \(state\.review == null && !providerReady\)/);
  assert.match(sheet, /else if \(state\.review == null\)/);
});

test('package 7 contract documents historical review behavior', () => {
  const packageDoc = read('docs/android-package7-daily-review.md');

  assert.match(packageDoc, /Historical generation always uses the normalized 14-day snapshot ending on the selected date/);
  assert.match(packageDoc, /future dates cannot generate reviews/);
  assert.match(packageDoc, /independent of the training-log correction window/);
  assert.match(packageDoc, /complete conclusion/);
  assert.match(packageDoc, /must not phrase an already completed day as pre-training guidance/);
});

test('daily review snapshots version retrospective review policy', () => {
  const repository = read('android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/DailyReviewRepository.kt');

  assert.match(repository, /put\("reviewPolicyVersion", JsonPrimitive\(2\)\)/);
});

test('daily review generation is long-running, idempotent, and exposes retry only after terminal failure', () => {
  const repository = read('android-app/core/data-sync/src/main/kotlin/io/s2qtech/shenk/sync/DailyReviewRepository.kt');
  const androidWorker = read('android-app/app/src/main/java/io/s2qtech/shenk/DailyReviewWork.kt');
  const application = read('android-app/app/src/main/java/io/s2qtech/shenk/ShenkApplication.kt');
  const wrangler = read('wrangler.toml');
  const worker = read('cloudflare/worker.js');
  const sheet = read('android-app/app/src/main/java/io/s2qtech/shenk/DailyReviewSheet.kt');
  const today = read('android-app/app/src/main/java/io/s2qtech/shenk/TodayScreen.kt');

  assert.match(repository, /\.readTimeout\(0, TimeUnit\.SECONDS\)/);
  assert.match(repository, /"AWAITING_SERVER"/);
  assert.match(androidWorker, /ExistingWorkPolicy\.APPEND_OR_REPLACE/);
  assert.match(application, /policy = ExistingWorkPolicy\.KEEP/);
  assert.match(worker, /42_066/);
  assert.match(worker, /8192/);
  assert.match(worker, /timeoutMs: 0/);
  assert.match(worker, /class DailyReviewWorkflow extends WorkflowEntrypoint/);
  assert.match(worker, /sealDailyReviewPayload/);
  assert.match(worker, /AI_JOB_ENCRYPTION_KEY/);
  assert.match(worker, /DAILY_REVIEW_WORKFLOW\.create/);
  assert.match(wrangler, /binding = "DAILY_REVIEW_WORKFLOW"/);
  assert.match(worker, /ai_daily_review_jobs/);
  assert.match(worker, /isRepairableDailyReviewError/);
  assert.match(worker, /"ai_provider_timeout"/);
  assert.match(sheet, /"generation_timeout", "ai_provider_timeout"/);
  assert.match(sheet, /"ai_provider_job_expired", "ai_provider_job_abandoned"/);
  assert.match(today, /dailyReviewFailureMessage\(/);
});
