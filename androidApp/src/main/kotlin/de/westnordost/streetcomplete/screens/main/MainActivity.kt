package de.westnordost.streetcomplete.screens.main

import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.App
import de.westnordost.streetcomplete.AppLocaleUpdater
import de.westnordost.streetcomplete.AppViewModel
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.screens.settings.custom_geometry_changed
import de.westnordost.streetcomplete.screens.settings.gpx_track_changed
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

/** Android entry point for the shared application. */
class MainActivity : ComponentActivity(), AndroidScopeComponent {
    override val scope: Scope by activityScope()
    private val viewModel: AppViewModel by viewModel()
    private val mainViewModel: MainViewModel by viewModel()
    private val appLocaleUpdater: AppLocaleUpdater by inject()
    private val prefs: Preferences by inject()

    private var questMonitorJob: Job? = null
    private val questMonitorConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        appLocaleUpdater.update()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        loadFileKit()

        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            KoinActivityScope {
                val uri by viewModel.pendingUri.collectAsState()
                App(
                    uri = uri,
                    onConsumedUri = viewModel::consumeUri,
                    viewModel = viewModel,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Stop NearbyQuestMonitor while the main map is visible.
        stopQuestMonitor()
    }

    override fun onResume() {
        // Android can reset the default locales without another Application configuration callback.
        appLocaleUpdater.update()
        super.onResume()
        if (gpx_track_changed) {
            mainViewModel.reloadGpxTrack.value = true
            gpx_track_changed = false
        }
        if (custom_geometry_changed) {
            mainViewModel.reloadCustomGeometry.value = true
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
        val uriString = uri.toString()
        if (intent.type?.startsWith("text/") == true) {
            mainViewModel.textIntentUri.value = uriString
        }
        viewModel.openUri(uriString)
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
