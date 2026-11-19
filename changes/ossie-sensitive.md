### New Features (Ossie export - `@sensitive`)

#### `@sensitive` leaves a field out of the Apache Ossie semantic model
- Why: the Ossie export is what a BI or AI tool writes its queries from. It left out `@password` and `@writeonly` columns,
  but a member's email, a sign-in provider's account id or an API key went out as ordinary fields, so a tool reading the
  model was told it may select them (issue 184).
- Now: a field marked `@sensitive` is left out of its dataset and recorded in `omitted_columns`, as a `@password` column
  is; the model's `ai_context` adds that such columns are left out (only when one is marked). On an `@Embedded` value it
  leaves out all the value's columns; on a reference, its join column and its relationship. A reference that joins on a
  `@sensitive` field (its identity field, or `@joinBy`) is not exported as a relationship either, with a warning, so the
  column does not come back that way. A metric over it is refused.
  On an `@id` or a collection it is ignored with a `[ossie]` warning: the key and the relationships to it need the column,
  and a collection has no column of its own.
- Unchanged: nothing else reads the word - DTOs, responses, access rules, DDL, the 2.x code and the Kotlin/TypeScript
  output are the same with it and without. A model without it exports byte for byte what it did.
- Checked: `OssieSensitiveTest` (export with and without the word; embedded value, reference, key, collection, metric;
  every other generated file identical with and without for v3 hexagonal Java, layered Kotlin, layered Java + TypeScript
  and the 2.x generator), the Ossie tests, `GuideCoverageTest`/`GuideCoverageKoTest`, `elcoreRulesCheck -Pstrict`
  and `elcoreTreeCheck`.
- Files: `OssieSemanticModelExporter`, annotation guide and rule guide (en, ko), `OssieSensitiveTest`, `GuideCoverageTest`.
