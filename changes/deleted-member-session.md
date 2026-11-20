### Security Fixes (sign-in - a member kept under `@deleted`)

#### A `@signin` entity with `@deleted` and no `refresh=request` is warned about; the MongoDB session fix is run over HTTP
- Why: a member deleted under `@deleted` cannot sign in again, but without `@signin(refresh=request)` a session is not
  read again - in any store - and keeps working, an administrator's role included, until it expires. The guides say
  so; the build said nothing. Over MongoDB `me()` also read the deleted document (fixed with Kotlin MongoDB, #239, and
  in 3.0.261120): `GET /member/me` answered 200 with it, and `refresh=request` kept the session.
- Now: the build warns - `[Shop] Member: a member deleted (@deleted) signs in no more, but a session it already has
  keeps working - an administrator's role included - until it expires. Say @signin(refresh=request) to end it with
  its next request.` Generated code does not change. JPA and MyBatis were checked in layered, ddd and hexagonal (JPA
  in Java and Kotlin): `me()` reads through a `findById` that already leaves a deleted row out (the repository's
  `@Query ... IS NULL`, the mapper's `findById` statement).
- Checked: `MongoGenerationTest.aDeletedMemberWhoseSessionIsNotReadAgainIsWarnedAbout`,
  `overJpaAndMyBatisMeReadsThroughAFindByIdThatLeavesADeletedRowOut`. `elasticore-template-mongo` (Java) gains a
  `club` domain (a `@signin(refresh=request)` member with `@deleted`) and `ClubApi`: over HTTP against MongoDB 7 a
  member signs in, deletes themselves, cannot sign in again, and its session gets 401 from `/member/me` (200 with the
  deleted document on 3.0.261119).
- Files: `ServiceAccessRules.java`, `MongoServiceFileRules.java` (comment), `MongoGenerationTest.java`,
  `elasticore-template-mongo` (`club` blueprint, `ClubApi.java`, `ClubApiTest.java`, `ClubApiOnServerTest.java`,
  `schema.yml`).
