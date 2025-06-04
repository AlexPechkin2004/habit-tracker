plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.sonarqube) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("androidx.core:core:1.9.0")
            force("androidx.appcompat:appcompat:1.6.1")

            exclude(group = "com.android.support", module = "support-compat")
            exclude(group = "com.android.support", module = "support-v4")
            exclude(group = "com.android.support", module = "support-annotations")
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}