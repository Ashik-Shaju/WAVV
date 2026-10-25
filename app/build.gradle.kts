plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.wavv.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wavv.app"
        minSdk = 26
        targetSdk = 37

        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeystore = file("keystore/android key")
    val releaseSigningValues = listOf(
        System.getenv("WAVV_KEYSTORE_PASSWORD"),
        System.getenv("WAVV_KEY_ALIAS"),
        System.getenv("WAVV_KEY_PASSWORD")
    )
    val hasAnyReleaseSigningValue = releaseSigningValues.any { !it.isNullOrBlank() }
    val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }

    require(!hasAnyReleaseSigningValue || hasReleaseSigning) {
        "Set all three signing variables: WAVV_KEYSTORE_PASSWORD, WAVV_KEY_ALIAS, and WAVV_KEY_PASSWORD."
    }
    require(!hasReleaseSigning || releaseKeystore.exists()) {
        "Release signing variables are set, but app/keystore/android key was not found."
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseSigningValues[0]
                keyAlias = releaseSigningValues[1]
                keyPassword = releaseSigningValues[2]
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.kotlinx.serialization.bom))

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.kyant.backdrop)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.inspector)
    implementation(libs.androidx.mediarouter)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.onnxruntime.android)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
