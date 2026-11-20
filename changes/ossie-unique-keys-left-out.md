### Bug Fixes (Ossie export - a unique key over a column left out)

#### A unique key that holds a column the export leaves out is not written to `unique_keys`
- Why: the export leaves `@password`, `@writeonly` and `@sensitive` columns out of a dataset's `fields` and lists them
  in `omitted_columns`, but `unique_keys` still named them: `email: string(100)! @unique @sensitive` wrote
  `unique_keys: [[email]]`, and `@Index(columnList="provider,provider_user_id", unique="true")` with
  `providerUserId: string(100)! @sensitive` wrote `[[provider, provider_user_id]]`. A `@password @unique` or
  `@writeonly @unique` column, and an `@Index` over an embedded value's `@sensitive` column, did the same. Ossie reads a
  key name that is not a field of the dataset as an error, and the key told a reader the left-out column exists and is
  unique (issue 245).
- Now: a unique key (`@unique` or `@Index(..., unique="true")`) that holds any column the field list left out is not
  written, as a whole - its other columns alone are another key, which is not unique - and a `[ossie]` warning names
  the key and the column. The decision is the field list's own: a column counts as left out when the field list
  recorded it in `omitted_columns` (names compared ignoring case). The dataset's other unique keys are written as
  before.
- Unchanged: a dataset with no left-out column writes the same unique keys. The committed exports of
  `elasticore-sample-petstore`, `elasticore-template-ddd` and `elasticore-template-ddd-mybatis`, regenerated with the
  fix, are unchanged.
- Checked: `OssieSensitiveTest` - the new test failed before the fix (expected `[[code]]` but was `[[email], [code]]`,
  and the export it printed held the key in each case above) and passes after it; the same model without the marks
  writes every key. The Ossie tests,
  `GuideTranslationTest`, `elcoreRulesCheck -Pstrict` and `elcoreTreeCheck`.
- Files: `OssieSemanticModelExporter`, `OssieSensitiveTest`, annotation guide and rule guide (en, ko).
