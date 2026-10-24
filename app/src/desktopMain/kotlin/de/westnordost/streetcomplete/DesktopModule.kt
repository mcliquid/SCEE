package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.util.platform.DesktopToaster
import de.westnordost.streetcomplete.util.platform.PlatformToaster
import de.westnordost.streetcomplete.util.readBundledAsset
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.data.preferences.DesktopPreferenceStore
import de.westnordost.streetcomplete.data.preferences.SettingsWithSnapshot
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.data.Cleaner
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.DatabaseImpl
import de.westnordost.streetcomplete.data.DesktopPeriodicCleaner
import de.westnordost.streetcomplete.data.PeriodicCleaner
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseConfigurator
import de.westnordost.streetcomplete.data.connection.ActiveNetworkConnection
import de.westnordost.streetcomplete.data.connection.DesktopActiveNetworkConnection
import de.westnordost.streetcomplete.data.download.DesktopDownloadController
import de.westnordost.streetcomplete.data.download.DownloadController
import de.westnordost.streetcomplete.data.initialize
import de.westnordost.streetcomplete.data.maptiles.MapLibreMapTilesDownloader
import de.westnordost.streetcomplete.data.maptiles.MapTilesDownloader
import de.westnordost.streetcomplete.data.upload.DesktopUploadController
import de.westnordost.streetcomplete.data.upload.UploadController
import de.westnordost.streetcomplete.screens.about.AppStoreInfo
import de.westnordost.streetcomplete.screens.about.DesktopAppStoreInfo
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import de.westnordost.streetcomplete.util.error_reporting.CrashReportHolder
import de.westnordost.streetcomplete.util.error_reporting.CrashReportsUncaughtExceptionHandler
import de.westnordost.streetcomplete.util.sound.DesktopSoundEffectPlayer
import de.westnordost.streetcomplete.util.sound.SoundEffectPlayer
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.createDefaultLocationProvider
import org.maplibre.compose.map.MapRuntime
import org.maplibre.compose.map.createMapRuntime
import org.maplibre.compose.offline.offlineStorage
import org.maplibre.compose.util.ExperimentalMaplibreComposeApi
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.prefs.Preferences

/** Identifies the desktop app to the system, e.g. for the app's data directories and settings.
 *  Keep in sync with bundleID in desktopApp/build.gradle.kts */
const val DESKTOP_APP_ID = "de.westnordost.streetcomplete.desktop"

/** Services shared by the development launcher and packaged desktop application. */
val desktopModule = module {

    // metadata

    single<de.westnordost.countryboundaries.CountryBoundaries> {
        Buffer().apply { write(runBlocking { readBundledAsset("boundaries.ser") }) }.use {
            de.westnordost.countryboundaries.CountryBoundaries.deserializeFrom(it)
        }
    }

    single<FeatureDictionary> {
        val metadataDirectory = desktopMetadataDirectory(File(FileKit.cacheDir.path, "osmfeatures"))
        FeatureDictionary.create(
            fileSystem = SystemFileSystem,
            presetsBasePath = File(metadataDirectory, "default").path,
            brandPresetsBasePath = File(metadataDirectory, "brands").path,
        )
    }

    // error reporting

    single {
        CrashReportsUncaughtExceptionHandler(get(), Path(FileKit.filesDir.path, "crashreport.txt"))
    }
    single<CrashReportHolder> { get<CrashReportsUncaughtExceptionHandler>() }

    // database

    single<Database> {
        val databaseFile = Path(FileKit.filesDir.path, ApplicationConstants.DATABASE_NAME)
        DatabaseImpl(BundledSQLiteDriver().open(databaseFile.toString())).apply {
            initialize(StreetCompleteDatabaseConfigurator)
        }
    } onClose { it?.close() }

    // avatars cache dir

    factory(named("AvatarsCacheDirectory")) {
        Path(FileKit.cacheDir.path, ApplicationConstants.AVATARS_CACHE_DIRECTORY)
    }
    factory(named("EliIndexCacheDirectory")) {
        Path(FileKit.cacheDir.path, ApplicationConstants.ELI_CACHE_DIRECTORY)
    }

    // app store info

    single<AppStoreInfo> { DesktopAppStoreInfo }

    // AR

    factory<ArSupportChecker> { object : ArSupportChecker {
        override fun invoke(): Boolean = false
    } }

    // location

    single<LocationProvider> { createDefaultLocationProvider() } onClose { it?.close() }

    // settings

    single<SettingsWithSnapshot> {
        DesktopPreferenceStore(Preferences.userRoot().node(DESKTOP_APP_ID.replace('.', '/')))
    }
    single<ObservableSettings> { get<SettingsWithSnapshot>() }
    single<PreferencesMigration> { DesktopPreferencesMigration(get()) }

    single<PlatformToaster> { DesktopToaster() }

    // sound

    single<SoundEffectPlayer> { DesktopSoundEffectPlayer() }

    // connection

    factory<ActiveNetworkConnection> { DesktopActiveNetworkConnection() }

    // map

    single<MapRuntime> {
        @OptIn(ExperimentalMaplibreComposeApi::class)
        runBlocking(Dispatchers.Main) {
            createMapRuntime { cacheFile = Path(FileKit.filesDir.path, "maplibre-cache.db") }
        }
    } onClose { it?.close() }

    factory<MapTilesDownloader> {
        val density = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat()
        MapLibreMapTilesDownloader(get<MapRuntime>().offlineStorage, density)
    }

    // background jobs

    single<UploadController> { DesktopUploadController(get()) } onClose { (it as? DesktopUploadController)?.close() }

    single<DownloadController> { DesktopDownloadController(get(), get()) } onClose { (it as? DesktopDownloadController)?.close() }

    single { DesktopPeriodicCleaner { get<Cleaner>().cleanOld() } } onClose { it?.close() }

    single<PeriodicCleaner> { get<DesktopPeriodicCleaner>() }
}
