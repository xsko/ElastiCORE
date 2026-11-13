### Changes (Ossie export - enum codes)

#### An enum field's stored values and their meanings are data in the export
- Why (issue 174): the codes of an enum column were only a sentence in `ai_context`
  (`"Codes: NONE = Disabled, YYYY, ..."`). A program had to parse it: a code whose label is the same is written alone,
  and a label holding `, ` or ` = ` cannot be read back.
- What: the field's ELASTICORE extension carries them beside the enum's name:
  `data: {"enumeration": "DatePart", "codes": {"NONE": "Disabled", "YYYY": "YYYY"}}`. The keys are what is stored -
  the `@db` code, the name under `@jpa:Enumerated(STRING)`, or the ordinal (`"0": "SMALL (Small)"`). `ai_context`
  stays as it was.
- Changes the output: every exported enum field gets the `codes` key, so `semantic_model.yml` changes wherever there
  is an enum. The samples' committed models are regenerated with it (pet store, `elasticore-template-ddd`,
  `elasticore-template-ddd-mybatis`); nothing else in them moves. Not the 2.x-compatible output: the export is
  `ossie-export` only.
- Checked: `OssieExportTest.anEnumsCodesAreData` - a code enum with a label equal to its code and one holding ` = `,
  and an ordinal enum; the `ai_context` sentence unchanged.
- Files: `OssieSemanticModelExporter.java`, `OssieExportTest.java`, the three `semantic_model.yml`,
  `elasticore-rule-guide.md` (en, ko).
