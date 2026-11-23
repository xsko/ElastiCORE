### New Features (tests - `testCode: "contract"`: `@access`)

#### The contract tests check who each `@access` entry lets in and whom it refuses
- Why (issue 295, phase 3): the contract tests signed in an administrator and checked what the model declares of the data;
  who may do what - `@access` with its roles, `|`, `&` and `owner` - was tested by hand or not at all.
- Now:
  - Each operation the API serves - read, search, create, update, delete, each command - is tested at the level its entry gives
    it, else `*`, else `admin` (the default). A caller each term lets in passes (2xx; a search, narrowed to the test's row by
    its key, finds it). A visitor is refused 401 `not-signed-in`; a caller of a role no term lets in 403 `forbidden` - on a row
    of theirs where `owner` is a word, so the role is what refuses them; where `owner` is a word, a caller each owner term
    lets in, on another's row, 404 `not-found` (a search does not find the row). So `REP&owner` refuses another role and a REP
    on another's row, and `owner|MANAGER` lets in either. The expected answer is read as the generated code gives it: the
    service asks the level, then the row's owner; the API reads a row before it deletes it, so a delete asks the read first;
    a visitor's create of a row keyed by its owner (`@owner` the key the request chooses) is refused 401, whatever create says.
    A refusal is checked by its status, and by its problem `code` where the answer has one - never by message.
  - The support class signs in principals of a role: `signIn(role)` makes one as the application itself, with the role in the
    `@signin` entity's role field (its JSON value - `"M"` for `UserRole.MANAGER` written `@json(code)`, a string role as
    written), signs it in through the generated sign-in and returns a `Caller` (its key and session); `sentBy(caller, ...)`
    sends a request as it, or as a visitor for `null`; `assertAllowed`, `assertDenied`, `assertFinds`, `assertMisses` check
    the answer. `signIn()` - the administrator the rows are made by - is as it was.
  - Each test signs in its own principals and makes its own rows. A row of a caller's is made by the administrator with the
    owner sent - as the application itself (`asTheSystem`, added to the support class then) where the principal has no role
    to be the administrator by, so an admin-only create does not abort the test - or, where the server sets `@owner`, by the
    caller, who owns what they create; the `@signin` entity's own row is the caller's.
  - The build never breaks: after the support class is written or merged, the run reads the file it left. Where it lacks a
    member a test class calls (`signIn(String)`, `sentBy`, `Caller`, `assertAllowed` ... - a class of the project's a merge
    kept as it was), the tests of `@access` are not written - the class keeps its other tests where the kept class has what
    they call, else it is not written - and the summary says so: `Shop: ShopContractSupport has no signIn(String) - add the
    members from build/elasticore/contract/ShopContractSupport.java, the version this run generated; ...`. A delete or a command a lifecycle guards is
    given after the commands that bring a new row to where it may be (as the administrator).
  - Let in, a request refused by `@access` (401, 403, a 404 `not-found`) or failing (5xx) fails the test; refused by another
    rule of the application's own - a reference to a row it needs, say - it is aborted with the reason.
  - Counted as not tested, with why, in the summary and in the support class's comment: no principal to sign in (no
    `@signin`, or it cannot be built), a role the role field cannot hold, a term of two roles (`REP&MANAGER` - a session holds
    one), `owner` where `@owner` names no field, an entry not read (`read=owners`), a row of a caller's that cannot be made,
    a delete or a command no sequence of commands brings a new row to, a search that takes no key (it could miss the test's
    row by its page alone), a composite key (only create is tested), a visitor's create that clears a required `@owner`.
- Unchanged: a model without `testCode: "contract"` generates byte-identical output; a domain without `@access` gets the same
  contract tests and support class as before. `testCode: "true"` is as it was. `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF /
  2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra. No runtime library change, no DSL word.
- Samples: `elasticore-template-kt`'s Ticket says `cancel=owner|LEAD, bill=LEAD&owner` (a string role: the agent's `grade`), so
  the Kotlin sample has a role expression; its edited `ShopContractSupport.kt` takes the new members. The pet store already has
  `read=owner|MANAGER`, `delete=MANAGER&owner`; its edited `PurchaseOrderContractTest` takes the new tests (merged, the
  delivery address kept).
- Checked, over HTTP: the pet store (Spring Boot 4, hexagonal, JPA) 122 contract tests (45 before), its whole suite 193.
  Weakening three generated checks of `PurchaseOrderCoreService` - the search's owner filter, the read's owner check,
  `delete=MANAGER&owner` as `delete=owner` - failed exactly `searchHidesAnothersRowFromAUser`,
  `readRefusesAUserOnAnothersRow` and `deleteRefusesAUserOnTheirRow` (409 instead of 403); restored.
  `elasticore-template-kt` (Kotlin, hexagonal) 73 contract tests (34 of Ticket), its whole suite 228. Switched on for the run
  only: the MyBatis pet store (layered, Spring Boot 4) 106 - with its order builder given an item, which its application
  requires and its model does not (without it those tests are aborted); the `club` domains of `elasticore-template-mongo` (44)
  and `-kt-mongo` (71) on MongoDB 7.0.12 through `ELASTICORE_IT_MONGODB_URI`. The Kotlin MongoDB run found that a principal
  with no role field signs in no administrator, so a row it made with the owner sent was its own: such rows are now made as
  the application itself; rerun with that, the two `club` domains pass again (44, 71), the rows through `asTheSystem`.
  Whole suites of both MongoDB samples pass on the server (27 contract tests each, of `*=public`). `elasticore-template-ddd`
  (70) and `-ddd-mybatis` (45) contract tests pass, unchanged. `ContractTestsTest` (+11): a support class kept without the new members (no test of `@access`
  written, the others kept, the version generated beside the build, the summary), each case of a model with roles, `|`,
  `&` and `owner` in Java and Kotlin, the sign-in entity's own row, a lifecycle-guarded delete and a command, `none` and `*`,
  a visitor's create keyed by its owner, the skips (no `@signin`, two roles, an entry not read, no `@owner`, a role too long
  for its field, no role field, a required owner a visitor's create clears), and that a model with `@access` adds only test
  files in JPA, Kotlin, MyBatis and MongoDB.
- Files: `verify/ContractTestSource.java`, `ContractTestsTest.java`, the env guide 6.2.1 and the annotation guide's `@access`
  (en, ko), `elasticore-template-kt`'s `ticket.yml` and `ShopContractSupport.kt`, the pet store's `PurchaseOrderContractTest.java`.
