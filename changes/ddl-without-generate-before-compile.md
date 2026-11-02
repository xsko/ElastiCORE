### Bug Fixes (Gradle plugin - project DDL and `generateBeforeCompile`)

#### A build that does not generate before compiling no longer runs `elcoreDdl` before its resources
- Why: with `output = 'v2'` and `elasticore { ddl { databases = [...] } }`, the plugin runs `elcoreDdl`
  after `elcore` and also makes `processResources` depend on it - whatever `generateBeforeCompile` says.
  A project turns `generateBeforeCompile` off to generate only when `elcore` is run, yet every `build`,
  `test` or `bootRun` then ran `elcoreDdl`, which loads the model: a domain whose `env.yml` names a remote
  diagram (`resource:`) fetched it again and rewrote `_resource/model-*.yml`, on CI too.
- What: `processResources` waits for `elcoreDdl` only when the build generates before it compiles (the
  default). `elcore` is still finalized by `elcoreDdl`, so the DDL comes whenever the code is generated.
  The env guide (9-1, English and Korean) says so.
- Checked: `ElastiCorePluginTest.theDdlFollowsTheBuildsGeneration` - by default `processResources` and
  `elcore` both bring `:elcoreDdl`; with `generateBeforeCompile = false` only `elcore` does (fails without
  the change).
- Files: `ElastiCorePlugin.java`, `ElastiCorePluginTest.java`, `elasticore-env-guide.md` (en, ko)
