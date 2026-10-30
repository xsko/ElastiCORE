# elasticore-template-ddd-mybatis

The domain model and the hexagon over MyBatis, generated, compiled and run on every build.

The blueprint is `elasticore-template-ddd`'s with `mode: mybatis` in each `env.yml`: shop is the hexagon
(`architecture = 'hexagonal'`), billing the domain model (`publisher.rules: [ddd-springboot]`), catalog layered
(`[jpa-springboot-v3]`) - each read as its MyBatis bundle. What JPA did for an aggregate by itself, the generated
repository adapter (`infrastructure/persistence/<Root>RepositoryAdapter.java`) does: the members compared with the
rows stored, the version, the one-to-one member, the aggregate's `Lifecycle` where the callbacks were, its events
published after the save. The dbms port (`shop/port/report_port.yml`) is a MyBatis mapper and an adapter too.

What differs from the JPA blueprint, and why:

| | |
|---|---|
| `@audited` / `@notaudited` | gone - a row's history is Hibernate Envers'; refused under MyBatis |
| `tags: string[]` | gone - a list of values in one column is a JPA attribute converter |
| `mapping: xml` | gone - the MyBatis domain model names no persistence API already |
| repository.yml | the JPQL queries are SQL (`nativeQuery: true`) - MyBatis runs SQL |
| report_port.yml | a page and a write added; `CONCAT` of two arguments, which Oracle takes |

The tests in `src/test/java/dddmybatis` are the DDD sample's, run on MyBatis - the ones that asked JPA
(`@DataJpaTest`, the EntityManager, Hibernate's statistics, `validate`) ask the ports, the statements MyBatis
prepared and the generated schema instead. `DomainOnDatabase` runs on H2 (`DomainOnH2Test`) and on the database
`ELASTICORE_IT_*_URL` names (`DomainOn{PostgreSql,Oracle,SqlServer}Test`; CI runs all three).

```bash
./gradlew :elasticore-project:elasticore-template-ddd-mybatis:test      # H2
./gradlew :elasticore-project:elasticore-template-ddd-mybatis:dbTest    # the databases ELASTICORE_IT_* name
```

The generated trees are not committed (`.gitignore`); the blueprint and the tests are.
