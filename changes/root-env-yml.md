### New Features (`blueprint/env.yml` - settings every domain shares)

#### An `env.yml` directly in `blueprint/` is inherited by every domain's `env.yml` (issue 248)
- Why: every domain folder has its own `env.yml`, and what all of them share - `mode`, `framework`, `j2ee`, `apiPath`,
  `annotations`, `interface`, the package pattern - was copied into each one. A project with nine domains kept nine
  copies in step by hand, and one that drifted (another `mode`, an annotation missing, `enumeration` where the others
  write `enums`) was found late. `blueprint/env.yml` was not read at all.
- Now: only a project that has `blueprint/env.yml` is affected; without it every domain is read as before. The file
  takes the keys a domain's `env.yml` takes; `domainName` and `enable` (one per domain) get a warning there and are
  ignored, and the build goes on. Order: the domain's `env.yml` > `blueprint/env.yml` > the build (`elasticore { }`,
  `-Delasticore.*`) > the defaults; a key whose build setting wins over a domain's file (`springBoot`) wins over the
  root file too. No new key and no DSL word.
  - Namespaces: the root holds patterns with `{base}` (the root's `namespace.base`) and `{domain}` (the domain's
    `domainName`, its folder name when it has none), and a pattern alone switches nothing on. A key the domain writes
    with no value (`port:`) is generated in the root's pattern; a key with a value keeps it; a key with no value and no
    pattern is a warning and is not generated, as before; a domain without `namespace:` generates nothing. The one-line
    `namespace: base: ...` of a domain keeps its meaning (`base:` with no value takes the root's base).
  - Every other key: maps (`config:`, `annotations:`, `interface:`, ...) merge key by key and the domain wins; a list
    (`publisher.rules`, `publisher.suppress`, ...) is replaced as a whole; a key written with no value takes the
    root's. There is no removal syntax.
  - Every reader takes it the same way, through `FileBasedModelLoader` (new `RootEnv`): the rule engine
    (`BlueprintLoader`, `elcore3`, `elcoreValidate`, `-PlistRules`), the 2.x generator (`ModelExtractor.extract`,
    `output = 'v2'`), the `@ElastiCore` annotation processor, and multi-module builds (`BlueprintLoader.loadModules`,
    `ModelExtractor.extractMultiModule`) - one root file per module's `blueprint/`. `RulesGenRunner.modelDigest` hashes
    the root `env.yml` (only that file of the blueprint root), so a changed `apiPath` there marks a TypeScript client
    as made from an older model. The plugin's `ElastiCorePlugin.usesGraphQl` reads `graphql:` in the root file too.
  - Seeing it: the build prints one line when it reads the file
    (`[ElastiCORE] blueprint/env.yml: 17 settings inherited by 5 domains`), and `-PlistRules` prints each domain's
    settings as read, `*` marking those from the root file. Without the file neither appears.
  - Compatibility: a project with a stray `blueprint/env.yml`, ignored until now, sees it applied from 3.1 - the line
    above shows it on the first build. No sample or template has one.
- Checked: `RootEnvTest` (21 cases: the namespace table row by row, `base:`, `{base}` without a root base, a
  `module://` pattern, map merge and the domain winning, list replace, a key with no value, the warnings for
  `enable` / `domainName` and for a key without a pattern, the line and its count, a root that is no YAML, the
  `-PlistRules` listing, and the loader giving a thin domain plus the root the context of the same domain with
  everything written in it); `RootEnvReadersTest` (9 cases, every one failing on `main` before the change): the same
  model generated with the shared settings in each domain and with them in the root file gives byte-equal trees through
  `elcore3`, the 2.x generator, the annotation processor and the 2.x multi-module build; `BlueprintLoader.load` and
  `loadModules` (two modules, each with its own root file) give the same settings; `-PlistRules` marks the inherited
  ones and says nothing without the file; the env guide's example prints the line and the listing the guide shows; the
  client digest changes with the root file and not with `schema.yml`.
  `ElastiCorePluginTest.theRootEnvTurnsTheGraphQlWriterOn` (fails before the change). A real run:
  `elasticore-template-ddd` (five domains) changed temporarily - the shared `framework`, `mode`, `j2ee`, `apiPath`,
  `testCode` and four `annotations` moved to a root file, `catalog`'s six namespace keys written with no value against
  `{base}.{domain}.*` patterns - then `elcore3`, `compileJava` (rerun) and `processResources` (with `elcoreGraphQl`):
  all 485 files under `src/` (generated and hand-written, `env.yml` and the copied guides aside) equal those of the
  run with the per-domain files; reverted after. `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED,
  `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `RootEnv.java` (new), `FileBasedModelLoader.java`, `BlueprintLoader.java`, `ModelExtractor.java`,
  `RulesGenRunner.java`, `ElastiCorePlugin.java`, `RootEnvTest.java`, `RootEnvReadersTest.java`,
  `ElastiCorePluginTest.java`, env guide (en, ko) section 1-1, 7-1 and 8.0.
