### Changes (supportAI deprecated)

#### `supportAI` is deprecated; a generation that uses it says so
- Why: the Spring AI tools it writes on `XxxService` repeat the REST API that an agent can already call
  (OpenAPI), add about a dozen tools per entity, and open deletes by condition and bulk changes to a model by
  default.
- Before: `supportAI: true` wrote the tools without a word.
- After: the same tools are written, and the run that writes the services prints, once per domain it
  generates, `[ElastiCORE] domain '<name>': supportAI is deprecated and will be removed in a later release. ...`
  - `elcore3` (also with `-Pv2`) and the 2.x template generator (`./gradlew elcore`, which
  `output = 'v2'` runs). The TypeScript client's run and the DDL-only run (`elcoreDdl`) say nothing. The env
  guide marks section 3 deprecated, and the rule guide's mention of the setting, in both languages. The
  repository's own Kotlin sample (`elasticore-template-kt`, domain `hr`) keeps `supportAI: true`, so its build
  shows the warning.
- Generated code: unchanged.
- Checked: `AiToolDescriptionTest` - the rule engine warns once for the 2.x bundle and for v3, not when
  `supportAI` is off and not in the TypeScript run; the 2.x generator, run in its own JVM, warns once and still
  writes `@Tool`.
- Files: `ServiceSrcPublisher`, `RulesGenRunner`, `elasticore-guide/elasticore-env-guide.md`,
  `elasticore-guide/elasticore-rule-guide.md` and their `ko/` versions.
