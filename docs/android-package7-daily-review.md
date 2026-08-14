# Android Package 7: Daily AI Review

Status: accepted on Xiaomi 14 on 2026-08-09; progress advanced to `8 / 9`.

Updated: 2026-08-15

## Scope

Package 7 generates one factual daily review after a formal workout, confirmed rest, or an explicit skip. It also supports an explicitly requested partial review when critical status fields are missing. Reasoning is enabled for review generation. The review may explain evidence, point out avoidable inactivity, warn about risk, and create one local suggestion inside the current strategy boundary. It cannot write or alter formal plans, plan adjustments, routine templates, timer facts, training logs, goals, strategies, or body metrics.

## Data and ownership

- `daily_reviews` is owned by `AI_REVIEW` and uses Contract v2.
- Required fields are `id`, `date`, `version`, `status`, `conclusion`, `actions`, `inputDigest`, `provider`, `model`, and `generatedAt`. Optional fields are `assessment` and `localSuggestion`.
- Status is `generated`, `invalidated`, or `failed`.
- The input digest is derived from a normalized 14-day snapshot of sleep, status, pain, body metrics, formal training, effective formal plans, goals, and strategy boundaries.
- `localSuggestion` remains part of `daily_reviews`; it is never copied into a planning entity. Day display priority is actual record > effective formal plan > AI local suggestion > deterministic offline fallback.
- A changed digest creates a new version. The previous generated review is invalidated only when the replacement is successfully generated.
- The Android AI job queue and the Worker execution job are durable implementation state, not shared business entities and not part of the normal sync outbox. The Worker job stores only job state, normalized review output, bounded error codes, finish reason, and numeric token usage; it never stores the snapshot or provider key.

## Security and transport

- Phase 1 fixes the user-facing provider to DeepSeek V4 Flash. The user configures only the API key; provider URL and model are canonical application defaults and are not exposed as routine settings.
- The Worker transport remains OpenAI Chat Completions-compatible internally so a later provider adapter can be added without changing review ownership, queue, validation, confirmation, or audit semantics.
- The provider key is stored only through Android Keystore.
- Android sends the provider key to the Shenk Worker over TLS for the current request only.
- The Worker validates the endpoint, rejects local/private/credential-bearing URLs, does not log or store the key, calls the provider, validates bounded JSON output, and returns only normalized review fields.
- Daily-review calls keep reasoning enabled, request JSON output explicitly, and use a 42,066-token primary completion budget: twice the observed 21,033-token real-device request baseline. Because DeepSeek `max_tokens` excludes input, the actual total capacity is intentionally greater than two times the observed total. A structurally incomplete, empty, or length-truncated response receives one internal non-reasoning repair pass with an 8,192-token budget; this remains one user-visible job.
- The Worker records DeepSeek's numeric prompt/cache/completion/reasoning/total usage, finish reason, and upstream request count for each execution job. It does not record prompt content, snapshot facts, response bodies, or credentials.
- Secrets are excluded from Room business rows, DataStore, contracts, logs, fixtures, backups, URLs, and cloud records.
- Provider setup belongs to the app settings surface. The review sheet never exposes routine credential controls; after a successful test it shows only connection status and an explicit replace-key action.
- A replacement key is tested before it replaces the current Keystore value. A failed replacement leaves the last working key intact.

## Information hierarchy

- Only the short conclusion is attached to the primary Today guidance card, directly after the effective plan or actual training summary.
- Calendar date details subscribe to the selected date's current `daily_reviews` record and place the complete conclusion in the same day-overview surface as the effective guidance and formal training details. The compact date card does not repeat action items and does not truncate the conclusion; actions remain in the full review sheet.
- Any past or current date can open the same review detail and generation flow. Historical generation always uses the normalized 14-day snapshot ending on the selected date; future dates cannot generate reviews.
- Review generation is independent of the training-log correction window. An old day may receive a review even when its formal training record is already read-only.
- Pending generation is visible immediately on that card and in the detail sheet. `PENDING`, `RUNNING`, and `AWAITING_SERVER` are all one non-retryable generating state. The UI never exposes retry until the Worker has recorded `FAILED` for that job.
- Opening a missing review is the generation action when the provider and critical inputs are ready: it queues once and immediately shows the durable pending state. Missing critical inputs still require an explicit "按现有事实生成" confirmation, and existing, pending, retrying, or completed jobs are never auto-duplicated.
- The detail sheet contains the professional assessment, bounded action list, cautions, optional local suggestion, and secondary evidence. Raw status facts are supporting evidence and must not dominate the review.
- The Worker prompt requires a concise retrospective conclusion and professional synthesis that adds interpretation beyond the already visible morning-status and training summaries. It evaluates what was completed, whether execution matched the plan, what problems or causes appeared, and how to correct the next session or following days. It must not phrase an already completed day as pre-training guidance.

## Missing data and offline behavior

- Missing data stays missing. Morning status, sleep duration, sleep quality, energy, and fatigue are critical review inputs.
- Automatic generation does not assume values and does not queue an unprocessable job when provider configuration is absent.
- The user may explicitly generate a partial review after seeing the missing-field list.
- Offline jobs remain queued until connectivity returns. After submission, Android uses the same deterministic job id and polls Worker state after any uncertain connection loss; it never submits a second provider request while the Worker reports `RUNNING`.
- The Worker persists `RUNNING`, `SUCCEEDED`, or `FAILED` before returning the corresponding state. A repeated running or successful job id is idempotent and cannot call DeepSeek again. Only an explicit Worker `FAILED` state opens the user retry action.
- Android preserves only the Worker's bounded machine-readable error code. It never displays or persists provider response bodies, but it distinguishes rejected keys, exhausted balance, unavailable models, rate limits, upstream availability, and malformed review output. Both transient and terminal jobs can be retried explicitly.

## Notifications

- At 23:15, an unrecorded day receives one prompt. The prompt never records rest automatically.
- A successfully generated review posts one completion notification.
- Morning/status corrections requeue only when an earlier review exists and the normalized input digest changed.

## Automated gates

- Contract and Worker regression tests: `45 / 45` passing.
- Android unit tests, Lint, and debug APK assembly: passing.
- Repository tests cover missing-data gating, missing-key behavior, deterministic digesting, queue creation, corrected-input superseding, failed-key replacement rollback, Worker role authorization, private-endpoint blocking, provider authorization forwarding, and secret non-echo.
- Reliability regression tests also cover the reasoning completion budget, explicit JSON response mode, safe Worker error-code parsing, and retry UI for both transient and terminal jobs.

## Xiaomi 14 acceptance

1. Enter a DeepSeek API key and test the DeepSeek V4 Flash connection.
2. Open Today > Daily Review and verify missing critical inputs are named.
3. Generate explicitly with incomplete data and verify no values are invented.
4. Complete a formal workout/rest/skip online and verify a review is generated.
5. Repeat while offline, reconnect, and verify the queued review completes automatically.
6. Correct the morning status and verify a new review version becomes current.
7. Verify the formal plan and routine library remain unchanged throughout.
8. Verify the 23:15 prompt does not create a rest record.

Device acceptance passed on 2026-08-09. Package 8 remains a separate work package and has not started.

The 2026-08-12 reliability correction was deployed to the existing Worker and installed over the Xiaomi 14 build with application data preserved. The previously retrying August 11 job completed automatically after restart, and the device exposed the generated conclusion, assessment, and follow-up sections without another manual tap.
