plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("package2Check") {
    group = "verification"
    description = "Runs the Package 2 native Android local-first quality gate."
    dependsOn(
        ":app:testDebugUnitTest",
        ":app:lintDebug",
        ":app:assembleDebug",
        ":core:model-domain:test",
        ":core:data-sync:testDebugUnitTest",
        ":feature:timer-engine:test",
    )
}

tasks.register("package0Check") {
    group = "verification"
    description = "Compatibility alias for the native Android quality gate."
    dependsOn("package2Check")
}
