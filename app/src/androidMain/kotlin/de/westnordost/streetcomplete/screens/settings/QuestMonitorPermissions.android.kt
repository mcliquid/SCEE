package de.westnordost.streetcomplete.screens.settings

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Composable
internal actual fun rememberQuestMonitorPermissions(): QuestMonitorPermissions? {
    val ctx = LocalContext.current
    return remember(ctx) {
        object : QuestMonitorPermissions {
            override fun ensureGranted(): Boolean {
                val activity = ctx.getActivity()!!
                if (!activity.hasPermission(ACCESS_FINE_LOCATION)) {
                    ActivityCompat.requestPermissions(activity, arrayOf(ACCESS_FINE_LOCATION), 0)
                    return false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !activity.hasPermission(ACCESS_BACKGROUND_LOCATION)) {
                    ActivityCompat.requestPermissions(activity, arrayOf(ACCESS_BACKGROUND_LOCATION), 0)
                    return false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !activity.hasPermission(POST_NOTIFICATIONS)) {
                    ActivityCompat.requestPermissions(activity, arrayOf(POST_NOTIFICATIONS), 0)
                    return false
                }
                return true
            }
        }
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}
