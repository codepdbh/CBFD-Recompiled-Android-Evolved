plugins {
    id("com.android.application")
}

android {
    namespace = "com.codepdbh.cbfdrecomp"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.codepdbh.cbfdrecomp"
        minSdk = 30
        targetSdk = 36
        versionCode = 11
        versionName = "0.2.9"
        externalNativeBuild {
            cmake {
                // The recompiled game is far too slow unoptimised, even in debug builds.
                // ARM (not Thumb) code on 32-bit ARM: mods patch functions with ARM instructions.
                arguments += listOf("-DCMAKE_BUILD_TYPE=RelWithDebInfo", "-DANDROID_ARM_MODE=arm")
            }
        }
    }

    // An APK per ABI. arm64-v8a is the port; armeabi-v7a (32-bit) is an untested alpha, without
    // mods (LiveRecomp needs a 64-bit host) and with the N64's memory unguarded.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    sourceSets {
        getByName("main") {
            // SDL's Java side (SDLActivity and friends).
            java.srcDir("../../tools/SDL2/android-project/app/src/main/java")
            // The launcher's fonts, icons and style sheet (host/assets), copied to the
            // game's folder by MainActivity.
            assets.srcDir("../../host/assets")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../../host/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Each ABI its own version code, the 64-bit one higher, so it wins where both would install.
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            val abi = output.getFilter(com.android.build.OutputFile.ABI)
            val abiCode = when (abi) { "arm64-v8a" -> 2; "armeabi-v7a" -> 1; else -> 0 }
            output.versionCodeOverride = variant.versionCode * 10 + abiCode
            output.outputFileName = "ConkerRecompiled-Android-v${variant.versionName}-${abi}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
