### Changes (supportAI deprecated)

#### `supportAI` is deprecated; a generation that uses it says so
- Why: the Spring AI tools it writes on `XxxService` repeat the REST API that an agent can already call
  (OpenAPI), add up to eleven tools per entity, and open deletes by condition and bulk changes to a model by
  default. Nobody was found using them.
- Before: `supportAI: true` wrote the tools without a word.
- After: the same tools are written, and each generator prints, once per domain,
  `[ElastiCORE] domain '<name>': supportAI is deprecated and will be removed in a later release. ...` -
  the rule engine (`elcore3`, `output = 'v2'` too) and the 2.x template generator (`./gradlew elcore`).
  The env guide marks section 3 deprecated, in both languages.
- Generated code: unchanged.
- Checked: `AiToolDescriptionTest` - the rule engine warns once for the 2.x bundle and for v3, not when
  `supportAI` is off, and the 2.x generator run in its own JVM warns and still writes `@Tool`.
- Files: `ServiceSrcPublisher`, `RulesGenRunner`, `elasticore-guide/elasticore-env-guide.md`,
  `elasticore-guide/ko/elasticore-env-guide.md`.
