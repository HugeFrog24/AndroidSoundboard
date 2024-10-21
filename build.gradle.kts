// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config = files("$projectDir/config/detekt/detekt.yml")
}
