### Internal (v3 규칙 엔진 - 저장 방식 축)

#### 번들을 고르는 세 번째 기준으로 저장 방식(persistence)을 둔다 - JPA 출력은 그대로
- 왜: v3 규칙 엔진은 JPA 코드만 쓴다. MyBatis 등 JPA 를 쓰지 않는 저장 방식을 붙이려면, 아키텍처·언어와 나란히 "어디에 저장하는가"로 번들을 고르는 자리가 먼저 있어야 한다.
- 무엇: `RulesConfig.Persistence` (JPA, MYBATIS, MONGO) 와 `persistenceOfMode` 를 두고, 번들 선택(`bundleFor(아키텍처, 언어, 저장 방식)`)이 도메인의 저장 방식을 거친다. 새 DSL 키는 없다 - 2.x 가 이미 읽는 env.yml `config.mode` 의 한 단어가 저장 방식이다.
- 동작:
  - JPA 는 지금까지와 같은 번들을 받는다.
  - `mode: mybatis` 는 "MyBatis 코드는 아직 생성되지 않는다"며 아무 소스도 쓰지 않고 거절한다. 2.x 생성기로 안내하지 않는다 (2.x 도 쓰지 않으므로).
  - `mode: jpa,mongo` 처럼 jpa 와 함께 적힌 경우는 전처럼 JPA 코드를 쓰고 나머지는 경고로 알린다.
- 확인: `PersistenceAxisTest`, `DefaultGenerationTest` (mybatis 거절, jpa,mongo 생성), `elcoreRulesCheck -Pstrict`, `elcoreTreeCheck` 결과 변화 없음.
- 관련 파일: `RulesConfig.java`, `RulesGenRunner.java`
