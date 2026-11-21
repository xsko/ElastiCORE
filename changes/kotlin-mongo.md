### New Features (MongoDB - Kotlin)

#### `mode: mongo` is generated in Kotlin: `language = 'kotlin'`, layered
- Why: a MongoDB domain was generated as layered Java only; a Kotlin build was refused ("MongoDB code is written in
  Java, layered"). A DSL word should work in every language the generator writes.
- Now: a Kotlin build writes the same layered MongoDB code in Kotlin, from the new bundle `mongo-springboot-kt`
  (chosen by `bundleFor(LAYERED, KOTLIN, MONGO)`; a domain naming `jpa-springboot-kt` or `mongo-springboot-kt` in
  `publisher.rules` / `publisher.rules.kt` gets it too). The entity is a class of `var` properties with the
  annotations the Java one carries (`@Document`, `@Id`, `@Version`, `@Indexed`, `@CompoundIndex`, `@Field`,
  `@ValueConverter(AttributeValueConverter::class)`, `@DocumentReference(lazy = true)`, `@Transient`), without
  Lombok; the repository's search (`criteria`, `hasCondition`, `sort`, `SORT_FIELDS`) is its `companion object`,
  each function `@JvmStatic`, so `OrderRepository.criteria(...)` is the same call from Java and Kotlin; the service
  (`kt/mongo_service.tmpl`), the mapper and the repository helper name neither `EntityManager` nor `Specification`.
  Everything the Java cell writes is written: `@sequence` (long and int) and `@genid` keys, members embedded in their
  document, references by id, `@deleted`, `@jpa:CreatedDate` / `LastModifiedDate` / `CreatedBy` (through the
  application's `AuditorAware`), `@jpa:Version`, every search condition, `@access` / `@owner`, `@signin`
  (`refresh=request`), `@password`, admin-only fields, html fields, `@readonly`, enums by their code, money.
  A reference is read through a proxy of the referenced class, which Kotlin makes final: the project opens its
  documents with all-open on `org.springframework.data.mongodb.core.mapping.Document`, as a Kotlin JPA project opens
  `@Entity` (env guide 2.4). ddd and hexagonal over MongoDB stay refused, in Java and Kotlin; the Java cell's
  "Not yet" list carries over unchanged.
- Previously refused - no existing output changes: the JPA Kotlin tree (`elasticore-template-kt`, `src/main/kotlin`,
  `src/test/kotlin` and resources, 407 files) and the Java MongoDB tree (`elasticore-template-mongo`, 53 files)
  regenerate byte-identical; the Java-only branches the change touches are guarded by the language. The three fixes
  below change output only for the models they name, which did not compile or did not work before.
- `testCode: "true"` writes no service test over MongoDB (its skeleton is JPA's); a Kotlin build, where `testCode`
  otherwise works, now says so once per domain instead of leaving the tests out without a word (env guide 6.2).
- The client run of a Kotlin project (`elcore3ts`) reads the domain's `publisher.rules` as the Kotlin server run does,
  so naming `mongo-springboot-kt` does not stop it.
- Fixed on the way:
  - `money` over MongoDB (Java and Kotlin) failed every read with a 500: `AttributeValueConverter` handed the
    `Decimal128` MongoDB keeps a `BigDecimal` as to Money's converter (`ClassCastException`). It reads it back as the
    `BigDecimal` it was. Runtime only; no generated file changes.
  - `@signin(refresh=request)` with `@deleted` over MongoDB (Java and Kotlin): `me()` read the member with
    `findById` alone, which a MongoDB repository answers for a deleted document too, so a member who deleted their
    own account kept a working session. `me()` now leaves a deleted document out, as every other read of the
    service does (`.filter(e -> e.getLeftAt() == null)`), and the session ends with the next request. The Java
    MongoDB output changes for such a model only; JPA has no live filter there and is unchanged.
  - Kotlin (JPA and MongoDB): an `int` identity (`@owner`, a signed-in key) was read with
    `Integer::class.javaObjectType`, which an `Int` property does not take - the service did not compile. It is
    `Int::class.javaObjectType` now. The JPA Kotlin output changes only for an `int` owner or sign-in key; the
    sample has none.
- Checked: `elasticore-template-kt-mongo` (new) generates the shop of `elasticore-template-mongo` and a club domain
  (sign-in, owners, int keys, html, auditing, every search condition, money, a compound index, `@readonly`) in
  Kotlin, compiles it, and runs `elasticore-template-mongo`'s HTTP tests word for word plus the club's against a
  real MongoDB 7.0 - 8 tests, all passing (Testcontainers on CI, as `elasticore-template-mongo`). The same blueprint
  and tests on Spring Boot 4.0.8, Kotlin 2.2 and `elasticore-springboot4` (a local build outside the repository: this
  build pins Kotlin 1.9 for every module): generated, compiled, 8 of 8 passing - which is why a `between` search
  reads its bounds into a value (Spring Data MongoDB 5 declares the operand non-null). `MongoGenerationTest` (Kotlin
  cases, among them ddd and hexagonal still refused in Kotlin, the bundle names, the client run, `testCode`, the
  deleted member's session and the JPA Kotlin int owner), `PersistenceAxisTest`, `DefaultGenerationTest`,
  `AttributeValueConverterTest`; the full `elasticore-base` suite; `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF /
  2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `RulesConfig.java`, `RuleCatalog.java`, `RulesGenRunner.java`, `mongo/MongoEntityFileRules.java`,
  `MongoRepositoryRules.java`, `MongoKotlinRepository.java` (new), `MongoServiceFileRules.java`,
  `lang/kotlin/KotlinMapperAssembleRules.java`, `mybatis/MyBatisMapperMemberRules.java`,
  `MyBatisServiceFileRules.java`, `base/ServiceAccessRules.java`; templates `kt/mongo_repository.tmpl`,
  `kt/mongo_repositoryHelper.tmpl`, `kt/mongo_mapper.tmpl`, `kt/mongo_service.tmpl`; `elasticore-springboot3`
  `mongo/AttributeValueConverter.java`; `elasticore-template-kt-mongo` (new); `elasticore-env-guide.md` and
  `elasticore-rule-guide.md` (en, ko) 2.4 and the bundle tables; the root `build.gradle` declares the Kotlin plugins
  once for both Kotlin samples.
