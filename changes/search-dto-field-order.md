### Bug Fixes (search DTO - its fields no longer depend on the order of the entities and files, issue 376)

#### Which fields a search DTO searches no longer depends on where its entity or its `dto:` section is written
- Why: an `@expose` search DTO gives every field without a `@search` of its own a condition (`auto` for text,
  `between` for dates, `eq` otherwise), and it wrote that condition into the field's annotation map. The search DTOs
  built after it then saw those fields as declared `@search` fields:
  - The fields of an `@extend` parent are one object for every entity that extends it. A `@service` entity written
    below an `@expose` sibling got `createdBy`, `createDateFrom`/`To` and the other parent fields in its search DTO,
    its mapper's `toSpec` and its `<Entity>SrchDTO.ts`; written above, it did not.
  - A `@searchable(entity=X)` DTO written in the DSL got every own field of an `@expose` X when it was built after
    X's search DTO. Model files are read by name, and a file's `dto:` section before its `entity:` one, so a `dto:`
    section in `search.yml` (after `entity.yml`), or in a domain loaded later, came after X's search DTO; one in
    `dto.yml` (before `entity.yml`) did not.
  - Generating one domain (`-Pdomain`) or all of them could differ the same way.
  - Both the 2.x generator (`SearchDtoSrcFilePublisher`) and the rule engine (`SearchDtoBaseRules`, which copied
    the write to stay equal to 2.x) did this. 2.2.261026 fixed it on the 2.x line; this is the same fix on 3.x.
- Now: the condition is decided, never written into the field. `AutoSearch` decides which fields are searched without
  a `@search` of their own, for the 2.x generator (`SrcFilePublisher.getAutoSearchCondition`, used by the search DTO
  and the mapper) and the rule engine (`SpecFactory` marks the fields, `SearchDtoBaseRules` derives the condition and
  hands it to the mapper's `toSpec` and to the MyBatis / MongoDB search through `FieldSpec.derivedSearch`, not
  through the shared field). As in 2.2.261026:
  - an `@expose` entity's search DTO searches every field, as before;
  - any other search DTO - of a `@service` entity, or a `@searchable` DTO written in the DSL - searches the fields it
    declares, the fields its entity inherits through `@extend` when the search DTO of some `@expose` entity has the same
    field, and the entity's own fields only when they have a `@search` of their own.
- Still not local to the search DTO: whether it gets a parent's fields depends on whether any `@expose` entity, in any
  domain, extends the same parent. Adding `@expose` to an unrelated entity on that parent adds the parent's fields to
  every `@service` or declared search DTO of the parent's other entities - 2.2.261026 decided to include them, so
  the result no longer changes with the order.
- Generated code changes, in both directions:
  - **Added:** a search DTO that came before its `@expose` sibling now gets the inherited fields, its mapper's
    `toSpec` the conditions, its TypeScript type the optional properties. In this repository: `elasticore-template`
    (the committed 2.x baseline, regenerated) `ArticleSrchDTO`, `EmployeeSrchDTO`, `ContentMapper`, `HrMapper`;
    `elasticore-template-kt` the same four in Kotlin and `ArticleSrchDTO.ts`, `EmployeeSrchDTO.ts`;
    `elasticore-template-ddd` `OrderSrchDTO`, `ProductSrchDTO` (both written in `dto.yml`), `ShopMapper` and the
    committed `graphql/shop.graphqls` (`OrderSrchInput`, `ProductSrchInput`). The pet store generates the same files
    as before.
  - **Removed:** a search DTO written in the DSL whose `dto:` section came after the `@expose` entity's search DTO
    (a later file, a later domain) loses the entity's own fields it had no `@search` for - e.g. `ProductNameSrchDTO`
    in `search.yml` with `keyword: string @search(like) @ref(name)` had `name` and `price` too, and now has `keyword`
    (plus any inherited fields) only. A client that still sends such a property gets no error: it is ignored. None of
    the samples here is written that way.
- Upgrading:
  - To keep an own field in such a search DTO, give the entity's field a `@search` of its own (`price: int
    @search(eq)`); the `@expose` search DTO then searches it by that condition too.
  - To keep a parent's field out, mark it `@searchBypass` on the parent: it then leaves every search DTO of every entity
    that extends that parent, `@expose` ones included.
- Also from 2.2.261026, in the 2.x generator: a search name used by two fields (`@s(like$keyword)` on both) ended the
  field loop instead of skipping the repeat, and `SearchDtoSrcFilePublisher.publish` then failed with a
  `NullPointerException` - the domain's search DTOs, repositories, mappers, services and controllers were not
  written. Only the repeated field is skipped now.
- Also from 2.2.261026: the check whether an existing generated file must be rewritten (`isSemanticallyIdentical`)
  compared its lines as a set, so a file whose new lines repeat lines another method already has (the audit fields'
  `toSpec` lines are the same for every entity) was taken for unchanged and never rewritten. Lines are counted now;
  whitespace, line order, braces and an `import` written twice are still no change. `-Pmode=valid` shows a line whose
  count alone differs with its counts, e.g. `(2 vs 1)`.
- The contract tests, which read how a field is searched from the field's own `@search` (where the condition used to
  be written), are generated as before: the fields they ask about are text, whose derived condition `auto` they take
  as an equality, the same as no condition; an encrypted field they search by has a `@search` of its own. The rule
  engine collects the `@expose` search DTOs' fields once per generation run, not once per search DTO spec (a contract
  test builds one per field).
- The annotation guide (`@searchable`, English and Korean) says which fields such a search DTO searches and that
  `@searchBypass` keeps a parent's field out.
- Checked: `SearchDtoFieldOrderTest` (new, 16 tests):
  - the 2.x generator: a `@service` search DTO and its `toSpec` are the same in both orders and carry the parent's
    fields; a `dto.yml` search DTO gets the inherited fields and not the entity's own; the fields after a repeated
    search name are kept; a mapper whose new `toSpec` line repeats another method's is rewritten; a repeated `import`
    is no change and a repeated statement is one;
  - the 2.x-compatible rule engine gives the 2.x generator's search DTO and `toSpec`;
  - a declared `ProductNameSrchDTO` in `a-search.yml` and in `search.yml` is the same, `keyword` without `name` and
    `price`, through the 2.x generator, the 2.x-compatible rule engine and the 3.0 code (one test each), while
    `ProductSrchDTO` keeps them; `price: int @search(eq)` brings `price` back to it (2.x generator and 3.0 code);
  - the issue's model (`Notice` `@service`, `Post` `@expose`, both `@extend(AuditEntity)`) in both orders gives the
    same `NoticeSrchDTO` (`title`, `createdBy`, `createDateFrom`, `createDateTo`), `toSpec` and `NoticeSrchDTO.ts` in
    the 3.0 code, the 2.x code and the TypeScript client, and the same MyBatis XML;
  - `@searchBypass` on a parent field keeps it out of the `@service` and the `@expose` search DTO and their `toSpec`
    (2.x generator and 3.0 code).
  - All 16 fail without the change, each on the order or file it depends on (or the crash, the missed rewrite).
  - In the test JVM, on a model loaded there (`SearchDtoFieldOrderTest` runs each generator in a JVM of its own):
    `AutoSearchTest` (the condition of each type kind - `time` by equality, as 2.x does - inherited fields, the
    searched entity, which DTOs' fields count as an `@expose` search DTO's, the 2.x publisher's condition per search
    DTO kind), `SemanticLineCountTest` (counts differing by one and by more, equal counts, the `(3 vs 1)` of the
    diff), `SearchDtoOrderInProcessTest` (the 2.x generator in both orders) and `SearchedAsInheritedTest` (the
    marks `SpecFactory` sets, the collection made once per run and per context and kept by nothing after the run).
    `diffMutation`: 96.6% of the 59 mutations of the changed lines detected.
  - The samples generated before and after the change differ only in the files listed above, by added lines;
    `elasticore-template-ddd` (332 tests) and `elasticore-template-kt` (240 tests) pass on the new output, and the
    contract tests they generate are unchanged. `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED,
    `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `AutoSearch.java` (new), `SrcFilePublisher.java`, `SearchDtoSrcFilePublisher.java`, `MapperSrcPublisher.java`,
  `FieldSpec.java`, `SearchDtoBaseRules.java`, `MapperSpecRules.java`, `MyBatisSearchRules.java`, `SpecFactory.java`,
  `RulesGenRunner.java`, `SearchDtoFieldOrderTest.java`, `AutoSearchTest.java`, `SemanticLineCountTest.java`,
  `SearchDtoOrderInProcessTest.java`, `SearchedAsInheritedTest.java` (all new),
  `elasticore-annotation-guide.md` and its `ko/` copy; `elasticore-template` `ArticleSrchDTO.java`,
  `EmployeeSrchDTO.java`, `ContentMapper.java`, `HrMapper.java`; `elasticore-template-ddd` `graphql/shop.graphqls`.
