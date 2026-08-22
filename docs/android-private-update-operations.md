# Android Private Update Operations

Updated: 2026-08-22
Status: P8.2 transport runbook; publishing begins only with the signed P8.8 release candidate

## Trust boundary

- Metadata and APK bytes are available only through authenticated Worker routes.
- The Android client sends the existing Shenk bearer credential in the request header; credentials never enter a URL or release metadata.
- Private APK bytes live in an R2 bucket bound to the Worker as `ANDROID_RELEASES`. The object key is never returned to the client.
- `ANDROID_RELEASE_METADATA` is configured outside Git. It is not a business record and is not stored in D1.
- The client independently verifies application ID, increasing version code, byte count, SHA-256, archive version, and the installed signing certificate before opening Android's installer.

## Metadata shape

```json
{
  "applicationId": "io.s2qtech.shenk",
  "versionCode": 11,
  "versionName": "private-release-name",
  "sha256": "64-lowercase-hex-characters",
  "sizeBytes": 12345678,
  "objectKey": "android/shenk-11.apk",
  "publishedAt": "2099-01-01T00:00:00.000Z"
}
```

The example is synthetic. Never commit a real release manifest, APK, signing certificate, keystore path, password, token, or object-storage credential.

## P8.8 publication sequence

1. Establish the intended long-lived signing identity outside Git. Before any install, compare its certificate with the certificate of the application already installed on Xiaomi 14. A different certificate cannot perform a data-preserving Android update; stop for an explicit signing/data-transition decision and never uninstall or clear the existing app as a shortcut.
2. Check out a clean accepted source revision with a `versionCode` greater than every installed or previously published build. The first candidate is `1.0.0-rc.1` / `11`.
3. Supply all external `SHENK_RELEASE_*` properties and run `android-app/ci/build-private-release.ps1 -RollbackRevision <known-good-revision>`. The script invokes `package8ReleaseCandidateCheck` with signing required, verifies the exact APK with Android build tools, and creates the ignored private release record.
4. Run `android-app/ci/verify-private-release-device.ps1 -ApkPath <archived-apk> -AdbPath <adb>` on the connected Xiaomi 14. The script verifies application ID, increasing version, signing-certificate equality, `adb install -r`, unchanged first-install identity, installed version, and cold launch. It contains no uninstall or data-clear path.
5. Confirm the accepted daily critical path and archive the release record outside the repository. Only source revision, version, checksum, byte size, signing certificate digest, build time, and rollback revision belong in that record; signing secrets remain external.
6. Upload that exact signed APK to the private R2 bucket under a new immutable `android/` object key.
7. Configure the Worker's `ANDROID_RELEASES` R2 binding and set `ANDROID_RELEASE_METADATA` outside Git.
8. Query the authenticated metadata route and confirm the object key is absent from the response.
9. Install through the app on Xiaomi 14 and confirm Android shows the expected update, not a new application or downgrade.

The default archive directory is `outputs/private-release/`, which is excluded from Git. Copy the accepted archive to the private release-record location before relying on it for recovery.

## Disable and rollback

- Removing `ANDROID_RELEASE_METADATA` immediately makes metadata checks return no release and makes the APK route unavailable.
- Removing the R2 object after clearing metadata is safe; never leave metadata pointing to a missing object.
- Rollback is a known-good source revision rebuilt and signed with the same certificate at a higher `versionCode`. Android downgrade installation is not used.
- Failed checks, downloads, or validation never modify Room, the outbox, plans, routines, logs, or the installed APK.
