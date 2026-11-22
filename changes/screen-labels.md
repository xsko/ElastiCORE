### New Features (TypeScript client and label bundle - what a screen needs around labels)

#### `XxxMeta`, `validateXxxCodes` and an enumeration's labels in other languages as `label_en` columns
- Why (issue 268): a screen in more than one language had the field labels (`XxxFields.f.labels`) but not the type's
  own label, not the checks as codes it can say in its own words, and no way to give an enumeration's constants a label
  per language - `@label` is not written on a constant line.
- Now:
  - **`XxxMeta`** - each DTO file of the TypeScript client gets
    `export const XxxMeta: { label: string; labels: Record<string, string> } = { label: "...", labels: { en: "..." } }`
    (`Xxx` as in `XxxFields`): the DTO's own `meta:` label, or its entity's when it has none, with the translations of
    that `@label(en=...)` (`labels: {}` without). Written only when there is such a label - a `--` comment on the
    `meta:` line is one - and not when the file already imports or declares that name (a field of a type `XxxMeta`,
    which would not compile: TS2440).
  - **`validateXxxCodes(value, partial)`** beside `validateXxx`, which is unchanged: the same checks, as the server's
    `fieldErrors` codes - `{ field, code, values }[]` with `NotNull`, `Size` (`min`, `max`), `Min`/`Max` (`value`;
    `DecimalMin`/`DecimalMax` where the server's DTO carries those), `Pattern` (`regexp`), `Email`. A list with
    `@minsize` is `NotNull` when missing and `Size` (`min`) when shorter. This is an accepted change of the TypeScript
    client's output: every DTO file that has `validateXxx` gains this function and nothing else changes in it.
  - **Enumeration columns `<label column>_<language>`** (`name_en`, `label_ja`, `label_pt_BR` -> `pt-BR`): a text
    column whose suffix, read as an `@label(xx=...)` key is (`LabelTranslations.isLanguage`, `normalized`), starts with
    a two-letter ISO 639-1 language (`Locale.getISOLanguages()`). camelCase (`nameEn`), three letters or a non-ISO pair
    (`name_kor`, `label_old`, `name_cd`) and a number or date column (`name_no: int`, `desc_seq: long`) are ordinary
    columns; a text `name_no` / `name_id` is Norwegian / Indonesian. The label column is the one `XxxLabel` already
    shows. Beside at least one real translation column, a text column `<label column>_<suffix>` whose suffix is not a
    language (`name_kor` beside `name_en`, likely a typo) is a `DSL_WARN` naming the enumeration and the column; an
    enumeration without a translation column says nothing (`name_short`, `name_kana`). The TypeScript client writes
    `export const XxxLabels: Record<Xxx, Record<string, string>> = { [Xxx.CODE]: { en: "...", "zh-TW": "..." } }` beside
    the unchanged `XxxLabel`, only for an enumeration with such columns; the label bundle gets `Enum.CONSTANT` (the
    constant's name) - the label column in the default file, each translation in its language's file - and is now
    written also when such columns are the only translations of the model. The shared reading is
    `EnumLabelColumns` (label column, translation columns, values per constant), which the Ossie export reads too
    (`ossie-code-labels.md`);
    rules read it as `EnumSpec.labelTranslations()`.
- On by default, as agreed on the issue (the owner: an added function with `validateXxx` kept as it is; `XxxMeta` with
  `label` and `labels`): a model without any translation still gains `validateXxxCodes` in every DTO file that has
  `validateXxx`, and `XxxMeta` in each DTO file whose type has a meta label (a `--` comment on the `meta:` line
  included). Nothing else in its TypeScript output changes, and it writes no label bundle and no `XxxLabels`.
- Unchanged: `validateXxx`, `XxxFields`, `XxxLabel`. The columns stay columns of the enumeration
  (properties of the Java and Kotlin enums); the Ossie export and the generated messages read them too
  (`ossie-code-labels.md`, `message-labels.md`). The
  2.x-compatible output and the 2.x generator are untouched.
- Checked: `ScreenLabelsTypeScriptTest` (`XxxMeta` with and without translations, none without a label; the codes of
  each constraint - `NotNull`, `Size` with both lengths, `Min`/`Max`, `DecimalMin`/`DecimalMax`, `Pattern`, `Email`, a
  list's `NotNull` then `Size`; a shown-only field unchecked; `validateXxx` byte for byte as before; `XxxLabels` with a
  quoted `"zh-TW"` and an escaped quote; which columns are translations - ISO first subtag, text only; a `--` comment
  makes the label column). `ScreenLabelsGenerationTest` through `RulesGenRunner`: the bundle of a model whose only
  translations are enumeration columns (a line without a column has none in that language, no file for
  `label_remark`), the warning said once, the Java and Kotlin enums with the columns, `XxxLabels` and `XxxMeta` in the
  TypeScript run (a DTO without a label takes its entity's), a model with neither writes no bundle, no `XxxMeta`, no
  `XxxLabels`; look-alike columns (`name_no: int`, `name_kor`, `name_short`, `name_kana`) give no bundle, no
  `XxxLabels` and no warning; a DTO importing a type `PetMeta` declares no `PetMeta`.
  `elasticore-template-kt`: `OrderStatus` has `name_ko` and `name_zh_TW` columns; `elcore3tsVerify -Pstrict` type-checks
  the generated client with `tsc`, `compileKotlin` compiles the enum and `ShopApiContractTest` passes. Its client
  generated before and after the change: 46 of 143 files differ, and each only by the additions (45
  `validateXxxCodes`, 8 `XxxMeta`, 1 `OrderStatusLabels`). `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED,
  `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `EnumLabelColumns` (new), `LabelTranslations`, `EnumSpec`, `SpecFactory`, `LabelCatalog`, `RulesGenRunner`,
  `TypeScriptDtoFileRules`, `TypeScriptEnumRules`, `DtoBaseRules` (`decimalBound`), the two tests,
  `elasticore-template-kt` (`enum.yml`, `schema.yml`, `.gitignore`), the DSL guide, the rule guide and the rule-dev
  guide (en, ko).
