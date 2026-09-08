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

val notiflyCore = file("core")
if (!notiflyCore.resolve("settings.gradle.kts").isFile) {
    throw GradleException(
        "core is not initialized. Run: git submodule update --init --recursive",
    )
}

includeBuild(notiflyCore) {
    dependencySubstitution {
        substitute(module("com.github.team-michael.notifly-android-sdk:core"))
            .using(project(":"))
    }
}

if (System.getenv("JITPACK") == null) {
    include(":sample")
}
