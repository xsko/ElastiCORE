### New Features (tests - `testCode: "contract"`: `export`)

#### The contract tests check what an entity's export promises
- Why (issue 295, phase 5): the export of issue 271 (`POST .../export`, where `@expose(methods)` lists `export`) promises the
  rows of the search, in its order, and a refusal above `elasticore.export.max-rows` before anything is written. A project that
  turns the contract tests on had none of it tested.
- Now, for each entity whose `@expose(methods)` lists `export` and whose controller has it:
  - `exportHoldsTheSearchsRowsInItsOrder`: two rows of the test's own, sharing a unique text the search finds them by (the first
    text field of the request the search takes with `eq`, `like` or no condition - one a request writes, not `@writeonly` nor set
    by the server, without `@pattern`, `@email`, `@unique`, `@Mask` or `@encrypt`, of six characters or more); where the search
    takes none, every row the caller may see. The search and the export
    get the same body - the conditions and `sortCode` `<key>-`: the key, which every response shows and a search may sort by
    (issue 304), descending, so an export that ignored the order (it puts the key after it, ascending) is told. The file (200,
    `text/csv`, an attachment, the key alone as its column) holds as many rows as the search matches (its `totalElements`), the
    test's two among them, and first the search's page, in its order.
  - `exportAboveMaxRowsIsRefusedBeforeAnythingIsWritten`: the same two rows; with `elasticore.export.max-rows` at the number of
    rows the export holds they are written, at one fewer the answer is 400 `export.too-many-rows` - a problem, with no
    `Content-Disposition` and no CSV. The setting is put into the application's `Environment` for that one request
    (`exportAtMost`, a `MapPropertySource` first and removed after): the export reads it at each request, so the suite keeps
    one application context - and tests run in parallel would see the setting too (the support class and the guide say so).
  - The support class gets what these tests call - `exportOf`, `exportAtMost` (with the `ConfigurableEnvironment` injected),
    `searched` and its `Rows`, `exportedKeys`, `exportedRows`, `assertSameRows`, `assertWritten`, `assertTooManyRows`, and a
    CSV reader (RFC 4180, the byte order mark, the formula guard's `'`) - only when a test calls it. A kept support class
    without them gets none of these tests and keeps the others (`... the tests of the export are not written`); the kept
    class's fallbacks are now every set of the groups it may lack - `@access`, `@Mask` and `@encrypt`, the export - the fewest
    left out first (the summary's wording for the first two is as before).
  - A refusal for more rows than the application exports (`export.too-many-rows` on the first export: a large table where
    nothing narrows the search) aborts the test; any other `export.*` refusal of the body fails it. Where the file and the
    search differ, or the capped export does not answer as expected, the search or the export is asked again: when it now
    answers otherwise, rows were written meanwhile (nothing narrows the search to the test's rows, or MongoDB's rows stay)
    and the test is aborted with both answers, not failed.
  - Counted as not tested, with why, in the summary and the support class's comment: a controller without `POST /export` - a
    rule set leaves it out, or the developer's controller, kept by the merge, has none (the run reads the file it leaves), a
    composite key, a `@searchResult` DTO without the key, the
    order where the API takes no search (`@expose methods` without `search`: the cap is still tested). An entity that exports
    but takes no create is counted as before ("its API takes no create").
- Unchanged: a model without `testCode: "contract"` generates byte-identical output; a domain without `export` gets the same
  contract tests and support class as before. No runtime library change, no DSL word.
- Samples: the pet store's `PurchaseOrderContractTest` (the sample's own file) takes the two tests as the generator writes them
  (no text narrows the order search: every order); `elasticore-template-kt`'s edited `ShopContractSupport.kt` takes the new
  members; `elasticore-template-kt-mongo`'s shop `Customer` lists `export` (it had none).
- Checked, over HTTP: the pet store (Java, JPA, hexagonal, Boot 4) `ItemContractTest` 20 (both narrowed by `name`) and
  `PurchaseOrderContractTest` 31 (every order, a `@genid` key, `@owner`), whole suite 199; `elasticore-template-kt` (Kotlin, JPA)
  `TicketContractTest` 36, whole suite 237 - before its `ShopContractSupport.kt` took the members, the run left the two out
  and said why (`ShopContractSupport has no exportOf(...) ... the tests of the export are not written`); `-ddd-mybatis` (MyBatis, ddd) `CustomerContractTest` 12, whole suite 160; `-mongo` (Java) and
  `-kt-mongo` (Kotlin) on MongoDB 7.0.14 through `ELASTICORE_IT_MONGODB_URI`: `CustomerContractTest` 12 each, whole suites 61
  and 52 (their Docker tests skipped as before). Broken on purpose, then restored: with the runtime's limit one row lax
  (`count <= max + 1`), both `exportAboveMaxRows...` tests failed (`expected 400 export.too-many-rows: 200`); with the export
  ignoring the asked order, both `exportHoldsTheSearchsRowsInItsOrder` tests failed (`[2011, 2010]` against `[2010, 2011]`).
  With the re-check in place the lax limit still fails both, not aborts them.
  `ContractTestsTest` (+9): the tests in Java, Kotlin, MyBatis and MongoDB, narrowed and not, the skips (a kept controller
  without `POST /export` among them), the re-check of the rows written meanwhile, a support class kept
  without the new members (with and without those of `@access`), a domain without export unchanged, and that a model with
  export adds only test files. `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0
  missing / 0 extra.
- Files: `verify/ContractTestSource.java`, `verify/RulesGenRunner.java` (the controllers with `POST /export`, the kept support
  class's fallbacks), `ContractTestsTest.java`, the env guide 6.2.1 (en, ko), the pet store's `PurchaseOrderContractTest.java`,
  `elasticore-template-kt`'s `ShopContractSupport.kt`, `elasticore-template-kt-mongo`'s shop `entity.yml`.
