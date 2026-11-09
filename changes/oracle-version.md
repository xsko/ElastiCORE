### New Features (DDL - the Oracle version the tables are for)

#### `ddl { oracleVersion = 23 }`: a boolean is a `BOOLEAN` column on Oracle 23, as Hibernate maps it there
- Why: the project DDL wrote a boolean as `NUMBER(1)` on Oracle, and Hibernate's `OracleDialect` maps it to `BOOLEAN`
  from version 23, so `ddl-auto=validate` failed there on any entity with a boolean field: `wrong column type
  encountered in column [terminal] in table [ref_status_code]; found [number (Types#NUMERIC)], but expecting [boolean
  (Types#BOOLEAN)]` (#114). `BOOLEAN` is no column type below 23, so the DDL cannot simply write it.
- What: a setting in `elasticore { ddl { } }`, beside `databases`, one value for the project (passed as
  `elasticore.ddl.oracleVersion`). From 23, Oracle's DDL writes a boolean as `BOOLEAN` and says
  `for Oracle 23 or later` in its header; below 23, and when it is not set - the default - it writes `NUMBER(1)`, as
  before, so nothing changes for a project that does not say it. A version below 12, or anything but a number, stops
  the run. Only the boolean changes, and only on Oracle; every other type is the same for 23 as before.
- Migrations: the state (`schema-state.json`) records the version, only when it is set, so a state without it reads
  as before. Setting it over Oracle tables already there is a migration of Oracle's alone; the other databases get
  none. Oracle changes a column's type only while the column is empty (`ORA-01439: column to be modified must be empty
  to change datatype`, tried on Oracle 23 Free with rows), so an `ALTER ... MODIFY` from `NUMBER(1)` to `BOOLEAN` would
  fail on existing data. Instead each boolean column is added again with the new type, its values copied, the old
  column dropped and the new one renamed to it; its `NOT NULL`, the indexes it is in and its comment are made again.
  The values copy as they are: 1 is `TRUE`, 0 `FALSE`, an empty one stays empty. It runs on the rows already there, so
  it waits for review (commented out, `REVIEW .../oracle/V...` in the log). Unsetting it again is the same change
  back to `NUMBER(1)`.
- MyBatis: `template-mybatis` and `template-ddd-mybatis` bind a boolean as `jdbcType=BOOLEAN`. Their Oracle tests
  (`ShopApiOnOracleTest` 10, `DomainOnOracleTest` 14) pass on Oracle 23 Free with ojdbc11 23.7 against `NUMBER(1)`
  columns, as now, and against `BOOLEAN` ones, with the setting on in a local run; nothing to fix. The templates keep
  the default.
- Checked: `OracleVersionTest` (both answers, every other type unchanged, the setting's values, the state, the
  migration both ways, a copy name already taken, an index the model drops); `DdlMigrationTest` (a project turning it
  on: the Oracle migration for review, none for H2, the state, nothing on the next build); `ElastiCorePluginTest`
  (passed when said). The pet store sets `oracleVersion = 23` and its `StatusCode` has `terminal: boolean`:
  `SchemaValidateOnDatabase` passes on Oracle 23 Free, where without the setting it fails with the error above, and
  `AbstractEntityCheck` reads the boolean through Hibernate and binds it in a query on every database.
  `MigrationRun.oracleBooleans` (run by the job on Oracle) makes the tables below 23 with rows, runs the change as
  written and finds `BOOLEAN` columns with their values, nullability and index.
- Files: `Database.java`, `DdlConfig.java`, `Schema.java`, `SchemaWriter.java`, `SchemaDiff.java`, `DdlRun.java`,
  `DdlSpec.java`, `ElastiCorePlugin.java`, `OracleVersionTest.java`, `SchemaDiffTest.java`, `DdlMigrationTest.java`,
  `ElastiCorePluginTest.java`, the pet store's `build.gradle`, `code.yml`, schemas, `semantic_model.yml` and
  `AbstractEntityCheck.java`, `template-mybatis`'s `MigrationRun.java` and `MigrationTest.java`,
  `elasticore-env-guide.md` 9-1 and the `@abstract(entity)` limits of `elasticore-annotation-guide.md` (en, ko),
  `abstract-entity.md`.
