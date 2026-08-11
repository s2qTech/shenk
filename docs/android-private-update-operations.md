# Android Private Update Operations

Updated: 2026-08-12
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

1. Check out the accepted source revision and build with a `versionCode` greater than every installed or previously published build.
2. Supply the external signing properties and require signing. Verify the resulting APK with Android build tools before upload.
3. Calculate SHA-256 and byte size from the exact signed APK that will be uploaded.
4. Upload that APK to the private R2 bucket under a new immutable `android/` object key.
5. Configure the Worker's `ANDROID_RELEASES` R2 binding and set `ANDROID_RELEASE_METADATA` outside Git.
6. Query the authenticated metadata route and confirm the object key is absent from the response.
7. Install through the app on Xiaomi 14 and confirm Android shows the expected update, not a new application or downgrade.
8. Archive only the source revision, version, checksum, build record, and rollback revision in the private release record. Signing secrets remain external.

## Disable and rollback

- Removing `ANDROID_RELEASE_METADATA` immediately makes metadata checks return no release and makes the APK route unavailable.
- Removing the R2 object after clearing metadata is safe; never leave metadata pointing to a missing object.
- Rollback is a known-good source revision rebuilt and signed with the same certificate at a higher `versionCode`. Android downgrade installation is not used.
- Failed checks, downloads, or validation never modify Room, the outbox, plans, routines, logs, or the installed APK.
