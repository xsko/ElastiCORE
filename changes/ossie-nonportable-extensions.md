### Changes (Ossie export - `ossie-export`)

#### The ELASTICORE extensions are `nonportable_extensions`, with `data` an object (closes #130)
- Why: apache/ossie#522 (`08d2ad3`, 11 November) changed `0.3.0.dev0` in place: `custom_extensions` is renamed
  `nonportable_extensions`, the old name is not accepted, and `data` is an object, not a JSON-encoded string. The export
  declared `0.3.0.dev0` and wrote the old form, so every document failed the schema that now defines that version - the
  pet store 40 errors, `elasticore-template-ddd` 24, `elasticore-template-ddd-mybatis` 22, one per extension.
- What: each ELASTICORE extension is written as `nonportable_extensions:` and its `data` as the JSON the export already
  built, without the string around it - a YAML flow mapping. Every string in it stays double-quoted, as the spec asks
  under `data`, so a code such as `NO`, `007` or `2026-01-01` is still a string. The model description says
  "nonportable extensions". Nothing else in the document changes.
- Upgrade: only a project that names the `ossie-export` rule set regenerates differently - two lines per extension and
  the description line of its `semantic_model.yml`. A consumer that read `data` as a JSON string now gets an object.
  3.0.261106-rc11 and earlier write `custom_extensions`. `0.3.0.dev0` may change again before `0.3.0-incubating`; the
  export follows Ossie's `main` until that release, and the release after it.
- Checked: the three committed `semantic_model.yml`, regenerated, validate against `ossie-schema.json` at `08d2ad3`
  (the current `main`) with no error; at `ee89dbd`, before #522, they fail on the new name, as expected. `OssieExportTest`
  reads `data` as an object and has no `custom_extensions`, and `dataIsAnObjectWhoseStringsStayStrings` reads back `NO`,
  `yes`, `007`, `2026-01-01`, `a: b` and a quoted text as the strings they were. The 53 Ossie tests and the three samples'
  tests pass.
- Files: `OssieSemanticModelExporter.java`, `OssieExportTest.java`, `OssieFormulaDialectTest.java`,
  `OssieMetricScopeTest.java`, `OssieUniqueKeysTest.java`, `elasticore-rule-guide.md` (en, ko), the pet store's
  `guide/build_guide.py` and `static/dsl-guide.html`, the three `semantic_model.yml`
