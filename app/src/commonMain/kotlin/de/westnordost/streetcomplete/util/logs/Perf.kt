package de.westnordost.streetcomplete.util.logs

import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext

/**
 * Few aggregated performance logs for the quest loop.
 *
 * Logs go through [KermitLogger] only (logcat / platform log). They do not go through [Log] and
 * therefore do not insert into the database.
 *
 * The first rendered frame is intentionally not measured: Compose and MapLibre do not expose a
 * reliable, non-invasive callback for that. Open measurements stop when the bottom-sheet model is
 * ready. Pin measurements stop when the GeoJSON data has been handed to `rememberGeoJsonSource`.
 */
object Perf {
    const val TAG = "PERF"
    const val SLOW_QUEST_TYPE_MS = 100L

    private val logger = KermitLogger()
    private val clock = TimeSource.Monotonic
    private val ids = atomic(0L)
    private val armedOpen = atomic<PerfTrace?>(null)
    private val armedPin = atomic<ArmedPin?>(null)
    private val currentPin = atomic<PinRebuild?>(null)

    fun mark(): TimeMark = clock.markNow()

    fun ms(start: TimeMark): Long = start.elapsedNow().inWholeMilliseconds

    fun nextId(): Long = ids.incrementAndGet()

    fun log(message: String) {
        try {
            logger.i(TAG, "PERF $message")
        } catch (e: RuntimeException) {
            // android.util.Log is a stub in android host tests
            val message = e.message.orEmpty()
            if (!message.contains("not mocked") && !message.contains("Stub!")) throw e
        }
    }

    fun armOpen() {
        armedOpen.value = PerfTrace("open", nextId(), mark())
    }

    fun cancelOpen() {
        armedOpen.value = null
    }

    fun takeOpen(): PerfTrace? = armedOpen.getAndSet(null)

    fun armPinRebuild(reason: String) {
        armedPin.value = ArmedPin(nextId(), reason, mark())
    }

    fun beginPinReload(defaultReason: String): PinRebuild {
        val armed = armedPin.getAndSet(null)
        val rebuild = PinRebuild(
            id = armed?.id ?: nextId(),
            reason = armed?.reason ?: defaultReason,
            started = armed?.started ?: mark(),
        )
        currentPin.value = rebuild
        return rebuild
    }

    fun pinGeneration(): Long = currentPin.value?.id ?: -1

    fun notePinFeature(generation: Long, part: String, durationMs: Long) {
        val rebuild = currentPin.value ?: return
        if (rebuild.id != generation) return
        when (part) {
            "icon" -> {
                rebuild.iconFeatureMs = durationMs
                rebuild.iconReady = true
            }
            "dot" -> {
                rebuild.dotFeatureMs = durationMs
                rebuild.dotReady = true
            }
            "geometry" -> {
                rebuild.geometryFeatureMs = durationMs
                rebuild.geometryReady = true
            }
        }
    }

    fun finishPinRebuildIfReady(
        pins: Collection<*>,
        iconPins: Int,
        iconFeatures: Int,
        dotPins: Int,
        dotFeatures: Int,
    ) {
        val rebuild = currentPin.value ?: return
        if (rebuild.isLogged) return
        if (rebuild.emittedPins !== pins) return
        if (!rebuild.iconReady || !rebuild.dotReady || !rebuild.geometryReady) return
        if (iconFeatures != iconPins || dotFeatures != dotPins) return
        if (!rebuild.markLogged()) return
        val total = ms(rebuild.started)
        log(
            "pins#${rebuild.id} reason=${rebuild.reason} orders=${rebuild.ordersMs}ms " +
                "getAll=${rebuild.getAllMs}ms toPins=${rebuild.toPinsMs}ms quests=${rebuild.quests} " +
                "pins=${rebuild.pins} iconFeatures=${rebuild.iconFeatureMs}ms " +
                "dotFeatures=${rebuild.dotFeatureMs}ms geometryFeatures=${rebuild.geometryFeatureMs}ms " +
                "geoJson=handed-off firstFrame=not-measured total=${total}ms"
        )
    }
}

internal fun currentPerfTrace(): PerfTrace? = currentPerfState().trace

internal class PerfThreadState {
    var trace: PerfTrace? = null
    var visibleQuestHolder: LongArray? = null
}

internal expect fun currentPerfState(): PerfThreadState

internal class PerfTraceElement(
    private val trace: PerfTrace,
) : ThreadContextElement<PerfTrace?> {
    companion object Key : CoroutineContext.Key<PerfTraceElement>

    override val key: CoroutineContext.Key<PerfTraceElement> get() = Key

    override fun updateThreadContext(context: CoroutineContext): PerfTrace? {
        val state = currentPerfState()
        val previous = state.trace
        state.trace = trace
        return previous
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: PerfTrace?) {
        currentPerfState().trace = oldState
    }
}

internal suspend fun <T> withPerfTrace(trace: PerfTrace, block: suspend CoroutineScope.() -> T): T =
    withContext(PerfTraceElement(trace), block)

class PerfTrace(
    val event: String,
    val id: Long,
    val start: TimeMark,
) {
    private val phases = LinkedHashMap<String, Long>()
    private val fields = LinkedHashMap<String, String>()
    private val finished = atomic(false)
    private var edit: UploadEditSample? = null
    var editIndex: Int = 0
        private set

    fun addMs(name: String, ms: Long) {
        phases[name] = (phases[name] ?: 0L) + ms
        edit?.add(name, ms)
    }

    fun set(name: String, value: String) {
        fields[name] = value
    }

    fun beginEdit(): Int {
        editIndex += 1
        edit = UploadEditSample()
        return editIndex
    }

    fun logEdit(index: Int, quest: String) {
        val sample = edit ?: return
        Perf.log(
            "uploadEdit#$id index=$index quest=$quest " +
                "localBefore=${sample.localBefore}ms network=${sample.network}ms " +
                "markSynced=${sample.markSynced}ms updateAll=${sample.updateAll}ms " +
                "mapDataOnUpdated=${sample.mapDataOnUpdated}ms " +
                "rebuildLocalChanges=${sample.rebuildLocalChanges}ms localAfter=${sample.localAfter}ms"
        )
        edit = null
    }

    fun finish() {
        if (!finished.compareAndSet(false, true)) return
        val total = Perf.ms(start)
        if (event == "upload") {
            Perf.log(
                "upload#$id edits=$editIndex " +
                    "${phase("waitSerializeSync")} ${phase("localBefore")} ${phase("network")} " +
                    "${phase("localAfter")} ${phase("otherUploads")} total=${total}ms"
            )
            return
        }
        val phaseText = phases.entries.joinToString(" ") { "${it.key}=${it.value}ms" }
        val fieldText = fields.entries.joinToString(" ") { "${it.key}=${it.value}" }
        Perf.log("$event#$id $phaseText $fieldText total=${total}ms".replace("  ", " ").trim())
    }

    private fun phase(name: String): String = "$name=${phases[name] ?: 0L}ms"
}

private class UploadEditSample {
    var localBefore = 0L
    var network = 0L
    var markSynced = 0L
    var updateAll = 0L
    var mapDataOnUpdated = 0L
    var rebuildLocalChanges = 0L
    var localAfter = 0L

    fun add(name: String, ms: Long) {
        when (name) {
            "localBefore" -> localBefore += ms
            "network" -> network += ms
            "markSynced" -> markSynced += ms
            "updateAll" -> updateAll += ms
            "mapDataOnUpdated" -> mapDataOnUpdated += ms
            "rebuildLocalChanges" -> rebuildLocalChanges += ms
            "localAfter" -> localAfter += ms
        }
    }
}

class PinRebuild(
    val id: Long,
    val reason: String,
    val started: TimeMark,
) {
    private val logged = atomic(false)
    var ordersMs: Long = 0
    var getAllMs: Long = 0
    var toPinsMs: Long = 0
    var quests: Int = 0
    var pins: Int = 0

    @Volatile var emittedPins: Collection<*>? = null
    @Volatile var iconFeatureMs: Long = 0
    @Volatile var dotFeatureMs: Long = 0
    @Volatile var geometryFeatureMs: Long = 0
    @Volatile var iconReady: Boolean = false
    @Volatile var dotReady: Boolean = false
    @Volatile var geometryReady: Boolean = false

    val isLogged: Boolean get() = logged.value

    fun markLogged(): Boolean = logged.compareAndSet(false, true)

    fun publish(pins: Collection<*>) {
        emittedPins = pins
    }
}

private class ArmedPin(val id: Long, val reason: String, val started: TimeMark)
