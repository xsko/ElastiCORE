### Changes (supportAI removed)

#### `supportAI` is removed; the service is generated without Spring AI tools
- Why: the Spring AI tools it wrote on `XxxService` repeated the REST API that an agent can already call
  (OpenAPI), added about a dozen tools per entity, and opened deletes by condition and bulk changes to a model
  by default. 3.0 is not released yet, so it goes before the release rather than after it.
- Before: `supportAI: true` wrote the extension service from `service_extend_ai.tmpl` (2.x, v3 and Kotlin),
  each core method again with `@Tool` / `@ToolParam`.
- After: the setting is ignored. Every generator writes the plain extension service, and the run that writes
  the services prints, once per domain that still sets it,
  `[ElastiCORE] domain '<name>': supportAI was removed and is ignored - ...` - `elcore3` (also with `-Pv2`)
  and the 2.x template generator (`./gradlew elcore`, which `output = 'v2'` runs). The TypeScript client's
  run and the DDL-only run say nothing. The three templates, `EntitySpec.supportAI` and the tool variables
  of `ServiceExtendBaseRules` are gone; the setup wizard no longer writes `supportAI: false`, and the
  samples no longer set it (the Kotlin sample's `hr` domain set `true`, with a Spring AI milestone
  dependency, both removed).
- Upgrading a project that used it: a `XxxService` the earlier version wrote and nobody touched is written
  again without the tools; an edited one loses the tool methods and keeps the edits where they do not
  overlap (`MERGED(your edits kept)`). One left `KEPT` or `SKIP(user-owned)` keeps them until they are
  deleted by hand. A `ToolCallbackProvider` bean the project registered has no tools left to find. Take
  `supportAI` out of `env.yml`.
- Generated code: unchanged for every domain without `supportAI: true`.
- Checked: `SupportAIRemovedTest` - the rule engine (2.x bundle and v3) and the 2.x generator in its own
  JVM write the plain service and warn once; no warning without the setting or in the TypeScript run.
  `GeneratedMergeTest.toolsAnEarlierVersionWroteLeaveAnEditedService` - a service the cache holds with a
  tool, edited by the developer, is merged without the tool and with the edit.
- Files: `ServiceSrcPublisher`, `RulesGenRunner`, `ServiceExtendBaseRules`, `EntitySpec`, `SpecFactory`,
  `DddRepositoryPortRules`, `InitializationHelper`, `service_extend_ai.tmpl` (three, deleted), the sample
  `env.yml` files, `elasticore-template-kt/build.gradle`, `elasticore-guide/elasticore-env-guide.md`,
  `elasticore-rule-guide.md`, `elasticore-migration-guide.md` and their `ko/` versions.
