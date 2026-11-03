### New Features (project DDL - column comments)

#### `ddl { comments = true }` says each column's label to the database
- Why: a field's label (`-- label`) becomes Hibernate's `@Comment` on the entity, and Hibernate's own
  schema export writes it, but the project DDL wrote no comment: a database made from it, or from its
  migrations, had none.
- What: an opt-in `ddl { comments = true }` (the plugin's `DdlSpec.getComments`, passed as
  `elasticore.ddl.comments`; `DdlConfig.comments`), which a domain overrides with `ddl.comments` in its
  env.yml. With it on, a JPA domain's column takes its field's `@Comment` (a relation's goes on its
  join column) and a MyBatis domain's (`MyBatisTables`) its field's label. The schema and a new table
  say it after `CREATE TABLE`: `COMMENT ON COLUMN` on H2, PostgreSQL and Oracle, and on SQL Server the
  `MS_Description` property with `N'...'`. A quote in it is doubled.
- On SQL Server, whether the property is there is the database's to say, not the state's - comments
  turned off and on again, a database Hibernate made, a baseline can each hold one the state does not:
  `IF EXISTS (... sys.extended_properties ...)` updates it, or else adds it, and a drop runs only where
  it is there. The schema is the table's, or the login's default (`SCHEMA_NAME()` through a variable)
  where the table names none.
- A comment added, changed or removed is a change that runs as written, outside `sameStorage`; none
  is `IS NULL`, Oracle's `IS ''` and `sp_dropextendedproperty`. A new column's comment comes in the
  same change. A Kotlin entity's escaped `\$` is a dollar.
- The state keeps a comment only when they are on (`"comment"` of a column), so a state written
  without them reads as it did; off, nothing is compared, written or kept, and the schema is the bytes
  it was; turning them off leaves the comments already in the database. A label longer than Oracle
  keeps (4000 bytes in UTF-8) or SQL Server (7500 bytes, two a character) stops the run, and nothing is
  written for its database group. Columns only: an entity's label is written nowhere yet.
- Checked: `ColumnCommentTest` (each database's words and a quote, a table in a schema, a new table
  and column, added, changed and removed, off is byte-identical and compares nothing, the state with
  and without, bytes counted per database, the project's setting and a domain's), `DdlMigrationTest`
  (JPA and MyBatis: the schema, the first migration and the state; a label changed is
  `comment of note.title` and no review; off says nothing unless the domain asks; a label over
  Oracle's limit stops the run; a Kotlin entity's dollar), `ElastiCorePluginTest.theDdlCommentsArePassedWhenOn`,
  and the template-mybatis `MigrationRun.comments` - a table's comments, their change, then comments
  off and on again and a drop of one the database does not hold, read back from `information_schema`
  (H2, every build), `pg_description`, `user_col_comments` and `sys.extended_properties` (`dbTest`).
- Files: `Column.java`, `Table.java`, `Schema.java`, `Database.java`, `SchemaWriter.java`,
  `SchemaDiff.java`, `DdlConfig.java`, `DdlRun.java`, `JpaSchemaReader.java`, `MyBatisTables.java`,
  `DdlSpec.java`, `ElastiCorePlugin.java`, the tests above, `elasticore-env-guide.md` 9-1 (en, ko)
