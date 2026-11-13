### New Features (derived values - `@count`)

#### `@count(Comment.post[, status is VISIBLE])`: a count kept on a row of the rows that point to it
- Why (issue 179): a post's `commentCount` and `likeCount`, where a comment and a like are roots of their own, were
  counted again by hand in the same transaction as every change of a comment or a like. `@compute` reaches only the
  aggregate's own members.
- What: on the counting entity, `commentCount: long @count(Comment.post, status is VISIBLE)`. The counted entity's
  CoreService touches the count at each write of a row - a save, an update (the row it pointed to before and the one
  it points to now), a delete by key and by search, an update by condition, a command that moves its state - and a
  row an `@onDelete(cascade)` removes says so too. The runtime's new `io.elasticore.springboot3.domain.Counts`
  counts each touched row again, once, just before the transaction commits:
  `update Post x set x.commentCount = (select count(c) from Comment c where c.postId = x.postId and ...) where
  x.postId = :id`. Counting again rather than adding one keeps it right when two writes cross.
- The field is the model's: `@readonly`, `@updatable(false)` and `@default(0)` are implied - no request writes it,
  a save of the counting row does not write it back, a new row starts at 0. A soft-deleted row is not counted.
- Form: the condition says `is` (`status is VISIBLE and pinned is false`) - an `=` in an annotation reads as a
  key=value pair and drops the rest. A field is compared with an enum constant, `true`, `false`, a number or
  `'text'`.
- Misuse stops the build: no `Entity.reference`, a reference that does not point to the counting entity, a value
  the field has not, a count that is not a `long` or an `int`, a counting entity without a single key; MyBatis and
  the 2.x-compatible output.
- Not done: a row written around the generated service is not counted until a write through it touches the count;
  the eventually consistent reading through a domain event the issue mentions for later.
- Unchanged: a model without `@count` generates the same.
- Samples: `elasticore-template-ddd` board - `Post.commentCount` (visible comments: `Comment` gains a `status`
  with `hide` / `restore`) and `Post.likeCount` (new root `PostLike`); its DDL files gain the columns and the
  table. `elasticore-template-kt` content - `Article.commentCount @count(Comment.article, approved is true)`; its DDL
  files gain the column.
- Checked:
  - `CountTest` (generator): the JPQL (enum constant, boolean, soft delete), each hook in the Java CoreService
    (save, update, update by condition, delete by key, delete by search, the command), the Kotlin method and hooks,
    the layered key through the relation, the column `updatable = false` with `0L`, the DTO's read-only property,
    the generated Kotlin test's query stub, the MyBatis and 2.x-compatible refusals, four misuses.
  - `BoardCountTest` (template-ddd, HTTP on H2): three comments and a like counted; a hidden comment leaves the
    count and a restored one comes back; a deleted comment takes its reply out of the count with it (`@onDelete`);
    a like taken back; a request sending `commentCount` writes nothing.
  - template-kt `OnDeleteTest.anArticleCountsItsApprovedComments` (Kotlin on the generated schema): an approved
    comment counted and an unapproved one not; a deleted one leaves the count. The whole module's 180 tests pass.
- Files: `Counts.java` (springboot3, shared with springboot4), `CountRules.java` (new), `EntitySpec.java`,
  `SpecFactory.java` (`countsOf`, `prepareCounts`), `RulesGenRunner.java`, `RuleCatalog.java`,
  `DddCommandRules.java` (the command's hook), `OnDeleteRules.java` (a cascaded row's counts, the shared test stub),
  the tests above, the samples' blueprints and DDL files, `elasticore-annotation-guide.md` (en, ko).
