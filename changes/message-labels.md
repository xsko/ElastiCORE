### New Features (messages - a refusal names the field, the entity and the state by its label)

#### `invariant.*` and `state.*` take the labels as values, in the language of the response; `config: labelLanguage`
- Why (issue 274): a refusal said in Korean still named the field, the entity and the state by their code names
  (`PurchaseOrder cannot cancel when status is SHIPPED`), and a translation could not do better - `invariant.*` took no
  values and `state.*` only the codes - although the generator knows every label (`--`, `@label(en=...)`, the
  enumeration's `label_<language>` columns).
- Now:
  - The generated refusals pass the labels as values of the runtime's new `io.elasticore.springboot3.validation.Label`
    (keys as the label bundle has them: `Account`, `Account.annualRevenue`, `Stage.WON`). `invariant.*`: `{0}` the field
    (for `@invariant(condition)` the first field the condition reads, else the entity), `{1}` the entity. `state.*`
    (moves, commands of several lifecycles, `delete:`): `{0}` and `{1}` the codes as before, then `{2}` the field, `{3}`
    the entity, `{4}` the current state, `{5}` the allowed states. Where no label is found each reads exactly as the code
    beside it: `{4}` as `{0}` (`String.valueOf`), `{5}` framed as `{1}` (`[PLACED, SHIPPED]`, as `Arrays.toString` and the
    delete check say it; Kotlin's step `PLACED or SHIPPED`, as `joinToString(" or ")` - `Label.listOf` / `Label.anyOf`).
    Java and Kotlin, every mode and architecture that writes these refusals. The exception's own message
    (`getMessage()`, a log) is unchanged.
  - The default bundle names them: `invariant.Account.annualRevenue.min={0} must be at least 0`,
    `{1} violates <condition>` for an `@invariant` without a sentence, `state.<E>.<f>.<command>={3} cannot <command> when {2}
    is {4}; it must be {5}`, `state.<E>.<f>.delete={3} cannot be deleted when {2} is {4}; it must be {5}`. A sentence
    written in the DSL stays as written. The header lists the values, only in a domain that has such codes.
  - `ErrorMessages` says a `Label` in the one language the response is in: the label bundle's file of that language itself
    (`i18n/<domain>-labels_ko.properties`; `_en` for the bundles' own wording) - never the default file, which holds the
    `--` text - else the name. Elsewhere (a log, `MessageFormat`, another `MessageSource`) a `Label` is its name, and it is a
    `MessageSourceResolvable` with its key as the code.
  - A refusal that carried values before (`state.*`, `reference.*`, a hand-written code with values) is a `MessageFormat`
    pattern as it always was, placeholder or not (`''` an apostrophe). A refusal whose values are all labels - an
    `invariant.*`, which had none - passes them only to a sentence that holds a `{`: an `invariant.*` translation without
    a placeholder such as `can't be negative` is said as written, as before, and so is one that is no `MessageFormat`
    pattern (`금액은 {최소} 이상`, a DSL sentence with a brace).
  - A sentence that names a value the refusal did not give - a generated code thrown by hand with the two codes
    (`state.*`) or with none (`invariant.*`) - is answered with the refusal's own message, never with `{3}` left in it.
    Without a message to fall back on it is said as before.
  - A project with `runtimePackages` (its own copy of the runtime) adds the new `Label` to its copy and takes the
    changed `ErrorMessages`: the generated refusals name `Label`.
  - env.yml `config: labelLanguage: ko` (optional, domain-wide, normalised as an `@label` key; not a language tag: a
    `DSL_WARN`, ignored) names the language of the `--` labels: the label bundle gets a file of that language with every
    label - `@label(ko=...)`, else the `--` text; an enumeration's `label_ko` column, else its label column. Without it, a
    language with no `@label(<language>=...)` gets the names in these sentences.
- Unchanged: a model without invariants and lifecycles generates the same code; a model without labelLanguage and
  without translations writes no label bundle; `reference.*`; existing `_ko` translations of `state.*` keep their meaning
  (`{0}`/`{1}` still the codes); every answer - English and translated - of a model without labels, word for word; the
  2.x-compatible output and the 2.x generator. Changed by default, as the feature: the refusal lines of a model with
  invariants or lifecycles carry the labels, and the default bundle's sentences of those codes are patterns naming them.
- Checked: `RefusalLabelsTest` (springboot3: English takes `_en` or the name and never the `--` file; ko and ko_KR take
  `_ko`; ja translated without labels says the names; Accept-Language `fr, ko;q=0.8` / `en` / `sv` / none through the
  `AcceptHeaderLocaleResolver`; a ko `state.*` translation with the codes unchanged; an `invariant.*` translation with
  `can't` and no placeholder kept intact, a pattern with `''` rendered; a `state.*` and a hand-written translation
  without a placeholder written with `''` said with `'`, as Spring's own MessageSource says them with the codes alone;
  a hand-thrown `state.*` with two values and an `invariant.*` with none answered with their own message, a translation
  naming only `{0}`/`{1}` said with them; an invariant translation and a DSL sentence that are no pattern said as
  written; `format` with a label; `Label` as a name - `listOf` equal to `Arrays.toString`, `anyOf` to `joinToString(" or ")` -,
  its codes, another `MessageSource`). `MessageLabelsGenerationTest` (base, through `RulesGenRunner`: the bundle
  sentences, read back with `MessageFormat`; a model without labels whose sentences, with the names and the code lists,
  read as the sentences before; the Java and Kotlin throw lines of the invariants, the mover and the delete check; no
  `Label` in a model without refusals; the labelLanguage file, `_en` unchanged, `ko_kr` -> `_ko_KR`, `korean` warned and
  ignored; a DSL sentence with a brace kept as written), `LiteralPatternTest` (a condition's or a bound's text with an
  apostrophe or a brace said back as written), `ErrorCatalogTest`, `ShopModelTest` (assertions on the old sentences
  updated). A model without English
  labels answers in English as before, by the runtime: `elasticore-template-ddd` `EnglishRefusalAsBeforeTest` (Java: a
  move `... it must be [READY]`, a command of two lifecycles `... [PAID]`, `sortOrder must be at least 0` / `at most 999`,
  each equal to the exception's message, the sentence the answer had) and `elasticore-template-kt`
  `DomainRulesDslTest.withoutEnglishLabelsTheEnglishAnswerIsAsBefore` (Kotlin: `... it must be OPEN`, `... UNBILLED`,
  the delete check `... [CLOSED]`, the bounds). Over HTTP in the Pet Store (`labelLanguage: ko`,
  `PurchaseOrder.status @label(en=Order status) -- 주문 상태`, `OrderStatus.label_ko`): `ShopFlowTest` - cancel and delete
  of a shipped order without a header, `en` and `fr` answer `PurchaseOrder cannot ... when Order status is SHIPPED; it
  must be [PLACED]` / `[CANCELLED]` (the field by its English label; was `status`), `ko` delete answers
  `주문 상태이(가) 배송됨인 주문은(는) 지울 수 없습니다 (지울 수 있는 상태: [취소됨])`, `ko` cancel by the translation with the
  codes is unchanged; `LabelBundleTest` (the `_ko` file). `elasticore-template-kt` `DomainRulesDslTest` (the values
  thrown by a two-lifecycle command and by `checkDeletable`) and `ShopApiContractTest`; `elasticore-template-ddd`
  `LifecycleTogetherTest`, `AggregateInvariantTest`, `ApiContractTest`, `StateStampApiTest`, `BoundaryInvariantTest`;
  `elasticore-template-ddd-mybatis` `LifecycleTogetherTest`. With the main sources of the change taken back (the
  runtime `Label` kept so the tests compile), `MessageLabelsGenerationTest`, `RefusalLabelsTest`, `ErrorCatalogTest` and
  `ShopModelTest` fail.
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `Label` (new), `ErrorMessages`, `DddInvariantRules`, `DddStateRules`, `ErrorCatalog`, `LabelCatalog`,
  `RulesGenRunner`, `RefusalLabelsTest` and its bundles (new), `MessageLabelsGenerationTest` (new), `ErrorCatalogTest`,
  `ShopModelTest`, the Pet Store (`env.yml`, `entity.yml`, `enum.yml`, `semantic_model.yml`,
  `petstore-messages_ko.properties`, `ShopFlowTest`, `LabelBundleTest`, the site guide page `static/dsl-guide.html`
  regenerated by `guide/build_guide.py`), `LiteralPatternTest` (new), `elasticore-template-kt` `DomainRulesDslTest`,
  `elasticore-template-ddd` `EnglishRefusalAsBeforeTest` (new),
  the rule, env and DSL guides (en, ko), `docs/tour.md`, `docs/tour.ko.md`, `changes/screen-labels.md`.
