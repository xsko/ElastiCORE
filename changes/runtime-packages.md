### New Features (Gradle plugin - `runtimePackages`)

#### `elasticore { runtimePackages }` generates the code against the project's own copy of the runtime
- Why: the generated code imports the ElastiCORE runtime (`elasticore-springboot3`/`-springboot4` and a few classes of
  `elasticore-base`). A project that may not ship an ElastiCORE jar had no way to run the generated code without it.
- Now: `runtimePackages = ['io.elasticore.springboot3': 'com.acme.springboot', ...]` rewrites the runtime's package
  prefixes in the 3.0 code to the project's packages - imports and qualified names in code, Java and Kotlin, and the
  resources the run writes (orm.xml under `mapping: xml`). Only the prefix changes, whole package segments, the longest
  key first. The text is rewritten before the merge and `.elasticore/cache`, so the file and its base agree; turned on in
  a project generated before, a merged file's cached base and the file on disk are renamed too before the merge, so an
  edit next to a runtime name stays and does not conflict. Kotlin with `mapping: xml` gets the mapped `PersistentModel`.
  A merging run records the mapping in `.elasticore/runtime-packages`; when it changes or is removed, the merged files
  are renamed back from the recorded one and on to the new one, and a file the run does not write that still names the
  previous mapping is said. A file only renamed is reported `RENAMED`. A key that is not under `io.elasticore.` or covers
  a namespace of the project, a value that is not a package name in Java and Kotlin, is under `io.elasticore`, or is or
  holds a namespace of the project fail the build. The run prints the mapping, the
  runtime classes the generated code now names under the project's packages (also `build/elasticore/runtime-classes.txt`),
  and a warning for each package under `io.elasticore` a generated file still names - a mapping missing, or a file the run
  did not write (the developer's, or one kept because a source of theirs uses it). A nested class is listed as the class it
  is in; a copy under the project's own package is listed like any other. Only the rule-engine tasks are given the
  setting; `elcoreGraphQl` runs the schema writer from the project's copy.
- Unchanged: unset, nothing is passed and the generated code is byte for byte what it was. With `output = 'v2'` the
  setting is ignored with a warning, and the code 2.x generates keeps `io.elasticore`. No DSL word or env.yml key; no
  runtime class moved. `blueprint/schema.yml` and the Ossie export still name the runtime's types: they describe the
  model and are not compiled.
- Checked: `RuntimePackagesTest` (segments, longest first, imports and inline names, Kotlin, no-op, leftovers without the
  project's own packages, the class list); `RuntimePackagesGenerationTest` (the pet store on Spring Boot 4 and the ddd
  sample with orm.xml: nothing of the three packages left in sources, resources or the cache, the list written, a second
  run writes nothing, a mistyped mapping stops the run; a Kotlin build; the 2.x code left as it is);
  `RuntimePackagesProjectTest` (turned on over hand edits - next to an inline runtime name, on the line after it, in
  the imports - with no conflict; Kotlin with `mapping: xml`; a copy under the project's package listed; a key over the
  project's packages refused; a file kept in use reported; unset as before; the mapping changed A -> B and removed, an
  edit next to a runtime name kept with no conflict; a real conflict still marked; a kept skeleton renamed and said so; a
  2.x custom block renamed; an edited stamped 3.0 file confirmed by its unrenamed text; any namespace checked);
  `ElastiCorePluginTest` (passed, not passed unset, refused, ignored with v2, the GraphQL writer's class);
  `GuideCoverageTest`/`GuideCoverageKoTest`; `elcoreRulesCheck -Pstrict` and `elcoreTreeCheck`. End to end:
  elasticore-template-ddd generated through the plugin with the three packages mapped, 92 of the runtime's 140 classes
  copied with their packages rewritten, compiled and its tests run with no `io.elasticore` artifact on the classpath.
- Files: `RuntimePackages` (new), `RulesGenRunner`, `ElastiCoreExtension`, `ElastiCorePlugin`, rule guide and env guide
  (en, ko), `RuntimePackagesTest`, `RuntimePackagesGenerationTest`, `RuntimePackagesProjectTest`, `ElastiCorePluginTest`,
  `GuideCoverageTest`.
