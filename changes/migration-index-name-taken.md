### Bug Fixes (v3 - migrations)

#### A migration that gives an index name to another table drops the old index first (#329)
- Why: when a table's name changed (a `table.prefix` added, `@table` changed, the entity renamed), the migration made
  the new table's `@Index(name=...)` under its name while the old table - kept on purpose, its `DROP TABLE` waiting
  for review - still held an index of that name. PostgreSQL, Oracle and H2 hold an index name once in a schema, so
  the `CREATE INDEX` failed, Flyway stopped and the application did not start; nothing was said at generation. The
  same happened when an index name moved from one table to another, made before the old one was dropped.
- Now: an index name the model gives another table than the one holding it in the state is dropped from the old table
  first, as written (`drop index uk_coverage_product_code of coverage - jp_coverage takes its name`) - it drops an index,
  not rows; the old table's `DROP TABLE` still waits for review. An index moved between kept tables is dropped once.
  Foreign keys were not affected (their generated names carry the table's name).
- Checked: `SchemaDiffTest` (`anIndexNameAnotherTableTakesIsDroppedFirst` - the order in the PostgreSQL migration and
  the SQL Server form; `anIndexMovedBetweenTablesIsDroppedOnce`; `aNameStillHeldIsNotDropped`).
- Files: `SchemaDiff.between`.

### Documentation

#### `@table` says that `table.prefix` goes before its name too
- The annotation guide (en, ko) now says next to `@table` what the env guide said of the prefix: it goes before a name
  written with `@table` as well, as in 2.x (`@table(PL_QUOT)` with `prefix: jp_` is `jp_pl_quot`); a project using
  names exactly as written (a data dictionary) leaves `table.prefix` out. Asked in #326 and #328.
