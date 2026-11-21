### Bug Fixes (TypeScript - a team's domain-level file)

#### A TypeScript run asks a `DomainArtifactContributor` listed under `ts:` for its files
- Why (issue 266): the DDD customize guide tells a team to list its TypeScript rule set with `ts-model` under `ts:` of
  `publisher.rules`. Its per-entity files (`ArtifactContributor`) were written, but a domain-level one
  (`DomainArtifactContributor`) was never asked for: the TypeScript branch of the runner returned after the enumerations
  and `apiSupport.ts`. No file, no line in the log, no warning.
- Now: the TypeScript run asks the rule sets it runs for their domain files after `apiSupport.ts`, and writes them to
  `src/main/ts/` as the per-entity ones. The rule sets are those of `publisher.rules.ts` (or the default `ts-model`),
  never the server's; `ts-model` declares no domain file, so a project without its own rule writes the same.
- Checked: `TsDomainContributorTest` - a team rule under `ts:` writes `src/main/ts/crmUiModel.ts` beside
  `apiSupport.ts` and the DTOs; it failed before the fix (no file) and passes after.
- Files: `RulesGenRunner`, `TsDomainContributorTest` (with the test rule `com.acme.probe.TsUiRules` and its
  `ts/tsUiModel.tmpl`), the DDD customize guide's troubleshooting row (en, ko).
