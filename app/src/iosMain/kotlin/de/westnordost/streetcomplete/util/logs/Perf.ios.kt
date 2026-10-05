package de.westnordost.streetcomplete.util.logs

@kotlin.native.concurrent.ThreadLocal
private val state = PerfThreadState()

internal actual fun currentPerfState(): PerfThreadState = state
