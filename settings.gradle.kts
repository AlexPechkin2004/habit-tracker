pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") } // For MPAndroidChart
  }
  versionCatalogs {
    create("myLibs") {  // Changed from "libs" to "myLibs"
      from(files("gradle/libs.versions.toml"))
    }
  }
}

rootProject.name = "HabitTracker"
include(":app")