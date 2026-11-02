### Bug Fixes (runtime - `checkEmpty` with `IS_NULL` / `IS_NOT_NULL`)

#### checkEmpty no longer drops a condition that takes no value
- Why: `FieldInfo.where(op, value, checkEmpty)` returns a condition that is always true when
  `checkEmpty` is on and the value is null, blank or an empty collection - a search field left
  empty adds nothing. `IS_NULL` and `IS_NOT_NULL` take no value, so theirs is null every time, and
  `Q.Order.deletedAt(Op.IS_NULL, null, true)` - "deletedAt is null" - added no condition at all and
  the query returned every row, without a word. Generated mappers are not affected: `@search(isnull)`
  writes `isNull`/`isNotNull` itself.
- What: the empty check applies to operators that take a value. `IS_NULL` and `IS_NOT_NULL` always
  write their condition; every other operator is as before.
- Checked: `FieldInfoCheckEmptyTest` - `IS_NULL`/`IS_NOT_NULL` with a null value give `isNull`/
  `isNotNull` (fails without the change: the condition was `conjunction()`), and `EQ`, `LIKE` and `IN`
  with an empty value are still dropped.
- Files: `FieldInfo.java` (`where`), `FieldInfoCheckEmptyTest.java`
