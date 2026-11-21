### New Features (labels - `@label(en=...)`)

#### A label in other languages: `@label(en=Unit cost, ja=単価) -- 단가`
- Why: the `--` label is in one language, and a screen, an export or an AI tool asked in another language had nothing
  to go by. The DSL is the place the labels already live, so their translations belong beside them, on the same line.
- Now: `@label` takes `language=text` pairs on a field line and on a `meta:` line (entity, DTO, enumeration). The `--`
  text stays the label; each pair is that label in another language. The key is a language tag, read in one form
  (`pt_BR`, `PT-br` -> `pt-BR`; `zh-hant-tw` -> `zh-Hant-TW`); a value runs to the next comma, spaces and apostrophes
  included, and is quoted when it holds one. `@label(단가, en=...)` without a `--` comment is the same label, and
  several `@label(...)` on a line are one. Only an `@label` the developer writes is read this way - never the one the
  loader builds from `--`, so `-- sum = price * qty` stays a label. An explicit `@label` with a key that is not a
  language tag is read as in 2.x, with a `DSL_WARN`. Read with `FieldSpec.labelTranslations()` and
  `EntitySpec.labelTranslations()`; `getAnnotationValue("label.en")` reads one.
  - **The label bundle:** beside the message bundle, `i18n/<domain>-labels.properties` (every label, as the `--` text
    says it) and one `<domain>-labels_<language>.properties` per language (`zh-TW` -> `_zh_TW`), keys `Type` and
    `Type.field`, non-ASCII escaped. Written by the v3 bundles for a domain with an API (`@expose`), in every mode and
    both languages, and merged against the cache like the message bundle.
  - **The TypeScript client:** a field's entry in `XxxFields` gets `labels: { en: "Unit cost", ... }`.
  - **The Apache Ossie export:** the translations are the field's and the dataset's `ai_context.synonyms`, after the
    `@terms` words, each once; the `description` stays the `--` label.
- Before: `@label(en=Unit cost) -- 단가` lost the label: the explicit `@label` came first and, holding only key=value
  pairs, had no value, so the `--` text, which the loader adds as a second `@label`, was a sibling nobody read - the
  entity comment, `@Comment`, `@Schema`, the TypeScript label and the Ossie description were all empty (run on the
  loader before this change: `getAnnotationValue("label")` and `getDescription()` both null).
- Unchanged: a `@label` whose keys are not all language tags is read as before (2.x key=value), and so is a plain `--`
  label. A domain without a translation writes no label bundle and the same TypeScript and Ossie output. Not
  translated: Javadoc, `@Schema(description)`, `@Comment` and column comments, and the server's error sentences (the
  message bundle translates those). An enumeration's constant lines take no `@label`.
- Left: when a language is taken out of the DSL, its `_<language>` file is removed by a run that merges against the
  cache (unedited, as any stale generated file); a run without the cache leaves it.
- Sample: the pet store's `Item` (meta label and `unitCost`) is in English and Japanese.
- Checked: `LabelTranslationParseTest` (the merge with `--` and `| description`, quoted and unquoted values,
  apostrophes, two `@label`s on a line, tags in any case, a meta line; a `--` comment with `=` and an explicit `@label`
  with a non-language key read as in 2.x), `LabelTranslationTypeScriptTest`, `LabelTranslationGenerationTest` (the
  bundle and the Ossie synonyms from a generated project; the same bundle from MyBatis layered Java, MongoDB layered
  Java and Kotlin, and JPA ddd Kotlin; no bundle and no synonyms without a translation); in the pet store
  `LabelBundleTest` (Spring's `ResourceBundleMessageSource` answers `Unit cost` in English, `単価` in Japanese and `단가`
  in Korean and French; with the system-locale fallback on, French gets the English file, as the guide warns) with
  `SemanticModelTest` and `ShopFlowTest`; the pet store's export passes Apache Ossie main's
  validator (`1b1ebb6`); `elasticore-base` tests; `elcoreRulesCheck -Pstrict` (158 / 0 / 2) and `elcoreTreeCheck`
  (0 differ / 0 missing).
- Files: `LabelTranslations`, `LabelCatalog` (new), `Annotation`, `AbstractModelLoader`, `FieldSpec`, `EntitySpec`,
  `TypeScriptDtoFileRules`, `OssieSemanticModelExporter`, `RulesGenRunner`; the DSL, env and rule-dev guides (en, ko);
  the pet store model, `.gitignore`, `semantic_model.yml` and `LabelBundleTest`.
