### Documentation (dev guide - the third argument of a `Q` condition)

#### The dev guide says what `chkEmpty` does, and where not to use it
- Why: every generated `Q` (2.x, v3 and Kotlin) has `field(Op op, Object value, boolean chkEmpty)` beside the
  two-argument form, and no guide named it. The `true` reads like "ignore case". A blank value then drops the condition
  without a word, which on a condition that must always hold - the tenant a user belongs to - returns every row.
- What: dev guide §5 (en, ko) shows the three-argument form as an optional search condition, says what counts as
  empty (null, a blank string, an empty collection), that `IS_NULL` and `IS_NOT_NULL` always apply (#72), that the
  two-argument form is `chkEmpty = false`, and not to use it for a condition that must hold.
- Checked: `FieldInfo.where(Op, Object, boolean)`; `QBaseRules`, `KotlinQRules` and `EntitySrcFilePublisher` write the
  three-argument method. `GuideTranslationTest`.
- Files: `elasticore-dev-guide.md` (en, ko).
