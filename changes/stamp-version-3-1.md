### Bug Fixes (elcoreTreeCheck - the generator's version)

#### elcoreTreeCheck finds the files the rule engine writes more than 2.x with any 3.x version
- Why: a generated file's hash line ends in the generator's major.minor (`//ecd:..._V3.1` from 3.1 on).
  `TreeCheckRunner` looked for `_V3.0` to tell the files this run wrote, so from 3.1 an extra file would
  have gone uncounted.
- Now: it looks for `_V` and the running generator's major.minor (`Version.MAJOR`). The tests that turn
  a run's stamps into 2.2's (`GeneratedMergeTest`, `MigrationCheckTest`) read the same.
- Checked: `GeneratedMergeTest`, `MigrationCheckTest` with `elasticoreVersion=3.1.261120-SNAPSHOT`
  (they failed before: the stamps stayed `_V3.1`, so the project did not read as one 2.x generated);
  `elcoreTreeCheck` 0 differ / 0 missing / 0 extra in CI.
- Files: `verify/TreeCheckRunner.java`, `GeneratedMergeTest.java`, `MigrationCheckTest.java`.
