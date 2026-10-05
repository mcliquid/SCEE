import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

repositories {
    google()
    mavenCentral()
    // for com.github.ticofab:android-gpx-parser
    maven { url = uri("https://www.jitpack.io") }
}

android {
    namespace = "de.westnordost.streetcomplete.app"
    compileSdk = 37

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "de.westnordost.streetcomplete.expert"
        minSdk = 25
        targetSdk = 37
        versionCode = 6402
        versionName = "64.0-alpha3"

        // no x86: the MapLibre Compose runtime has no x86 build, and other native libraries must
        // not make the app installable where the map cannot run
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
    }

    signingConfigs {
        create("release") {
        }
    }

    buildTypes {
        all {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            testProguardFile("test-proguard-rules.pro")
            // Explicit switch for ApplicationConstants.DEBUG. Not derived from the package name.
            buildConfigField("boolean", "APPLICATION_DEBUG", "false")
        }
        // Release-like build that updates the existing *.debug installation in place.
        // Same application id and debug certificate as the previous debug variant.
        getByName("debug") {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = false
            matchingFallbacks += listOf("release")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
        // Previous debuggable debug variant, with its own data directory.
        create("legacyDebug") {
            applicationIdSuffix = ".legacydebug"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            buildConfigField("boolean", "APPLICATION_DEBUG", "true")
            matchingFallbacks += listOf("debug", "release")
        }
        // Local release-like build with a separate data directory.
        create("perf") {
            initWith(getByName("release"))
            applicationIdSuffix = ".perf"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = false
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(project(":app"))

    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Android / UI
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Compose
    implementation("org.jetbrains.compose.runtime:runtime:1.12.1")
    implementation("org.jetbrains.compose.ui:ui:1.12.1")
    implementation("org.jetbrains.compose.material:material:1.12.1")
    implementation("org.jetbrains.compose.components:components-resources:1.12.1")
    add("legacyDebugImplementation", "androidx.compose.ui:ui-tooling:1.12.1")

    // location
    implementation("org.maplibre.compose:location:0.19.0")

    // Dependency Injection
    implementation("io.insert-koin:koin-android:4.2.2")
    implementation("io.insert-koin:koin-androidx-compose:4.2.2")
    implementation("io.insert-koin:koin-androidx-workmanager:4.2.2")

    // Settings (NearbyQuestMonitor and other SCEE androidApp code)
    implementation("com.russhwolf:multiplatform-settings:1.3.0")

    // Database / IO
    implementation("androidx.sqlite:sqlite-bundled:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")

    // HTTP Client
    implementation("io.ktor:ktor-client-android:3.5.2")

    // finding OSM features
    implementation("de.westnordost:osmfeatures:8.0.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    val props = Properties()
    props.load(FileInputStream(keystorePropertiesFile))
    val releaseSigningConfig = android.signingConfigs.getByName("release")
    releaseSigningConfig.storeFile = file(props.getProperty("storeFile"))
    releaseSigningConfig.storePassword = props.getProperty("storePassword")
    releaseSigningConfig.keyAlias = props.getProperty("keyAlias")
    releaseSigningConfig.keyPassword = props.getProperty("keyPassword")
}
