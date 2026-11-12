### Bug Fixes (v3 search - `@searchResult`)

#### The 3.0 search answers with the `@searchResult(Entity)` DTO, as 2.x does
- Why: a DTO declared `@searchResult(Entity)` (with `--` to leave a large column out of a list) was written by the rule
  engine, but nothing used it: `findBySearch` still returned `Page<EntityDTO>` through `toDTO`, the mapper had no
  `toEntityResultDTO`, and the list carried every field of the entity. Reported by a user moving a 2.x project to 3.0.
  The spec field every consumer reads (`EntitySpec.searchResultDtoName`) was never set.
- What: `SpecFactory` sets it from the `SEARCH_RESULT` relationship, as 2.x `findSearchResultDTOName` finds it, when
  the model has that DTO (the entity's own `EntityDTO` is not taken: the search already answers with it). The service
  (layered, ddd, hexagonal, MyBatis, Kotlin), the controller, the hexagonal use case and the TypeScript client then use the
  result DTO. A result DTO without `@template` gets its entity-to-DTO mapping
  (`toEntityResultDTO`, one way, as 2.x writes it for the relationship); one with `@template` already had it. The v3
  controller and the hexagonal use case import the result DTO (`${searchResultImport}`, an empty slot without one).
  An entity without `@searchResult` generates what it did. The 2.x-compatible output now matches the 2.x generator
  for such a model too (service and mapper); the golden template has none, so its checks are unchanged.
- Checked: `SearchResultDtoTest` (8 tests, all failing without the change): v3 layered service `Page<XResultDTO>` +
  `Mapper::toXResultDTO`, the mapper method without the removed column, controller import and return type, an entity
  without one unchanged (no added controller line); a `@template` + `@searchResult` DTO mapped once; the compatible
  service and mapping body equal to the 2.x generator's; Kotlin, hexagonal, ddd, MyBatis and the TypeScript client.
  Compiled with a `CategoryResultDTO @searchResult(Category)` added for the run (then removed) to
  elasticore-template-ddd (hexagonal, and with `architecture` switched to `ddd` and `layered`), elasticore-template-kt
  (`compileTestKotlin`, `elcore3tsVerify` type-checks the client), elasticore-template-mybatis and
  elasticore-template-ddd-mybatis (`compileTestJava`). `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED,
  `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `rules/verify/SpecFactory.java`, `rules/verify/RulesGenRunner.java`, `rules/base/ControllerBaseRules.java`,
  `rules/hex/HexUseCaseRules.java`, `elasticore-template/v3/control.tmpl`, `elasticore-template/kt/control.tmpl`,
  `SearchResultDtoTest.java`.
