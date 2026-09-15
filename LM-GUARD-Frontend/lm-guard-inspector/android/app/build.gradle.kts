plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.lm_guard_inspector"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.example.lm_guard_inspector"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        // Pinned above the Flutter default: ARCore (the Rule 7 numeral-height measurement
        // feature, see ArMeasurementActivity.kt) requires API 24+. The ARCore dependency is
        // "optional" in the manifest, so devices below this floor simply don't get the app at
        // all rather than getting it minus one feature - acceptable here since 24 (Android 7.0,
        // 2016) is already far below what a current-generation inspection tablet/phone runs.
        minSdk = maxOf(flutter.minSdkVersion, 24)
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    // Rule 7 numeral-height AR measurement (see ArMeasurementActivity.kt) - a small, purpose-
    // built platform channel, not a general-purpose AR-scene plugin (see the plan for why).
    implementation("com.google.ar:core:1.42.0")
}
