plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("../contracts/conformance"))
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
