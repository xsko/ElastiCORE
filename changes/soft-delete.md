### New Features (v3 - `@deleted`)

#### `deletedAt: timestamp @deleted` - a delete keeps the row, with when, and no read finds it again
- Why: soft delete was hand code - a class `@jpa:SQLRestriction` filtered reads, but the delete still removed
  the row, nothing kept a request from writing the column, MyBatis dropped the restriction, and the rows that
  named a deleted one could not be read any more. A row other rows still name (a product orders were placed
  for) could not be deleted at all.
- After: one field line. JPA: the entity gets Hibernate's `@SQLDelete` (`UPDATE ... SET deleted_at =
  CURRENT_TIMESTAMP WHERE key = ? [AND version = ?] AND deleted_at IS NULL`) - the delete is still JPA's
  remove, so `@PreRemove`, the lifecycle's delete guard and the invariants run as before; on a split root it
  is on the developer's class and kept in line like `@Table`. The repository redeclares `findById`,
  `existsById`, `findAll()`, `findAll(Sort)`, `findAll(Pageable)` and `count()` with a `@Query`, the mapper's
  `toSpec` ends with `isNull(deletedAt)`, and a collection of such rows carries `@SQLRestriction` - a member
  taken out of its aggregate is kept and does not come back. No class-level `@SQLRestriction`: it would also
  hide the row from every row that names it (an item would answer 404 once its product was deleted). MyBatis:
  `deleteById` and `deleteAllBy...` are UPDATEs; `findById`, `findStoredById`, `countById`, `findAll`, the
  search, `findAllByLogin` and `findAllBy...In` read the live rows; `findAnyById` serves the Lookup, so a
  reference check still finds a deleted row and a row that names one can be saved again. The column is
  never written by an insert, an update or a request: read-only in the DTO and in the TypeScript field
  metadata, `insertable = false, updatable = false`, left out of the search DTO.
- Sign-in (`authenticate`, JPA and MyBatis) finds a deleted row no more. The layered MyBatis service replaces
  a collection on update by removing the old live rows of a `@deleted` member outright (`purgeAllBy...`) - kept
  rows would hold the keys the insert writes again; a delete of the root keeps them (`deleteAllBy...`).
- Refused, with the reason - the build stops: a delete that would remove other rows outright with it (members
  of the aggregate, collections and to-one alike, a `@cascade` that removes, `orphanRemoval`) unless they are
  `@deleted` too - `@cascade(PERSIST)` deletes nothing and is not refused; two `@deleted` fields; a type other
  than `datetime` / `timestamp`; the key; a composite key; a key the request gives under JPA without a
  `@jpa:Version` (a create of a deleted row's key would be merged into that row, which stays deleted); a
  `@deleted` inherited from a parent with a table of its own; an `@embeddable`, a `@rollup` entity, a
  single-table hierarchy; JPA with `mapping: xml` (Hibernate 6.5 reads neither `<sql-delete>` nor `<where>`
  from orm.xml - checked on `elasticore-template-ddd`, the row was removed). The 2.x-compatible bundle, which
  writes no delete that keeps the row, names `@deleted` in its unenforced-rule warning. Not generated:
  restoring a deleted row, and the filter in queries the developer declares (`@query`, derived finders, dbms
  ports, native SQL).
- A `@SQLDelete` the developer wrote on their own class (a split root) stays theirs: the class annotations are
  kept in line with the DSL for `@SQLDelete` only where the generator writes one.
- 2.x output: unchanged (rulesCheck -Pstrict 157 MATCH / 0 DIFF / 2 ACCEPTED, treeCheck 0 differ / 0 missing /
  0 extra). A model without `@deleted` generates what it did.
- Samples: pet store `Product.discontinuedAt` (JPA annotations, Hibernate 7), `elasticore-template-mybatis`
  `Category.retiredAt` (layered MyBatis, `datetime`), `elasticore-template-ddd-mybatis` `Shipment.deletedAt`
  (DDD over MyBatis), `elasticore-template-kt` `Ticket` and its member `TicketEntry` (Kotlin, with the
  lifecycle's delete guard); their DDL files are regenerated.
- Checked: `SoftDeleteRulesTest` (each refusal, the members rule - to-one, `@cascade(PERSIST)` - the JPA
  entity, collection and repository in Java and Kotlin, the MyBatis statements, the search DTO);
  `SoftDeleteGenerationTest` (hexagonal Java and Kotlin and the TypeScript client: an inherited `@deleted`, a
  split root, a member collection, the mapper's `toSpec`, sign-in, the refusals, `mapping: xml`; layered
  MyBatis: the members kept on delete and replaced on update); over HTTP and in the table - pet store `SoftDeleteTest` (an item
  still reads its deleted product, a request cannot write the column), `ShopApi` of `elasticore-template-mybatis`
  (a category with products and a child category, on H2 here, Oracle, PostgreSQL and SQL Server in CI),
  `DomainOnDatabase.aSoftDeleteKeepsTheRow` of `elasticore-template-ddd-mybatis` (the four databases),
  `DomainRulesDslTest` of `elasticore-template-kt` (the guard refuses an open ticket; a closed one is kept with
  its entries; an entry taken out is kept and does not come back).
- Files: `SoftDelete` (new), `JpaEntityAnnotationRules`, `JpaEntityBaseRules`, `RepositoryBaseRules`,
  `MapperSpecRules`, `MapperSpecShellRules`, `KotlinMapperSpecShellRules`, `SearchDtoBaseRules`,
  `MyBatisMapperRules`, `MyBatisSql`, `DddMyBatisPorts`, `EntitySpec`, `SpecFactory`, `RulesGenRunner`
  (managed class annotations), `EntityModelLoader`; the annotation and DSL guides (en and ko).
