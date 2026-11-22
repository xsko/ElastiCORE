### New Features (Ossie export - an enumeration's code labels in every language)

#### The codes of an enum field carry their labels from the `label_en` columns
- Why (issue 272): an enumeration's label columns per language (issue 268: `name_en`, `label_ja`, `name_zh_TW`) reached
  the TypeScript client and the label bundle, but the Apache Ossie export read the label column only - a question in
  English about `PRS` found only the Korean `발굴`.
- Now: the label column is the one the export already picks (the first field named `label`, `name` or `desc`), and its
  translation columns are read with `EnumLabelColumns.translations` (the same rules: a text column, first subtag an
  ISO 639-1 language, `pt_BR` -> `pt-BR`).
  - The codes text - the field's `ai_context`, or its `ai_context.instructions` when the field has `@terms` - gives each
    code its other languages in brackets after its label, in language name order:
    `"Codes: PRS = 발굴 (en: Prospecting, ja: 見込み), WON = 성사 (ja: 成約), LST = 실패"`. A code with no label in a
    language leaves that language out; a code with none keeps today's text. An enum stored by ordinal reads
    `0 = SMALL (작음) (zh-TW: 小)`.
  - The field's `ELASTICORE` extension gets `code_labels` beside `codes` (whose shape is unchanged), keyed by the same
    stored values: `"code_labels": {"PRS": {"en": "Prospecting", "ja": "見込み"}, "WON": {"ja": "成約"}}` - written only
    when at least one code has such a label; a code without any is not in it.
- Unchanged: an enumeration without label columns per language exports byte for byte as before; the committed
  `semantic_model.yml` of `elasticore-sample-petstore`, `elasticore-template-ddd` and `elasticore-template-ddd-mybatis`
  (none has such columns) are regenerated identical. Which column is the label (Ossie's pick, the TypeScript pick) is
  unchanged; an enumeration whose label column is only `description` or `title` has no label column in the export,
  as before. The 2.x-compatible output and the 2.x generator are untouched.
- Checked: `OssieExportTest` through `RulesGenRunner`: the translations in the plain `ai_context` and in
  `instructions` with `@terms`, languages in name order though declared `ja` before `en`, a code missing one language
  and one missing all, an ordinal enum with `name_zh_TW` (`zh-TW`), `code_labels` present only for the enumerations
  with columns, the same model without the columns giving today's text and no `code_labels`, and the guides' two
  examples; `anEnumsCodesAreData` asserts no `code_labels`. Without the change the three tests of the new output fail; the no-change test passes either way. A test
  export with `code_labels` and the three samples' exports pass Apache Ossie main's validator
  (`validation/validate.py --schema core-spec/ossie-schema.json`, apache/ossie `83d39d6`).
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `OssieSemanticModelExporter`, `OssieExportTest`, the DSL guide ("A label in other languages", "An
  enumeration's labels in other languages", "Value types at a glance") and the rule guide (DSL -> Ossie mapping), en
  and ko.
