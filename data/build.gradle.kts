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
plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "io.openmessages.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // For BuildConfig.DEBUG, which keeps the diagnostic
    // logging in this module out of release builds.
    buildFeatures {
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // androidx
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.documentfile)

    // glide
    implementation(libs.glide)
    kapt(libs.glide.compiler)

    // dagger
    implementation(libs.dagger)
    implementation(libs.dagger.android.support)
    kapt(libs.dagger.android.processor)
    kapt(libs.dagger.compiler)
    implementation(libs.javax.annotation.api)

    // rxjava
    implementation(libs.rxandroid)
    implementation(libs.rxjava)
    implementation(libs.rxkotlin)

    // testing
    androidTestImplementation(libs.androidx.test.espresso.core) {
        exclude(group = "com.android.support", module = "support-annotations")
    }
    androidTestImplementation(libs.mockito.android)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)

    // coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.rx2)
    implementation(libs.coroutines.reactive)

    implementation(libs.callcontrol.datashare)
    implementation(libs.rx.preferences)
    implementation(libs.timber)
    implementation(libs.moshi)
    implementation(libs.okhttp3)
    implementation(libs.libphonenumber)

    // room, and the Realm library that still reads the database Room took over from. Room is part
    // of what this module hands out, since the app provides the database it builds.
    api(libs.room.runtime)
    implementation(libs.room.rxjava2)
    ksp(libs.room.compiler)
    implementation(libs.realm)

    // work manager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.work.rxjava2)

    implementation(project(":android-smsmms"))
    implementation(project(":common"))
    implementation(project(":domain"))
}
