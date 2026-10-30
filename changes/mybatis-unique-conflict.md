### Bug Fixes (MyBatis - 고유값 충돌의 답)

#### MyBatis 도메인에서 이미 쓰인 고유값이 JPA 와 같은 409 `unique` 로 답한다
- 왜: 같은 모델의 회원가입에서 이미 있는 로그인(유일 인덱스)을 보내면 JPA 는 409 `code: unique`("A unique value is already
  taken."), MyBatis 는 409 `code: already-exists`(드라이버 메시지 그대로)였다. Spring 의 JDBC 변환기가 MyBatis 의 고유 위반을
  `DuplicateKeyException` 으로 바꾸고, 생성된 `<Domain>ApiErrors` 는 그것을 "생성할 때 이미 있는 식별자"(서비스가 던지는 것)로만
  읽었다. 에이전트 킷의 계약 테스트가 shop 레시피를 MyBatis·헥사고날로 돌리다 찾았다.
- 무엇이: `DuplicateKeyException` 의 원인에 DB 의 `SQLException` 이 있으면 `DataIntegrityViolationException` 과 같은 답(SQLSTATE 로
  `unique`, 모르는 종류면 `conflict`) - 드라이버의 글(SQL 이 든)은 나가지 않음. 원인이 없으면(서비스가 던진 것) 그대로
  `already-exists`. 동시에 같은 키로 만들어 DB 의 기본키가 거절한 것은 이제 `unique`. Java·Kotlin 템플릿.
  `<Domain>ApiErrors` 는 한 번만 생성하는 파일 - 고치지 않은 프로젝트는 다음 생성에서 받고, 고친 프로젝트는 병합으로 받는다.
- 확인: `elasticore-sample-petstore-mybatis` 의 `ShopFlow.aTakenLoginIsAConflict`(코드 `unique`, 글에 SQL 없음)(H2, CI 에서 Oracle·PostgreSQL·
  SQL Server) - 고치기 전 `already-exists` 로 실패. 에이전트 킷 계약(shop on MyBatis) S2.
- 관련 파일: `elasticore-template/v3/apiErrors.tmpl`, `elasticore-template/kt/apiErrors.tmpl`, 샘플 `ShopFlow.java`.
