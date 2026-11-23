### New Features (tests - `testCode: "contract"`: callers the project makes)

#### The contract tests sign in callers a project's own code makes: `newSignInRow(role)` in the support class
- Why (issue 295, a report on a forum: hexagonal, JPA, Spring Boot 4.1, Java, 12 entities): a sign-in entity whose API takes
  no create - sign-up is the project's own, a mailed code, a social sign-in - left no caller to sign in, so no test of `@access`
  was written at all (`Post @access: not tested - no caller can be signed in (Member (signing in): its API takes no create, ...)`).
  The support class said "sign in your way there", but editing `signIn()` brought nothing back: the tests were left out when
  the code was generated. The project's own tests signed in in two steps - a member saved through its service as the system,
  then the generated sign-in - and only the first step is the project's.
- Now a project may write that first step in its support class, at the end of the class:
  - Java `protected Map<String, Object> newSignInRow(String role) throws Exception`, Kotlin
    `protected fun newSignInRow(role: String?): Map<String, Any?>`: the row of a caller of that role made the project's way,
    answered as its login, its plain password and its key (`obj("email", email, "password", "Secret-1", "memberId", id)`).
    `role` is the role field's value as a request sends it (`null`: a caller of no role); it runs as the application itself
    (`CurrentUser.runAsSystem`, in the generated `signInRow`), which checks that the three are answered.
  - The run reads the support class on disk - the project's - before it plans the tests (the generated class never declares
    the member, so the merge keeps it, or keeps the file as it is). Where it is declared as the generated sign-in calls it, the
    callers are made by it: `signIn()` signs in the administrator (`newSignInRow` of the `adminRole`) and `signIn(role)` a
    caller of each role an entry names, and the owner of a row, through the generated sign-in; the tests of `@access` are
    written as for a principal the model builds. Another signature, or one in a comment, is not read as it.
  - Preferred to the model's row whenever it is written, not only where the model cannot build one: a project writes it
    because its sign-up makes a caller otherwise than the API's create (more fields, a check of its own, a verified flag), and
    a row the project's code would not make tests nothing the application does.
  - Not generated and not an interface: without it the build is as it was. Where no principal can be built from the model and
    the run has a sign-in to sign one in through, the summary's line of the principal says what to write
    (`Member (signing in): its API takes no create, so no administrator is made to sign in as - to sign callers in, write
    protected Map<String, Object> newSignInRow(String role) throws Exception in ForumContractSupport: a Member of that role
    made your way, answered as obj("email", <its login>, "password", <its plain password>, "memberId", <its key>)`), and the
    support class's comment shows it with a short example. The tests still say what each entry expects (allowed, 401, 403, 404).
- And where nobody can be signed in - no `@signin` in the run, or no principal built - the rows of an `@owner` entity are made by
  the application itself, without their owner (the server sets `@owner` from who is calling): a project's own code that reads
  the owner answered 500, and a lifecycle test failed rather than aborted. Now the summary counts each such entity
  (`Verification: its rows are made without their owner (@owner(memberId)) - nobody can be signed in, so the application itself
  makes them; a 5xx answer on such a row aborts the test rather than fails it`), the support class lists them, and its
  `withoutOwner` aborts a test where a row it needs, a step or the move - a request on such an entity's path - answers 5xx, or
  lets out what the application threw (MockMvc rethrows it rather than answering 500: `thrownWithoutOwner`). Any other answer
  is checked as before: a 409 where a move is allowed, a 2xx where it is refused, still fails. This changes the support class of
  a run with no `@signin` and an `@owner` entity too, on purpose: its rows have no owner either. The trade-off: a 5xx on such a
  row that is not about the owner aborts too, with the answer in the reason.
- Unchanged: a model without `testCode: "contract"` generates byte-identical output; a domain whose principal the model builds,
  or without an `@owner` entity, without `newSignInRow`, gets the same support and test classes as before. No runtime library
  change, no DSL word.
- Checked, over HTTP: `elasticore-template-ddd` (Java, hexagonal, JPA, Spring Boot 3) has a new `forum` domain shaped as the
  report: `Member @signin` with no create in its API, `Topic` and `Verification` with `@owner` and `@access` (roles, `owner`,
  a lifecycle a MODERATOR moves). Without the member the run said `contract tests: 85 written, 9 skipped (... Member (signing in):
  ... - to sign callers in, write ... newSignInRow ...; Topic @access: not tested ...; Verification: its rows are made without
  their owner ...)` and wrote 11 forum tests; with `ForumContractSupport.newSignInRow` (a `MemberService.save`, the role from
  `MemberRole.fromCode`) the support class is `MERGED(your edits kept)`, the run says `115 written, 4 skipped`, and the 41 forum
  tests pass - 30 of `@access` - in a suite of 244 (203 before). Broken on purpose, then restored, on that sample: with
  `update=user` for Topic and `approve=user` for Verification in the generated services, exactly
  `updateRefusesAUserOnAnothersRow` (200, not 404) and `approveRefusesAUser` (200, not 403) failed. Without the member (rows
  without their owner), an `approve` override that throws on a missing owner made four lifecycle tests fail before; they are
  now aborted with `the request failed on a row made without its owner (Verification @owner(memberId) - nobody is signed in):
  ... The given id must not be null`; with the moves broken instead (approve allowed from APPROVED, reject only from
  REJECTED) on the same owner-less rows, `approveIsRefusedWhenStatusIsApproved` (200, not 409), `rejectFromPending` and the two
  tests whose step rejects still failed. Kotlin, `elasticore-template-kt` switched for the run only, then restored: with
  `newSignInRow` (an `AgentService.save`) its 80 contract tests pass; with Agent's create taken out of its API too, Ticket's 36
  (`@access` among them) pass; without the member, the owner-less Kotlin helpers compile and its 35 pass.
  `ContractTestsTest` (+7): the hint in Java and Kotlin (and none without `@signin`), the member written and merged (Java,
  Kotlin), preferred where the model builds a row, how the declaration is read, the owner-less rows said and their 5xx
  handled, the example's login. Unchanged suites: `elasticore-template-kt` 237, the pet store 199. `elcoreRulesCheck -Pstrict`
  158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `verify/ContractTestSource.java`, `verify/RulesGenRunner.java` (the support class read before the plan),
  `ContractTestsTest.java`, the env guide 6.2.1 (en, ko), `elasticore-template-ddd`'s new `forum` domain (`env.yml`,
  `entity.yml`), its `ForumContractSupport.java`, `DddSampleApp.java`, `build.gradle` and `.gitignore`, and its regenerated
  `context-map.md`, `schema.yml`, `semantic_model.yml` and `db/schema-*.sql` (the forum's tables added).
