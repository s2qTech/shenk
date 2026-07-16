plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

val sourceFiles = fileTree(rootDir) {
    include("**/*.kt", "**/*.kts")
    exclude("**/build/**", "**/.gradle/**")
}

tasks.register("checkSourceFormatting") {
    group = "verification"
    description = "Rejects tabs, trailing whitespace, and missing final newlines in Kotlin sources."
    inputs.files(sourceFiles)

    doLast {
        val violations = mutableListOf<String>()
        sourceFiles.files.sortedBy { it.invariantSeparatorsPath }.forEach { file ->
            val text = file.readText()
            text.lineSequence().forEachIndexed { index, line ->
                if ('\t' in line) violations += "${file.relativeTo(rootDir)}:${index + 1}: tab"
                if (line != line.trimEnd()) violations += "${file.relativeTo(rootDir)}:${index + 1}: trailing whitespace"
            }
            if (text.isNotEmpty() && !text.endsWith("\n")) {
                violations += "${file.relativeTo(rootDir)}: missing final newline"
            }
        }
        check(violations.isEmpty()) {
            "Kotlin formatting violations:\n${violations.joinToString("\n")}"
        }
    }
}

tasks.register("package0Check") {
    group = "verification"
    description = "Runs the Package 0 native Android quality gate."
    dependsOn(
        "checkSourceFormatting",
        ":app:testDebugUnitTest",
        ":app:lintDebug",
        ":app:assembleDebug",
        ":core:model-domain:test",
        ":core:data-sync:testDebugUnitTest",
        ":feature:timer-engine:test",
    )
}
