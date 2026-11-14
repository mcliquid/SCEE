package de.westnordost.streetcomplete.data

/** Creates the database tables and upgrades them to the current version */
interface DatabaseConfigurator {
    /** Current version of the database */
    val version: Int

    /** Create [db] at current [version] */
    fun onCreate(db: Database)

    /** Update [db] from [oldVersion] to [version] */
    fun onUpgrade(db: Database, oldVersion: Int)
    /** Run inside initialize's transaction, including when the file-format version is unchanged. */
    fun migrate(db: Database, oldVersion: Int) {
        if (oldVersion == 0) onCreate(db)
        else if (oldVersion < version) onUpgrade(db, oldVersion)
    }
}
