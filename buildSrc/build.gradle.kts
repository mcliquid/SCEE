repositories {
    google()
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    implementation(libs.klaxon)
    implementation(libs.countryboundaries)
    implementation(libs.kotlinx.io)
    implementation(libs.yamlbeans)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)
    implementation(libs.markdown)
}

plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.serialization)
}
