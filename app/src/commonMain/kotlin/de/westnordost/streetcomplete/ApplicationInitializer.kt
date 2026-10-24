package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.ApplicationConstants.MAX_UNDO_HISTORY_AGE
import de.westnordost.streetcomplete.data.FeedsUpdater
import de.westnordost.streetcomplete.data.Preloader
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesController
import de.westnordost.streetcomplete.data.edithistory.EditHistoryController
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.ResurveyIntervalsUpdater
import de.westnordost.streetcomplete.data.quest.AutoSyncer
import de.westnordost.streetcomplete.util.TempLogger
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import de.westnordost.streetcomplete.util.logs.DatabaseLogger
import de.westnordost.streetcomplete.util.logs.KermitLogger
import de.westnordost.streetcomplete.util.logs.Log
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Common things on all platforms that are done at the start of the application. */
class ApplicationInitializer(
    private val preloader: Preloader,
    private val feedsUpdater: FeedsUpdater,
    private val editHistoryController: EditHistoryController,
    private val databaseLogger: DatabaseLogger,
    private val downloadedTilesController: DownloadedTilesController,
    private val prefs: Preferences,
    private val resurveyIntervalsUpdater: ResurveyIntervalsUpdater,
    private val appLocaleUpdater: AppLocaleUpdater,
    private val preferencesMigration: PreferencesMigration,
    // need to hold references to these so that they exist and can do their thing
    private val autoSyncer: AutoSyncer,
) {
    private val scope = CoroutineScope(SupervisorJob() + CoroutineName("Application"))

    fun initialize() {

        setLoggerInstances()

        appLocaleUpdater.start()

        resurveyIntervalsUpdater.update()

        feedsUpdater.updateNow()

        scope.launch(Dispatchers.IO) {
            editHistoryController.deleteSyncedOlderThan(nowAsEpochMilliseconds() - MAX_UNDO_HISTORY_AGE)
        }

        scope.launch {
            preloader.preload()
        }

        val migrated = try {
            val lastVersion = prefs.lastDataVersion
            preferencesMigration.migrate(BuildConfig.VERSION_NAME)
                && lastVersion != null && lastVersion != BuildConfig.VERSION_NAME
        } catch (failure: Exception) {
            Log.e("ApplicationInitializer", "Preferences migration failed; will retry on next start", failure)
            false
        }
        if (migrated) {
            onNewVersion()
        }
    }

    private fun onNewVersion() {
        scope.launch {
            withContext(Dispatchers.IO) { downloadedTilesController.invalidateAll() }
        }
    }

    private fun setLoggerInstances() {
        Log.instances.add(KermitLogger())
        if (prefs.getBoolean(Prefs.TEMP_LOGGER, false))
            Log.instances.add(TempLogger)
        else
            Log.instances.add(databaseLogger)
    }
}
