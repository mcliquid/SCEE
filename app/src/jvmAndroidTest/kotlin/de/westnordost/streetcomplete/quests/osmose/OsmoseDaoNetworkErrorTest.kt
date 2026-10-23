package de.westnordost.streetcomplete.quests.osmose

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.westnordost.streetcomplete.data.DatabaseImpl
import de.westnordost.streetcomplete.quests.osmose.OsmoseTable.NAME
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.nio.channels.UnresolvedAddressException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OsmoseDaoNetworkErrorTest {
    private val db = DatabaseImpl(BundledSQLiteDriver().open(":memory:")).apply {
        exec("CREATE TABLE $NAME (uuid text)")
        exec("INSERT INTO $NAME VALUES ('id')")
    }

    private fun dao(handler: MockRequestHandler) = OsmoseDao(db, inMemoryPrefs(), { HttpClient(MockEngine(handler)) })

    private fun assertIssueCount(expected: Int) = assertEquals(expected, db.query(NAME) { }.size)

    @Test fun `reportChange survives unresolved host and keeps issue`() = runBlocking {
        dao { throw UnresolvedAddressException() }.reportChange("id", false)
        assertIssueCount(1)
    }

    @Test fun `reportChange keeps issue on server error`() = runBlocking {
        dao { respond("", HttpStatusCode.InternalServerError) }.reportChange("id", false)
        assertIssueCount(1)
    }

    @Test fun `reportChange deletes issue on success`() = runBlocking {
        dao { respondOk() }.reportChange("id", false)
        assertIssueCount(0)
    }

    @Test fun `reportChange deletes issue unknown to Osmose`() = runBlocking {
        dao { respond("", HttpStatusCode.Gone) }.reportChange("id", false)
        assertIssueCount(0)
    }

    @Test fun `doesIssueStillExist assumes it exists on unresolved host`() = runBlocking {
        assertTrue(dao { throw UnresolvedAddressException() }.doesIssueStillExist("id"))
    }

    @Test fun `cancellation is passed through`() = runBlocking<Unit> {
        val dao = dao { throw CancellationException() }
        assertFailsWith<CancellationException> { dao.reportChange("id", false) }
        assertFailsWith<CancellationException> { dao.doesIssueStillExist("id") }
    }
}
