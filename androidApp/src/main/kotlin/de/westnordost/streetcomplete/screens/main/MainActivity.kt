package de.westnordost.streetcomplete.screens.main

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.screens.BaseActivity
import de.westnordost.streetcomplete.screens.about.AboutActivity
import de.westnordost.streetcomplete.screens.settings.SettingsActivity
import de.westnordost.streetcomplete.screens.settings.custom_geometry_changed
import de.westnordost.streetcomplete.screens.settings.gpx_track_changed
import de.westnordost.streetcomplete.screens.user.UserActivity
import de.westnordost.streetcomplete.ui.theme.AppTheme
import de.westnordost.streetcomplete.util.ktx.loadFileKit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.scope.AndroidScopeComponent
import org.koin.androidx.compose.scope.KoinActivityScope
import org.koin.androidx.scope.activityScope
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.scope.Scope

/** Android host for the shared main screen and application lifecycle work. */
class MainActivity : BaseActivity(), AndroidScopeComponent {
    override val scope: Scope by activityScope()

    private val prefs: Preferences by inject()
    private val viewModel: MainViewModel by viewModel()

    private var questMonitorJob: Job? = null
    private val questMonitorConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        loadFileKit()

        if (savedInstanceState == null) handleIntent(intent)

        setContentView(ComposeView(this).apply {
            setContent {
                AppTheme {
                    KoinActivityScope {
                        MainScreen(
                            viewModel = viewModel,
                            onClickSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                            onClickQuestSettings = { startActivity(SettingsActivity.createLaunchQuestSettingsIntent(this@MainActivity)) },
                            onClickAbout = { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) },
                            onClickProfile = { startActivity(Intent(this@MainActivity, UserActivity::class.java)) },
                            onClickLogin = {
                                startActivity(Intent(this@MainActivity, UserActivity::class.java).apply {
                                    putExtra(UserActivity.EXTRA_LAUNCH_AUTH, true)
                                })
                            },
                        )
                    }
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Stop NearbyQuestMonitor while the main map is visible.
        stopQuestMonitor()
    }

    override fun onResume() {
        super.onResume()
        if (gpx_track_changed) {
            viewModel.reloadGpxTrack.value = true
            gpx_track_changed = false
        }
        if (custom_geometry_changed) {
            viewModel.reloadCustomGeometry.value = true
            custom_geometry_changed = false
        }
    }

    override fun onStop() {
        super.onStop()
        // Start NearbyQuestMonitor when leaving the main screen (if enabled).
        startQuestMonitor()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (intent.type?.startsWith("text/") == true) {
            viewModel.textIntentUri.value = uri.toString()
        }
        viewModel.setUri(uri.toString())
    }

    private fun startQuestMonitor() {
        if (prefs.getBoolean(Prefs.QUEST_MONITOR, false) && !NearbyQuestMonitor.running) {
            questMonitorJob?.cancel()
            questMonitorJob = lifecycleScope.launch {
                delay(1000) // wait, as we don't want to start the monitor if onDestroy follows
                applicationContext.bindService(
                    Intent(this@MainActivity, NearbyQuestMonitor::class.java),
                    questMonitorConnection,
                    BIND_AUTO_CREATE
                )
            }
        }
    }

    private fun stopQuestMonitor() {
        if (prefs.getBoolean(Prefs.QUEST_MONITOR, false) || NearbyQuestMonitor.running) {
            try { applicationContext.unbindService(questMonitorConnection) }
            catch (_: IllegalArgumentException) { }
            questMonitorJob?.cancel()
            questMonitorJob = lifecycleScope.launch {
                delay(5000)
                try { applicationContext.unbindService(questMonitorConnection) }
                catch (_: IllegalArgumentException) { }
            }
        }
    }
}
