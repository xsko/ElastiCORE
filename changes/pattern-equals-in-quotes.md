### Bug Fixes (DSL loader - a quoted value with `=` or nested parentheses)

#### `@pattern('^(?=.*[A-Za-z])(?=.*[0-9]).+$', '...')` keeps its regular expression
- Why (issue 267): an `=` anywhere in an annotation's parameters made the loader read them as `key=value` pairs, and an
  `=` inside a quoted string counted too. The lookahead `(?=` of a password rule was split there, and the last piece
  became the value: `@Pattern(regexp = "digit'", message = "비밀번호 is not in the expected form")`. Every value was
  refused (`demo1234` too), the TypeScript validator got the same pattern, and nothing warned. A group inside a group,
  `'^((AB|CD)-[0-9]{3})$'`, was not read at all, because the annotation's parentheses were matched one level deep.
- Now: an `=` inside a quoted string - one that is closed later, so the apostrophe of `Owner's` is a letter - is part of
  the value. Parameters whose every `=` is quoted are one value, read as before (`@pattern` splits its regular
  expression and message at the first comma outside quotes). Parentheses inside a value nest two levels deep.
- Unchanged: an `=` outside quotes is a pair as in 2.x (`@pattern(regexp="...", message="...")`, `@access(read=public)`),
  a value without `=` is one value, and a single quoted value is one value. A model without a quoted `=` or a group in a
  group generates the same.
- Checked: `ParenthesesInQuotedValueTest` - the issue's model failed before (`digit'`) and passes, in the Java DTO
  (`@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$", message = "...")`) and the TypeScript client; a group in a group;
  an `=` inside double quotes; the named form, an apostrophe before a pair and a plain value read as before.
  `PatternAnnotationTest`, `QuotedParameterTest`, `StarParameterTest`, `ComplexLengthParameterTest`,
  `elcoreRulesCheck -Pstrict` and `elcoreTreeCheck`.
- Files: `AbstractModelLoader` (`equalsOutsideQuotes`, the annotation pattern), `ParenthesesInQuotedValueTest`.
