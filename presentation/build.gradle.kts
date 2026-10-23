/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of Open Messages.
 *
 * Open Messages is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open Messages is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Messages.  If not, see <http://www.gnu.org/licenses/>.
 */
import java.util.Properties

plugins {
    id("com.android.application")
    id("realm-android") // Realm needs to be before Kotlin or the build will fail
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    namespace = "io.openmessages"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.openmessages"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // First Open Messages release. The rebrand to the io.openmessages package means it installs
        // fresh, never over the app it was forked from, so the counter restarts at 1. Bump
        // versionCode by 1 and update versionName for each release.
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePropsFile = rootProject.file("keystore.properties")
            if (keystorePropsFile.exists()) {
                val keystoreProps = Properties().apply { keystorePropsFile.inputStream().use(::load) }
                // storeFile is resolved relative to the repo root (where keystore.properties lives),
                // so an entry like storeFile=my-release-key.jks just works. Absolute paths also work.
                // Each entry is set only when present, so a file missing one of them is reported by
                // the signing check at the bottom of this file rather than failing here on a null.
                keystoreProps.getProperty("storeFile")?.let { storeFile = rootProject.file(it) }
                keystoreProps.getProperty("storePassword")?.let { storePassword = it }
                keystoreProps.getProperty("keyAlias")?.let { keyAlias = it }
                keystoreProps.getProperty("keyPassword")?.let { keyPassword = it }
            }
            // Sign with the legacy JAR scheme (v1, for API < 24) plus the modern full-APK schemes
            // (v2 for API 24+, v3 for API 28+ key rotation) when building the release variant via
            // Gradle. The "Generate Signed APK" wizard only applies v1 + v2.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            // Built like release (R8, resource shrinking, not debuggable) so it runs as smoothly;
            // only the package, the name and the signing key set it apart.
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Open Messages Debug")
            // The launcher entries are labelled with launcher_name, not app_name, so overriding
            // app_name alone left both installed versions showing the same name on the home screen.
            resValue("string", "launcher_name", "Messages Debug")
            resValue("string", "application_id", "io.openmessages")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

// Names every built file after the version: OpenMessages-v1.0.0-release.apk and
// OpenMessages-v1.0.0-debug.apk, so a signed release never needs renaming by hand and the two never
// collide.
//
// The build type is part of the name rather than absent from the release one, because the only way
// to control the file name exactly is the old variant API: deprecated on AGP 8, removed in AGP 9,
// and with nothing public to replace it (the new VariantOutput carries no file name, and the class
// that does is internal). Taking the suffix is what buys a build that has no deprecated API left in
// it and nothing to repair on the day of that upgrade.
base {
    archivesName = "OpenMessages-v${android.defaultConfig.versionName}"
}

dependencies {
    // lifecycle
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.common.java8)

    // androidx
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.emoji2.bundled)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.material)
    implementation(libs.androidx.work.runtime.ktx)

    // conductor
    implementation(libs.conductor)
    implementation(libs.conductor.archlifecycle)

    // glide
    implementation(libs.glide)
    kapt(libs.glide.compiler)

    // media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // rxbinding
    implementation(libs.rxbinding.kotlin)
    implementation(libs.rxbinding.support.v4.kotlin)

    // autodispose
    implementation(libs.autodispose.android.archcomponents)
    implementation(libs.autodispose.android.archcomponents.test)
    implementation(libs.autodispose.android)
    implementation(libs.autodispose)
    implementation(libs.autodispose.lifecycle)

    // dagger
    implementation(libs.dagger)
    implementation(libs.dagger.android.support)
    kapt(libs.dagger.compiler)
    kapt(libs.dagger.android.processor)
    implementation(libs.javax.annotation.api)

    // ezvcard
    implementation(libs.ez.vcard) {
        exclude(group = "org.jsoup", module = "jsoup")
        exclude(group = "org.freemarker", module = "freemarker")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
    }

    // realm
    implementation(libs.realm.adapters)
    kapt(libs.realm.annotations)
    kapt(libs.realm.annotations.processor)

    // rxjava
    implementation(libs.rxandroid)
    implementation(libs.rxjava)
    implementation(libs.rxkotlin)
    implementation(libs.rxdogtag)
    implementation(libs.rxdogtag.autodispose)

    // testing
    androidTestImplementation(libs.androidx.test.espresso.core) {
        exclude(group = "com.android.support", module = "support-annotations")
    }
    androidTestImplementation(libs.mockito.android)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)

    // moshi
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.rx2)
    implementation(libs.coroutines.reactive)

    implementation(libs.photoview)
    implementation(libs.rx.preferences)
    implementation(libs.flexbox)
    implementation(libs.timber)
    implementation(libs.shortcutbadger)
    implementation(project(":android-smsmms"))
    implementation(project(":common"))
    implementation(project(":data"))
    implementation(project(":domain"))
}

// An unsigned release APK installs nowhere, and nothing along the way says so: the build succeeds
// and the file looks normal. Fail as soon as the task graph is known instead, before spending a
// full build on it. This checks the signing configuration itself rather than the presence of
// keystore.properties, so it holds for every way the credentials can arrive.
gradle.taskGraph.whenReady {
    if (allTasks.none { it.name.matches(Regex("^(assemble|bundle|install)Release$")) }) {
        return@whenReady
    }
    val signing = android.signingConfigs.getByName("release")
    val missing = mapOf(
        "storeFile" to signing.storeFile,
        "storePassword" to signing.storePassword,
        "keyAlias" to signing.keyAlias,
        "keyPassword" to signing.keyPassword
    ).filterValues { it == null }.keys
    if (missing.isNotEmpty()) {
        throw GradleException(
            "Release signing is incomplete, missing: ${missing.joinToString()}.\n" +
                "Create keystore.properties at the repository root with storeFile, storePassword, " +
                "keyAlias and keyPassword. storeFile is resolved from the repository root."
        )
    }
}
