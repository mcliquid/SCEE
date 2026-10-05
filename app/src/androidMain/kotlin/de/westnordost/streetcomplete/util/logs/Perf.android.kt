package de.westnordost.streetcomplete.util.logs

private val states = ThreadLocal<PerfThreadState>()

internal actual fun currentPerfState(): PerfThreadState {
    val existing = states.get()
    if (existing != null) return existing
    val created = PerfThreadState()
    states.set(created)
    return created
}
