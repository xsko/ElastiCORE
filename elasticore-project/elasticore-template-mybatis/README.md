# elasticore-template-mybatis

A shop kept in MyBatis rather than JPA, generated, compiled and run on every build.

`src/main/resources/blueprint/shop/env.yml` says `mode: mybatis`; everything else is the DSL any domain is
written in. `elcore3` writes, beside the DTOs, mapper and controller a JPA domain would have:

| File | What |
|---|---|
| `domain/model/<Entity>.java` | the entity, a plain class (no JPA annotations) |
| `infrastructure/persistence/<Entity>Repository.java` | the MyBatis mapper interface (`@Mapper`) |
| `resources/.../<Entity>Repository.xml` | its statements: the row with its to-one relations joined, the search, sort and page, insert, update, delete |
| `application/<Entity>CoreService.java` | the service the controller calls, with the JPA one's methods |
| `resources/db/schema-{h2,postgresql,oracle,sqlserver}.sql` | the tables in each database's types (`elasticore { ddl }` in build.gradle) |

The tests in `src/test/java/mybatis` (`ShopApi`) drive the generated API: create, read, every search condition
of the model, paging and sorting, a refused sort, an update and a stale version, a delete still referenced, a
taken unique value, the number of statements a page takes, and every column type (`Gadget`) written and read
back - each one set and each one empty. `MyBatisApiTest` runs them on H2; `ShopApiOn{PostgreSql,Oracle,SqlServer}Test`
on the database `ELASTICORE_IT_*_URL` names, from that database's schema (CI runs all three).

```bash
./gradlew :elasticore-project:elasticore-template-mybatis:test      # H2
./gradlew :elasticore-project:elasticore-template-mybatis:dbTest    # the databases ELASTICORE_IT_* name
```

The generated trees are not committed (`.gitignore`); the blueprint and the tests are.
