### Bug Fixes (`@search` - like)

#### A contains, prefix or suffix search looks for the value as written
- Why (issue 159): `@search(like)` and the prefix / suffix forms put the caller's value into the LIKE pattern as it
  was, so its `%` and `_` were wildcards: `100%` found every row with `100` in it, `kim_t` found `kimat` too, and a
  value of `%_%_%_%` was a pattern the database had to work through. The guide calls `like` a contains search.
- After: in the 3.0 code (JPA, Java and Kotlin) and the MyBatis search, the value's own `%`, `_` and the escape
  character are escaped with `!`, and the LIKE says `ESCAPE '!'` - every database reads that escape, and the MyBatis
  repository queries (`Containing`, `StartingWith`) already did so (`Rows.like`). The JPA mapper gets a `likeText`
  helper; the MyBatis search binds through `Rows.like`.
- Unchanged: `auto` (a string `@search` without a condition) is a like only when the value holds a `%`, so there the
  caller's `%` stays a pattern, as documented. The 2.x-compatible output and the 2.x generator are unchanged
  (`elcoreRulesCheck`, `elcoreTreeCheck`).
- Checked: the pet store's `Category.name @search(like)` finds `Like 100% wool` and not `Like 1000 wool`, and `a_b`
  not `axb`, on H2 and - in CI - on Oracle, PostgreSQL and SQL Server (`LikeSearchCheck`); the MyBatis shop's like
  and `_%` searches the same (`ShopApi.everySearchConditionOfTheModelNarrowsTheRows`, H2 and the three databases).
- Files: `MapperSpecRules`, `MyBatisMapperRules`, `v3/mapper.tmpl`, `kt/mapper.tmpl`, the annotation guide (en, ko).
