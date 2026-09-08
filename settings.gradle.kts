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
        mavenLocal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "NotiflyAndroidSDK"
include(":notifly")

val notiflyKmpSdk = file("notifly-kmp-sdk")
if (!notiflyKmpSdk.resolve("settings.gradle.kts").isFile) {
    throw GradleException(
        "notifly-kmp-sdk is not initialized. Run: git submodule update --init --recursive",
    )
}

includeBuild(notiflyKmpSdk) {
    dependencySubstitution {
        substitute(module("com.github.team-michael.notifly-android-sdk:core"))
            .using(project(":"))
    }
}

if (System.getenv("JITPACK") == null) {
    include(":sample")
}
