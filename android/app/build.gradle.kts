import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

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
        applicationId = "com.unopenedparachute.wayirun"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
            buildConfigField("String", "APP_ENVIRONMENT", "\"development\"")
            buildConfigField("String", "API_ORIGIN", "\"https://wayirun-dev.unopenedparachute.workers.dev\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"933230558080-ko4r7v0kmhip4i0n7u32diaimv1in73q.apps.googleusercontent.com\"")
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"933230558080-8o82hopmd4ibnt2fllqpr8252lg3q44t.apps.googleusercontent.com\"")
            buildConfigField("boolean", "PRODUCTION_READY", "false")
        }
        release {
            buildConfigField("String", "APP_ENVIRONMENT", "\"production\"")
            buildConfigField("String", "API_ORIGIN", "\"https://wayirun.slopcopy.com\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"__PRODUCTION_GOOGLE_WEB_CLIENT_ID_MILESTONE_7__\"")
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"__PRODUCTION_GOOGLE_ANDROID_CLIENT_ID_MILESTONE_7__\"")
            buildConfigField("boolean", "PRODUCTION_READY", "false")
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

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.applicationId.set("com.example.runningapp.debug")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    add("kspDebug", "androidx.room:room-compiler:2.8.4")
    add("kspRelease", "androidx.room:room-compiler:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
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

tasks.register("verifyProductionReadiness") {
    group = "verification"
    description = "Rejects a release artifact that still contains placeholders or development identity/configuration."
    dependsOn("assembleRelease")
    doLast {
        val releaseApk = layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk").get().asFile
        check(releaseApk.isFile) { "Release APK not found: $releaseApk" }
        val entries = ZipFile(releaseApk).use { zip ->
            zip.entries().asSequence().filterNot { it.isDirectory }.joinToString("\n") { entry ->
                zip.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) }
            }
        }
        val problems = buildList {
            if ("__PRODUCTION_GOOGLE_WEB_CLIENT_ID_MILESTONE_7__" in entries) add("production Web OAuth placeholder remains")
            if ("__PRODUCTION_GOOGLE_ANDROID_CLIENT_ID_MILESTONE_7__" in entries) add("production Android OAuth placeholder remains")
            if ("933230558080-" in entries) add("development Google OAuth identifier is packaged")
            if ("wayirun-dev.unopenedparachute.workers.dev" in entries) add("development Worker hostname is packaged")
            if ("com.example.runningapp.debug" in entries) add("debug application ID is packaged")
            if ("https://wayirun.slopcopy.com" !in entries) add("production API origin is absent")
            if ("com.unopenedparachute.wayirun" !in entries) add("production application ID is absent")
            if ("development" in entries) add("development environment marker is packaged")
        }
        check(problems.isEmpty()) { "Release is not production-ready:\n- ${problems.joinToString("\n- ")}" }
    }
}
