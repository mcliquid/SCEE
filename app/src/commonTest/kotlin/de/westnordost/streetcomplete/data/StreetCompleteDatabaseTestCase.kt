package de.westnordost.streetcomplete.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.uuid.Uuid

abstract class StreetCompleteDatabaseTestCase {
    private lateinit var database: Database
    private lateinit var connection: SQLiteConnection
    private val databaseName = "streetcomplete_test_${Uuid.random()}.db"

    @BeforeTest fun setUp() {
        connection = BundledSQLiteDriver().open(databaseName)
        database = DatabaseImpl(connection)
        database.initialize(StreetCompleteDatabaseConfigurator)
        onDatabaseInitialized(database)
    }

    abstract fun onDatabaseInitialized(database: Database)

    @AfterTest fun tearDown() {
        connection.close()
        SystemFileSystem.delete(Path(databaseName), mustExist = false)
    }
}
