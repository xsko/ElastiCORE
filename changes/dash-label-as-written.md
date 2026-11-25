### Bug Fixes (DSL loading - the `--` label)

#### A `--` label holding `=` is the whole text, not key=value pairs (#345)
- Why: `AbstractModelLoader.loadAnnotationMap` wrote the `--` text into the line as `@label(<text>)` and parsed it as
  annotation parameters, so a label with `=` - a formula, `1 = ...` - was read as key=value pairs. A label such as
  `-- shortest policy period in calendar months (1 = to the same day of the next month, or that month's last day)`
  became `day)` in the entity Javadoc, `schema.yml`, the DDL column comment and the migration, without a warning. The
  DSL guide already said the opposite: `-- sum = price * qty` is a label.
- Before and after: the `--` text (and, after ` | `, the description) is now taken as written, whatever it holds -
  `=`, `,`, parentheses - and never parsed as parameters. It still follows the annotations written on the line, as a
  sibling of an `@label` or `@desc` written there, so a hand-written `@label(...)` keeps its reading (translations, or
  the 2.x key=value pairs with a `DSL_WARN`). One code path reads every `--` text of the YAML loaders: entity, DTO,
  enumeration and port fields, and `meta:` lines - in every mode (JPA, MyBatis, MongoDB). A label that had no `=` and
  balanced parentheses reads as before; one with `=`, or an unbalanced or three-deep parenthesis, now keeps its whole
  text (a bug fix). None of the template and sample blueprints has such a label, so their output does not change.
- Checked: `DashLabelAsWrittenTest` (field, formula, parentheses, the description after ` | `, `meta:` lines, a
  hand-written `@label` beside it - all fail without the fix), `DdlMigrationTest.aLabelWithEqualsIsTheWholeTextEverywhere`
  (JPA and MyBatis: the H2 schema comment, the Flyway migration and the entity), `LabelTranslationParseTest`,
  `elasticore-template` regenerated with the 2.x generator (no file changes), `elcoreRulesCheck -Pstrict`
  (158 MATCH / 0 DIFF / 2 ACCEPTED), `elcoreTreeCheck` (0 / 0 / 0).
- Files: `AbstractModelLoader.java`, `LabelTranslations.java` (comment), `elasticore-dsl-guide.md` (en, ko).
