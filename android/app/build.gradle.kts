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
        versionCode = 2
        versionName = "0.2.0"
        ndk {
            // 64-bit only: the runtime reserves 4 GB of address space for the N64's memory, and
            // mods patch arm64 (or x86-64) code. A 32-bit (armeabi-v7a) build can't work.
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                // The recompiled game is far too slow unoptimised, even in debug builds.
                arguments += listOf("-DCMAKE_BUILD_TYPE=RelWithDebInfo")
            }
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
