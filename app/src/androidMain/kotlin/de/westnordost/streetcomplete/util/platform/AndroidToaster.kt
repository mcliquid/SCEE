package de.westnordost.streetcomplete.util.platform

import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat

class AndroidToaster(private val context: Context) : PlatformToaster {
    override val canShowOverDialogs = true

    override fun show(text: String) {
        ContextCompat.getMainExecutor(context).execute {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}
