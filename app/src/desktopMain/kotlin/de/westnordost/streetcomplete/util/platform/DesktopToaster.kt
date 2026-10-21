package de.westnordost.streetcomplete.util.platform

import de.westnordost.streetcomplete.util.logs.Log
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/** Desktop notification through the system tray; where there is no tray, the text is only logged. */
class DesktopToaster : PlatformToaster {
    // Popups of the main window are not reliably shown above dialogs, a tray message is not meant as a replacement.
    override val canShowOverDialogs = false

    private var trayIcon: TrayIcon? = null

    @Synchronized
    override fun show(text: String) {
        try {
            if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
                Log.w(TAG, text)
                return
            }
            val icon = trayIcon ?: TrayIcon(BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "SCEE")
                .also { SystemTray.getSystemTray().add(it); trayIcon = it }
            icon.displayMessage("SCEE", text, TrayIcon.MessageType.INFO)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to show \"$text\": ${e.message}")
        }
    }

    companion object { private const val TAG = "Toast" }
}
