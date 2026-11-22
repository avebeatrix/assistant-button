import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application") version "8.11.0"
    id("org.jetbrains.kotlin.android") version "2.0.21"
}

android {
    namespace = "dev.avebeatrix.assistantbutton"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.avebeatrix.assistantbutton"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}
