plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.register("package0Check") {
    group = "verification"
    description = "Runs the Package 0 native Android quality gate."
    dependsOn(
        ":app:testDebugUnitTest",
        ":app:lintDebug",
        ":app:assembleDebug",
        ":core:model-domain:test",
        ":core:data-sync:testDebugUnitTest",
        ":feature:timer-engine:test",
    )
}
