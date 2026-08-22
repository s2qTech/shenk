plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val shenkVersionCode = providers.gradleProperty("SHENK_VERSION_CODE").get().toInt()
val shenkVersionName = providers.gradleProperty("SHENK_VERSION_NAME").get()

fun releaseSetting(name: String): String = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))
    .orNull
    ?.trim()
    .orEmpty()

val releaseStorePath = releaseSetting("SHENK_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSetting("SHENK_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSetting("SHENK_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSetting("SHENK_RELEASE_KEY_PASSWORD")
val requireReleaseSigning = releaseSetting("SHENK_REQUIRE_RELEASE_SIGNING").toBoolean()
val releaseSigningValues = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val releaseSigningConfigured = releaseSigningValues.any(String::isNotBlank)
val releaseSigningComplete = releaseSigningValues.all(String::isNotBlank)

if (releaseSigningConfigured && !releaseSigningComplete) {
    throw GradleException("Release signing is partially configured. Supply all SHENK_RELEASE_* values or none.")
}

if (shenkVersionCode < 8) {
    throw GradleException("Package 8 requires SHENK_VERSION_CODE >= 8.")
}
if (Regex("package[0-7]", RegexOption.IGNORE_CASE).containsMatchIn(shenkVersionName)) {
    throw GradleException("SHENK_VERSION_NAME still identifies an accepted earlier package.")
}
if (requireReleaseSigning && !releaseSigningComplete) {
    throw GradleException("SHENK_REQUIRE_RELEASE_SIGNING is true but release signing is incomplete.")
}
val signedReleaseRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.substringAfterLast(':') in setOf(
        "package8ReleaseCandidateCheck",
        "verifySignedReleaseConfiguration",
    )
}
if (signedReleaseRequested && !requireReleaseSigning) {
    throw GradleException("A private release candidate requires SHENK_REQUIRE_RELEASE_SIGNING=true.")
}
if (signedReleaseRequested && !releaseSigningComplete) {
    throw GradleException("A private release candidate requires all external SHENK_RELEASE_* values.")
}
if (
    signedReleaseRequested &&
    Regex("(?:dev|debug|snapshot)", RegexOption.IGNORE_CASE).containsMatchIn(shenkVersionName)
) {
    throw GradleException("A private release candidate must not use a development version name.")
}
if (releaseSigningComplete) {
    val store = file(releaseStorePath).canonicalFile
    val repository = rootProject.projectDir.parentFile.canonicalFile.toPath()
    if (!store.exists()) {
        throw GradleException("Release keystore does not exist: $store")
    }
    if (store.toPath().startsWith(repository)) {
        throw GradleException("Release keystore must be stored outside the repository.")
    }
}

android {
    namespace = "io.s2qtech.shenk"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.s2qtech.shenk"
        minSdk = 36
        targetSdk = 36
        versionCode = shenkVersionCode
        versionName = shenkVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningComplete) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            // A configured private developer machine keeps debug installs on the
            // same long-lived identity as the private release so device data can
            // survive local validation. CI has no release credentials and keeps
            // the normal generated debug identity.
            if (releaseSigningComplete) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            if (releaseSigningComplete) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.register("verifyReleaseConfiguration") {
    group = "verification"
    description = "Validates Package 8 versioning and optional private release signing inputs."
}

tasks.register("verifySignedReleaseConfiguration") {
    group = "verification"
    description = "Requires complete external signing inputs for a distributable private release candidate."
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model-domain"))
    implementation(project(":core:data-sync"))
    implementation(project(":feature:timer-engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
