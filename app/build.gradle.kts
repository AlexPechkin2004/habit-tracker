import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.sonarqube)
}

android {
    namespace = "com.alexpechkin.habittracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alexpechkin.habittracker"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        resValue("string", "default_web_client_id", getDefaultWebClientId())
    }

    signingConfigs {
        create("release") {
            keyAlias = "alexpechkin"
            keyPassword = "PrettyPassword1964!"
            storeFile = file("C:\\Users\\TempAdmin\\Downloads\\keystore.jks")
            storePassword = "PrettyPassword1964!"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    sonar {
        properties {
            property("sonar.projectKey", "alexpechkin2004_habittracker")
            property("sonar.organization", "alexpechkin2004")
            property("sonar.host.url", "https://sonarcloud.io")
            property("sonar.java.binaries", "build/intermediates/javac/debug/classes")
            property("sonar.scm.disabled", "true") // Add this
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.google.play.auth)

    implementation(libs.firebase.auth)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.gson)

    implementation(libs.mpandroidchart)

    implementation(libs.firebase.messaging)
    implementation(libs.volley)
    implementation(libs.androidx.work)
}

fun getDefaultWebClientId(): String {
    val props = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { props.load(it) }
    }
    return "\"${props.getProperty("defaultWebClientId") ?: ""}\""
}