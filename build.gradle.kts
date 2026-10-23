// Top-level build file where you can add configuration options common to all sub-projects/modules.

import com.android.build.api.dsl.CommonExtension

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(libs.android.gradle.plugin)
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.ksp.gradle.plugin)
        classpath(libs.room.gradle.plugin)
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// The Kotlin built into the Android plugin compiles for the same Java version as the Java sources
subprojects {
    plugins.withId("com.android.base") {
        extensions.configure<CommonExtension> {
            compileOptions.sourceCompatibility = JavaVersion.VERSION_17
            compileOptions.targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
