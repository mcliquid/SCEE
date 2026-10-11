package de.westnordost.streetcomplete.screens.settings

import de.westnordost.streetcomplete.data.StreetCompleteDatabaseConfigurator
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseImportExportVersionTest {

    @Test fun `database import export supports current schema version`() {
        assertEquals(
            StreetCompleteDatabaseConfigurator.version.toLong(),
            LAST_KNOWN_DB_VERSION,
        )
    }
}
