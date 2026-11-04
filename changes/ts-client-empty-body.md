### Bug Fixes (TypeScript client - a 2xx without a body)

#### The TypeScript client reads a 200 without a body as no value
- Why: the generated server answers a void endpoint either way - `POST /signout` with 204, the batch update
  `PUT /batch` with `ResponseEntity.ok().build()`, a 200 with no body. The client's `request` read only a 204 as
  empty and parsed every other 2xx as JSON, so `batchUpdate(inputs)` rejected with
  `SyntaxError: Unexpected end of JSON input` - not an `ApiError` - after the server had saved every row.
  `requestOrNull`, the find by id, did the same on an empty 200.
- What: `apiSupport.ts` reads the body as text first. No text is `undefined` for `request` and `null` for
  `requestOrNull`, whatever the status; a body is parsed as JSON as before. The server's answers do not change.
- Checked: `check/apiSupport.test.ts` in `elasticore-template-kt` runs the generated `apiSupport.ts` in node
  (`elcore3tsTest`) against stubbed responses - a 200 without a body, a 204, a JSON body, a 404 and an empty 200
  for a find, a 400 still an `ApiError`. Without the change, the empty-200 tests fail with that `SyntaxError`.
- Files: `ts/apiSupport.tmpl`, `elasticore-template-kt/src/main/ts/check/apiSupport.test.ts`,
  `elasticore-rule-guide.md` 2, "Error codes and languages" (en, ko)
