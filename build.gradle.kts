// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}

/**buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // This upgrades the library that Room uses to read your class files
        classpath("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.9.0")
    }
}*/