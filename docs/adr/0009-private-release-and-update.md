# ADR 0009: Private Android Release and Update

Status: Accepted
Date: 2026-08-09

## Context

The Android app is a private, single-user product installed on the current primary device. It still needs reproducible releases, trustworthy updates, rollback, and local reminders without introducing an account system, a public app store, or an always-running background service.

## Decision

1. Release signing credentials stay outside the repository and CI logs. CI may build an unsigned release artifact, but a distributable APK requires explicit external signing configuration and `SHENK_REQUIRE_RELEASE_SIGNING=true`.
2. The app checks for updates asynchronously after the first frame when it enters the foreground, at most once per 24 hours. Offline, failed, and no-update checks remain silent.
3. Update metadata is read through the authenticated Worker. APK bytes are streamed from private object storage through an authenticated route. Tokens and secrets never appear in URLs.
4. Before offering installation, the app verifies application ID, increasing `versionCode`, APK SHA-256, and the expected signing certificate.
5. Download and installation are user initiated. Android's system confirmation remains mandatory; there is no silent install and permanent background services are prohibited.
6. Rollback uses a known-good source revision rebuilt with a higher `versionCode`; Android package downgrades are not part of the recovery path.
7. Phase 1 reminders are local and use WorkManager or AlarmManager as appropriate. Remote push is phase 2.

## Consequences

- Release builds are reproducible without committing secrets.
- A leaked download URL is not sufficient to retrieve an APK.
- Update failures never block normal offline use.
- Publishing to an app store, multi-user identity, remote push, and unattended installation remain out of scope.
