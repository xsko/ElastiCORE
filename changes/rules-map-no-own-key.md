### Bug Fixes (rule selection - `publisher.rules` as a map)

#### A run whose key a `publisher.rules` map does not hold takes its default rules instead of stopping
- Why: `publisher.rules` is a list, or a map of per-language lists (`kt:`, `ts:`). A run that found no key of its own
  in the map read the map's text as a rule name and stopped: a Java build's `elcore3` with any map
  (`Unknown rule set '{ts=[ts-model]}'`, `'{kt=[jpa-springboot-kt'`), a Kotlin build's with only `ts:`. So a Java build
  could not choose its TypeScript rules in `env.yml` at all. `elcore3ts` of a Java build with a map read the same text
  as the server's rules, so its client was shaped for a server with none of its rules (a ddd build's `.ts` files
  differed from those of the same build without `publisher.rules`).
- Now: a map is never read as a list (`RulesConfig.domainList`). A run with no key of its own in it runs exactly as if
  the domain had no `publisher.rules` - the default of its architecture, language and mode: `elcore3` of a Java build
  (Java has no key; none was added), `elcore3` of a Kotlin build without `kt:`, `elcore3ts` without `ts:`. A Kotlin
  build still reads `kt:` and `elcore3ts` `ts:`. The list form is unchanged, and so is the refusal of a Java bundle in
  a Kotlin build (in the list or under `kt:`). A key no run reads (`java:`, `kts:`) is a DSL warning naming the keys
  that are read (`kt`, `ts`), also in `-PlistRules`; it does not stop the run. Every reader goes through it: the
  selection of `elcore3` / `elcore3ts` and `-PlistRules`, the server's rules a TypeScript run shapes the client by, the
  Kotlin Java-bundle refusal, the MyBatis and MongoDB checks of what a domain names, the v3 / 2.x mode decision, and
  the Ossie export request (which now reads the list the run takes - `kt:` in a Kotlin build - instead of searching
  the text; `ossie-export` named only under `ts:` no longer turns the export on; it is a server-side rule set). `elcoreValidate`, `elcoreDdl` and `elcoreMigrationCheck` run the same runner. The rule guide (en, ko)
  "Choosing rules per language" and the DDD customize guide's TypeScript row say so, with a Java build choosing its
  TypeScript rules (`ts:` alone).
- Checked: `RulesMapPerLanguageTest` (10 cases; 6 failed before the fix with `Unknown rule set` or a different
  client): Java build with only `ts:` and with `kt:` + `ts:` lists and generates what a domain with no rules does; Kotlin
  build with only `ts:` likewise, with `kt:` runs `jpa-springboot-kt + ddd-aggregate`; `elcore3ts` of a Java build runs
  its `ts:` list and, without one, its default; a ddd Java build's `.ts` client with a `ts:` map is byte-equal to the
  one without `publisher.rules`; the list form and the Kotlin refusal are unchanged; `java:` / `kts:` warn; the Ossie
  export follows `kt:` in a Kotlin build only. Standalone projects (plugin and `elasticore-base` from this branch):
  a Java build with `rules: { ts: [ts-model] }` - `elcore3` lists `jpa-springboot-v3` and writes the Java server,
  `elcore3ts -PlistRules` lists `ts-model` and writes the client, `compileJava`, `elcoreValidate`,
  `elcoreMigrationCheck` and `elcoreDdl` pass (the same project on the jar before the fix: `Unknown rule set
  '{ts=[ts-model]}'`); with `java:` + `kt:` + `ts:` the warning is shown and the build passes; a Kotlin build with only
  `ts:` lists `jpa-springboot-kt`, and `elcore3`, `elcore3ts` and `compileKotlin` pass.
- Files: `RulesConfig.java`, `RulesGenRunner.java`, `OssieSemanticModelExporter.java`, `RulesMapPerLanguageTest.java`,
  rule guide (en, ko), DDD customize guide (en, ko).
