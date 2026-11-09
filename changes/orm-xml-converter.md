### Bug Fixes (project DDL - `mapping: xml`)

#### Under `mapping: xml`, a `money` or `quantity` column is a decimal or an integer again, not text
- Why: `META-INF/orm.xml` names a nested converter by its binary name, as Hibernate loads it:
  `io.elasticore.springboot3.domain.Money$JpaConverter`. The schema reader knew the runtime's two converters as
  `Money.JpaConverter` and `Quantity.JpaConverter`, and the classes it read from source by their dotted names, so the
  `$` form matched neither. Each such column was written as text, with the warning `the converter ... was not read -
  the column is text`, and `ddl-auto=validate` refused the schema (#116). That is the limit the 3.0.261106-rc11 entry
  of #113 states; with the mapping on the classes, the default, nothing was wrong.
- What: `JpaSchemaReader.converterColumn` reads a `$` in the converter's name as `.` before it looks the converter up.
  The runtime's `Money` and `Quantity` converters, and a project's own converter nested in a class (`Outer$Inner`),
  are then found as they are with the annotations. `StringArrayConverter` and `IntegerArrayConverter` are classes of
  their own, named without a `$`, and were not affected. An enum's converter was not affected either, since
  `mapping: xml` moves it out to a class of its own.
- Checked: `JpaSchemaReaderTest.anOrmXmlNamesANestedConverterByItsBinaryName` reads an orm.xml with
  `Money$JpaConverter`, `Quantity$JpaConverter`, a converter nested in an enum and an enum's converter moved out. It
  expects `DECIMAL(14,2)`, `INTEGER`, `INTEGER` and a text column of length 10, with no warning. On `main` the first
  three fail as text, with three warnings.
  - The pet store, generated once with `mapping: xml` in its `petstore` env.yml:
    - `SchemaValidateTest` passes.
    - Its four `schema-*.sql` hold the same tables, columns and constraints as the committed ones, which the
      annotations give; only their order differs.
    - Without the change, the same run warns eight times, and `SchemaValidateTest` fails with `wrong column type
      encountered in column [unit_cost] in table [ps_item]; found [character varying (Types#VARCHAR)], but expecting
      [numeric(10,2) (Types#NUMERIC)]`.
  - The sample was put back. Regenerated as it is, its schemas are unchanged.
- Files: `JpaSchemaReader.java`, `JpaSchemaReaderTest.java`
