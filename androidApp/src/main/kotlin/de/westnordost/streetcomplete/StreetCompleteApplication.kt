package de.westnordost.streetcomplete

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.core.content.getSystemService
import de.westnordost.streetcomplete.data.CacheTrimmer
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseConfigurator
import de.westnordost.streetcomplete.data.user.OsmAuthConfig
import de.westnordost.streetcomplete.screens.settings.LAST_KNOWN_DB_VERSION
import de.westnordost.streetcomplete.util.error_reporting.CrashReportsUncaughtExceptionHandler
import de.westnordost.streetcomplete.util.logs.Log
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class StreetCompleteApplication : Application() {

    private val crashReportsUncaughtExceptionHandler: CrashReportsUncaughtExceptionHandler by inject()
    private val cacheTrimmer: CacheTrimmer by inject()
    private val applicationInitializer: ApplicationInitializer by inject()
    private val appLocaleUpdater: AppLocaleUpdater by inject()

    override fun onCreate() {
        super.onCreate()

        ApplicationConstants.DEBUG = de.westnordost.streetcomplete.app.BuildConfig.APPLICATION_DEBUG
        ApplicationConstants.NAME = de.westnordost.streetcomplete.app.BuildConfig.APPLICATION_USER_AGENT_NAME
        OsmAuthConfig.clientIdLive = de.westnordost.streetcomplete.app.BuildConfig.OAUTH2_CLIENT_ID_LIVE
        OsmAuthConfig.clientIdTest = de.westnordost.streetcomplete.app.BuildConfig.OAUTH2_CLIENT_ID_TEST
        OsmAuthConfig.callbackScheme = de.westnordost.streetcomplete.app.BuildConfig.OAUTH2_CALLBACK_SCHEME
        OsmAuthConfig.requestedScopes = de.westnordost.streetcomplete.app.BuildConfig.OAUTH2_REQUESTED_SCOPES
            .split(' ')
            .filter { it.isNotEmpty() }

        startKoin {
            androidContext(this@StreetCompleteApplication)
            workManagerFactory()
            modules(androidModule, commonModule)
        }

        require(StreetCompleteDatabaseConfigurator.version == LAST_KNOWN_DB_VERSION.toInt()) { "update database import/export" }

        crashReportsUncaughtExceptionHandler.install()

        applicationInitializer.initialize()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // the system resets the default locales to the new configuration
        appLocaleUpdater.update()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // very low on memory -> drop caches
                cacheTrimmer.clearCaches()
                Log.i("StreetCompleteApplication", "onTrimMemory, level $level: ${getMemString()}")
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // memory needed, but not critical -> trim only
                Log.i("StreetCompleteApplication", "onTrimMemory, level $level: ${getMemString()}")
                cacheTrimmer.trimCaches()
            }
        }
    }

    private fun getMemString(): String {
        val memInfo = MemoryInfo()
        getSystemService<ActivityManager>()?.getMemoryInfo(memInfo)
        return "${memInfo.availMem / 0x100000L} MB of ${memInfo.totalMem / 0x100000L} available, mem low: ${memInfo.lowMemory}, mem low threshold: ${memInfo.threshold / 0x100000L} MB"
    }
}
