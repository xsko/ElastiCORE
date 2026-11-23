### New Features (tests - `testCode: "contract"`: `@Mask` and `@encrypt`)

#### The contract tests check what a masked and an encrypted field promise
- Why (issue 295, phase 4): what the runtime's `@Mask` and the 3.0 code's `@encrypt` promise - the answer masked, the masked
  text a client sends back kept as the stored value (issue 270), no plain value in the column, a blind-index search by the
  value - was tested by hand in a few samples, and not at all in a project that turns the contract tests on.
- Now, for each field of an entity's DTO that the runtime's `@Mask(MaskType.X)` marks (an env.yml `@dto:` annotation, found
  as the generated update finds it) or that is `@encrypt`:
  - `@Mask`: a value of the mask's shape is sent - a phone of three groups for `PHONE`, an address for `EMAIL`, enough digits
    for `CARD`, `RRN`, `PLATE`, three letters or more for `NAME`, the field's `@pattern`'s value where it has one. The read and
    the search (narrowed to the row by its key, else by the value) show what the field's `MaskType` makes of it - its own
    `apply`, as the generated update compares it. A value the mask writes as it is aborts the test (nothing would show it).
  - Issue 270: an update (`PUT`) that sends the masked text back keeps the stored value, and one with a new value replaces it -
    read past the application: the column through `JdbcTemplate` (under JPA after the test's transaction is written out),
    the document through `MongoTemplate`, decrypted with the application's key where the field is also `@encrypt`.
  - `@encrypt`: the column holds no plain value and decrypts to the value sent; the API answers the value (where it is not
    masked). With `@search(eq)`: a search by the value finds the row, one by another value does not.
  - The support class gets what these tests call - `read`, `search`, `put`, `digits`, `masked`, `assertMasked`,
    `assertFoundMasked`, `assertFound`, `assertNotFound`, `assertUpdated`, and where a test reads what is stored `stored`,
    `assertStored` (`jdbc`, `entityManager` or `mongo` injected), `decrypted`, `assertEncrypted` - only when a test calls it.
    A support class kept by a merge without them gets none of these tests and keeps the others - the tests of `@access` too
    where it has their members - and the summary says so (`... the tests of @Mask and @encrypt are not written`).
  - Counted as not tested, with why, in the summary and the support class's comment: a composite key, a field a request does
    not write, a field no value the generator builds fits with a change the mask shows, no read (the search too where it takes
    neither the key nor the field), no update of the field (a root its commands change - `@editable` opens it), a `@pattern`
    field sent back (its pattern may refuse the masked text), an inherited field or a name not written plainly (its column is
    not read), an `@encrypt @search(eq)` field the search does not take.
- Unchanged: a model without `testCode: "contract"` generates byte-identical output; a domain without `@Mask` and `@encrypt`
  gets the same contract tests and support class as before. `testCode: "true"` is as it was. `elcoreRulesCheck -Pstrict` 158
  MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra. No runtime library change, no DSL word.
- Samples: `elasticore-template-ddd-mybatis`'s `Customer.phone` is masked (`@env:maskphone`); `elasticore-template-kt`'s
  `Agent` has `phone: string(20) @encrypt(digits) @s(eq) @env:maskphone` (its keys are the tests' `TestCryptoKeys`), and its
  edited `ShopContractSupport.kt` takes the new members. `elasticore-template-ddd`'s `Subscriber` and the MongoDB samples'
  `Customer.email` were masked and encrypted already.
- Checked, over HTTP: `elasticore-template-ddd` (Java, JPA) `SubscriberContractTest` 11, 4 of them new (phone and e-mail
  kept encrypted, the phone found by its value, the e-mail shown masked; sent back is counted - the root takes no `PUT`), whole
  suite 203; `-ddd-mybatis` (MyBatis) `CustomerContractTest` 13 (the phone shown masked and sent back), whole suite 158;
  `-kt` (Kotlin, JPA) `AgentContractTest` 23 (all four on `Agent.phone`), whole suite 233 - before its `ShopContractSupport.kt`
  took the members, the run wrote the class without them, as said; `-mongo` and `-kt-mongo` on MongoDB 7.0.14 through
  `ELASTICORE_IT_MONGODB_URI`: `CustomerContractTest` 10 each (the e-mail shown masked and sent back, read with
  `MongoTemplate`), whole suites 57 and 48 (their Docker tests skipped as before). Broken on purpose, then restored: without
  the update's keep of the stored value (`MaskedFields`), `phoneSentBackMaskedKeepsTheStoredValue` failed (`010-****-5479`
  stored); with the converter writing the value as sent, both `IsStoredEncrypted` tests of `Subscriber` failed; without the
  DTO's `@Mask`, `emailIsShownMasked` failed. `ContractTestsTest` (+8): each test in Java, Kotlin, MyBatis and MongoDB, the
  skips, a support class kept without the new members (with and without those of `@access`), a domain without them unchanged,
  and that a model with `@Mask` and `@encrypt` adds only test files.
- Files: `verify/ContractTestSource.java`, `verify/RulesGenRunner.java` (the kept support class's fallbacks), `ContractTestsTest.java`,
  the env guide 6.2.1 (en, ko), `elasticore-template-ddd-mybatis`'s shop `env.yml` and `entity.yml`, `elasticore-template-kt`'s
  shop `env.yml`, `ticket.yml`, `ShopContractSupport.kt` and the regenerated `schema.yml`, `db/schema-*.sql` and `shop.graphqls`.
