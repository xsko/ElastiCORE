### Changes (conditions - `==`, `>=`, `<=`)

#### A condition compares with `==`, `>=` and `<=`
- Why: the annotation parser read any `=` in the arguments as the start of a key=value pair, so `@invariant(total >= 0, ...)`
  lost its condition and `@count` had to say `status is VISIBLE`.
- What: in `@count`, `@invariant` and `@specification` an `=` that is part of `==`, `>=`, `<=` or `!=` is a comparison,
  and the arguments are one value. `@invariant` and `@specification` gain `>=`, `<=` and `==` on numbers and on `.size`
  (`@invariant(totalAmount >= 0, '...')`); Kotlin compares a `Double`/`Float` for equality with `compareTo`, TypeScript
  with `===`. `@count` (unreleased) says `status == VISIBLE and pinned == false` instead of `is`.
- Unchanged: every other annotation is read as before (`@search(>=)` included); a single `=` is still a key=value pair.
- Checked: `ConditionComparisonTest` (the parser, `@invariant` in Java, Kotlin and TypeScript, `@specification`),
  `CountTest`, the samples' `@count` (`BoardCountTest`, template-kt `OnDeleteTest`), `elcoreRulesCheck -Pstrict`,
  `elcoreTreeCheck`.
- Files: `AbstractModelLoader.java`, `DddCondition.java`, `SpecFactory.java`, `elasticore-annotation-guide.md` (en, ko),
  the samples' blueprints.
