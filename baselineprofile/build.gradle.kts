plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

// Generates app/src/release/generated/baselineProfiles/baseline-prof.txt from a scripted
// session against the fake Xtream server (tools/testserver) on a connected emulator or TV:
//   ./gradlew :app:generateReleaseBaselineProfile
android {
    namespace = "com.lck07.lumen.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
