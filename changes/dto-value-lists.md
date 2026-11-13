### Bug Fixes (DTO - lists of values and arrays)

#### A DTO carries a list of values: `List<Long>`, `List<String>`
- Why (issue 158): a DTO field `tagIds: List<Long>` or `tags: List<String>` was left out of the DTO without a word,
  in Java and in the TypeScript client, though the DSL guide's table says `List<T>` is `List<T>`. The DTO keeps a
  field whose type names a model component; a list's item, `Long` or `String`, names none, so the list was taken
  for a relation whose counterpart has no DTO - in 2.x, and so in the 2.x-compatible output, the same. `long[]`
  was an unresolved type, and dropped with a warning.
- After (3.0 code - Java, Kotlin, TypeScript): a list or set of a value type is carried as that value's Java type,
  `List<Long>`, also for the DSL's words (`List<long>`, `List<string>` are `List<Long>`, `List<String>`);
  TypeScript reads `number[]`, `string[]`. `long[]`, `double[]` and `boolean[]` are arrays beside `int[]`, wrapped
  as it is (`Long[]`), known to the rule engine only, as `Instant` and `byte[]` are. A list of a type nobody knows
  (`List<Strng>`) is a DSL warning naming the field instead of a silent drop.
- 2.x-compatible output: unchanged - the list is left out as 2.x leaves it, and the build now says so. 2.x
  generator unchanged.
- Checked: `ValueListDtoTest` (Java, Kotlin, TypeScript, 2.x-compatible); `ProductDTO` of `elasticore-template-ddd`
  (Java hexagonal) and `elasticore-template-kt` carry `relatedIds: List<Long>` and `keywords: List<string>`,
  generated, compiled and tested.
- Files: `SpecFactory`, `BaseFieldType`, `RulesGenRunner`, `RulesVerifyRunner`, the DSL guide (en, ko).
