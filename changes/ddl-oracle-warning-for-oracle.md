### Bug Fixes (project DDL - a warning about Oracle only for Oracle)

#### `columnDefinition "TEXT"` is said to have no Oracle type only when a schema is written for Oracle
- Why: reading a JPA entity for the project DDL, a column with `columnDefinition = "TEXT"` (what the
  DSL's `text` writes) warned `Oracle has no TEXT - its schema makes the column a CLOB, which
  ddl-auto=validate refuses there`, once per column, whatever `ddl.databases` named. A project writing
  its schema for PostgreSQL only got one line for every `text` field (37 on a 9-domain project) about a
  database it does not use.
- What: `DdlRun` tells `JpaSchemaReader` whether any group's databases include Oracle; the warning is said
  only then. A reader nobody told keeps warning, as before. The env guide's limits (9-1) say when, in
  English and Korean.
- Checked: `DdlMigrationTest.whatOnlyOracleRefusesIsSaidOnlyForASchemaForOracle` - a JPA entity with a
  `text` field: no warning with `h2,postgresql`, the warning with `h2,oracle` (fails without the change).
- Files: `JpaSchemaReader.java` (`forOracle`), `DdlRun.java`, `DdlMigrationTest.java`,
  `elasticore-env-guide.md` (en, ko)
