### Changes (conditions - `==`, `!=`, `>=`, `<=`)

#### A condition compares with `==`, `!=`, `>=` and `<=`
- Why: the annotation parser read any `=` in the arguments as the start of a key=value pair, so `@invariant(total >= 0, ...)`
  lost its condition and `@count` had to say `status is VISIBLE`.
- What: in `@count`, `@invariant` and `@specification` an `=` that is part of `==`, `>=`, `<=` or `!=` is a comparison,
  and the arguments are one value. `@invariant` and `@specification` gain `>=`, `<=`, `==` and `!=` on numbers and on
  `.size` (`@invariant(totalAmount >= 0, '...')`). Kotlin compares for equality with `compareTo` (`total!!.compareTo(7)
  == 0`) except an `Int` against a whole number - `==` between a `Long` and an `Int` literal does not compile -
  TypeScript with `===` / `!==`. `@count` (unreleased) says `status == VISIBLE and pinned == false` instead of `is`.
- Takes effect: an `@invariant` or `@specification` written with `>=`, `<=`, `==` or `!=` was dropped without a word
  (the guide said so); it is now generated - an invariant refuses a write that breaks it (422).
- Unchanged: every other annotation is read as before (`@search(>=)` included); a single `=` is still a key=value pair.
- Not done: a quote escaped inside a condition's message (`'can\'t'`) is not read as one; a `'text'` value of `@count`
  that holds `==`.
- Checked: `HasAssignmentTest` (the parser, `@search` unchanged), `ConditionComparisonTest` (`@invariant` and
  `@specification` in Java, Kotlin - a `Long` and a `Double` with `compareTo`, an `Int` and a count as written - and
  TypeScript), `CountTest`, the samples' `@count` (`BoardCountTest`, template-kt `OnDeleteTest`), the whole
  `elasticore-base` suite, `elcoreRulesCheck -Pstrict`, `elcoreTreeCheck`.
- Files: `AbstractModelLoader.java`, `DddCondition.java`, `SpecFactory.java`, `elasticore-annotation-guide.md`,
  `elasticore-rule-guide.md`, `elasticore-dsl-guide.md` (en, ko), the samples' blueprints.
