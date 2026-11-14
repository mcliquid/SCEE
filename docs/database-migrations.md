# Database migration tracks

## Initialization contract

Database.initialize runs DatabaseConfigurator.migrate on every open, inside the
existing Database transaction. PRAGMA user_version is the file-format compatibility
gate (25), not upstream progress. The scee_schema_tracks table has exactly two rows:
upstream and fork. Each stores independently validated progress. New upstream steps
extend onUpgrade and UPSTREAM_VERSION; new fork steps append to forkMigrations.
Never reorder or reuse a fork slot. For example upstream 23 runs from upstream 22
even when fork 2 (formerly file version 24) has already completed. Fresh installation
creates the current schema and records both current tracks in the same transaction.
Failure rolls back schema/data/progress and the file-format marker together.
Unknown future progress, missing metadata in file format 25, and inconsistent legacy
structures are rejected without resetting data. Migration callbacks must not perform
network I/O or publish external observer events.

## Historical inventory

This inventory compares the checked-in historical configurator with the locally
available streetcomplete/master reference. A version here is a historical upgrade
threshold, not a claim about a published release. Entries 2-22 exist in the branch
history and upstream reference; fork additions are identified separately. Actual
installed/released builds cannot be inferred from Git commits alone.

| Legacy version | Source | Operation / precondition and dependency | Idempotence |
| --- | --- | --- | --- |
| 1 | upstream | Initial notes, map, edits, quests, statistics schema | Fresh DB only |
| 2 | upstream | Create created-elements table, from v1 | No |
| 3 | upstream | Presets/order; rebuild quest visibility with default preset, from v2 | No |
| 4 | upstream | Drop obsolete new_achievements table | Yes |
| 5 | upstream | Split old elements_geometry into way/relation geometry, then drop old table | No |
| 6 | upstream | Add note track column; old code tolerates duplicate column | Guarded/tolerant |
| 7 | upstream | Weekly statistics and active dates | No |
| 8 | upstream | Delete obsolete AddShoulder edits | Yes, destructive legacy policy |
| 9 | upstream + fork | Rename picnic-cover quest; fork also ExternalQuest to CustomQuest | Yes |
| 10 | upstream | Recreate downloaded tiles | No, cache reset |
| 11 | upstream | Recreate element edit/action format and reference table | No, deletes legacy edits |
| 12 | upstream | Log table and index | No |
| 13 | upstream | Clear geometry/map/tiles for binary format change | Yes, cache reset |
| 14 | upstream | Add near-user and changeset-position columns; duplicate-column tolerance | Guarded/tolerant |
| 15 | upstream | Rename ShopsOverlay | Yes |
| 16 | upstream | Delete obsolete AddCrossingType references | Yes |
| 17 | upstream | Rename moped-access quest | Yes |
| 18 | upstream | Drop direction_of_flow; delete old oneway quest references | Yes |
| 19 | upstream | Delete obsolete parcel-locker/shoulder quest references | Yes |
| 20 | upstream | Calendar events | No |
| 21 | upstream | Remove merged playground/pedestrian/tower quest references | Yes |
| 22 | upstream | Remove charging-capacity quest references | Yes |
| pre-23 addons | fork | External-source hidden/edit links, Osmose and quest element index; IF NOT EXISTS | Yes; old code swallowed errors |
| 23 / fork 1 | fork | Crossing quest table/index plus previous addons, commit 00451c14e5 | IF NOT EXISTS |
| 24 / fork 2 | fork | Note CREATE/COMMENT/CLOSE attempt journal, commit 4d919e5376 | Once only, previous fork structures required |
| fork 3 | fork | Persistent element rejection/review table; requires split migration tracks | Once only |
| 25 file gate | fork | Validate and translate historical progress into two tracks | Metadata absent only on verified legacy schemas |

Legacy schema 22 (including upstream-only), fork 23 and fork 24 are supported
bootstrap paths. Essential tables, edit columns, crossing columns and note journal
shape are checked before assigning track identities. Existing optional v22 fork
addons are retained; absent addons are created by fork 1. A marker/table mismatch
is not guessed into a valid state. Versions below 22 are refused with a diagnostic:
several original migrations delete edits/cache, and this package does not claim a
lossless reconstruction of those historical payload formats. They require an
explicit, separately validated upgrade path rather than running destructive legacy
steps against user data. Old migration bodies remain as the reference for future
upstream integration; they are not replayed for supported bootstrap paths.

## Historical fixtures and failure checks

LegacySchemaFixtures uses the historical onCreate sequences from 00451c14e5 and
4d919e5376, with the two schema additions guarded by their original version.
The referenced Table definitions were compared across the v22 predecessor and the
starting HEAD: only the crossing-table addition changed in those Table files.
The journal definition comes from 4d919e5376. Fixtures never call the new configurator.
Tests seed unsynced element edits, negative ID allocations, source links, notes,
uncertain note attempts and presets, then compare all pre-existing table rows.
Tests also cover repeat initialization, failure after DDL, retry, unknown/inconsistent
state, independent future upstream/fork evolution, and rollback of both progress
tracks when a later migration fails. All tests use disposable SQLite databases.

The file gate intentionally prevents older applications from opening new files.
Future upstream integration must update its own track and migration bodies; merging
upstream alone does not constitute an audited integration. This mechanism avoids
numeric collisions, not arbitrary incompatible changes to serialized edit payloads.

## Rejected element edits (fork 3)

osm_element_edit_reviews is keyed by the stable local edit ID. Rows explicitly
represent needs_review and hold the confirmed HTTP status (currently 400), a
sanitized reason of at most 512 characters and an epoch-millisecond rejection time.
Original actions and allocations are not rewritten or marked synced. Add/update,
retry deletion and ordinary edit deletion use the controller's B9 transactions;
cache and history invalidation follow commit. A2 claims are retained through rejection
persistence and released by finally. Retry and undo use the same controller lock.

Only HTTP 400 **from the changeset upload operation** is converted to the typed
ElementUploadRejectedException. CGImap parses and validates the osmChange and commits
at the end of that handler ([source](https://github.com/zerebubuth/openstreetmap-cgimap/blob/master/src/api06/changeset_upload_handler.cpp)).
Read/changeset-metadata errors and generic exception messages are not classified as
edit validation rejection. Unknown 4xx (including 406/422), authentication, conflict,
413, timeout, rate limiting, I/O, 5xx and parsing failures retain their separate error
paths. In particular, an uncertain response never creates a review row. Unexpected
local IllegalArgumentException now propagates rather than silently deleting edits.

The queue consults review state plus indexed element-reference dependencies. Rejected
roots block transitive descendants and later edits using the same element keys.
Allocated negative IDs participate in this graph. Unknown temporary target references
are skipped; a final payload guard rejects unresolved node/member references before
opening/sending an upload. Negative references to creations within the same osmChange
remain valid. A per-pass considered set prevents repeated attempts, even if explicit
retry occurs concurrently. Independent eligible edits continue, then the outer uploader
continues note/external uploads before surfacing the review summary.

The existing history shows a Needs review label and an accessible information button.
Its dialog identifies the local edit, rejection code/reason or blocking root, and
provides explicit retry via the normal UploadController and undo via the existing
lifecycle. Rejected revert actions are included in history for recovery. Retry clears
the root review atomically; dependent blocks are derived and disappear as IDs resolve.

This is not an attempt journal. A lost successful reply, process death after remote
commit, or local acknowledgement failure still has the A1 duplicate-upload exposure.
B2 trace/photo deduplication is unchanged. An actual process kill and WorkManager
scheduler execution require separate device validation.
