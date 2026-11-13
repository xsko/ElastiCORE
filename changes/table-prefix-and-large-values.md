### Bug Fixes (3.0 code - table names and large values)

#### The 3.0 code puts table.prefix before an `@table` name, as 2.x does
- Why: with `table.prefix: el_` and `@table(note)`, 2.x generated `@Table(name="el_note")`, and the 3.0 code
  `@Table(name = "note")`. It generated and compiled without a warning and failed at run time
  (`relation "note" does not exist`), for every entity with `@table` in a project moving from 2.x. 2.x put the prefix
  before an `@table` name that already had it too (`@table(el_def)` is `el_el_def`).
- Before: the 3.0 JPA entity, the MyBatis SQL, the reserved-name check and the Ossie `source` used the `@table` name
  alone. After: all of them name the table `table.prefix` + the `@table` name (`EntitySpec.tableName()`), as 2.x
  does. An `@table` that starts with the prefix is warned about with the name it maps to and the line that names the
  table without the doubled prefix. The v3 samples that wrote `@table(shop_products)` beside `prefix: shop_` now write
  `@table(products)`: their tables keep their names.
- 3.0 is not released: a project that rewrote its `@table` names with the prefix for the earlier release candidates
  gets the warning and writes them back.

#### `clob` and `blob` keep the value in the row
- Why: the 3.0 code mapped `clob` to `String` + `@Lob`. On PostgreSQL, Hibernate keeps such a value apart as a large
  object and the column holds only its number (oid): SQL cannot read it, and Hibernate 7's `ddl-auto: validate`
  refuses a `text` column (`found [text], but expecting [oid (Types#CLOB)]`). `blob` the same.
- After: `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)` / `LONG32VARBINARY` - text / bytea on PostgreSQL, clob / blob on
  Oracle, varchar(max) / varbinary(max) on SQL Server. The 2.x-compatible output keeps `@Lob`.
- SQL Server: Hibernate maps a long text there to varchar(max) and refused the NVARCHAR(MAX) the DDL wrote, so a JPA
  long text (`clob`, a `@Lob` String) is now `VARCHAR(MAX)` in the generated DDL - with a UTF-8 collation (SQL Server
  2019+) it keeps any language, as Oracle keeps it in a CLOB of a UTF-8 database (an NCLOB was considered and left:
  not what Korean databases use). MyBatis's clob stays NVARCHAR(MAX). The pet store's SQL Server test database is
  UTF-8. The build and `elcoreMigrationCheck` warn about a `String` with `@Lob` in the project's code (one
  added by hand to a 2.x file), which does the same on PostgreSQL.
- Checked: `TablePrefixAndLargeValueTest` (Java, Kotlin, MyBatis), `StringLobTest`, `OssieExportTest`; the pet store's
  `Category` has a `clob` and a `blob`, and `SchemaValidateOnDatabase.aLargeValueIsKeptInTheRow` writes them through
  Hibernate and reads them back with SQL on Oracle, PostgreSQL and SQL Server (CI).
- Files: `EntitySpec`, `JpaEntityAnnotationRules`, `JpaEntityBaseRules`, `EntityChecks`, `MyBatisSql`,
  `OssieSemanticModelExporter`, `RulesGenRunner`; the DSL, annotation, rule and migration guides (en, ko).
