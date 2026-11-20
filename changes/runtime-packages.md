### New Features (Gradle plugin - `runtimePackages`)

#### `elasticore { runtimePackages }` generates the code against the project's own copy of the runtime
- Why: the generated code imports the ElastiCORE runtime (`elasticore-springboot3`/`-springboot4` and a few classes of
  `elasticore-base`). A project that may not ship an ElastiCORE jar had no way to run the generated code without it.
- Now: `runtimePackages = ['io.elasticore.springboot3': 'com.acme.springboot', ...]` rewrites the runtime's package
  prefixes in the 3.0 code to the project's packages - imports and qualified names in code, Java and Kotlin, and the
  resources the run writes (orm.xml under `mapping: xml`). Only the prefix changes, whole package segments, the longest
  key first. The text is rewritten before the merge and `.elasticore/cache`, so the file and its base agree. A key that
  is not under `io.elasticore.`, or a value that is not a package name, fails the build. The run prints the mapping, the
  runtime classes the generated code now names under the project's packages (also `build/elasticore/runtime-classes.txt`),
  and a warning for each package under `io.elasticore` a generated file still names - a mapping missing, or a file of the
  developer's the run did not rewrite. `elcoreGraphQl` runs the schema writer from the project's copy.
- Unchanged: unset, nothing is passed and the generated code is byte for byte what it was. With `output = 'v2'` the
  setting is ignored with a warning, and the code 2.x generates keeps `io.elasticore`. No DSL word or env.yml key; no
  runtime class moved. `blueprint/schema.yml` and the Ossie export still name the runtime's types: they describe the
  model and are not compiled.
- Checked: `RuntimePackagesTest` (segments, longest first, imports and inline names, Kotlin, no-op, leftovers without the
  project's own packages, the class list); `RuntimePackagesGenerationTest` (the pet store on Spring Boot 4 and the ddd
  sample with orm.xml: nothing of the three packages left in sources, resources or the cache, the list written, a second
  run writes nothing, a mistyped mapping stops the run; a Kotlin build; the 2.x code left as it is);
  `ElastiCorePluginTest` (passed, not passed unset, refused, ignored with v2, the GraphQL writer's class);
  `GuideCoverageTest`/`GuideCoverageKoTest`; `elcoreRulesCheck -Pstrict` and `elcoreTreeCheck`. End to end:
  elasticore-template-ddd generated through the plugin with the three packages mapped, 92 of the runtime's 140 classes
  copied with their packages rewritten, compiled and its tests run with no `io.elasticore` artifact on the classpath.
- Files: `RuntimePackages` (new), `RulesGenRunner`, `ElastiCoreExtension`, `ElastiCorePlugin`, rule guide (en, ko),
  `RuntimePackagesTest`, `RuntimePackagesGenerationTest`, `ElastiCorePluginTest`, `GuideCoverageTest`.
