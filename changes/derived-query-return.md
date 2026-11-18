### Bug Fixes (repository - a derived query without `return:`)

#### A derived query without `return:` answers by its name instead of `void`
- Why: a repository method with neither `query:` nor `return:` was declared `void` in every generator - a
  `findByLastName` too. It compiled, and the call failed: `JpaSystemException: Specified result type [void] did not
  match Query selection type [...Customer]` (2.x template on H2, `@DataJpaTest`). The DSL guide did not say what an
  omitted `return:` means.
- Now: the model reads the return from the name, as Spring Data does: `find`/`read`/`get`/`query`/`search`/`stream`
  ...`By` -> `List<Entity>` (`Page<Entity>` with `pageable: true`), `countBy` -> `long`, `existsBy` -> `boolean`;
  `deleteBy`/`removeBy` and any other name stay `void`. A method with `query:` or `return:` is unchanged, and so is
  a native query (already `List<Entity>`). The 2.x generator changes too, for this case only: its `void` find failed
  when it was called. No template or sample declares such a method (`elcoreRulesCheck` / `elcoreTreeCheck` unchanged).
- Checked: `DerivedQueryMethodTest` - the 2.x JPA and MongoDB generators, the 2.x-compatible rule-engine output (equal
  to 2.x), v3 layered Java and Kotlin declare `List<Stock> findByOnHandLessThan`, `Page<Stock>
  findByWarehouseOrderByOnHand(..., Pageable pageable)`, `long countByWarehouse`, `boolean existsByWarehouse`,
  `void deleteByWarehouse`; fail without the change. The MongoDB repository's `Page<Stock> findByWarehouse(String
  warehouse, Pageable pageable);` is asserted now (review note on `derived-query-method.md`). Run on H2 (probe not
  committed): the 2.x template with `findByLastName` and `countByLastName` without `return:` - 2 of 3 customers and
  `2`; before the change, the `JpaSystemException` above.
- Guides: the DSL guide (6. Repository definition), English and Korean.
- Files: `loader/module/RepositoryModelLoader.java`, `DerivedQueryMethodTest.java`,
  `elasticore-guide/elasticore-dsl-guide.md` and `ko/`.