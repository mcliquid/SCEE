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
    implementation("com.beust:klaxon:5.6")
    implementation("de.westnordost:countryboundaries:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
    implementation("com.esotericsoftware.yamlbeans:yamlbeans:1.17")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.charleskorn.kaml:kaml:0.104.0")
    implementation("org.jetbrains:markdown:0.7.17")
}

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "2.0.0"
}
