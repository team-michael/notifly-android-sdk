plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

apply(from = "$rootDir/constants.gradle.kts")

android {
    namespace = "tech.notifly"
    compileSdk = extra["moduleCompileSdkVersion"] as Int

    defaultConfig {
        minSdk = extra["moduleMinSdkVersion"] as Int
        targetSdk = extra["moduleCompileSdkVersion"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        consumerProguardFiles("consumer-rules.pro")
        val version = project.property("version") as String
        buildConfigField("String", "VERSION", "\"$version\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        unitTests.all {
            it.jvmArgs("-noverify")
            it.testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")

    implementation("com.google.firebase:firebase-messaging:23.1.2")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.browser:browser:1.5.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20210307")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.mockk:mockk:1.13.12")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:2.0.0")

    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.team-michael"
                artifactId = "notifly-android-sdk"
                version = project.property("version") as String
            }
        }

        repositories {
            mavenLocal()
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/detekt.yml")
}
