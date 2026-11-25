### Bug Fixes (contract tests - `create=none` and rows the project makes, issue 358)

#### No contract test that can only abort for an entity nobody creates through the API; `new<Entity>Row()` for rows made the project's way
- Why: for an entity with `@access(create=none)` every generated contract test made its row with a `POST` the API always
  refuses (403): the field tests went through `POST`, the `@access` tests read, updated and deleted a row `createdId(...)`
  made first - and each aborted (129 of one application's tests). The refusal itself was not tested: no test said the
  administrator's create is refused. And a row the project's own `beforeSave` refuses (422) had no way to be made the
  project's way, so its field, `@access` and `states:` tests aborted too (92 more).
- Before and after:
  - `create=none` (every term of the create level says `none`): no test that needs a row made by a `POST` is written - the
    field tests, the `@access` tests on a row, `@invariant`, the moves of `states:`, `@Mask`, `@encrypt`, the export - and the
    summary says why and what to write: `AccessLog: its API takes no create (create=none), so no row of it is made to test its
    fields and @access with - to have them written, write protected Map<String, Object> newAccessLogRow() throws Exception in
    ForumContractSupport: an AccessLog made your way, answered as obj("logId", <its key>)`. A row of another entity that needs
    one is not built either (`it needs a Notice, which the API does not create (create=none)`), as for an `@expose` without create.
  - Each operation whose every term says `none` (`create=none`, `update=none`, `delete=none`, ...): besides the visitor and the
    caller of another role, a test that the signed-in administrator is refused - 403 `forbidden`, as `AccessPolicy` answers
    (`rule.single == NONE`; checked by running the tests).
  - `new<Entity>Row()` - optional, written by the project in its support class, never by the generator, the same pattern as
    `newSignInRow(role)`: Java `protected Map<String, Object> newNoticeRow() throws Exception`, Kotlin `protected fun
    newNoticeRow(): Map<String, Any?>`. Read from the support class on disk as `newSignInRow` is (`writesRow`; comments and other
    shapes are not read). It makes a row of that entity the project's way and answers it with its key; the test class calls it as
    the application itself (`CurrentUser.callAsSystem`, a private `noticeRow()` that checks the key is there), so `create=none`
    lets it in. Where it is written, every row of that entity a test needs is the one it makes - for any entity, create open or
    not: the rows of the `@access` tests, the moves of `states:`, the rows another entity points to. The fields are checked through
    `PUT` on it where the API takes an update not refused to all (`assertUpdated(put(NOTICE, obj("noticeId", id, "title",
    text(50))))`, then 51 characters refused 400 `Size`); `!` is not (an update keeps what it does not send) and is counted. What
    only a create of the model's row checks - a create let in, `@invariant`, where a new row starts, `@Mask`, `@encrypt`, the
    export - is counted, not written; a create refused (visitor, other role, administrator under none) still sends the model's
    row, refused before it is read. The support class gets `put` and `assertUpdated` where such a test needs them and no test of
    `@Mask` brings them, and its comment names the members used.
  - Without `new<Entity>Row()` and without `none`, the output is the one it was (the existing contract tests are unchanged).
  - The env guide's contract-test section (en, ko) documents both; `elasticore-template-ddd`'s `forum` domain gains `AccessLog`
    (create, update and delete none) and `Notice` (create=none, a moderator edits) with `newAccessLogRow()` and `newNoticeRow()` in
    `ForumContractSupport` (and `put`/`assertUpdated`, which a support class the generator no longer merges - this one has no
    header - has to be given by hand, as the summary says).
- Checked: the `forum` contract tests generated and run (`elasticore-template-ddd`, H2): with the old generator `AccessLog` 16
  tests, 12 aborted, `Notice` 15 tests, 12 aborted (the domain 123 tests, 24 aborted); with this one and the two members
  `AccessLog` 15, `Notice` 15, the domain 122 tests, 0 aborted, 0 failed - the administrator refused 403 `forbidden` on create,
  update and delete under none, the fields of `Notice` checked through PUT. `ContractTestsTest`: create=none without the member
  (nothing that aborts, the summary's hint, the refusals), with it (rows by it, PUT field tests, the support's `put`, a create-open
  entity's row replaced, another entity's reference), Kotlin, and what is read as the member; the whole class passes.
- Files: `ContractTestSource.java`, `RulesGenRunner.java`, `ContractTestsTest.java`; `elasticore-env-guide.md` and
  `ko/elasticore-env-guide.md`; `elasticore-template-ddd` `forum/model/entity.yml`, `ForumContractSupport.java`, `db/schema-*.sql`.
