package de.westnordost.streetcomplete.screens.settings

import androidx.compose.runtime.Composable

internal interface QuestMonitorPermissions {
    /** True if everything needed to monitor nearby quests is granted. Otherwise asks for the next missing permission and returns false. */
    fun ensureGranted(): Boolean
}

/** Shared by the row and its switch, including the permission-free disabling path. */
internal fun QuestMonitorPermissions?.enableIfGranted(enable: Boolean): Boolean =
    enable && this?.ensureGranted() == true

/** Null on platforms that cannot monitor quests in the background (the monitor is an Android service). */
@Composable
internal expect fun rememberQuestMonitorPermissions(): QuestMonitorPermissions?
