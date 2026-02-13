package de.westnordost.streetcomplete.quests.socket

import de.westnordost.streetcomplete.R

val SocketType.iconResId: Int get() = when (this) {
    SocketType.TYPE2 ->        R.drawable.ic_socket_type2
    SocketType.TYPE2_CABLE ->  R.drawable.ic_socket_type2_cable
    SocketType.TYPE2_COMBO ->  R.drawable.ic_socket_ccs2
    SocketType.CHADEMO ->      R.drawable.ic_socket_chademo
    SocketType.DOMESTIC ->     R.drawable.ic_socket_domestic
}

val SocketType.titleResId: Int get() = when (this) {
    SocketType.TYPE2 ->        R.string.quest_socket_type2
    SocketType.TYPE2_CABLE ->  R.string.quest_socket_type2_cable
    SocketType.TYPE2_COMBO ->  R.string.quest_socket_type2_combo
    SocketType.CHADEMO ->      R.string.quest_socket_chademo
    SocketType.DOMESTIC ->     R.string.quest_socket_domestic
}
