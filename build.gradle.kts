// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.3.15")
        if (System.getenv("JITPACK") == null) {
            classpath("org.jlleitschuh.gradle:ktlint-gradle:12.1.1")
            classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.6")
        }
    }
}

plugins {
    id("com.android.application") version "8.0.1" apply false
    id("com.android.library") version "8.0.1" apply false
    id("org.jetbrains.kotlin.android") version "1.8.10" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}
