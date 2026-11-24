### Bug Fixes (tests - `testCode: "contract"`: the signed-in session under Spring Session)

#### The contract support class keeps the sign-in's cookies with its session and sends them with it
- Why (issue 295, a report on a forum: hexagonal, JPA, Spring Boot 4.1, Java, sessions in Spring Session JDBC): the support
  class kept `in.getRequest().getSession(false)` - the container's `MockHttpSession` - after the generated sign-in, and dropped
  the answer's cookies. With Spring Session (`spring-session-jdbc`, ...) its `SessionRepositoryFilter` is in front under
  `@AutoConfigureMockMvc`, and the signed-in session is the one the `SESSION` cookie the sign-in answers with names: every
  later request was a visitor, and of 186 tests 173 were aborted and 4 failed with 401 `not-signed-in`. `request(...)` could
  not carry it, as the cookies differ from caller to caller.
- Now the support class keeps the sign-in's cookies wherever it keeps the session, and sends them wherever it sends the session:
  - a `cookies` field (Java `Cookie[]`, Kotlin `Array<Cookie>`, empty as generated) that `signIn()` fills with
    `in.getResponse().getCookies()` - the model's administrator and the one `newSignInRow` makes;
  - the `Caller` of `signIn(role)` holds them too (`caller.cookies`); Java keeps the two-argument constructor, Kotlin gives the
    parameter a default, so a `Caller` a project's own code makes still compiles;
  - `perform(...)` and `sentBy(...)` add them to the request (`builder.cookie(...)`) next to the session;
  - where nobody signs in, the requests run as the application unless the session or the cookies are set - a `signIn()` of the
    project's that keeps only cookies is signed in too. The class comment says to set the session and its cookies there.
  Where the container keeps the sessions the sign-in answers with no cookie, and nothing more is sent.
- Existing projects: the support class is written once and then merged against what the generator last wrote
  (`.elasticore/generated-base.zip`). One whose edits are elsewhere - `request(...)`, `newSignInRow` - takes the new lines over
  (`MERGED(your edits kept)`); one that changed the same lines - its own cookie fix - is `KEPT` and the run says where, to take
  the change over by hand. A file the run has no base for (no cache) stays as it is, as before.
- Unchanged: a model without `testCode: "contract"` generates byte-identical output; the generated test classes are as before.
  No DSL word, no runtime library change.
- Checked: `elasticore-template-ddd`'s `forum` domain (Java, hexagonal, Member signed in through `newSignInRow`) with
  `spring-session-jdbc` on the test classpath for the run only: with the old support class 59 forum tests gave 9 passed,
  3 failed, 47 aborted - every signed-in request 401 `Sign in first`, as reported; with the new one all 59 pass, none aborted. Without Spring
  Session (the sample as committed) the module's 262 tests pass. `elasticore-template-kt`'s `ShopContractSupport.kt` (the
  sample's own, kept) updated the same way: its 237 tests pass. `ContractTestsTest` (+2): the cookies kept and sent in Java and Kotlin - the model's
  principal, `newSignInRow`, nobody signed in - and a support class an earlier version wrote, edited in `request(...)`, merged
  with them (`MERGED(your edits kept)`), or `KEPT` where its own lines meet them. `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF
  / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `verify/ContractTestSource.java`, `ContractTestsTest.java`, the env guide 6.2.1 (en, ko),
  `elasticore-template-ddd`'s `ForumContractSupport.java` and `elasticore-template-kt`'s `ShopContractSupport.kt`.
