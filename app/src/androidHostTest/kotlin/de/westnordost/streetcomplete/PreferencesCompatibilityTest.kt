package de.westnordost.streetcomplete

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.LocaleList
import de.westnordost.streetcomplete.data.preferences.AndroidPreferenceStore
import de.westnordost.streetcomplete.data.FeedsUpdater
import de.westnordost.streetcomplete.data.UnsyncedChangesCountSource
import de.westnordost.streetcomplete.data.Preloader
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesController
import de.westnordost.streetcomplete.data.edithistory.EditHistoryController
import de.westnordost.streetcomplete.data.elementfilter.filters.CompareTagAge
import de.westnordost.streetcomplete.data.elementfilter.filters.RelativeDate
import de.westnordost.streetcomplete.data.logs.LogsController
import de.westnordost.streetcomplete.data.logs.LogsDao
import de.westnordost.streetcomplete.data.meta.CountryInfos
import de.westnordost.streetcomplete.data.osmcal.CalendarEventsController
import de.westnordost.streetcomplete.data.osmcal.CalendarEventsDao
import de.westnordost.streetcomplete.data.osmcal.OsmCalUpdater
import de.westnordost.streetcomplete.data.osmnotes.AvatarsDownloader
import de.westnordost.streetcomplete.data.preferences.Autosync
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.ResurveyIntervalsUpdater
import de.westnordost.streetcomplete.data.preferences.Theme
import de.westnordost.streetcomplete.data.download.strategy.MobileDataAutoDownloadStrategy
import de.westnordost.streetcomplete.data.download.strategy.WifiAutoDownloadStrategy
import de.westnordost.streetcomplete.data.connection.ActiveNetworkConnection
import de.westnordost.streetcomplete.data.quest.AutoSyncer
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.data.user.UserApiClient
import de.westnordost.streetcomplete.data.user.UserDataController
import de.westnordost.streetcomplete.data.user.UserLoginSource
import de.westnordost.streetcomplete.data.user.UserUpdater
import de.westnordost.streetcomplete.data.weeklyosm.WeeklyOsmUpdater
import de.westnordost.streetcomplete.quests.general_access.AddGeneralAccess
import de.westnordost.streetcomplete.quests.getPrefixedFullElementSelectionPref
import de.westnordost.streetcomplete.screens.settings.renamedQuests
import de.westnordost.streetcomplete.util.logs.DatabaseLogger
import de.westnordost.streetcomplete.util.logs.Log
import de.westnordost.streetcomplete.util.logs.Logger
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import org.maplibre.compose.location.LocationProvider
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import kotlin.test.*

/** Real Android XML/storage and the production initializer; no copy of the migration algorithm. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PreferencesCompatibilityTest {
    private lateinit var raw: SharedPreferences
    private lateinit var migrationStore: SharedPreferences
    private lateinit var prefs: Preferences
    private val preferencesField = Prefs::class.java.getDeclaredField("preferences").apply { isAccessible = true }
    private var previousPreferences: Any? = null
    private val previousLocale = Locale.getDefault()
    private val previousLocales = LocaleList.getDefault()
    private val previousLoggers = Log.instances
    private val previousMultiplier = RelativeDate.MULTIPLIER
    private val previousKeys = CompareTagAge.resurveyKeys
    private val previousDate = CompareTagAge.resurveyDateMillis
    private val clients = mutableListOf<HttpClient>()

    @Before fun setUp() {
        previousPreferences = preferencesField.get(Prefs)
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.applicationInfo.dataDir, "shared_prefs/kmp_m2a_fixture.xml")
        file.parentFile!!.mkdirs()
        javaClass.getResourceAsStream("/preferences/legacy-scee.xml")!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        raw = context.getSharedPreferences("kmp_m2a_fixture", Context.MODE_PRIVATE)
        prefs = Preferences(AndroidPreferenceStore(raw))
        migrationStore = raw
        Prefs.preferences = prefs
        Log.instances = mutableListOf()
    }

    @After fun tearDown() {
        clients.forEach { it.close() }
        preferencesField.set(Prefs, previousPreferences)
        Locale.setDefault(previousLocale)
        LocaleList.setDefault(previousLocales)
        Log.instances = previousLoggers
        RelativeDate.MULTIPLIER = previousMultiplier
        CompareTagAge.resurveyKeys = previousKeys
        CompareTagAge.resurveyDateMillis = previousDate
    }

    @Test fun `boolean fixture is read overwritten and persisted through recreation`() {
        assertTrue(prefs.expertMode)
        prefs.expertMode = false
        assertFalse(raw.getBoolean("expert_mode", true))
        assertFalse(recreated().expertMode)
    }

    @Test fun `int fixture preserves type and overwrite`() {
        assertEquals(15, prefs.getInt(Prefs.LOCATION_INTERVAL, 0))
        prefs.putInt(Prefs.LOCATION_INTERVAL, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, recreated().getInt(Prefs.LOCATION_INTERVAL, 0))
        assertIs<Int>(raw.all[Prefs.LOCATION_INTERVAL])
    }

    @Test fun `long fixture keeps values beyond int range`() {
        assertEquals(1234567890123L, prefs.userId)
        prefs.userId = Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, recreated().userId)
        assertIs<Long>(raw.all["osm.userid"])
    }

    @Test fun `float fixture preserves raw float representation`() {
        assertEquals(75.5f, prefs.getFloat(Prefs.QUEST_MONITOR_RADIUS, 0f))
        prefs.putFloat(Prefs.QUEST_MONITOR_RADIUS, 0.125f)
        assertEquals(0.125f, recreated().getFloat(Prefs.QUEST_MONITOR_RADIUS, 0f))
        assertIs<Float>(raw.all[Prefs.QUEST_MONITOR_RADIUS])
    }

    @Test fun `string fixture keeps unicode escaping and empty values`() {
        assertEquals("Fixture user ä & <test>", prefs.userName)
        prefs.userName = ""
        assertEquals("", recreated().userName)
        prefs.userName = null
        assertNull(recreated().userName)
        assertFalse(raw.contains("osm.username"))
    }

    @Test fun `legacy string sets stay native sets through editor and all view`() {
        val key = "legacy_set_AddPlaygroundAccess"
        assertEquals(setOf("AddPlaygroundAccess", "ä; <unchanged>"), raw.getStringSet(key, null))
        raw.edit().putStringSet(key, setOf("", "second")).commit()
        assertEquals(setOf("", "second"), recreated().all[key])
    }

    @Test fun `double settings use raw long bits not a native float`() {
        prefs.mapZoom = 18.125
        assertEquals(18.125.toRawBits(), raw.getLong("map.zoom2", 0))
        assertEquals(18.125, recreated().mapZoom)
        raw.edit().putLong("map.zoom2", 12.5.toRawBits()).commit()
        assertEquals(12.5, prefs.mapZoom)
    }

    @Test fun `missing primitive keys return supplied defaults without storing them`() {
        assertFalse(prefs.getBoolean("missing", false))
        assertEquals(17, prefs.getInt("missing", 17))
        assertEquals(18L, prefs.getLong("missing", 18L))
        assertEquals(1.5f, prefs.getFloat("missing", 1.5f))
        assertEquals("fallback", prefs.getString("missing", "fallback"))
        assertNull(prefs.getStringOrNull("missing"))
        assertNull(raw.getStringSet("missing", null))
        assertFalse(prefs.contains("missing"))
    }

    @Test fun `empty store preserves application defaults without materializing keys`() {
        raw.edit().clear().commit()
        assertFalse(prefs.expertMode)
        assertFalse(prefs.showQuickSettings)
        assertFalse(prefs.overrideCountryRestrictions)
        assertEquals(0L, prefs.selectedEditTypePreset)
        assertEquals(-1L, prefs.userId)
        assertEquals(Theme.SYSTEM, prefs.theme)
        assertEquals(Autosync.ON, prefs.autosync)
        assertTrue(prefs.mapIsFollowing)
        assertFalse(prefs.mapIsNavigationMode)
        assertTrue(prefs.isSynchronizingStatistics)
        assertTrue(prefs.statisticsSynchronizedOnce)
        assertNull(prefs.lastDataVersion)
        assertNull(prefs.oAuth2AccessToken)
        assertTrue(raw.all.isEmpty())
    }

    @Test fun `remove clears persisted value and restoring component does not recreate it`() {
        prefs.remove(Prefs.LOCATION_INTERVAL)
        assertFalse(raw.contains(Prefs.LOCATION_INTERVAL))
        assertEquals(99, recreated().getInt(Prefs.LOCATION_INTERVAL, 99))
    }

    @Test fun `wrong stored primitive type throws rather than using default`() {
        raw.edit().putString(Prefs.LOCATION_INTERVAL, "15").commit()
        assertFailsWith<ClassCastException> { prefs.getInt(Prefs.LOCATION_INTERVAL, 0) }
    }

    @Test fun `old AUTO theme is accepted but an unknown enum is not`() {
        assertEquals(Theme.SYSTEM, prefs.theme)
        assertEquals(Autosync.ON, prefs.autosync)
        raw.edit().putString("theme.select", "INVALID").commit()
        assertFailsWith<IllegalArgumentException> { prefs.theme }
    }

    @Test fun `JSON last picked fixture is decoded and corrupt JSON falls back without deleting it`() {
        val serializer = ListSerializer(String.serializer())
        assertEquals(listOf("yes", "no"), prefs.getLastPicked(serializer, "AddPicnicTableCover"))
        raw.edit().putString("imageListLastPicked.AddPicnicTableCover", "not JSON").commit()
        assertEquals(emptyList(), prefs.getLastPicked(serializer, "AddPicnicTableCover"))
        assertEquals("not JSON", raw.getString("imageListLastPicked.AddPicnicTableCover", null))
    }

    @Test fun `preset selection custom quest config and expert mode interpret legacy keys`() {
        assertEquals(7L, prefs.selectedEditTypePreset)
        assertEquals("CustomOverlay", prefs.selectedOverlayName)
        assertTrue(AddGeneralAccess().prefs.expertMode)
        assertEquals("ways with access != private", prefs.getString("7_qs_AddPlaygroundAccess_full_element_selection", ""))
        assertEquals(7L, recreated().selectedEditTypePreset)
    }

    @Test fun `migrated quest setting matches real preset prefix helper`() {
        prefs.putBoolean(Prefs.QUEST_SETTINGS_PER_PRESET, true)
        initialize()
        val key = AddGeneralAccess().getPrefixedFullElementSelectionPref(prefs)
        assertEquals("7_qs_AddGeneralAccess_full_element_selection", key)
        assertEquals("ways with access != private", prefs.getString(key, ""))
    }

    @Test fun `existing settings export has five typed sections but omits native sets`() {
        val method = Class.forName("de.westnordost.streetcomplete.screens.settings.DataManagementScreenKt")
            .getDeclaredMethod("settingsToString", Map::class.java).apply { isAccessible = true }
        val text = method.invoke(null, raw.all) as String
        for (type in listOf("boolean", "int", "long", "float", "string")) {
            assertTrue(text.contains("$type settings\n"))
        }
        assertTrue(text.contains("expert_mode"))
        assertTrue(text.contains("1234567890123"))
        assertFalse(text.contains("legacy_set_AddPlaygroundAccess"))
    }

    @Test fun `failed settings import retains values already applied before invalid section`() {
        val method = Class.forName("de.westnordost.streetcomplete.screens.settings.DataManagementScreenKt")
            .getDeclaredMethod("readToSettings", List::class.java, Preferences::class.java).apply { isAccessible = true }
        val result = method.invoke(null, listOf("boolean settings", "{\"expert_mode\":false}",
            "int settings", "{\"gps_interval\":\"not an int\"}"), prefs) as Boolean
        assertFalse(result)
        assertFalse(prefs.expertMode)
        assertEquals(15, prefs.getInt(Prefs.LOCATION_INTERVAL, 0))
    }

    @Test fun `all uses the instance backing store independently of the compatibility global`() {
        val other = RuntimeEnvironment.getApplication().getSharedPreferences("kmp_m2a_other", 0)
        other.edit().putBoolean(Prefs.EXPERT_MODE, false).commit()
        val instance = Preferences(AndroidPreferenceStore(other))
        assertFalse(instance.expertMode)
        assertEquals(false, instance.all[Prefs.EXPERT_MODE])
    }

    @Test fun `quest bridge requires initialization but instance enumeration is independent`() {
        preferencesField.set(Prefs, null)
        assertFailsWith<UninitializedPropertyAccessException> { AddGeneralAccess().prefs }
        assertTrue(prefs.all.containsKey("expert_mode"))
        migrationStore = raw
        Prefs.preferences = prefs
        assertSame(prefs, AddGeneralAccess().prefs)
        assertTrue(prefs.all.containsKey("expert_mode"))
    }

    @Test fun `production Koin initializes quest bridge and aliases the same Android file`() {
        val requestedNames = mutableListOf<String>()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                requestedNames += name
                return raw
            }
        }
        preferencesField.set(Prefs, null)
        val app = koinApplication {
            androidContext(context)
            modules(androidModule, commonModule)
        }
        try {
            val registered = app.koin.get<Preferences>()
            assertTrue(registered.expertMode)
            registered.expertMode = false
            assertFalse(raw.getBoolean(Prefs.EXPERT_MODE, true))
            assertEquals(listOf(context.packageName + "_preferences"), requestedNames)
            assertSame(registered, preferencesField.get(Prefs))
            Prefs.preferences = registered
            assertSame(registered, AddGeneralAccess().prefs)
        } finally {
            app.close()
        }
    }

    @Test fun `migration renames every current quest mapping without changing custom filter values`() {
        for ((old, new) in renamedQuests) {
            raw.edit().putString("7_qs_$old", "nodes with name").commit()
            initialize()
            assertFalse(raw.contains("7_qs_$old"))
            assertEquals("nodes with name", raw.getString("7_qs_$new", null))
            raw.edit().putString("lastVersion_data", "62.0").commit()
        }
    }

    @Test fun `unknown historical type keys and native sets remain untouched`() {
        raw.edit().putBoolean("boolean_AddPlaygroundAccess", true)
            .putInt("int_AddPlaygroundAccess", Int.MAX_VALUE)
            .putLong("long_AddPlaygroundAccess", Long.MAX_VALUE)
            .putFloat("float_AddPlaygroundAccess", 0.125f).commit()
        initialize()
        assertEquals(true, raw.all["boolean_AddPlaygroundAccess"])
        assertEquals(Int.MAX_VALUE, raw.all["int_AddPlaygroundAccess"])
        assertEquals(Long.MAX_VALUE, raw.all["long_AddPlaygroundAccess"])
        assertEquals(0.125f, raw.all["float_AddPlaygroundAccess"])
        assertEquals(setOf("AddPlaygroundAccess", "ä; <unchanged>"), raw.getStringSet("legacy_set_AddPlaygroundAccess", null))
    }

    @Test fun `set elements alone do not trigger migration or get renamed`() {
        raw.edit().putStringSet("legacy_set", setOf("AddPlaygroundAccess")).commit()
        initialize()
        assertEquals(setOf("AddPlaygroundAccess"), raw.getStringSet("legacy_set", null))
    }

    @Test fun `string payloads remain unchanged while recognized history keys migrate`() {
        raw.edit().putString("user_text", "prefixAddPlaygroundAccessSuffix").commit()
        initialize()
        assertEquals("prefixAddPlaygroundAccessSuffix", raw.getString("user_text", null))
        assertEquals("[\"yes\",\"no\"]", raw.getString("imageListLastPicked.AddAmenityCover", null))
    }

    @Test fun `existing destination wins and different old value is retained`() {
        raw.edit().putString("7_qs_AddGeneralAccess_full_element_selection", "new destination").commit()
        initialize()
        assertEquals("new destination", raw.getString("7_qs_AddGeneralAccess_full_element_selection", null))
        assertEquals("ways with access != private", raw.getString("7_qs_AddPlaygroundAccess_full_element_selection", null))
    }

    @Test fun `partial migration preserves existing destinations when matching old key is absent`() {
        raw.edit().remove("7_qs_AddPlaygroundAccess_full_element_selection").putString("7_qs_AddGeneralAccess_full_element_selection", "already migrated").commit()
        initialize()
        assertEquals("already migrated", raw.getString("7_qs_AddGeneralAccess_full_element_selection", null))
        assertTrue(raw.contains("legacy_set_AddPlaygroundAccess"))
    }

    @Test fun `new install without version still runs rename pass and writes current marker`() {
        raw.edit().remove("lastVersion_data").commit()
        initialize()
        assertEquals(BuildConfig.VERSION_NAME, recreated().lastDataVersion)
        assertFalse(raw.contains("7_qs_AddPlaygroundAccess_full_element_selection"))
    }

    @Test fun `current marker still repairs recognized old keys left by previous defect`() {
        raw.edit().putString("lastVersion_data", BuildConfig.VERSION_NAME).commit()
        initialize()
        assertFalse(raw.contains("7_qs_AddPlaygroundAccess_full_element_selection"))
        assertTrue(raw.contains("7_qs_AddGeneralAccess_full_element_selection"))
    }

    @Test fun `different newer marker also triggers migration without numeric version comparison`() {
        raw.edit().putString("lastVersion_data", "9999.0").commit()
        initialize()
        assertEquals(BuildConfig.VERSION_NAME, prefs.lastDataVersion)
        assertFalse(raw.contains("7_qs_AddPlaygroundAccess_full_element_selection"))
    }

    @Test fun `repeated initialization skips rename and forced replay preserves migrated payload`() {
        initialize()
        val loggerCount = Log.instances.size
        val once = raw.all.filterKeys { it != "lastFeedUpdate" }
        initialize()
        assertEquals(loggerCount * 2, Log.instances.size)
        assertEquals(once, raw.all.filterKeys { it != "lastFeedUpdate" })
        raw.edit().putString("lastVersion_data", "62.0").commit()
        initialize()
        assertEquals(once, raw.all.filterKeys { it != "lastFeedUpdate" })
    }

    @Test fun `locale resurvey and feed are set but version is unchanged at rename snapshot`() {
        var observed = false
        val recording = object : SharedPreferences by raw {
            override fun getAll(): MutableMap<String, *> {
                assertEquals(Locale.GERMANY, Locale.getDefault())
                assertEquals(0.5f, RelativeDate.MULTIPLIER)
                assertNotNull(raw.getString("lastFeedUpdate", null))
                assertEquals("62.0", raw.getString("lastVersion_data", null))
                observed = true
                return raw.all
            }
        }
        migrationStore = recording
        initialize()
        assertTrue(observed)
    }

    @Test fun `failure reading migration snapshot preserves marker and retries next start`() {
        migrationStore = object : SharedPreferences by raw {
            override fun getAll(): MutableMap<String, *> = error("snapshot failed")
        }
        initialize()
        assertEquals("62.0", recreated().lastDataVersion)
        migrationStore = raw
        initialize()
        assertFalse(raw.contains("7_qs_AddPlaygroundAccess_full_element_selection"))
        assertTrue(raw.contains("7_qs_AddGeneralAccess_full_element_selection"))
    }

    @Test fun `failure committing rename editor preserves marker and allows retry`() {
        migrationStore = object : SharedPreferences by raw {
            override fun edit(): SharedPreferences.Editor {
                val editor = raw.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit(): Boolean = error("commit failed before publication")
                    override fun remove(key: String): SharedPreferences.Editor { editor.remove(key); return this }
                    override fun putString(key: String, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor { editor.putStringSet(key, values); return this }
                }
            }
        }
        initialize()
        assertEquals("62.0", prefs.lastDataVersion)
        migrationStore = raw
        initialize()
        assertEquals(BuildConfig.VERSION_NAME, prefs.lastDataVersion)
        assertFalse(raw.contains("7_qs_AddPlaygroundAccess_full_element_selection"))
    }

    @Test fun `failed published migration is logged and next initializer completes it`() {
        val errors = mutableListOf<Throwable?>()
        Log.instances.add(object : Logger {
            override fun v(tag: String, message: String) {}
            override fun d(tag: String, message: String) {}
            override fun i(tag: String, message: String) {}
            override fun w(tag: String, message: String, exception: Throwable?) {}
            override fun e(tag: String, message: String, exception: Throwable?) { errors += exception }
        })
        var firstCommit = true
        migrationStore = object : SharedPreferences by raw {
            override fun edit(): SharedPreferences.Editor {
                val editor = raw.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun remove(key: String): SharedPreferences.Editor { editor.remove(key); return this }
                    override fun putString(key: String, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                    override fun commit(): Boolean {
                        val result = editor.commit()
                        return if (firstCommit) { firstCommit = false; false } else result
                    }
                }
            }
        }
        initialize()
        assertEquals("62.0", recreated().lastDataVersion)
        assertTrue(errors.single() is IllegalStateException)
        migrationStore = raw
        initialize()
        assertEquals(BuildConfig.VERSION_NAME, recreated().lastDataVersion)
        assertFalse(raw.contains("7_qs_AddPlaygroundAccess_full_element_selection"))
        assertEquals("ways with access != private", raw.getString("7_qs_AddGeneralAccess_full_element_selection", null))
    }

    @Test fun `fatal migration errors are not swallowed by initialization`() {
        val failure = OutOfMemoryError("injected")
        migrationStore = object : SharedPreferences by raw {
            override fun getAll(): MutableMap<String, *> = throw failure
        }
        assertSame(failure, assertFailsWith<OutOfMemoryError> { initialize() })
        assertEquals("62.0", recreated().lastDataVersion)
    }

    @Test fun `failure before version write leaves old marker eligible for next start`() {
        raw.edit().putString("quests.resurveyIntervals", "INVALID").commit()
        assertFailsWith<IllegalArgumentException> { initialize() }
        assertEquals("62.0", prefs.lastDataVersion)
        raw.edit().putString("quests.resurveyIntervals", "DEFAULT").commit()
        initialize()
        assertFalse(raw.contains("7_qs_AddPlaygroundAccess_full_element_selection"))
    }

    @Test fun `missing old keys do not invent migrated keys`() {
        raw.edit().clear().putString("lastVersion_data", "62.0").commit()
        initialize()
        assertFalse(raw.all.keys.any { it.startsWith("7_qs_") })
        assertEquals(BuildConfig.VERSION_NAME, prefs.lastDataVersion)
    }

    private fun recreated() = Preferences(AndroidPreferenceStore(raw))

    private fun initialize() {
        val client = HttpClient(MockEngine { error("Network forbidden in preferences baseline") }).also { clients += it }
        val api = mock<UserApiClient>()
        val login = mock<UserLoginSource> { every { isLoggedIn } returns false }
        val user = UserUpdater(api, AvatarsDownloader(client, api, SystemFileSystem, Path("kmp_m2a_unused_cache")), mock(),
            UserDataController(prefs, login), mock(), login)
        cancelScope(user, "coroutineScope")
        val feeds = FeedsUpdater(user, WeeklyOsmUpdater(mock(), prefs),
            OsmCalUpdater(mock(), CalendarEventsController(CalendarEventsDao(mock())), prefs), prefs, mock())
        val history = EditHistoryController(mock(), mock(), mock(), mock(), mock(), QuestTypeRegistry({ emptyList() }), mock())
        val downloadedTiles = DownloadedTilesController(mock())
        val unsyncedChanges = UnsyncedChangesCountSource(mock(), mock())
        val locationProvider = mock<LocationProvider> { every { updates(any()) } returns emptyFlow() }
        val activeNetworkConnection = mock<ActiveNetworkConnection> { every { capabilities } returns emptyFlow() }
        val autoSyncer = AutoSyncer(
            mock(), mock(),
            MobileDataAutoDownloadStrategy(mock(), downloadedTiles),
            WifiAutoDownloadStrategy(mock(), downloadedTiles),
            locationProvider, activeNetworkConnection, unsyncedChanges, mock(), mock(), prefs, mock(), downloadedTiles,
        )
        cancelScope(autoSyncer, "coroutineScope")
        val initializer = ApplicationInitializer(
            Preloader(lazy { error("Preloading forbidden") }, lazy { error("Preloading forbidden") }, CountryInfos { null }, prefs),
            feeds, history, DatabaseLogger(LogsController(LogsDao(mock()))), DownloadedTilesController(mock()),
            prefs, ResurveyIntervalsUpdater(prefs), AppLocaleUpdater(prefs),
            PreferencesMigration { migrateQuestPreferences(migrationStore, it) },
            autoSyncer,
        )
        // Suppress unrelated asynchronous preload/cleanup/invalidation; execute the real synchronous migration.
        cancelScope(initializer, "scope")
        initializer.initialize()
    }

    private fun cancelScope(instance: Any, name: String) {
        val scope = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance) as CoroutineScope
        scope.cancel()
    }
}
