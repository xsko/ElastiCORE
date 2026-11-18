### Bug Fixes (2.x generator - repository methods)

#### A repository method without `query:` is declared as a Spring Data derived query
- Why: the DSL guide (6. Repository definition) says a method without `query:` is a Spring Data derived query,
  read from its name, and its example starts with one. The 2.x generator - `./gradlew elcore`, the plugin with
  `output = 'v2'` and `@ElastiCore` - stopped on it with `NullPointerException: Cannot invoke
  "SqlQueryInfo.isSelectQuery()"`, in every version from 2.0 on, and wrote no repository. Such a method has no query
  info; the repository publisher asked it whether it selects before checking that it has one. The rule engine (v3
  and the 2.x-compatible `jpa-springboot`) already declared it.
- Now: the method is declared as it is named, without `@Query` or `@Modifying` -
  `List<Stock> findByWarehouseAndOnHandGreaterThan(String warehouse ,Integer onHand);`,
  `Page<Stock> findByWarehouse(String warehouse, Pageable pageable);` with `pageable: true`. The same in the 2.x MongoDB repository. A model without such a method generates the same as
  before: only the method that used to stop the run takes the new branch.
- Checked: `DerivedQueryMethodTest` - the 2.x generator (JPA and MongoDB) writes the derived methods next to a JPQL
  one, the rule engine's 2.x-compatible output declares the same lines, and the v3 layered, Kotlin and hexagonal
  repositories declare them; the 2.x cases fail without the fix. Run end to end: three derived methods (`Optional`,
  `List` with two conditions, a `Page`) added to the 2.x template's model, generated with `output = 'v2'` and called
  in a `@DataJpaTest` on H2 - Spring Data derived and ran each (not committed). `elcoreRulesCheck -Pstrict`,
  `elcoreTreeCheck`.
- Files: `pub/jpa/RepositoryFilePublisher.java`, `pub/mongodb/RepositoryFilePublisher.java`,
  `DerivedQueryMethodTest.java`.
