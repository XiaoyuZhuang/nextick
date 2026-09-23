plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nextick.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nextick.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.5.1"
        buildConfigField("String", "GITHUB_REPO", "\"XiaoyuZhuang/nextick\"")
    }

    signingConfigs {
        create("nextickPublic") {
            storeFile = rootProject.file("signing/nextick-public.jks")
            storePassword = "nextick-public"
            keyAlias = "nextick"
            keyPassword = "nextick-public"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("nextickPublic")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("nextickPublic")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
}
