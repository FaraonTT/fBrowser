plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from environment variables (set by the GitHub workflow).
// Without them a release build is simply left unsigned.
val releaseKeystore: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "dev.fbrowser.app"

    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.fbrowser.app"
        minSdk {
            version = release(26)
        }
        targetSdk {
            version = release(36)
        }
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 2
        versionName = (project.findProperty("versionName") as String?) ?: "0.2.0"

        // GeckoView is shipped for 64-bit ARM only (almost every current phone).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(checkNotNull(releaseKeystore))
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)

    // Engine: Gecko, the Firefox engine
    implementation(libs.geckoview)

    // Settings storage
    implementation(libs.androidx.datastore.preferences)
}
