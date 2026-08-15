pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "phone-away"

// :core is pure Kotlin/JVM -- it builds and tests anywhere a JDK exists.
include(":core")

// :app needs the Android SDK. Including it on a machine without one makes every
// Gradle invocation fail, including `gradle :core:test`, so it is opt-in by
// SDK presence. Android Studio always satisfies this via local.properties.
val sdkDirDeclared: Boolean = file("local.properties").let { propsFile ->
    propsFile.exists() &&
        java.util.Properties()
            .apply { propsFile.inputStream().use { stream -> load(stream) } }
            .getProperty("sdk.dir") != null
}

val androidSdkAvailable: Boolean = sdkDirDeclared ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null

if (androidSdkAvailable) {
    include(":app")
} else {
    logger.lifecycle("Android SDK not found -- skipping :app. Only :core will build.")
}
