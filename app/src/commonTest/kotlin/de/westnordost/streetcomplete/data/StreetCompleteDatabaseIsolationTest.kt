package de.westnordost.streetcomplete.data

import kotlin.test.Test
import kotlin.test.assertEquals

class StreetCompleteDatabaseIsolationTest {
    private class Fixture : StreetCompleteDatabaseTestCase() {
        lateinit var db: Database
        override fun onDatabaseInitialized(database: Database) { db = database }
    }

    @Test fun `two live fixtures never delete or share each others database`() {
        val first = Fixture()
        val second = Fixture()
        first.setUp()
        try {
            first.db.exec("CREATE TABLE isolation_probe(value TEXT)")
            first.db.exec("INSERT INTO isolation_probe VALUES ('first')")
            second.setUp()
            try {
                second.db.exec("CREATE TABLE isolation_probe(value TEXT)")
                second.db.exec("INSERT INTO isolation_probe VALUES ('second')")
                assertEquals(listOf("first"), first.db.rawQuery("SELECT value FROM isolation_probe") { it.getString("value") })
                assertEquals(listOf("second"), second.db.rawQuery("SELECT value FROM isolation_probe") { it.getString("value") })
            } finally { second.tearDown() }
            assertEquals(listOf("first"), first.db.rawQuery("SELECT value FROM isolation_probe") { it.getString("value") })
        } finally { first.tearDown() }
    }
}
