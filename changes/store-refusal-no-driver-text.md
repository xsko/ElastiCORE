### Security Fixes (REST and GraphQL errors - a refusal of the store)

#### A taken unique value is answered without the driver's text - no index, no value
- Why: over MongoDB (Java and Kotlin) a taken `@unique` value made a 409 whose `detail` was the driver's message -
  `Write operation error on server 127.0.0.1:27117. Write error: WriteError{code=11000, message='E11000 duplicate key
  error collection: club.member index: email dup key: { email: "kim@a.com" }'}` - the server address, the collection,
  the index and the value (an email). The generated `ApiErrors` answered the store's `DuplicateKeyException` as the
  service's own. Over GraphQL it was worse and not only MongoDB: the runtime's `GraphQlErrors` put the exception's
  message in the error for every `DuplicateKeyException`, `DataIntegrityViolationException` and
  `OptimisticLockingFailureException`, so a JPA or MyBatis unique violation answered `could not execute statement
  [Unique index or primary key violation: "PUBLIC.IDX_SHOP_CUST_EMAIL ... VALUES ( /* 1 */ 'grace@example.com' )";
  SQL statement: insert into shop_customer (...)]`. The REST answer over JPA and MyBatis was already the kind only.
- Now: REST (`ApiErrors`, Java and Kotlin) - a `DuplicateKeyException` with a cause that is not SQL (MongoDB) is
  `409 {"code":"unique","detail":"A unique value is already taken."}`, the answer JPA gets; the service's own (a
  create naming a taken identifier, no cause) keeps its message. GraphQL (both runtime libraries) - a store's refusal
  says its kind and the same sentence as REST, read from the SQLSTATE as the REST errors read it: `code` `unique`,
  `referenced`, `required`, `check`, `conflict` (no SQL state: MongoDB) or `stale` (a stale version), with status 409
  as before. `ApiErrors` is a generate-once file: one you have not edited takes the change on the next build; an
  edited one takes it when it merges cleanly (env guide 9-2), else the build says `KEPT(yours ...)` and the branch is
  yours to add. In the samples only the `*ApiErrors` files changed, besides the new tests. Also in 3.0.261120.
- Checked: `elasticore-template-mongo` `ShopApi.aTakenUniqueValueIsA409` and `ClubApi.aTakenLoginIsA409ThatNamesNoValue`,
  `elasticore-template-kt-mongo` `ClubApi.aMemberSignsInAndReadsOnlyWhatIsTheirs`, over HTTP against MongoDB 7
  (code `already-exists` with the E11000 text before the change); `GraphQlErrorsTest.aStoreRefusalNamesNoValue` /
  `eachDatabaseSaysWhichKind` / `theServicesOwnDuplicateKeepsItsMessage`; `elasticore-template-ddd` `GraphQlApiTest.aTakenUniqueValueNamesNoValue` -
  a second `createCustomer` with the same email over GraphQL on H2 (JPA), whose message named the SQL and the email
  before the change.
- Files: `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ApiErrorRules.java` (comment), `GraphQlErrors.java`,
  `GraphQlErrorsTest.java`, `ShopApi.java`, `ktmongo/ClubApi.java`, `GraphQlApiTest.java`, env guide 6.1.5 (GraphQL
  refusals, en and ko).
