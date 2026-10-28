### New Features (v3 규칙 엔진 - MyBatis)

#### `mode: mybatis` - 같은 모델을 JPA 대신 MyBatis 로 생성한다
- 왜: v3 는 JPA 코드만 썼다. 공공·금융·SI 현장의 상당수는 MyBatis 를 쓰고, 그런 프로젝트는 ElastiCORE 를 쓸 수 없었다.
- 무엇: env.yml `config.mode` 의 한 단어(2.x 가 이미 읽는 키)로 정한다. 새 DSL 키는 없다. 레이어드 Java, Spring Boot 3·4.
  - DTO·검색 DTO·컨트롤러·접근 검사·도메인 매퍼는 JPA 와 같고, 서비스는 JPA 판과 메서드 이름·시그니처가 같다 - 컨트롤러와
    확장 서비스는 그대로.
  - 엔티티: JPA 규칙이 그리는 클래스에서 JPA·Hibernate·Spring Data 어노테이션과 수명주기 콜백을 걷어낸 평범한 클래스.
  - `<Entity>Repository` (MyBatis `@Mapper`) + 같은 패키지 경로의 `<Entity>Repository.xml`: to-one 관계를 LEFT JOIN 해 한
    문장으로 읽는 resultMap(N+1 없음 - 페이지는 count 와 본문 두 문장), 검색 DTO 조건마다 `<if>`, 엔티티 속성 목록으로만
    하는 정렬(그 밖은 400), DB 별 페이지(`LIMIT/OFFSET`, Oracle·Tibero·SQL Server·DB2 는 `OFFSET ... FETCH`),
    insert(생성 키 회수)·update(버전 비교·증가)·delete.
  - `db/<domain>-schema.sql`: 테이블과 외래키(참조 중인 행 삭제는 JPA 처럼 409). H2 용이며, 다른 DB 는 일부 타입 이름이
    달라 이 파일에서 마이그레이션을 시작한다고 파일 머리에 적는다.
  - JPA 가 하던 `@genid`·`@jpa:CreatedDate`·`@jpa:LastModifiedDate` 는 서비스가 하고, `@updatable(false)` 컬럼은 UPDATE 에서
    뺀다. 이름은 Spring 의 JPA 명명과 같다(테이블·컬럼은 소문자 snake, 관계 컬럼 `field_targetkey`, enum 은 같은 코드 값) -
    한 테이블을 두 방식이 읽는다. `@formula` 필드는 엔티티에 남되 MyBatis 가 읽지 않아 null 이고 검색 조건에서 빠진다(경고).
  - 아직 쓰지 않는 것은 조용히 빼지 않고 빌드가 멈춘다: 컬렉션 관계·값 컬렉션·`@embedded`·복합 키·상속·Money·JDBC 타입이
    없는 타입·`@joinBy`·IDENTITY 가 아닌 키 생성·키가 아닌 `@sequence`·`@jpa:CreatedBy`/`LastModifiedBy`, 조인한 행보다 먼 검색
    경로(빼면 조건부 수정·삭제가 모든 행에 미침), 컬럼이 아닌 `@owner`, DDD·헥사고날·Kotlin(빌드의 선언이든 도메인의
    `publisher.rules` 든).
  - 프로젝트 단위의 거절: `-Pv2` 와 함께(2.x 코드는 JPA), 2.x 코드를 지키는 도메인과 같은 프로젝트(그 도메인을 v3 로
    다시 쓰지 않도록), JPA 엔티티가 MyBatis 엔티티를 가리킴(반대는 됨). `jpa,mongo` 처럼 jpa 와 함께 적힌 모드는 전처럼
    JPA 를 쓴다.
  - 도메인을 `jpa` 로 되돌리면 생성했던 매퍼 XML 과 스키마를 지운다(손대지 않은 것만).
- 기반: 번들을 아키텍처·언어에 더해 **저장 방식**으로도 고른다(`RulesConfig.Persistence`, `bundleFor(아키텍처, 언어, 저장 방식)`).
  XML·SQL 파일의 생성 표시(`//ecd:`)는 그 언어의 주석 안에 쓴다(`<!-- //ecd:... -->`, `-- //ecd:...`).
- 런타임 라이브러리 (`elasticore-springboot3`·`-springboot4`):
  - `ConvertedEnumTypeHandler`(enum 을 JPA 변환기와 같은 값으로), `ElastiCoreMyBatisAutoConfiguration`(MyBatis 에 DB 종류를
    알림). 이 자동 설정은 MyBatis 가 있는 앱에서 기본으로 켜지며, 앱이 직접 쓴 매퍼의 `databaseId` 문장도 그 DB 에서 쓰이게
    한다. 끄려면 `elasticore.mybatis.database-id=false`.
  - **`spring-boot-starter-data-jpa` 를 더는 끌어오지 않는다** (implementation → compileOnly). 끌어오면 MyBatis 앱 안에서
    Hibernate 가 떴다. JPA 프로젝트는 README 의 빌드처럼 data-jpa 스타터를 직접 선언한다 - 저장소의 JPA 샘플은 모두 이미
    그렇다. 증상: 기동 시 EntityManagerFactory 빈이나 Hibernate 를 못 찾으면 `spring-boot-starter-data-jpa` 를 선언한다.
  - `jakarta.persistence-api` 는 `api` 로 (NotFoundException 이 EntityNotFoundException 을 상속하므로 그것을 던지는 코드가
    컴파일되려면 필요).
  - `VersionCheck`: spring-orm 이 없는 앱에서는 `OptimisticLockingFailureException`(같은 409)을 던진다. 전에는 MyBatis 앱에서
    `NoClassDefFoundError` 였다.
- 확인:
  - `MyBatisGenerationTest`(생성물, 거절들, 다른 도메인 자기참조 순환, 2.x 도메인과 함께, JPA→MyBatis 관계, `@updatable(false)`,
    jpa 로 되돌릴 때 정리, 옆에 MyBatis 도메인이 있어도 JPA 도메인 출력이 바이트 단위로 같음), `MyBatisSearchResolutionTest`,
    `PersistenceAxisTest`, `DefaultGenerationTest`. 독립 리뷰의 지적(H1·H2·M1~M6·L1~L8)을 반영.
  - 새 모듈 `elasticore-template-mybatis`: 생성·컴파일 후 H2 위에서 API 를 돈다 - 관계 조인, 모든 검색 조건, 페이지 문장 수 2,
    정렬과 거절, 버전 409, 참조 행 삭제 409, unique 409, 시각 찍기. `OFFSET ... FETCH` 분기는 H2 Oracle 모드에서 SQL 까지 확인.
  - Spring Boot 4.0.8 + mybatis-spring-boot-starter 4.0.0 프로젝트를 만들어 같은 호출을 돌림(Hibernate 없음).
  - JPA 쪽: `elcoreRulesCheck -Pstrict` 157 MATCH / 0 DIFF, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra, base·런타임·
    template·template-ddd·template-kt·petstore 테스트 통과.
- 관련 파일: `RulesConfig.java`, `RuleCatalog.java`, `RulesGenRunner.java`, `rules/mybatis/*`, `v3/mybatis_*.tmpl`,
  `ServiceAccessRules.java`, `MapperAssembleRules.java`, `MapperFileRules.java`, `RepositoryHelperBaseRules.java`, `SourceCode.java`,
  `EntitySpec.java`, `HashUtils.java`, 런타임 `build.gradle`·`VersionCheck.java`·`mybatis/`·`autoconfigure/`,
  `elasticore-env-guide.md`(2.3 절), `elasticore-rule-guide.md`
