import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Prefer gradle.properties / -P / ORG_GRADLE_PROJECT_*; never invent client IDs.
fun oauthClientIdProperty(name: String): String =
    providers.gradleProperty(name).orElse("").get().trim()

val sceemcOAuthClientId = oauthClientIdProperty("app.sceemc.oauth2ClientId")
val sceemcOAuthClientIdTest = oauthClientIdProperty("app.sceemc.oauth2ClientIdTest")

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
        // Independent package so SCEE-mc installs alongside StreetComplete and SCEE.
        applicationId = "de.westnordost.streetcomplete.expert.mc"
        minSdk = 25
        targetSdk = 37
        // Bump for each published SCEE-mc APK so Android treats it as an in-place update.
        versionCode = 6406
        versionName = providers.gradleProperty("app.streetcomplete.versionName").get()

        // no x86: the MapLibre Compose runtime has no x86 build, and other native libraries must
        // not make the app installable where the map cannot run
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }

        buildConfigField("String", "APPLICATION_USER_AGENT_NAME", "\"SCEE-mc\"")
        // Client IDs from ~/.gradle/gradle.properties or CI. Never invent IDs; never log values.
        buildConfigField("String", "OAUTH2_CLIENT_ID_LIVE", "\"$sceemcOAuthClientId\"")
        buildConfigField("String", "OAUTH2_CLIENT_ID_TEST", "\"$sceemcOAuthClientIdTest\"")
        buildConfigField("String", "OAUTH2_CALLBACK_SCHEME", "\"sceemc\"")
        buildConfigField(
            "String",
            "OAUTH2_REQUESTED_SCOPES",
            "\"read_prefs write_api write_notes write_gpx\""
        )
    }

    signingConfigs {
        create("release") {
        }
    }

    buildTypes {
        all {
            isMinifyEnabled = true
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
            matchingFallbacks += listOf("release")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            isShrinkResources = true
        }
        // Previous debuggable debug variant, with its own data directory.
        create("legacyDebug") {
            applicationIdSuffix = ".legacydebug"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
            isMinifyEnabled = false
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
            isShrinkResources = true
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

// Fail release packaging when BuildConfig would embed a blank OSM client id.
// Typed task (buildSrc) — configuration-cache safe; only wired to release packaging.
val verifyReleaseOAuthClientId =
    tasks.register<VerifyReleaseOAuthClientIdTask>("verifyReleaseOAuthClientId") {
        group = "verification"
        description = "Ensures release BuildConfig embeds a non-blank SCEE-mc OAuth client id"
        dependsOn("generateReleaseBuildConfig")
        buildConfigFile.set(
            layout.buildDirectory.file(
                "generated/source/buildConfig/release/de/westnordost/streetcomplete/app/BuildConfig.java"
            )
        )
    }

tasks.matching { it.name == "assembleRelease" || it.name == "packageRelease" }.configureEach {
    dependsOn(verifyReleaseOAuthClientId)
}
