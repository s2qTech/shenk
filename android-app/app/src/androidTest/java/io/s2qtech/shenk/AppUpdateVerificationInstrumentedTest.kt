package io.s2qtech.shenk

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateVerificationInstrumentedTest {
    @Test
    fun installedApkPassesPackageVersionHashAndSigningVerification() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val apk = File(context.applicationInfo.sourceDir)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        verifyDownloadedApk(
            context,
            apk,
            AppUpdateRelease(
                applicationId = context.packageName,
                versionCode = packageInfo.longVersionCode,
                versionName = packageInfo.versionName.orEmpty(),
                sha256 = sha256(apk),
                sizeBytes = apk.length(),
                publishedAt = "2099-01-01T00:00:00Z",
            ),
        )
    }
}
