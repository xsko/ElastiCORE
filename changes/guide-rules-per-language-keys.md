### Documentation (rule guide - `publisher.rules.kt` / `publisher.rules.ts`)

#### The rule guide shows how `publisher.rules.kt` and `publisher.rules.ts` are written
- Why: "Choosing rules per language" listed `publisher.rules.kt` ("takes precedence over the one above if present") and
  `publisher.rules.ts` with no example. The runner reads them as the path `publisher` -> `rules` -> `kt` / `ts`, so they
  are keys under `rules`, and `rules` cannot be a list and a map at once: the two never sit beside a `publisher.rules`
  list. Written as a flat key they are not read at all. The DDD customize guide told a project to list its TypeScript
  rule "in `publisher.rules.ts` in `env.yml`", which in a Java build stops `elcore3`.
- Now: the guide shows the one form that is read - `rules: { kt: [...], ts: [...] }` - says a run with no key of its
  own in the map (a Java build's `elcore3`, a Kotlin one with only `ts:`) stops with `Unknown rule set '{ts=[ts-model]}'`,
  so a Java build cannot choose its TypeScript rules in `env.yml`, and names the spellings that are ignored without a
  warning. The table says both keys are for a Kotlin build only. The customize guide's troubleshooting row says the same.
  No code changed: `.kt` and `.ts` are read the same way, so there was no loader inconsistency to fix.
- Checked: standalone projects (plugin and `elasticore-base` from this branch), `elcore3 -PlistRules` / `elcore3ts -PlistRules`:
  - Kotlin build, `rules: { kt: [jpa-springboot-kt, ddd-aggregate] }` -> `[rules] demo: jpa-springboot-kt + ddd-aggregate`;
    with `ddd-reference` added, the generated `PurchaseOrder`, its DTOs, mapper and service differ from the plain
    `[jpa-springboot-kt]` run (6 files), so the list is used, not only listed. `ts: [jpa-dto, jpa-dto-file-ts]` in the
    map -> `elcore3ts` lists `jpa-dto + jpa-dto-file-ts`. The guide's example (`kt:` + `ts: [ts-model]`): `elcore3`
    and `elcore3ts` succeed and write the Kotlin server and the `.ts` client, and `compileKotlin` passes.
  - Not read (the run took the default or the `publisher.rules` list): `"rules.kt":` / `"rules.ts":` under `publisher:`
    with and without a `rules:` list beside them; `"publisher.rules.kt":` / `"publisher.rules.ts":` under `config:`.
  - Java build with `rules: { ts: [ts-model] }`, and Kotlin build with only `ts:`: `elcore3` fails with
    `Unknown rule set '{ts=[ts-model]}'`; Java build with a `kt:` map: `Unknown rule set '{kt=[jpa-springboot-kt'`.
- Later in the same release (`rules-map-no-own-key.md`): the failure this entry documents is fixed. A run with no key
  of its own in the map takes its default rules, so a Java build chooses its TypeScript rules with `ts:` alone, and the
  guides say so. The `Unknown rule set` lines above describe the behaviour before that fix.
- Files: rule guide (en, ko), DDD customize guide (en, ko).
