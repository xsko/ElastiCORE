### Bug Fixes (model loading - a `": "` inside a `--` label, issue 345)

#### A model file that does not load because of a `": "` in a `--` label says so
- Why: `versionNo: int -- version: raised when the same kind is uploaded again` is a YAML mapping inside a value - the
  file does not load (`LOAD ERROR`, `mapping values are not allowed here ... line N, column M`) and the build stops, which
  is right. But the column points into the middle of the label, and nothing said the label was the cause.
- Before: `LOAD ERROR <file> - not read, nothing it declares was generated: mapping values are not allowed here ...`.
- After: when the parser's error is on a field line with ` -- ` and its column is after the ` -- `, and the label holds a
  `:`, the error ends with the hint and the console prints it beside `* LOAD ERROR:` -
  `... - the label of versionNo contains ': ' - quote the whole value or reword it`. Any other load error reads as before,
  and a model that loads is read exactly as before: only the message of a file that does not load changes.
- DSL guide ("How the `--` comment parser works", English and Korean): a label cannot hold `": "` (the file does not load)
  or `" #"` (a YAML comment: the label ends before it, silently) unquoted; quote the whole value, type and marks included
  (`versionNo: "int -- version: raised ..."`), or in single quotes with an apostrophe written twice.
- Checked: `LabelYamlHintTest` (new) - the hint for a label with `": "`, Korean included; none for an error before the
  ` -- `, on a line without a label, or outside a field line; and, loading the field line as the loader does, that
  `-- price #2 of the list` is the label `price` and that the quoted forms keep the whole label. `LoadErrorFailsTest` checks
  the hint in a whole `elcore3` run. `LabelYamlHintTest` and the new assertion fail without the change. The guide tests
  (`--tests "*Guide*"`).
- Files: `YamlFileMainModelLoader.java`, `LabelYamlHintTest.java` (new), `LoadErrorFailsTest.java`,
  `elasticore-dsl-guide.md` and its `ko/` copy.
