### New Features (v3 entity - a repository alone)

#### `meta: entity @repository`: the repository and the helper's getter, no service and no API
- Why: moving a 2.x project to 3.0, 36 entities that code of its own reads and writes through
  `RepositoryHelper.getXxx()` had no repository: the 3.0 code writes one only for an entity with `@expose` or `@service`,
  where 2.x wrote one for every entity with a key - about 870 compile errors. `@expose` opens an API nobody wants,
  `@service` writes services nobody calls (up to 72 files).
- What: `@repository` on the entity line writes `<Entity>Repository` and puts it in the domain's `RepositoryHelper`, and
  nothing else - no CoreService, Service, Controller or API. v3 and Kotlin over JPA. Under `mode: mybatis` it is a DSL
  warning and writes nothing yet. `@expose` or `@service` beside it decide as before; an entity without a key gets
  nothing; the 2.x generator and the 2.x-compatible output are unchanged (every keyed entity has a repository there).
- Checked: `RepositoryOnlyTest` (Java and Kotlin: the repository and the helper field, no service or controller files; an
  entity with none of them still gets nothing; MyBatis warns). `elasticore-template-ddd`'s catalog has
  `Supplier @repository`: `CatalogApiTest.aRepositoryWithoutAnApi` saves and reads it through
  `CatalogRepositoryHelper.getSupplier()`, `/api/v1/catalog/supplier/SUP-1` answers 404, and no service or controller
  class exists; its DDL has `cat_supplier`. 105 tests of the module pass.

### Bug Fixes (2.x-compatible output on Spring Boot 4 - the delete by search)

#### `output = 'v2'` on Spring Boot 4 compiles: the delete by search removes the rows it reads
- Why: the 2.x `XxxCoreService.delete(SrchDTO)` calls `JpaSpecificationExecutor.delete(Specification)`, which Spring Data
  JPA 4 does not have (it takes a `PredicateSpecification`), so the 2.x-compatible output did not compile on Spring Boot
  4.0.8 or 4.1.2 (15 files in that project). README and the env guide said only enums differ on Boot 4.
- What: with Spring Boot 4 the compatible service reads the rows (`findAll(specification)`) and removes them
  (`deleteAll`), answering their number - the 3.0 service deletes row by row too. JPA's remove runs for each, so
  `@PreRemove` and cascades run where the bulk delete skipped them. Spring Boot 3 output is unchanged, byte for byte;
  the 2.x generator keeps its line. README (en, ko) and env guide 3-1 (en, ko) say so.
- Checked: `ServiceCompatFileTest.onBoot4TheDeleteBySearchRemovesTheRowsItReads` (Boot 3: the old line; Boot 4: the
  three lines, no template directive left). `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED,
  `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.

### Bug Fixes (generated-file merge - a 2.x wildcard import)

#### The merge keeps a 2.x wildcard import the developer's code relies on
- Why: 2.x writes `import ...dto.*;` and `import ...service.*;`; the 3.0 code names `FolderDTO`, `FolderService` one by
  one. In a file the developer wrote more into (`FolderController`, `MERGED(your edits kept)`), the import block takes the
  generated side where the developer left a base line alone, so the wildcards went - and the developer's code using
  `NoteService` and `ModelInfoService` through them failed with `cannot find symbol` (16 errors in 3 files, reported with
  the base, the developer's file, the merge and the log).
- What: `ThreeWayMerge.mergeSource` keeps a base wildcard the developer kept when they edited the code and the generated
  imports still name a class of that package (so the package is there). A wildcard of a package the generated code no
  longer imports from goes, as does every wildcard of a file the developer did not write into; a single-type import the
  DSL dropped still goes - its class may be gone.
- Checked: `ThreeWayMergeTest.aBaseWildcardTheEditedCodeKeptStaysWhileItsPackageIsImported` and
  `aWildcardIsKeptOnlyForAPackageAClassIsStillImportedFrom`; the reported `FolderController` merged again keeps both
  wildcards. Migration guide (en, ko).

### Changes (guides - their language)

#### Korean guides already in the blueprint stay Korean without `guides = 'ko'`
- Why: after 3.0.261031-rc7 the guides are English by default, and a project that had the Korean ones got the English ones
  on its next build, with nothing but `Copying guide files (en)` in the log.
- What: when `elasticore { guides }` is not set and the guides in the blueprint are Korean (5% or more of their characters
  Hangul - the Korean guides are a fifth and more, the English ones hold 26 Hangul characters of 68,000), the Korean ones
  are copied and the build says so, with `guides = 'en'` to change it. A new project, or one with English guides, gets
  English as before; a set `guides` decides alone.
- Checked: `GuideDeployerTest.koreanGuidesAlreadyThereStayKorean` (Korean kept and said; `en` replaces them; English stay
  English) and `anEmptyBlueprintIsNotKorean`. Env guide 12 (en, ko).

- Files: `RulesGenRunner.java`, `RepositoryHelperBaseRules.java`, `service.tmpl`, `JpaServiceCompatFileRules.java`,
  `ServiceSrcPublisher.java`, `ThreeWayMerge.java`, `GuideDeployer.java`, the tests above, `elasticore-template-ddd`
  (`catalog/model/entity.yml`, `CatalogApiTest.java`, the DDL and `schema.yml` / `semantic_model.yml`), README (en, ko),
  the DSL, annotation, env and migration guides (en, ko)
