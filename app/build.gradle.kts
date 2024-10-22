plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

android {
    namespace = "com.tibik.speechsynthesizer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tibik.speechsynthesizer"
        minSdk = 28
        targetSdk = 35
        versionCode = 9
        versionName = "1.3.3"

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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
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
            kotlin.srcDir("src/alt") // Adding your custom source directory for Kotlin files
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
}
