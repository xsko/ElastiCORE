### Changes (Ossie export - core spec `0.3.0.dev0`)

#### The Ossie export writes `version: 0.3.0.dev0`, the version Ossie's schema now requires
- Why: apache/ossie#691 (merged on 9 November, `c1a3318`) moved the spec's in-development version from `0.2.0.dev0`
  to `0.3.0.dev0`; there will be no 0.2.0 release. `ossie-schema.json` pins the version with `const`, so every
  `semantic_model.yml` the export wrote stopped validating against Ossie's `main`:
  `[Schema] version: '0.3.0.dev0' was expected` (#96).
- What: `OssieSemanticModelExporter.SPEC_VERSION` is `0.3.0.dev0`, which sets the header comment and the `version:`
  line. Nothing else in the document changes. The rule guide's Ossie section (en, ko) says the version, and that
  3.0.261106-rc11 and earlier write `0.2.0.dev0`.
- Upgrade: a project that names the `ossie-export` rule set gets a `semantic_model.yml` with a new `version:` line and
  header the next time it generates. A consumer that checks the document against Ossie's schema needs this one: a
  `0.2.0.dev0` document fails Ossie's `main` from `c1a3318` on, and a `0.3.0.dev0` document fails the schema before it.
  ElastiCORE 3.0.261106-rc11 and earlier write `0.2.0.dev0`; the releases after it write `0.3.0.dev0`.
- Checked: the three committed `semantic_model.yml` (pet store, `elasticore-template-ddd`,
  `elasticore-template-ddd-mybatis`), regenerated, differ from `main` in those two lines only.
  - Against apache/ossie `main` (`d700cde`), with sqlglot, `validation/validate.py` passes all three. Before the
    change, each failed with the error above, and that error alone.
  - Against the schema before #691 (`21d14d7`), the new pet store document fails with
    `version: '0.2.0.dev0' was expected`.
  - `OssieExportTest` and the three `SemanticModelTest` pass. The pet store's guide page (`static/dsl-guide.html`),
    rebuilt with `guide/build_guide.py`, changes in that line alone.
- Files: `OssieSemanticModelExporter.java`, `OssieExportTest.java`, the three `SemanticModelTest.java` and
  `semantic_model.yml`, the rule guide (en, ko), the pet store's `build_guide.py` and `dsl-guide.html`
