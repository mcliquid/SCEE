package de.westnordost.streetcomplete.data

/** Independently namespaced progress; user_version is only the database file-format gate. */
internal object SchemaTracks {
    const val TABLE = "scee_schema_tracks"
    const val CREATE = "CREATE TABLE scee_schema_tracks (track TEXT PRIMARY KEY NOT NULL, version INTEGER NOT NULL CHECK(version >= 0))"

    fun create(db: Database, upstream: Int, fork: Int) {
        db.exec(CREATE)
        db.insert(TABLE, listOf("track" to "upstream", "version" to upstream))
        db.insert(TABLE, listOf("track" to "fork", "version" to fork))
    }

    fun read(db: Database): Map<String, Int> {
        val rows = db.query(TABLE) { it.getString("track") to it.getInt("version") }
        check(rows.map { it.first }.toSet() == setOf("upstream", "fork") && rows.size == 2) {
            "Unknown or incomplete database migration tracks"
        }
        return rows.toMap()
    }

    /** Caller owns the transaction: DDL, data and progress roll back together on any failure. */
    fun migrate(db: Database, upstreamVersion: Int, upstreamUpgrade: (Database, Int) -> Unit,
        forkMigrations: List<(Database) -> Unit>) {
        val versions = read(db)
        val upstream = versions.getValue("upstream")
        val fork = versions.getValue("fork")
        check(upstream in 22..upstreamVersion && fork in 0..forkMigrations.size) {
            "Unsupported migration tracks: upstream=$upstream, fork=$fork"
        }
        if (upstream < upstreamVersion) {
            upstreamUpgrade(db, upstream)
            db.update(TABLE, listOf("version" to upstreamVersion), "track = 'upstream'")
        }
        for (index in fork until forkMigrations.size) {
            forkMigrations[index](db)
            db.update(TABLE, listOf("version" to index + 1), "track = 'fork'")
        }
    }
}
