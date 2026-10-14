rootProject.name = "StreetComplete"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // SCEE: locally published osmfeatures that reads the NSI format
        mavenLocal {
            content { includeGroup("de.westnordost") }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // SCEE: com.github.ticofab:android-gpx-parser
        maven { url = uri("https://www.jitpack.io") }
    }
}

include(":app")
include(":androidApp")
// SCEE: upstream desktop launcher (streetcomplete#7242) is not needed for Android builds
// and fails here because commonMain still has Android-only SCEE code. Re-enable later with:
// include(":desktopApp")
