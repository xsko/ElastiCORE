### New Features (tests - `testCode: "contract"`: the tests of what the DSL declares, lifecycles and constraints)

#### `testCode: "contract"` writes tests of each lifecycle move and each constraint, over the project's own API
- Why (issue 295, phase 2): the rules a model declares - a move of `states:`, a `@min`, a `@pattern`, an `@invariant` - each
  needed a test written by hand, and most had none. A regression in the generator or in a hand-written override passed the
  build. The tests the DSL already implies are now generated, and an AI agent that edits the model gets a precise signal.
- Now:
  - A new value of env.yml's `config.testCode`: `"contract"`. It writes the contract tests and nothing else - no Mockito, no
    service unit test. `"true"` keeps its meaning (the service unit tests of the 2.x output and Kotlin; the v3 Java bundles
    still write none under it). The v3 output and Kotlin, every architecture (layered, ddd, hexagonal), JPA, MyBatis and
    MongoDB; the 2.x output gets none, and a DSL warning says so.
  - Per domain, in `src/test/java/<base package>/` (`src/test/kotlin` for Kotlin): `<Domain>ContractSupport` and a
    `<Entity>ContractTest` for each entity whose API creates rows. Both carry the generate-once marker: written once, then
    merged when the DSL changes and kept where the developer changed them, as the architecture test.
  - What is tested, by status and the problem's `code` (and the field), never by message text:
    - each move of `states:` from each state it lists - 2xx, the state moved (the value the API shows, `@json(code)` too), a
      `@stamp` of the state entered set - the row brought there by the shortest sequence of commands; refused (409,
      `state.<Entity>.<field>.<command>`) from each other state a row can reach, preferring a state only that lifecycle
      refuses in when a command moves several; a new row starts in each lifecycle's `initial`;
    - `!` sent as null (400 `NotNull`); `string(n)` / `@length` at the length and one past it (400 `Size`, and the least
      length); `@min` / `@max` at the bound and one step past it (400 `Min` / `Max`, `DecimalMin` / `DecimalMax` where the DTO
      writes those); `@pattern` with a value made from the expression and checked with `java.util.regex` at generation, and
      one that does not match (400 `Pattern`); `@minsize(n)` with n and n - 1 members (400 `Size`);
    - `@invariant` of the forms the generator reads - `field > n` (`>=`, `<`, `<=`, `==`), `items.size > n`, `notNull`,
      `notEmpty` - at the boundary and one past it (422, the invariant's own code).
  - Rows are built from the model: what the DTO requires, the rows a required reference names (made first through their
    own API; the key the request sends - a flattened key, or the identity a reference by id holds), the references a
    `@snapshot` or `@compute` reads through, the members a collection must have, a required value object, enumerations by
    the value the API reads, dates in the DTO's format (`@past` / `@future` respected), unique text for every string, and a
    value of its own for each row where one must be unique (`@unique`, a unique index alone on it, the key): a `@pattern`'s
    value varied in the positions the pattern lets vary (each character checked in place, and combinations, at generation),
    a number in whole steps within its bounds. A unique enumeration, boolean or date, a unique `@pattern` with one value only,
    a fractional bound of a float (a float holds no exact 0.1, and its check widens it to a double) and a table H2 cannot
    make (`@columnDef('jsonb')`, `xml`) are counted as skipped. A whole number's promise (`qty > 0.5`) is tested at whole
    numbers (1 and 0). Each test makes its own rows and reads no seeded data.
  - The support class carries `@SpringBootTest` + `@AutoConfigureMockMvc` (Spring Boot 3 and 4 packages) and, over JPA and
    MyBatis, `@Transactional`: every test class extends it, so the suite starts one application context and each test's rows
    are rolled back. Spring finds the application above the tests' package; where it would not, the build names the
    project's single `@SpringBootApplication` (src/main first). Over JPA and MyBatis its properties pin an in-memory H2 of
    the tests' own, whatever datasource the application names (URL, driver; over JPA `ddl-auto=create-drop` and Flyway and
    Liquibase off, over MyBatis the application's schema); a project changes them there. Over MongoDB the tests run on the
    server `ELASTICORE_IT_MONGODB_URI` names, in the database `elasticore_contract`, and are skipped without it.
  - Every request goes through the support class: `request(builder)` (what every request gets added - empty; a CSRF token
    goes there) and `signIn()`. Where an operation asks who is calling (`@access`), `signIn()` makes an administrator as the
    application itself (`CurrentUser.runAsSystem`, the role from `adminRole`) and signs in through the generated sign-in
    (`@signin`); without one the requests run as the application. JSON is read with JsonPath and written by the support
    class: no Jackson version is assumed.
  - A refusal at run time by a rule of the application's own (a check its code makes, a unique value taken) aborts the test
    with the reason (`Assumptions.abort`, also printed - Gradle's XML report keeps no reason). A refusal the model declares
    (`invalid`, `invariant.*`, `state.*`) and a 5xx fail it - on a row the test needs as on the one it checks, so a
    regression in a row's builder (a required field it does not send) cannot pass as aborted tests. `testCode` is read
    exactly as `"true"` is; a value it does not take (`"Contract"`, `"contracts"`) is a DSL warning. A row the generator
    cannot build is counted:
    `=== contract tests: 70 written, 2 skipped (Receipt: its API takes no create (@expose methods), so no row is made to test;
    Product.price @min(0): @invariant(price > 0) refuses the boundary) ===` after the run's summary.
  - Rule API: `DddInvariantRules.declaredPromises`, `DddStateRules.stampedStates`; `DddCommandRules.kebab`,
    `ServiceAccessRules.keyGenerated` and `ServiceAccessRules.level` are public.
- Unchanged: a model without `testCode: "contract"` generates byte-identical output (a run with and one without the switch
  differ only in the added test files - `ContractTestsTest`); `"true"` writes what it wrote. `elcoreRulesCheck -Pstrict`
  158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra. No project build is changed: the tests
  need `spring-boot-starter-test` and, over JPA and MyBatis, H2; on Spring Boot 4 `spring-boot-starter-webmvc-test` too.
- Not yet (later phases of issue 295): `@access` itself, `@Mask` and `@encrypt`, `export`; nor a lifecycle's `delete:`
  states, or the constraints of an aggregate's members on their own.
- Samples: `testCode: "contract"` in `elasticore-template-ddd` (shop, board, catalog, billing), `-ddd-mybatis` (shop, catalog,
  billing), `-kt` (shop), the pet store (petstore), `-mongo` and `-kt-mongo` (shop). The generated tests are not committed,
  as the samples' generated code; the ones a sample edited are: the Kotlin sample's support class names its application
  (`ddd.KotlinShopApp`), the pet store's `PurchaseOrderContractTest` sends the delivery address `PurchaseOrder.place` needs.
  The Kotlin sample's Product maps PostgreSQL's `xml` and `jsonb`: it is counted as skipped (`contract tests: 38 written,
  1 skipped (Product: metadataRaw's column is jsonb, which H2 - the tests' database - cannot make)`).
- Checked: the generated tests run and pass - `elasticore-template-ddd` 70 (Java, hexagonal and layered, JPA: two lifecycles
  moved by one command, stamps, invariants, references by id), `-ddd-mybatis` 45 (MyBatis), `-kt` 38 (Kotlin, sign-in
  through the generated sign-in), the pet store 40 (Spring Boot 4, `@access` with an administrator signed in, money,
  embedded values, members with references); the MongoDB samples compile and skip 12 each without a server. Each module's
  whole suite passes with them (199, 156, 94, 111). A regression is caught: `PurchaseOrder.ship()` overridden without
  `super.ship()` failed 4 of the pet store's tests (the move, and the refusals from SHIPPED and CANCELLED). `ContractTestsTest`
  (30): the Java and Kotlin sources, the layered bundle, the sign-in, MongoDB, the application named, an edit kept and a
  DSL change merged, `"true"`, the 2.x output, the summary; a run without the switch the same but for the test files in Java
  hexagonal, Kotlin, layered, MyBatis and MongoDB; the H2 properties (Java, Kotlin, MyBatis) and Mongo's database; the
  support class failing a declared refusal of a row it needs; unique values (pattern varied, number in steps, what cannot
  be said); a float's fractional bound (and that Hibernate Validator's widening, `BigDecimal.valueOf((double) 0.1f)`, is
  above 0.1); a whole number's promise; a table H2 cannot make; `testCode` read exactly; two domains with an entity of one
  name; the pattern values (negated classes, `\w`, `\S`, inline flags, `{n,}`, combinations).
- Files: `verify/ContractTestSource.java`, `verify/PatternSample.java` (new), `verify/RulesGenRunner.java`,
  `ddd/DddInvariantRules.java`, `ddd/DddStateRules.java`, `ddd/DddCommandRules.java`, `base/ServiceAccessRules.java`,
  `ContractTestsTest.java` (new), the env guide 6.2 / 6.2.1 (en, ko), the samples' env.yml, `.gitignore` and
  `elasticore-template-kt/build.gradle`.
