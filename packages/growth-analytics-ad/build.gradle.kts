plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nexus.sdk.growth"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(project(":packages:core-user"))
    api("com.google.android.gms:play-services-ads:25.3.0")
    implementation("com.google.firebase:firebase-analytics:23.0.0")
    implementation("com.appsflyer:af-android-sdk:6.17.5")
    implementation("io.github.dataeyesdk:dataeye-android-sdk:2.8.3")
    testImplementation("junit:junit:4.13.2")
}
