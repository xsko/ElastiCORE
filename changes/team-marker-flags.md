### Bug Fixes (rule API - a team marker's pairs and flags)

#### A team marker's values without `=` are kept, and a field marker's pairs are read through `FieldSpec`
- Why (issue 273): `@ui(menu=sales, order=30, staff)` lost `staff`. The 2.x reading keeps a value only when an
  annotation has no `key=value` pair, so a flag written beside pairs was dropped with nothing said. A field marker's
  pairs (`@ui(title=true)`) were reachable only one by one, or through `FieldSpec.raw()`, the internal classes the rule
  guide says may change.
- Now: the loader also records, for an annotation with pairs, the pairs in the order written and the values without
  `=`. The rule API reads them: `EntitySpec.metaFlags("ui")` (`[staff]`, beside `metaProperties("ui")`,
  `{menu=sales, order=30}`), `FieldSpec.dslProperties("ui")` (every pair in the order written) and
  `FieldSpec.dslFlags("ui")`.
- Unchanged: what every existing reader sees - the annotation's value, its properties and `metaText` are as before, so
  `@access`, `@expose` and the rest read what they did; generated code does not change.
- Checked: `TeamMarkerParametersTest` - a field marker's pairs in order and its flags; the 2.x reading unchanged (no
  value, the flag not a property; a plain value with no flags); a team rule (`com.acme.probe.UiMarkerRules`) in a
  generated project reads `{menu=sales, order=30}`, `[staff]` and the field's `{title=true} [wide]`.
  `elcoreRulesCheck -Pstrict` and `elcoreTreeCheck`.
- Files: `Annotation` (`asWritten`), `AbstractModelLoader`, `FieldSpec`, `EntitySpec`, `SpecFactory`,
  `TeamMarkerParametersTest`, the rule-dev guide's `EntitySpec` and `FieldSpec` tables (en, ko).
