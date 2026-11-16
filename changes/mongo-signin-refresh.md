### Bug Fixes (MongoDB - `@signin(refresh=request)`)

#### A MongoDB domain's signed-in session follows its document too
- Why: `@signin(refresh=request)` (#205) and `mode: mongo` (#209) were written side by side. The new rule set
  `spring-signin-refresh` went into every bundle that existed then, and the MongoDB bundle came after: a MongoDB
  domain that asked for it got no filter and no word, and its sessions kept a role taken away, or a deleted member.
- After: `mongo-springboot` carries `spring-signin-refresh`. The MongoDB service has the same `me()`, so the filter
  `MemberSessionRefresh` reads the document again on each request as it does over JPA and MyBatis.
- Checked: `MongoGenerationTest.aSessionFollowsItsDocumentWhenAsked` (fails without the change: no filter is written).
- Files: `RuleCatalog` (`mongo-springboot`), `MongoGenerationTest`.
