### Bug Fixes (tests - `testCode: "contract"`: a command parameter that names a row)

#### A command parameter that fills a reference is sent the key of a row the test makes, not a fixed 1
- Why (issue 295, a report on a forum: hexagonal, JPA, Java): `Post.move(boardId: long!)` fills `Post.board` (the generated move
  keeps `boardId`, the key of the reference by id), and its contract tests sent `obj("boardId", num("1"))` - a value built for a
  `long`. No board 1 exists, since each test makes its own rows: the project's move answered 404 and the administrator's test
  failed, while `newPost()` made its board with `createdId(BOARD, newBoard(), "boardId")`.
- Now a command's parameter named as the field of the entity that holds a reference's key, of its type (`boardId` of
  `board: Board`, a reference by id; the DTO's `@ref` / `@idof` field of that name otherwise) is sent the key of a row the test
  makes first, as a row that needs one makes it: `obj("boardId", createdId(BOARD, newBoard(), "boardId"))`, in the lifecycle's
  tests (the move, its refusal, the steps to it) and in those of `@access`. The builder of the row is written in the test class
  where the entity's own row does not need one (an optional reference). In a test of `@access` the row is made as the rows of
  the case are: by the administrator, or as the application itself (`asTheSystem`) where no administrator signs in. Where that
  row cannot be made (its API takes no create, say), the command's tests are left out and counted, as for a parameter no value
  can be built for. Any other parameter is sent its value as before.
- Unchanged: a model without `testCode: "contract"` generates byte-identical output; a command without such a parameter gets the
  same tests. No DSL word, no runtime library change.
- Checked: `elasticore-template-ddd`'s `forum` domain has `Section` and `Discussion`, which a MODERATOR moves to another section
  (`move: sectionId: long!`, `@access(move=MODERATOR)`). With the old generator its tests sent `num("1")`, and the three that
  make the move - `moveFromOpen`, the refusal after the step, `moveLetsInAModerator` - were aborted, the move refused; now they
  send a section of their own and pass - the forum's 59 tests, and the module's 262, all pass. `ContractTestsTest` (+1): Java and Kotlin, the builder written for an optional
  reference, the lifecycle and `@access` tests, the row made as the application where no administrator signs in.
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `verify/ContractTestSource.java`, `ContractTestsTest.java`, the env guide 6.2.1 (en, ko), `elasticore-template-ddd`'s
  `forum` `entity.yml` and its regenerated `schema.yml`, `semantic_model.yml` and `db/schema-*.sql`.
