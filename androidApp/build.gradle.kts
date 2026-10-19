import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        versionCode = 6405
        versionName = providers.gradleProperty("app.streetcomplete.versionName").get()

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
    implementation(libs.kotlinx.coroutines.android)

    // Android / UI
    implementation(libs.androidx.activity.compose)
    implementation(libs.google.material)
    implementation(libs.androidx.appcompat)

    // Compose
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.resources)
    add("legacyDebugImplementation", libs.androidx.compose.uiTooling)

    // location
    implementation(libs.maplibre.location)

    // Dependency Injection
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidxCompose)
    implementation(libs.koin.androidxWorkmanager)

    // Settings (NearbyQuestMonitor and other SCEE androidApp code)
    implementation(libs.multiplatform.settings)

    // Database / IO
    implementation(libs.androidx.sqliteBundled)
    implementation(libs.kotlinx.io)

    // HTTP Client
    implementation(libs.ktor.client.android)

    // finding OSM features
    implementation(libs.osmfeatures)

    coreLibraryDesugaring(libs.desugar.jdkLibs)
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
