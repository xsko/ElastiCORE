### New Features (sign-in - `@signin(refresh=password)`)

#### `@signin(refresh=password)` ends the sessions opened before a password change
- Why: `@signin(refresh=request)` reads the signed-in row on every request and follows a deleted row and a changed role,
  but a session someone opened with the old password outlived a password change. Doing it by hand took a second read of
  the row per request, because `me()` answers a DTO and no DTO carries the hash (issue 216; the design agreed there).
- Now: `refresh=password` is a second value of `refresh`. It does everything `refresh=request` does, and the session
  also holds a stamp of the password hash - the first 16 hex characters of the SHA-256 of the stored hash, never the
  hash. The stamp is read in the same one read of the row: the generated filter extends the runtime's new
  `PasswordRefreshFilter` and reads through `signedInRow()`, which the CoreService (and the use case, hexagonal) gets
  beside `me()` and which reads the entity as `me()` does. A session whose stamp is not the row's ends, and the request
  goes on as a visitor's.
  - The generated update reports the hash it stores (`PasswordRefreshFilter.written`). When the transaction commits,
    the session of that row takes the new stamp at once, so a request it sends as soon as the answer arrives meets the
    new one; without a transaction (MongoDB), once the request is answered below 400. So the session that changes
    its own password keeps going, the member's other sessions end, and an administrator who changes a member's
    password ends that member's sessions and keeps their own.
  - A session without a stamp - signed in by the application's own code (`SessionCurrentUser.signIn`), or before the
    option was on - takes the row's at the end of the request that signed it in, or on its first request. The
    generated sign-in is stamped by its own request, so there is no gap before a session's first request.
  - A row that had no password and gets one is not a change; a password removed is.
  - The 2.x-compatible output has no generated sign-in: there `refresh=password` stops the build with a message.
- Unchanged: `refresh=request` generates what it did. `elcore3` of the pet store (JPA, hexagonal Java), the Kotlin
  template (JPA, hexagonal Kotlin), the MyBatis pet store, and the Java and Kotlin MongoDB templates, all on
  `refresh=request`, wrote the same 849 files with this change as without it. `SessionRefreshFilter` is not changed.
- Samples: the pet store, the MyBatis pet store, the Kotlin template and the Java MongoDB template say
  `refresh=password` now; the Kotlin MongoDB template keeps `refresh=request`.
- Checked: through HTTP on the generated applications - `PasswordRefreshTest` (pet store: own change, administrator's
  change, a session stamped by the request that signed it in, a session without a stamp, one read of the customer per
  signed-in request by Hibernate's statistics), `PasswordResetGuideTest` (a reset ends the sessions opened before it and
  keeps the one its confirm signs in), the MyBatis `ShopFlow` (own and administrator's change; a page of orders is
  still the customer read plus three statements), the Kotlin `ShopApiContractTest`, the MongoDB `ClubApi` (own change;
  a first password is not a change). The new pet store tests fail with `refresh=request` (5 of the 6). The existing
  role and delete tests run on `refresh=password`. `PasswordRefreshFilterTest` (runtime), `SignInRefreshTest`
  (generation in every mode, the 2.x refusal), a compile of the hexagonal MyBatis template and of the Kotlin MongoDB
  template with a temporary `refresh=password` model, `elcoreRulesCheck -Pstrict` and `elcoreTreeCheck`.
- Files: `ServiceAccessRules`, `SignInRefreshRules`, `HexUseCaseRules`, `RulesGenRunner`, templates
  `v3/sessionRefreshPassword.tmpl`, `kt/sessionRefreshPassword.tmpl`, runtime `PasswordRefreshFilter` (new); the
  annotation, DSL and rule guides (en, ko); the sample models and tests above.
