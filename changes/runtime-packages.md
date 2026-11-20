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
  `.elasticore/cache` records with each text the mapping it was renamed with, in the same save - so a `-Pdomain` run, a
  failed model check or a run that stops leaves every text paired with its own. When the mapping changes or is removed, a
  merged file's base and the file are renamed back from the mapping recorded for it and on to the new one; a recorded
  mapping that cannot be read, cannot be undone by name, or would rename the project is said and not used. A file the run
  does not write that still names a previous mapping is said; a run of every domain without the setting deletes
  `build/elasticore/runtime-classes.txt`, and a cache without the setting records no mapping. A file only renamed is
  reported `RENAMED`. A key that is not under `io.elasticore.` or covers a namespace of the project, a value that is not a
  package name in Java and Kotlin, is under `io.elasticore`, or is or holds a namespace of the project fail the build, and
  so does a generated package under a key or a value. The run prints the mapping, the
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
  `RuntimePackagesMappingChangeTest` (a change, then `-Pdomain` - an edited and a KEPT file renamed once; a change in a run
  whose model check failed, then a whole run - renamed once; the record in the cache and only with the setting; removal
  deleting the record and `runtime-classes.txt`; an unreadable, a project-renaming and an irreversible record said; a
  value equal to a namespace refused);
  `ElastiCorePluginTest` (passed, not passed unset, refused, ignored with v2, the GraphQL writer's class);
  `GuideCoverageTest`/`GuideCoverageKoTest`; `elcoreRulesCheck -Pstrict` and `elcoreTreeCheck`. End to end:
  elasticore-template-ddd generated through the plugin with the three packages mapped, 92 of the runtime's 140 classes
  copied with their packages rewritten, compiled and its tests run with no `io.elasticore` artifact on the classpath.
- Files: `RuntimePackages` (new), `RulesGenRunner`, `GeneratedCache`, `ElastiCoreExtension`, `ElastiCorePlugin`, rule
  guide and env guide (en, ko), `RuntimePackagesTest`, `RuntimePackagesGenerationTest`, `RuntimePackagesProjectTest`,
  `RuntimePackagesMappingChangeTest`, `ElastiCorePluginTest`, `GuideCoverageTest`.
