### Bug Fixes (rule API - the blind index of an encrypted field)

#### `FieldSpec.blindIndexOf()` tells a team rule which field is a blind index, and of what
- Why (issue 290): with `email: string(100) @encrypt @s(eq)` the generator adds the field `emailBidx` (a
  `string(22)`), and a team rule walking `EntitySpec.fields()` or `dtoFields()` saw it as a declared field. The only
  way to tell was the internal mark `EncryptRules.INDEX_OF`, which the rule guide does not name.
- Now: `FieldSpec.blindIndexOf()` answers, for the blind index of an `@encrypt @search(eq)` field, the name of the
  field it indexes (`emailBidx` -> `email`, `@encrypt(digits)` too), and `null` for every other field. The rules that
  read the mark through `FieldSpec` (`EncryptRules.indexes`, `EncryptRules.indexOf`, the service test's sample data)
  ask it now. The rule-dev guide's `FieldSpec` table names it, with the line that leaves such a field out of a screen
  model: `if (f.blindIndexOf() != null) continue;`.
- Unchanged: `fields()`, `allFields()` and `dtoFields()` return what they did; the DTO and the Ossie export leave the
  index out as before, by the same mark on the model's `Field` (they do not read `FieldSpec`); generated code does not
  change.
- Checked: `BlindIndexOfTest` - a team rule (`com.acme.probe.ScreenFieldRules`) in a generated project, Java and
  Kotlin, sees `emailBidx->email phoneBidx->phone` (`phone` is `@encrypt(digits)`) in `fields()` and lists the
  declared fields alone; a declared field answers `null`. `EncryptFieldTest`, `EncryptFieldMarkTest`.
  `elcoreRulesCheck -Pstrict` (158 MATCH / 0 DIFF / 2 ACCEPTED) and `elcoreTreeCheck` (0 differ / 0 missing /
  0 extra); `elasticore-template-ddd`, `-kt` and `-mybatis` (which use `@encrypt`) regenerate with no change.
- Files: `FieldSpec`, `EncryptRules`, `ServiceTestBaseRules`, `BlindIndexOfTest`, `ScreenFieldRules` and its
  templates (test), the rule-dev guide's `FieldSpec` table (en, ko).
