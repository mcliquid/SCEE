package de.westnordost.streetcomplete.util

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.logs.*
import kotlinx.coroutines.*
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util.TimeZone
import kotlin.test.*

class JvmActualsTest {
    @Test fun `perf thread state is stable and isolated across threads`() {
        val local = currentPerfState()
        val previous = local.visibleQuestHolder
        try {
            local.visibleQuestHolder = longArrayOf(7)
            var other: PerfThreadState? = null
            val thread = Thread {
                other = currentPerfState()
                assertSame(other, currentPerfState())
                other!!.visibleQuestHolder = longArrayOf(9)
            }
            thread.start()
            thread.join()
            assertNotSame(local, other)
            assertContentEquals(longArrayOf(7), local.visibleQuestHolder)
            assertContentEquals(longArrayOf(9), other!!.visibleQuestHolder)
        } finally { local.visibleQuestHolder = previous }
    }

    @Test fun `perf nested coroutine traces restore after exceptions and dispatcher hops`() = runBlocking {
        val previous = currentPerfTrace()
        val outer = PerfTrace("test", 1, Perf.mark())
        val inner = PerfTrace("test", 2, Perf.mark())
        withPerfTrace(outer) {
            assertSame(outer, currentPerfTrace())
            assertFailsWith<IllegalStateException> {
                withPerfTrace(inner) {
                    withContext(Dispatchers.Default) { assertSame(inner, currentPerfTrace()) }
                    error("expected")
                }
            }
            assertSame(outer, currentPerfTrace())
        }
        assertSame(previous, currentPerfTrace())
    }

    @Test fun `civil daylight standard times repeat in different timezones`() {
        for ((zone, pos) in listOf("Europe/Berlin" to LatLon(52.5, 13.4), "America/New_York" to LatLon(40.7, -74.0))) {
            val midday = java.time.LocalDateTime.parse("2026-06-21T12:00:00").atZone(java.time.ZoneId.of(zone))
            repeat(3) {
                assertTrue(isDayAt(pos, midday))
                assertFalse(isDayAt(pos, midday.withHour(0)))
            }
        }
    }

    @Test fun `polar missing solar times retain existing explicit parse failure`() {
        assertFailsWith<DateTimeParseException> {
            isDayAt(LatLon(89.0, 0.0), ZonedDateTime.parse("2026-06-21T12:00:00Z"))
        }
    }

    @Test fun `sunset after midnight uses the following date as on Android`() {
        val now = ZonedDateTime.parse("2026-06-21T23:00:00Z")
        assertTrue(isDayAt(LatLon(0.0, 170.0), now))
        assertFalse(isDayAt(LatLon(0.0, 170.0), now.withHour(12)))
    }
}
