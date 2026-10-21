package de.westnordost.streetcomplete.ui.common.quest

import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

@Composable
internal actual fun rememberQrScanner(onScanned: (String) -> Unit, onNoHandler: () -> Unit): (() -> Unit)? {
    val currentOnScanned = rememberUpdatedState(onScanned)
    val currentOnNoHandler = rememberUpdatedState(onNoHandler)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val content = result.data?.getStringExtra("SCAN_RESULT")
            if (!content.isNullOrBlank()) currentOnScanned.value(content)
        }
    }
    return {
        try {
            launcher.launch(Intent("com.google.zxing.client.android.SCAN"))
        } catch (_: ActivityNotFoundException) {
            currentOnNoHandler.value()
        }
    }
}
