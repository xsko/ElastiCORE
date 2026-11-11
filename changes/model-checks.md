### Improvements (v3 JPA - what cannot compile or cannot be created is said when the code is generated)

#### An exposed composite key that holds a relation stops the build
- Why: `order: Ledger @id @ManyToOne` beside `itemSeq: int @id` in an entity with `@expose` wrote a controller that
  takes each key field as a path variable - `@PathVariable("order") Ledger order` - which does not compile (the type is
  not imported, and a path cannot carry an entity). It did so in every v3 bundle, in Kotlin and in the 2.x-compatible
  output; the guides showed the form as a reserved-word example.
- What: a v3 or Kotlin run over JPA stops (`ModelCheck`, exit 1) before writing such an entity:
  `[elcore3] Probe.LedgerLine: its composite key holds the relation order: Ledger, and an exposed entity's controller
  takes each key field in the path, which a relation cannot be - key it by the related key's value (ledgerId: long
  @id), or do not expose it`. An entity that is not exposed, and a composite key of values, are generated as before.
  The 2.x-compatible output is unchanged.

#### A table or column named with an SQL reserved word is said on JPA, as on MyBatis
- Why: an entity `Order` is the table `order`, and `group: string(10)` the column `group`. Hibernate quotes nothing by
  default, so a database that reserves the word refuses to create them. MyBatis domains were warned
  (`MyBatisSchemaRules.warnReserved`); JPA domains were not.
- What: `EntityChecks.reservedNames` reads the table as `JpaEntityAnnotationRules` writes it (`@table`, or the prefix
  and the snake-cased name) and each column of a value or an enum (`@column`, or the snake-cased name) against the
  same word list, and a v3 or Kotlin JPA run warns once per entity:
  `[Probe] Order: the table order, the column group are reserved by Oracle, PostgreSQL or SQL Server and cannot be
  created there - name it otherwise with @table / @column.` A plain `@abstract` (no table) is checked for its columns
  only; an `@embeddable`, a relation, a collection, Money, a formula and a transient field are not columns of the
  table and are not checked. The generated code does not change.

#### A type the DSL does not have says what to write instead
- Why: `code: uuid` is written into the class as `private uuid code;`, which does not compile; the run warned
  `unresolved type 'uuid'` without saying what the DSL has for it.
- What: the warning (from the loader and from the run) ends with what to write for the words the guides list:
  `- write string(36), or string @id @genid for a generated key instead` for `uuid`; `int` for `short` and `byte`;
  `string(1)` for `char`; `timestamp` for `OffsetDateTime` and `ZonedDateTime` (`BaseFieldType.insteadOf`).

- Checked: `EntityChecksTest` - the relation in a composite key is said with the sentence above, a composite key of
  values or a single key is not; the table and the columns reserved (`@table(app.user)`, `@column(comment)`, an enum
  column `level`) are said, in the plural and the singular; a relation, a collection, a formula, an unknown type,
  Money and an `@embeddable` are not; each type word maps to what the guides say. `ModelChecksRunTest` runs the
  generator: an exposed `LedgerLine` keyed by `order: Ledger @id` exits 1 with the sentence; unexposed (hexagonal) or
  keyed by `ledgerId: long @id`, the run exits 0; `Order` with `group` and `code: uuid` is generated, with the
  reserved names said once and the `uuid` warning ending in what to write.
- Files: `EntityChecks.java` (new), `RulesGenRunner.java`, `BaseFieldType.java`, `ModelExtractor.java`,
  `YamlFileMainModelLoader.java`, `EntityChecksTest.java`, `ModelChecksRunTest.java`, `elasticore-dsl-guide.md` 10.1,
  10.3 and 10.5, `elasticore-annotation-guide.md` 8.2, 8.3 and 8.7 (en, ko)
