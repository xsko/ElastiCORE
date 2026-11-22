### New Features (tests - `testCode: "contract"`: lookahead patterns, the rest of the Bean Validation marks)

#### The contract tests read a password's `@pattern`, and test `@notblank`, `@email`, `@past` and `@future`
- Why (issue 295, the phase 2 follow-up): a `@pattern` with lookaheads - the usual password rule - was no sample, so an entity
  with such a password got no tests, and as the sign-in's principal it left every test running as the application. The other
  Bean Validation marks the DTO writes had no test, and a `@past` / `@future` field's value was fixed (2000, 2100).
- Now:
  - `@pattern` beginning with `^` and lookaheads `(?=.*X)` or `(?=.*?X)` - X one class, one escape (`\d`, `\w`, `\S` ...) or one
    character: the rest is sampled as before and a character of each X put where the rest takes it, within the field's length
    and `@length`; the whole value is checked with `java.util.regex`. A value varied per row varies a position that holds a
    lookahead only within it. The refused value matches the rest and misses one lookahead; where none can be built
    (`^(?=.*\d)\d+$`) the pattern test is not written and is counted. Any other lookaround - a negative lookahead, a
    lookbehind, a lookahead further in, one without `^` - stays a counted skip. Covered: `^(?=.*[A-Za-z])(?=.*[0-9]).+$` with
    `string(255)! @length(min=8)` and `^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$`.
  - Each mark the DTO writes on what a request sends gets one refusal, by status, field and code, after its valid value
    passes: `@notblank` - `"   "`, 400 `NotBlank`; `@email` - the address without its `@`, 400 `Email`; `@past` /
    `@pastorpresent` - ahead of now, 400 `Past` / `PastOrPresent`; `@future` / `@futureorpresent` - behind now, 400 `Future` /
    `FutureOrPresent`. The dates are read from the clock when the test runs - a year ago or ahead for the valid value; for
    the refused one two days on a date (a midnight passing during the request leaves it past now), a day on a datetime or a
    timestamp - so no time zone moves them across now; on a date, a datetime and a timestamp (Instant), in the DTO's
    format. A time of day is counted as skipped (past or future by the hour of the run), and no value of one is
    made: a required one leaves its row counted as skipped. A mark the DTO does not write is not
    tested: `@notblank` on a field the server sets (`@readonly`), which `v3-dto-system-managed` takes off; `@email` with a
    `@pattern` whose value is no address is counted.
  - Every other test's row satisfies the marks: a `@notblank` field is sent (it refuses null), a required `@past` /
    `@future` value is a year from the clock.
  - The sign-in's principal of a password with lookaheads is built: the support class makes it and signs in with the plain
    value it sent (the password is hashed on save). Where there is no sign-in, the support class's comment says why: the run
    has no `@signin`, or its principal cannot be built - with the summary's reason (`Agent (signing in): secret: ...`). A
    `@signin` entity the API does not create is now counted too.
- Fixed: `@pattern` is sampled as the DTO's `@Pattern` reads it. The DSL writes a string literal's content (`\\d`); the
  sampler read that text as the expression (a backslash and a d), so a pattern with an escape got values the DTO refused.
- Unchanged: a model without `testCode: "contract"` generates byte-identical output; the contract tests of a model without these
  marks are the same, but a `@past` / `@future` date in a row, which is now read from the clock, and the values of a
  `@pattern` with an escape (the fix above). `elcoreRulesCheck -Pstrict`
  158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra. No runtime library, no DSL word.
- Not in scope: an aggregate member's own constraints. The pet store's password takes a letter rule only
  (`^(?=.*[A-Za-z]).+$`): its demo accounts, admin/admin and user/user, are made through the validating service.
- Samples: the pet store's Customer `lastName` is `@notblank`, its `password` has `@pattern('^(?=.*[A-Za-z]).+$', ...)` (it
  already had `email @email` and `dateOfBirth date @past`); Category has `featuredUntil: datetime @future` (the column in
  `db/schema-*.sql` and the Ossie model).
- Checked: the pet store's contract tests 45 (40 before: `lastNameNotBlank`, `emailEmail`, `dateOfBirthPast`,
  `passwordPattern`, `featuredUntilFuture`), over HTTP with the administrator signed in as a Customer made with the
  pattern's password; its whole suite 116. Removing `@Email` from the generated `CustomerDTO` failed `emailEmail` (200 for the
  address without its `@`); restored. `elasticore-template-kt` contract tests 38, `-ddd-mybatis` 48, `-ddd` 74 pass.
  `ContractTestsTest` (+5): each mark in Java and Kotlin (date, datetime, timestamp, a date in `yyyyMMdd`), the row sending
  `@notblank`, no test of a mark the DTO does not write, the time of day, a lookahead the rest holds, a negative lookahead and
  an `@email` whose `@pattern` makes no address counted, the lookahead pattern's refusal; the principal with a lookahead
  password; the support class's reason (a principal that cannot be built, a `@signin` entity without create, no `@signin`);
  a reason safe in a comment. `PatternSampleTest` (new, 8): both password patterns (valid, refused, length), the lazy form and a
  literal, a varied value keeping every lookahead (2000 combinations), the impossible refusal, the other lookarounds, the
  unescaping.
- Files: `verify/ContractTestSource.java`, `verify/PatternSample.java`, `ContractTestsTest.java`, `PatternSampleTest.java`
  (new), the env guide 6.2.1 (en, ko), the pet store's `entity.yml`, `semantic_model.yml`, `db/schema-*.sql` and its site guide page `static/dsl-guide.html` (rebuilt by `guide/build_guide.py`).
