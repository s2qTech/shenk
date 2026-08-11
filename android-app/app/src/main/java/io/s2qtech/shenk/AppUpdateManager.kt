package io.s2qtech.shenk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.s2qtech.shenk.sync.DevicePreferencesStore
import io.s2qtech.shenk.sync.KeystoreSecretStore
import io.s2qtech.shenk.sync.SecretName
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

private val Context.updatePreferences by preferencesDataStore(name = "app_update_settings")

data class AppUpdateRelease(
    val applicationId: String,
    val versionCode: Long,
    val versionName: String,
    val sha256: String,
    val sizeBytes: Long,
    val publishedAt: String,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data class Available(val release: AppUpdateRelease) : AppUpdateState
    data class Downloading(val release: AppUpdateRelease) : AppUpdateState
    data class Ready(val release: AppUpdateRelease, val apk: File) : AppUpdateState
    data class Failed(val release: AppUpdateRelease, val message: String) : AppUpdateState
}

class AppUpdateManager(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    private val appContext = context.applicationContext
    private val preferences = DevicePreferencesStore(appContext)
    private val secrets = KeystoreSecretStore(preferences)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val checking = AtomicBoolean(false)
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    fun checkAfterFirstFrame() {
        if (mutableState.value != AppUpdateState.Idle) return
        if (!checking.compareAndSet(false, true)) return
        scope.launch {
            try {
                checkIfDue()?.let { mutableState.value = AppUpdateState.Available(it) }
            } finally {
                checking.set(false)
            }
        }
    }

    fun dismiss() {
        (mutableState.value as? AppUpdateState.Ready)?.apk?.delete()
        mutableState.value = AppUpdateState.Idle
    }

    fun download(release: AppUpdateRelease) {
        if (mutableState.value is AppUpdateState.Downloading) return
        mutableState.value = AppUpdateState.Downloading(release)
        scope.launch {
            mutableState.value = runCatching {
                AppUpdateState.Ready(release, downloadAndVerify(release))
            }.getOrElse {
                AppUpdateState.Failed(release, "更新包下载或校验失败，当前版本不受影响。")
            }
        }
    }

    fun openSystemInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(appContext, "${BuildConfig.APPLICATION_ID}.updates", apk)
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private suspend fun checkIfDue(): AppUpdateRelease? = withContext(Dispatchers.IO) {
        val now = clock.millis()
        val lastCheck = appContext.updatePreferences.data.first()[LAST_CHECK_AT]
        if (!shouldCheckForUpdate(lastCheck, now)) return@withContext null
        appContext.updatePreferences.edit { it[LAST_CHECK_AT] = now }
        runCatching {
            val endpoint = preferences.syncSettings()
            val token = secrets.get(SecretName.SHENK_TOKEN)
            if (endpoint.apiBase.isBlank() || token.isNullOrBlank()) return@runCatching null
            val request = Request.Builder()
                .url("${endpoint.apiBase.trimEnd('/')}/android/update/metadata")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                val bytes = body.source().readByteArray(MAX_METADATA_BYTES + 1L)
                if (bytes.size > MAX_METADATA_BYTES) return@use null
                val release = parseAppUpdateMetadata(bytes.toString(Charsets.UTF_8)) ?: return@use null
                release.takeIf {
                    isEligibleUpdate(it, BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE.toLong())
                }
            }
        }.getOrNull()
    }

    private suspend fun downloadAndVerify(release: AppUpdateRelease): File = withContext(Dispatchers.IO) {
        require(isEligibleUpdate(release, BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE.toLong()))
        val endpoint = preferences.syncSettings()
        val token = requireNotNull(secrets.get(SecretName.SHENK_TOKEN))
        val directory = File(appContext.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach(File::delete)
        val partial = File(directory, "shenk-${release.versionCode}.apk.part")
        val verified = File(directory, "shenk-${release.versionCode}.apk")
        partial.delete()
        verified.delete()
        try {
            val request = Request.Builder()
                .url("${endpoint.apiBase.trimEnd('/')}/android/update/apk")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "update_download_http_${response.code}" }
                val body = requireNotNull(response.body)
                val declaredLength = body.contentLength()
                check(declaredLength == -1L || declaredLength == release.sizeBytes) { "update_size_mismatch" }
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= release.sizeBytes) { "update_size_mismatch" }
                            output.write(buffer, 0, count)
                        }
                        check(total == release.sizeBytes) { "update_size_mismatch" }
                    }
                }
            }
            verifyDownloadedApk(appContext, partial, release)
            check(partial.renameTo(verified)) { "update_cache_finalize_failed" }
            verified
        } catch (error: Throwable) {
            partial.delete()
            verified.delete()
            throw error
        }
    }

    private companion object {
        val LAST_CHECK_AT = longPreferencesKey("last_update_check_at")
        const val MAX_METADATA_BYTES = 64 * 1024
    }
}

internal fun shouldCheckForUpdate(lastCheckAt: Long?, now: Long): Boolean =
    lastCheckAt == null || now < lastCheckAt || now - lastCheckAt >= TimeUnit.HOURS.toMillis(24)

internal fun parseAppUpdateMetadata(raw: String): AppUpdateRelease? = runCatching {
    val release = Json.parseToJsonElement(raw).jsonObject["release"]
    if (release == null || release is JsonNull) return null
    val value = release.jsonObject
    AppUpdateRelease(
        applicationId = value["applicationId"]!!.jsonPrimitive.content,
        versionCode = value["versionCode"]!!.jsonPrimitive.longOrNull!!,
        versionName = value["versionName"]!!.jsonPrimitive.content,
        sha256 = value["sha256"]!!.jsonPrimitive.content.lowercase(),
        sizeBytes = value["sizeBytes"]!!.jsonPrimitive.longOrNull!!,
        publishedAt = value["publishedAt"]!!.jsonPrimitive.content,
    ).takeIf {
        it.applicationId == "io.s2qtech.shenk" && it.versionCode > 0 &&
            it.versionName.length in 1..80 && it.sha256.matches(Regex("[a-f0-9]{64}")) &&
            it.sizeBytes in 1..(250L * 1024 * 1024) && runCatching { Instant.parse(it.publishedAt) }.isSuccess
    }
}.getOrNull()

internal fun isEligibleUpdate(release: AppUpdateRelease, applicationId: String, currentVersionCode: Long): Boolean =
    release.applicationId == applicationId && release.versionCode > currentVersionCode

internal fun verifyDownloadedApk(context: Context, apk: File, release: AppUpdateRelease) {
    check(apk.length() == release.sizeBytes) { "update_size_mismatch" }
    check(sha256(apk) == release.sha256) { "update_sha256_mismatch" }
    val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
    val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)) {
        "update_apk_invalid"
    }
    check(archive.packageName == release.applicationId) { "update_application_id_mismatch" }
    check(archive.longVersionCode == release.versionCode) { "update_version_mismatch" }
    val installed = context.packageManager.getPackageInfo(context.packageName, flags)
    check(signingDigests(archive) == signingDigests(installed)) { "update_signing_certificate_mismatch" }
}

private fun signingDigests(info: android.content.pm.PackageInfo): Set<String> =
    requireNotNull(info.signingInfo).apkContentsSigners.map { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
    }.toSet()

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
