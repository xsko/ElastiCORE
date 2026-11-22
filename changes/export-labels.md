### New Features (REST - an export writes an enumeration by its label: `labels` in the export's body)

#### `POST .../export` takes `"labels": ["status"]` or `"labels": "*"` beside `columns` and `headers`
- Why (issue 289): the export of issue 271 writes an enumeration as the response's JSON does, by its code (`PLC`) - a file a
  person reads wants the label (`Placed`, `주문됨`), in the language the column titles are already in.
- Now:
  - The body of `POST /api/<domain>/<entity>/export` has `labels`: the enumeration columns written by their label in the
    request's language, not their code; `"*"` (or `["*"]`) for every enumeration column of the export. A field it does not
    list is written by its code, as before. No DSL word: it is the request's choice, like `columns` and `headers`.
  - Which label: the label bundle's file of the request's language (`Enum.CONSTANT`, from the `label_<lang>` column, or every
    label column in the `labelLanguage` file - the same lookup as the titles, `_en` for English); where that file has none,
    the bundle's default file (the label column); where the bundle does not hold the enumeration (no `label_<lang>` column, no
    bundle at all), the label column the enumeration itself holds - its `label`, `name`, `desc`, `description` or `title`
    getter that is not the code; with no label at all, the code. Unlike a refusal (issue 274), which never reads the default
    file, the label column is the wanted fallback here.
  - A name that is not a column of the export (`columns`, else the DTO's) or not an enumeration: 400 `export.labels`, naming
    it and the columns it may name, before the search runs - as a wrong name in `columns` is refused.
  - `.xlsx`: a label is a text cell. CSV: the injection guard applies to it as to any text.
  - The runtime reads the constant from the row itself (the DTO's property), so the code -> constant mapping needs no
    generated code: every mode, language and architecture the export is in. `ErrorMessages.labelOrDefault(key)`: a label
    in the request's language, else the label bundle's default file.
  - The TypeScript client's `ExportRequest` (`apiSupport.ts`) gets `labels?: string[] | '*'`.
- Unchanged: a body without `labels` (or with an empty list) writes byte for byte what it wrote. The generated Java and
  Kotlin code is the same; only `apiSupport.ts` of a run with an export gains the one member (a model without `export`
  generates byte-identical output). Refusals still never read the default label file.
- Limits: an enumeration whose label column is marked `@label` under another name, in a domain whose bundle does not hold
  it (no `label_<lang>` column, no `labelLanguage`), is read by the getter names above, which may find another column or
  none (then the code). An enumeration of another domain is labelled by the exporting domain's bundle (which does not hold
  it), so by its own label column.
- Checked: `SearchExportTest` (runtime, on Jackson 2 in elasticore-springboot3 and Jackson 3 in elasticore-springboot4: no
  `labels` and an empty list give the codes as before; `["stage"]` in English - `label_en` for OPEN, the default file's label
  column for LOST, which has no `label_en` - in Korean and in a language without a label file; `"*"` with an enumeration the
  bundle does not hold (its own label column) and one with no label (its code), and `"*"` within `columns`; 400
  `export.labels` for a name not in `columns`, an unknown name and a text field, also for a workbook; an .xlsx with the labels
  as string cells; the body read with `"labels": "*"` and with a list). `RefusalLabelsTest` still says `LOST` in English, now
  that the test bundle's default file holds the constants. `SearchExportGenerationTest` (the `labels` member of
  `ExportRequest`). Over HTTP: the pet store (JPA, hexagonal, Boot 4, Jackson 3) `ExportTest` - a customer's orders by
  `labels: ["status"]` with `Accept-Language: ko` are `주문됨` / `배송됨` under the title `주문 상태`, with `"*"` and `en`
  `Placed` / `Shipped` (OrderStatus has no `label_en`: its label column), without `labels` `PLC` / `SHP`, 400 for `orderId`
  and for `total`; `elasticore-template-kt` (Kotlin, JPA, ddd, no label bundle) `ShopApiContractTest` - `"*"` writes
  `Open,Unbilled`, `["billing"]` `OPN,Unbilled`, 400 for `subject`. The generated TypeScript type-checked and run
  (`elasticore-template-kt` `elcore3tsTest -Pstrict`, the new member in its `apiSupport.ts`). `elcoreRulesCheck -Pstrict`
  158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: the runtime's `SearchExport`, `ExportColumns`, `JsonRows` (Jackson 2 and Jackson 3), `ErrorMessages`;
  `TypeScriptApiRules`; tests `SearchExportTest`, the test bundle `i18n/crm-labels.properties`, `SearchExportGenerationTest`,
  the pet store's `ExportTest`, `elasticore-template-kt` `ShopApiContractTest`; the annotation and env guides (en, ko).
