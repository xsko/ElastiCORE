### Internal (rule engine - one source for what a screen is told about a field)

#### The TypeScript client's field facts, the search DTO's properties and the access policy's arguments each come from one place
- Why (issue 373, first step): a later answer of `/schema/info` built from the model has to say exactly what the
  TypeScript client says - required, lengths, bounds, read-only, labels, the entity label, the enum values and
  labels - and describe the search DTO and the access policy the service enforces. Those facts were computed in
  private helpers of the TypeScript rules (`TypeScriptDtoFileRules`, `TypeScriptEnumRules`), the search DTO's
  property names were decided inside the rule that renders them, and the arguments of
  `ACCESS = AccessPolicy.parse(...)` were assembled twice in `ServiceAccessRules` (Java, Kotlin). A second writer
  copying them would drift.
- Now: `FieldFacts` (`io.elasticore.base.model.pub.rules`) has one function per fact - `label`, `labels`, `html`,
  `writable`, `readOnly`, `required`, `maxLength`, `minLength`, `min`, `max`, `bound`, `entityLabel` (the label
  and its translations), `enumWireValue`, `enumLabel`, `enumLabels` - stated as the TypeScript client states them
  (a field no request writes is read-only and asked for nothing else); the TypeScript rules call it and their
  private copies are gone. `FieldFacts.kind` (not used yet) names a field's kind in the Ossie export's words
  (`String`, `Integer`, `Decimal`, `Float`, `Boolean`, `Date`, `Time`, `DateTime`, `DateTimeTz`, `Opaque`) or,
  where the model knows more, `List`, `Ref`, `Enum`, `Object`, `Money`, `Html`. `SearchDtoBaseRules` decides each
  field's `SearchProperty` list (name, field, op, kind, range end, alias, words) and renders the search DTO from
  it; `SearchDtoBaseRules.properties(spec)` returns the same list and writes nothing. The decision is issue 376's:
  a derived condition is never written into the field, the rule hands it on through `FieldSpec.derivedSearch`,
  and a search DTO that is not @expose derives one only for the inherited fields marked `searchedAsInherited`.
  `ServiceAccessRules.policyArguments(entity)` is what both the Java and the Kotlin service pass to
  `AccessPolicy.parse`.
- Before and after: the generated output is the same, byte for byte. Nothing in the DSL or the settings changes.
- Checked: every sample regenerated before and after the change (`elasticore-template-ddd`,
  `elasticore-template-kt` with `elcore3ts`, `elasticore-sample-petstore` with `elcore3ts`,
  `elasticore-template-mybatis`, `-mongo`, `-ddd-mybatis`, `-kt-mongo`, `elasticore-sample-petstore-mybatis`)
  from main (after issue 376) and from this change, and compared byte for byte: 1999 files, 0 differ, 0 missing,
  0 extra; `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED; `elcoreTreeCheck` 0 differ / 0 missing /
  0 extra; `elasticore-base` tests pass (2058, 3 skipped). New in-process tests: `FieldFactsTest` (every fact),
  `SearchPropertyTest` (values, ranges, aliases, list items, emptiness checks, words, derived conditions handed on
  through the spec, the inherited fields of a @service search DTO, what is no property, in the clean and the
  2.x-compatible output), `ServiceAccessRulesTest` (the policy arguments and their source text).
- Files: `rules/FieldFacts.java` (new), `rules/lang/typescript/TypeScriptDtoFileRules.java`,
  `rules/lang/typescript/TypeScriptEnumRules.java`, `rules/base/SearchDtoBaseRules.java`,
  `rules/base/ServiceAccessRules.java`, `ossie/OssieSemanticModelExporter.java` (`ossieType` public); tests
  `FieldFactsTest`, `SearchPropertyTest`, `ServiceAccessRulesTest`.
