### New Features (TypeScript client)

#### `configureApi` and a per-call `RequestInit` in the generated TypeScript client
- Why (issue 176): the client could set only a base URL. An API on another origin needs `credentials: 'include'`
  for its session cookie, a token needs an `Authorization` header, a test or a server (SSR) needs its own `fetch`,
  and one call may need an `AbortSignal` - none of these could be passed without editing `apiSupport.ts`.
- What: `apiSupport.ts` gains `configureApi({ baseUrl?, fetch?, init? })`. `init` is a `RequestInit`, or a function
  called for each request (sync or async). Every generated function (`create`, `findById`, `search`, ... the
  lifecycle commands, `signIn`, `signOut`, `me`) takes an optional `init?: RequestInit` last; `request` and
  `requestOrNull` take it too. Headers merge `Content-Type`, then the configured ones, then the call's - the later
  one wins, whatever its case. `setApiBaseUrl` stays.
- Unchanged: a client that calls neither sends the same request as before (method, `Content-Type`, body) to the
  same URL with the global `fetch`. Existing callers compile unchanged: the new parameter is optional and last.
- Checked: `check/apiSupport.test.ts` in `elasticore-template-kt` runs the generated `apiSupport.ts` in node - the
  request without configuration, and a configured base URL, fetch, function `init` read for each request, and a
  per-call `Headers` and `cache` over it (15 tests pass). `elcore3tsVerify -Pstrict` compiles the generated
  clients; the pet store's `webBuild` (`noUnusedLocals`) builds. `TypeScriptModelTest` and
  `SignInAndReferencesTest` assert the generated signatures and calls.
- Files: `ts/apiSupport.tmpl`, `TypeScriptApiRules.java`, `TypeScriptModelTest.java`, `SignInAndReferencesTest.java`,
  `elasticore-template-kt/src/main/ts/check/apiSupport.test.ts`, `elasticore-rule-guide.md` (en, ko).
