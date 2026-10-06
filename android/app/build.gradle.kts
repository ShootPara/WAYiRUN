import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.runningapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.runningapp"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev-photo-sync1"
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"933230558080-ko4r7v0kmhip4i0n7u32diaimv1in73q.apps.googleusercontent.com\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    debugImplementation("androidx.health.connect:connect-client:1.1.0")
    debugImplementation("androidx.work:work-runtime-ktx:2.10.5")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    debugImplementation("androidx.credentials:credentials:1.6.0")
    debugImplementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    debugImplementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    debugImplementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    debugImplementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    debugImplementation("androidx.room:room-runtime:2.8.4")
    debugImplementation("androidx.room:room-ktx:2.8.4")
    add("kspDebug", "androidx.room:room-compiler:2.8.4")
    debugImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Name the actual packaged artifact so tooling and handoff use the same APK.
// AGP 8.13 has no public VariantOutput.outputFileName; keep this pinned-AGP bridge local.
android.applicationVariants.all {
    if (buildType.name == "debug") {
        val stamp = ZonedDateTime.now(ZoneId.of("America/New_York"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_z"))
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = "WAYiRUN-$stamp.apk"
        }
    }
}
