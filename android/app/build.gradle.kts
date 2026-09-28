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
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                // The recompiled game is far too slow unoptimised, even in debug builds.
                arguments += listOf("-DCMAKE_BUILD_TYPE=RelWithDebInfo", "-DCONKER_RT64=OFF")
            }
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
