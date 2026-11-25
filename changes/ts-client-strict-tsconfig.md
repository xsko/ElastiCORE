### Bug Fixes (TypeScript client - the compiler settings of a new Vite project)

#### The TypeScript client compiles under create-vite's React-TS settings (#346)
- Why: a client imported into a Vite + React app did not compile with the checks create-vite's `tsconfig.app.json`
  turns on. For `elasticore-template-kt` (143 files, TypeScript 5.9.3) that was 147 errors:
  - `noUnusedParameters` (34, TS6133): `validateXxx(value, partial = false)` and `validateXxxCodes` never read
    `partial` when the type has no required field to check;
  - `verbatimModuleSyntax` (111, TS1484): `Page` and the DTOs imported as values in the `...Api.ts` files, and a DTO
    imported as a value in DTO, invariant and specification files;
  - `erasableSyntaxOnly` (2, TS1294): `ApiError`'s `status` and `path` were constructor parameter properties.
  The repository's own checks could not see it: neither the template's `src/main/ts/tsconfig.json` nor the pet store's
  `web/tsconfig.json` set the three flags.
- Before and after:
  - A name used only as a type is imported as one: `import type { OrderDTO } from './OrderDTO';` in DTO, value object,
    search DTO, invariant, specification and API files, `import { request, seg, type Page } from './apiSupport';`
    (and `type ExportRequest`). A state enum a lifecycle check compares against (`OrderStatus.PLACED`) stays a value
    import. New `Syntax.typeImportLine` - the JVM languages write their one import.
  - `validateXxx` / `validateXxxCodes` name the parameter `_partial` where no field they check is required (no required
    field, no `@minsize` list); it is still taken, so every validate function is called the same way. A type with a
    required field is unchanged.
  - `ApiError` declares `readonly status: number` and `readonly path: string` and assigns them in the constructor - the
    same public shape and constructor.
  - `elasticore-template-kt` (`src/main/ts`, TypeScript 5.4.5 -> 5.9.3, the pet store's) and the pet store
    (`web/tsconfig.json`) type-check with `noUnusedLocals`, `noUnusedParameters`, `noFallthroughCasesInSwitch`,
    `isolatedModules`, `verbatimModuleSyntax` and `erasableSyntaxOnly` beside `strict`, so `elcore3tsVerify` and
    `webBuild` (CI, "TypeScript - the generated clients compile") fail on a client a new Vite project would refuse. The
    template's `tsconfig.run.json` (CommonJS for `node --test`) turns `verbatimModuleSyntax` off for the emit only. The
    pet store's pages import their DTOs with `import type`.
  - Out of scope: a required relation is still not checked by `validateXxx` (the issue's separate idea).
- Checked: the template-kt client generated before the fix with the new flags: 147 errors (34 TS6133, 111 TS1484,
  2 TS1294); after: `tsc -p .` 0 errors and `npm test` 15/15 in `src/main/ts`, `elcore3tsVerify -Pstrict`,
  `elcore3tsTest`, pet store `webBuild` (`tsc --noEmit` and the esbuild bundle). Unit tests on the generated text:
  `TypeScriptModelTest` (`aTypeIsImportedAsAType`, `partialIsNamedUnreadWhereNothingIsRequired`, the specification
  import), `ShopModelTest` (the API's type and value imports, `ApiError`'s fields), `ExposeMethodsTest`,
  `ExposeMethodsRulesTest`, `SearchExportGenerationTest`, `TypeScriptSearchDtoRelationTest`; they fail without the fix.
  `elcoreRulesCheck -Pstrict` (158 MATCH / 0 DIFF / 2 ACCEPTED) and `elcoreTreeCheck` (0 / 0 / 0): the 2.x output has
  no TypeScript client.
- Files: `Syntax.java`, `TypeScriptDtoFileRules.java`, `TypeScriptApiRules.java`, `DddInvariantRules.java`,
  `DddSpecificationRules.java`, `ts/apiSupport.tmpl`, `elasticore-rule-guide.md` (en, ko), template-kt
  `src/main/ts/package.json`, `package-lock.json`, `tsconfig.json`, `tsconfig.run.json`, pet store `web/tsconfig.json`,
  `web/src/main.ts`, `cart.ts`, `shop.ts`.
