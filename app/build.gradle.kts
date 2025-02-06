plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    alias(libs.plugins.googleGmsGoogleServices)
    alias(libs.plugins.googleFirebaseCrashlytics)
}

android {
    namespace = "com.tibik.speechsynthesizer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tibik.speechsynthesizer"
        minSdk = 28
        targetSdk = 35
        versionCode = 13
        versionName = "1.3.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug configuration if needed
        }
    }

    // Allow zip files to be stored uncompressed
    androidResources {
        noCompress += "zip"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Custom source sets configuration
    sourceSets {
        getByName("main") {
            java.srcDir("src/main/kotlin")
            kotlin.srcDir("src/alt")
        }
        getByName("debug") {
            assets.srcDirs("src/debug/assets")
        }
    }
}

detekt {
    source.setFrom(files("src/main/kotlin", "src/test/kotlin", "src/alt"))
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        sarif.required.set(true)
    }
}

dependencies {
    implementation(libs.androidx.material3.v121)
    implementation(libs.material.v150)
    implementation(libs.flexbox)
    implementation(libs.gson)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.junit.ktx)
    implementation(libs.firebase.crashlytics)
    testImplementation(libs.junit.junit)
    androidTestImplementation(libs.testng)
}
