### New Features (build - `elcoreValidate`)

#### `./gradlew elcoreValidate`: the DSL and the generated files checked for CI, nothing written
- Why: 2.x had `elcore -Pmode=valid` for a CI step; a project on the 3.0 rule engine had no way to check that its DSL
  passes and that the generated files in git are what the DSL generates now, short of generating and diffing.
- What: a plugin task that runs the build as configured (architecture, language, modules, DDL, merge) with nothing
  written but `build/elasticore/validate.md`. It fails (exit 1) on a model check failure or a failed generation, a
  generated file whose text the DSL would change (the DDL schema, orm.xml and the messages bundle included), one the
  DSL no longer produces, a Flyway migration the build would write, and a file with a conflict; `-Pstrict` fails on a
  DSL warning too. A file the developer edited and the build leaves alone passes and is listed; so does a file the
  build takes for the developer's (no header, no base in `.elasticore/cache`), as not checked. A file the build would
  write that is not there fails only where the project keeps its generated sources in git (one the generator owns is
  there - a generate-once skeleton does not count); a project that generates them in each build is checked for its DSL
  and the generated files it commits. The DDL is read off the sources as the run would leave them (laid out in a
  temporary directory). A build with `output = 'v2'` runs 2.x's `elcore -Pmode=valid`. Not checked: `schema.yml`, the
  context map, the Ossie model.
- Unchanged: `elcore3`, the build and `elcoreMigrationCheck`; the task runs only when asked for.
- Checked: `ValidateTest` (an up-to-date project passes and nothing is written; a changed DSL fails and names the file;
  an entity taken out fails on the files no longer produced; an edit of the developer's passes and is listed; a model
  check failure; a warning with and without `-Pstrict`; a project without generated files; the DDL read off the
  sources the run would leave, stale after a DSL change; a Flyway migration not written yet fails and neither it nor
  the state is written; orm.xml and the messages bundle without merging; committed skeletons alone; a project without
  its cache), `ElastiCorePluginTest` (`elcoreValidate` registered, not run by the build, the build's settings and
  `-Pstrict` passed, the 2.x check for `output = 'v2'`), `elasticore-template-ddd` (passes; generated sources not in
  git). The DDL and migration tests still pass.
- Files: `RulesGenRunner.java` (`finishValidate`, the DDL overlay, orm.xml and messages in a check), `DdlRun.java` (a
  dry run), `ElastiCorePlugin.java`, `elasticore-rule-guide.md`, `elasticore-dev-guide.md` (en, ko).
