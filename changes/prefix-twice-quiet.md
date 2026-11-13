### Bug Fixes (3.0 code - table names)

#### The build no longer warns about an `@table` that starts with `table.prefix`
- Why: rc16 warned on every build about `@table(el_mcp_server_def)` with `table.prefix: el_`, which maps to
  `el_el_mcp_server_def` as in 2.x. A project 2.x generated has exactly that table in its database, so the warning
  was wrong there and came back on every build.
- Before: a warning per entity on every `elcore3`. After: the build says nothing; `elcoreMigrationCheck` lists each
  such `@table` with the table it maps to, once, with what to write if the database has the name without the doubled
  prefix.
- Checked: `TablePrefixAndLargeValueTest` - nothing in the build's output, the line in the migration check report.
- Files: `RulesGenRunner`, `EntityChecks`, the migration guide (en, ko).
