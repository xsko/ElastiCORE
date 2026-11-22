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
    the entity, `{4}` the current state, `{5}` the allowed states (joined with `, `). Java and Kotlin, every mode and
    architecture that writes these refusals. The exception's own message (`getMessage()`, a log) is unchanged.
  - The default bundle names them: `invariant.Account.annualRevenue.min={0} must be at least 0`,
    `{1} violates <condition>` for an `@invariant` without a sentence, `state.<E>.<f>.<command>={3} cannot <command> when {2}
    is {4}; it must be {5}`, `state.<E>.<f>.delete={3} cannot be deleted when {2} is {4}; it must be {5}`. A sentence
    written in the DSL stays as written. The header lists the values, only in a domain that has such codes.
  - `ErrorMessages` says a `Label` in the one language the response is in: the label bundle's file of that language itself
    (`i18n/<domain>-labels_ko.properties`; `_en` for the bundles' own wording) - never the default file, which holds the
    `--` text - else the name. Elsewhere (a log, `MessageFormat`, another `MessageSource`) a `Label` is its name, and it is a
    `MessageSourceResolvable` with its key as the code.
  - The values reach only a sentence that holds a `{`: a translation without a placeholder is said as written, so an
    `invariant.*` translation such as `can't be negative` keeps its apostrophe; one with placeholders is a `MessageFormat`
    pattern (`''`), as `state.*` was.
  - env.yml `config: labelLanguage: ko` (optional, domain-wide, normalised as an `@label` key; not a language tag: a
    `DSL_WARN`, ignored) names the language of the `--` labels: the label bundle gets a file of that language with every
    label - `@label(ko=...)`, else the `--` text; an enumeration's `label_ko` column, else its label column. Without it, a
    language with no `@label(<language>=...)` gets the names in these sentences.
- Unchanged: a model without invariants and lifecycles generates the same code; a model without labelLanguage and
  without translations writes no label bundle; `reference.*`; existing `_ko` translations of `state.*` keep their meaning
  (`{0}`/`{1}` still the codes); the 2.x-compatible output and the 2.x generator. Changed by default, as the feature: the
  refusal lines of a model with invariants or lifecycles carry the labels, and the English answer of a state refusal reads
  `... it must be PLACED` (was `[PLACED]` in Java).
- Checked: `RefusalLabelsTest` (springboot3: English takes `_en` or the name and never the `--` file; ko and ko_KR take
  `_ko`; ja translated without labels says the names; Accept-Language `fr, ko;q=0.8` / `en` / `sv` / none through the
  `AcceptHeaderLocaleResolver`; a ko `state.*` translation with the codes unchanged; an `invariant.*` translation with
  `can't` and no placeholder kept intact, a pattern with `''` rendered; `format` with a label; `Label` as a name, its
  codes, another `MessageSource`). `MessageLabelsGenerationTest` (base, through `RulesGenRunner`: the bundle sentences,
  read back with `MessageFormat`; the Java and Kotlin throw lines of the invariants, the mover and the delete check; no
  `Label` in a model without refusals; the labelLanguage file, `_en` unchanged, `ko_kr` -> `_ko_KR`, `korean` warned and
  ignored), `ErrorCatalogTest`, `ShopModelTest` (assertions on the old sentences updated). Over HTTP in the Pet Store
  (`labelLanguage: ko`, `PurchaseOrder.status @label(en=Order status) -- 주문 상태`, `OrderStatus.label_ko`):
  `ShopFlowTest` - cancel and delete of a shipped order without a header, `en` and `fr` answer
  `PurchaseOrder cannot ... when Order status is SHIPPED; it must be PLACED` / `CANCELLED` (were `... status ... [PLACED]`,
  `[CANCELLED]`), `ko` delete answers `주문 상태이(가) 배송됨인 주문은(는) 지울 수 없습니다 (지울 수 있는 상태: 취소됨)`, `ko`
  cancel by the translation with the codes is unchanged; `LabelBundleTest` (the `_ko` file). `elasticore-template-kt`
  `DomainRulesDslTest` (Kotlin: the values thrown by a two-lifecycle command and by `checkDeletable`) and
  `ShopApiContractTest`; `elasticore-template-ddd` `LifecycleTogetherTest`, `AggregateInvariantTest`, `ApiContractTest`,
  `StateStampApiTest`, `BoundaryInvariantTest`; `elasticore-template-ddd-mybatis` `LifecycleTogetherTest`.
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `Label` (new), `ErrorMessages`, `DddInvariantRules`, `DddStateRules`, `ErrorCatalog`, `LabelCatalog`,
  `RulesGenRunner`, `RefusalLabelsTest` and its bundles (new), `MessageLabelsGenerationTest` (new), `ErrorCatalogTest`,
  `ShopModelTest`, the Pet Store (`env.yml`, `entity.yml`, `enum.yml`, `semantic_model.yml`,
  `petstore-messages_ko.properties`, `ShopFlowTest`, `LabelBundleTest`), `elasticore-template-kt` `DomainRulesDslTest`,
  the rule, env and DSL guides (en, ko), `docs/tour.md`, `docs/tour.ko.md`, `changes/screen-labels.md`.
