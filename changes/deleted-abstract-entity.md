### New Features (v3 entity - `@deleted` in an `@abstract(entity)` hierarchy)

#### `@deleted` on the root of an `@abstract(entity)` hierarchy keeps the rows of every subclass
- Why: `@deleted` (#112) was written before `@abstract(entity)` (#104) and read every `@abstract` as a mapped
  superclass, so #104 refused the two together. A hierarchy read through one repository is where a delete that keeps
  the row is wanted most - a party who left, a tag taken out of use - and the refusal said "not supported yet" (#117).
- What: the field goes on the root, or on a plain `@abstract` frame the root extends; every subclass inherits it.
  Hibernate deletes a row from each table a class maps to, each with that class's own `@SQLDelete`, so:
  - `JOINED`: the column is in the root's table. The root's `@SQLDelete` writes the time there, by key and version.
    Each subclass's leaves its own row as it is, by a statement that changes nothing and finds the one row Hibernate
    counts on: `UPDATE ref_party SET left_at = left_at WHERE party_id = ?`. It works in whichever order the two run.
    A root of another domain is named by that domain's `table.prefix` and `tableSchema`.
  - `TABLE_PER_CLASS`: the root has no table and no `@SQLDelete`. Each subclass's table has the column, and its
    `@SQLDelete` writes the time there.
  - The root's repository, the one that reads the whole hierarchy, redeclares `findById`, `existsById`, `findAll`
    (plain, sorted, paged) and `count` with `<field> IS NULL`, as a plain entity's does. The mapper's `toSpec` adds the
    condition to the search. A collection of the root's type gets `@SQLRestriction`, and `removedWithIt` sees a
    relation to the root as kept.
  - The inherited key is read from the inherited fields (`SoftDelete.key`): `pkField()` sees only an entity's own
    fields, so a subclass got "needs a key of one field" before.
- Refused, with the reason:
  - `@deleted` on a subclass only (`... is not supported - a read through Code would find kept and removed rows
    alike; declare it on Code.`).
  - A collection of a JOINED subclass (`List<Person>`): Hibernate tests a collection's restriction on the element's
    own table, which has no column - tried on the pet store, it fails with `Column "P1_0.LEFT_AT" not found`. The
    message says to hold `List<Party>`.
  - A subclass file of the developer's (hexagonal and DDD split it) that has no `@SQLDelete` when the generator cannot
    merge it in - no base in `.elasticore/cache`, or no ElastiCORE header: a delete would remove the row, for a JOINED
    subclass its own table's row, leaving the root's kept and unreadable. Before, the first case was a note and the
    second said nothing. With a base, the line is merged in beside the developer's code, as any change is.
  - What holds for any entity holds here: a key the request gives needs a version, a composite key and `mapping: xml`
    on Spring Boot 3 stop the build. MyBatis refuses an `@abstract(entity)` already.
- Opt-in: nothing changes for a model without `@deleted` in such a hierarchy; before, the build stopped. Two changes
  reach models with `@deleted` outside one, and only there: a `@deleted` parent of another domain (`@extend`) is now
  found for the DTO's search condition and a collection's restriction, as the entity's own `@SQLDelete` already found
  it; and the Kotlin service test of a `@deleted` entity matches the key with `(any(Long::class.javaObjectType) ?: 1L)`,
  as the DDD port test does - the redeclared `findById(id: Long)` takes no null, and `any()` gave one ("any(...) must
  not be null").
- Limit: the JOINED subclass's statement needs the database to count the rows it matched, not only those it changed -
  so does Hibernate's check of every update. MySQL and MariaDB with `useAffectedRows=true` count changed rows only.
- Checked: `SoftDeleteRulesTest` and `SoftDeleteHierarchyTest` (the whole run, Java and Kotlin: JOINED with a version,
  TABLE_PER_CLASS from a frame above the root, a collection of the root's type, a field on a subclass only, a
  collection of a JOINED subclass, an owned subclass with and without a merge base), `AbstractEntityTest` (every output,
  a frame above the root, a JOINED root of another domain with its own prefix). The pet store's `refdata` has `Party`
  (JOINED, held by `CaseNote.parties`) and `Tag` (TABLE_PER_CLASS, `@deleted` from the frame `Retirable`, held by
  `CaseNote.tags`): `SoftDeleteHierarchyTest` deletes a person and a tag over HTTP on H2, reads them through the root's
  API, its repository and the note's collections (gone), and finds the rows with the time in the tables;
  `SchemaValidateOnDatabase` runs the same on Oracle, PostgreSQL and SQL Server in CI. `elasticore-template-kt`
  (Kotlin, Spring Boot 3.3, Hibernate 6) does the same through the repositories (`AbstractEntityTest`).
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ.
- Files: `SoftDelete`, `JpaEntityAnnotationRules`, `JpaEntityBaseRules` (the collection), `ServiceTestBaseRules`,
  `RulesGenRunner` (`failIfSoftDeleteMissing`), `EntitySpec` (`abstractEntityRoot`, `knownKeptBelowJoined`),
  `SpecFactory` (`teachAbstractEntityRoot`, `modelOf`; `teachSoftDeletes` takes an `@abstract(entity)` root;
  `deletedFieldOf` follows a parent of another domain), the annotation guide (en, ko), the pet store's guide page and
  its and the Kotlin template's `refdata`.

### Bug Fixes (project DDL - a sequence Hibernate names)

#### The project DDL names an unnamed sequence after the root's table, as Hibernate does
- Why: for `@GeneratedValue(strategy = SEQUENCE)` without a generator, the DDL wrote `{entity}_seq`, but Hibernate 6 and
  7 name it `{table}_seq` (`implicit_name_base` is the root's table name, read from `IdentifierGeneratorUtil` 6.6 and
  `GeneratorParameters` 7.2). With a table prefix or `@table` the two differ, and `ddl-auto=validate` failed:
  `missing sequence [ref_tag_seq]` for the pet store's `Tag`, whose DDL said `tag_seq`. The first
  `@sequence(SEQUENCE)` in a sample showed it.
- What: `JpaSchemaReader.sequences` uses the root's own table name. A schema stays out of the sequence's name, as
  before. A table named like its entity gets the same name as before. Any other got a sequence Hibernate did not use,
  and now gets the one it does. A database made from the old DDL has the old sequence and not the new one: create
  `{table}_seq` starting past the table's largest key (Hibernate could not have used the old one, so validate failed there).
- Checked: `JpaSchemaReaderTest.anUnnamedSequenceIsTheTablesName` (a prefix, an `@table` name, a schema); the pet store
  and the Kotlin template validate their schema with the sequence (`ref_tag_seq`, `ref_marker_seq`).
- Files: `JpaSchemaReader`.

### Bug Fixes (Ossie export - a JOINED subclass)

#### A JOINED subclass's dataset holds the columns of its own table
- Why: the dataset of a subclass listed every inherited field, as if its table had them. Under JOINED it has its own
  and the key; the others are in the tables above it. The pet store's `SemanticModelTest` checks every column against
  the table: `ref_person has no column name` (and `version`, `left_at`).
- What: `OssieSemanticModelExporter.inItsTable` keeps, under a hierarchy whose topmost entity says `JOINED`, the key
  and the fields of the entity and of the plain `@abstract` frames between it and the entity it extends. The root of
  an `@abstract(entity)` has no dataset, as before.
- Checked: the pet store's `SemanticModelTest` on `Person` and `Company`.
- Files: `OssieSemanticModelExporter`.
