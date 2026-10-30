### New Features (MyBatis - 도메인 모델·헥사고날)

#### `architecture = 'ddd'`·`'hexagonal'` + `mode: mybatis` 를 생성한다
- 왜: rc5 까지 MyBatis 는 레이어드 Java 만 썼고, DDD·헥사고날은 `RulesConfig.mybatisRefusal` 이 빌드를 멈췄다
  ("the domain model and the hexagon rest on JPA's change tracking and lifecycle callbacks"). MyBatis 를 쓰는 팀이
  헥사고날·DDD 구조를 원했다.
- 무엇이: 묶음 `ddd-mybatis-springboot`(= `mybatis-springboot` + DDD 룰셋 + `mybatis-ddd`)와
  `hexagonal-mybatis-springboot`(+ `hex-usecase`). 빌드의 `architecture` 와 도메인의 `mode` 로 저절로 고르고, 도메인
  env.yml 의 `publisher.rules` 에 적은 `ddd-springboot`·`hexagonal-springboot`·`jpa-springboot-v3` 는 MyBatis 판으로 읽는다.
  Kotlin 은 여전히 거절(MyBatis 코드는 Java).
- JPA 가 애그리거트에 스스로 하던 일을 생성되는 저장소 어댑터(`<Root>RepositoryAdapter`, `ddd/DddMyBatisPorts`)가 한다:
  - 새 행 판단(버전 또는 키가 없으면 INSERT), UPDATE 의 버전 비교·증가(낡으면 409, 메모리의 버전도 올림);
  - 멤버 컬렉션은 저장된 행과 비교 - 빠진 것 DELETE, 남은 것 UPDATE(멤버 키 유지), 새것 INSERT;
  - 1:1 멤버(`@OneToOne @cascade`)는 루트보다 먼저 쓰고, 바뀌거나 빠진 이전 멤버를 지움;
  - `@PrePersist`·`@PreUpdate`(불변식 리스너)·`@PreRemove`·`@PostLoad`·`@PostPersist` 와 `@DomainEvents` 는 루트가
    구현하는 런타임의 `io.elasticore.springboot3.domain.Lifecycle` 로 옮김(`mybatis/MyBatisDddRules` 가 도메인 규칙이 쓴
    JPA·Spring Data 애노테이션을 지우고 그 메서드를 Lifecycle 에서 부름) - 어댑터가 같은 순간에 부르고, 저장 뒤
    이벤트를 `ApplicationEventPublisher` 로 발행;
  - 감사 필드(시각·`AuditorAware` 의 사람)를 저장 때 넣음;
  - `EntityManager.find` 대신 어댑터가 `Lookup.Source` - 런타임의 `ElastiCoreLookupAutoConfiguration` 이 `Lookup` 빈을 만듦
    (참조 검사·`@snapshot`);
  - 읽기는 조인 한 단계 + 컬렉션은 페이지 단위로 한 문장씩, 읽은 뒤 `afterLoad`.
- 애플리케이션 서비스(`v3/mybatis_port_service.tmpl`)는 포트만 받음 - 도메인 포트, 검색 포트(검색 DTO 를 받음 -
  `findAll(SrchDTO, owner, Pageable)`, `narrows`), `Lookup`. 쓴 뒤에는 포트로 다시 읽어 답함.
- 바운드된 애그리거트의 멤버(`@aggregate(X)`)는 `@service` 를 적어도 자기 서비스·컨트롤러가 없고 루트가 씀
  (`MyBatisSql.aggregateBoundaries`). 멤버 매퍼는 헬퍼에 남고 루트의 어댑터만 부름.
- 아웃박스(`outbox: true`): 메시지 POJO + MyBatis 매퍼(애노테이션 SQL) + 변경을 직접 쓰는 릴레이. 대기 행 잠금은 DB 마다
  - PostgreSQL·Oracle `FOR UPDATE SKIP LOCKED`, SQL Server `UPDLOCK, READPAST`, H2 `FOR UPDATE`. 테이블은 프로젝트 DDL 에.
- 확인: `MyBatisGenerationTest.aHexagonOverMyBatisIsItsPortsAndAnAdapterThatDoesWhatJpaDid`, `PersistenceAxisTest`;
  새 모듈 `elasticore-template-ddd-mybatis` 가 `elasticore-template-ddd` 의 모델(shop 헥사고날, billing ddd, catalog
  레이어드)과 테스트 전부를 MyBatis 로 옮겨 돌림 - 경계, 불변식, 멤버 비교, 낡은 버전, 1:1 멤버, 이벤트·컨텍스트 간 이벤트,
  아웃박스·프로세스 매니저, 선언 쿼리, dbms 포트, 아키텍처 테스트, 모든 값 타입 - 96/96(H2). `DomainOnDatabase` 를
  CI 의 Oracle·PostgreSQL·SQL Server 에서도 돌림(dbTest).
- 관련 파일: `RuleCatalog.java`, `RulesConfig.java`, `RulesGenRunner.java`, `ddd/DddMyBatisPorts.java`(새),
  `ddd/DddRepositoryPortRules.java`, `ddd/DddReferenceRules.java`, `ddd/DddAggregateRules.java`, `ddd/DddOutboxRules.java`,
  `mybatis/MyBatisDddRules.java`(새), `mybatis/MyBatisSql.java`, `SourceCode.java`(`rewriteVarLines`, `entityListeners`),
  `v3/mybatis_port_service.tmpl`·`mybatis_repositoryAdapter.tmpl`·`mybatis_outbox*.tmpl`(새),
  런타임 `domain/Lifecycle.java`·`mybatis/Rows.java`·`ElastiCoreLookupAutoConfiguration.java`(새), `domain/Lookup.java`.

### New Features (MyBatis - 모든 아키텍처)

#### `@abstract` 부모, 쓴 사람(`@CreatedBy`·`@LastModifiedBy`), repository.yml 쿼리, 1:1 의 반대편
- `@extend(AuditEntity)` 처럼 `@abstract` 부모의 필드가 행의 컬럼이 됨(`MyBatisSql.fields`) - 이전엔 상속 전체를 거절.
  `@abstract` 가 아닌 부모(테이블 상속)는 계속 거절.
- `@jpa:CreatedBy`·`@jpa:LastModifiedBy`: 서비스(레이어드)·어댑터(DDD)가 앱의 `AuditorAware` 빈의 사람을 넣음 - 이전엔 거절.
- repository.yml 의 메서드가 MyBatis 매퍼에 생김(`mybatis/MyBatisQueries`) - 이전엔 MyBatis 도메인에서 **말없이 빠졌다**.
  이름으로 읽는 쿼리(Spring Data 의 이름 규칙 - 조건·OrderBy·First/Top·페이지), `nativeQuery: true` SQL(엔티티는 키로 다시
  읽어 조인한 행까지, DTO 는 별칭으로, 쓰기는 바뀐 행 수). JPQL 은 이유를 밝혀 거절.
- `@OneToOne(mappedBy=...)` 쪽은 상대 행의 키 컬럼으로 조인해 읽기만 함 - 이전엔 이쪽에도 키 컬럼을 만들고 null 을 썼다.
- `@audited` 는 거절(행의 이력은 Envers 의 것). 레이어드에서 루트가 1:1 로 갖는 서비스 없는 멤버는 거절(이전엔 말없이
  키만 null 로 씀).
- `biginteger` 가 `NUMERIC(38,0)` - 이전엔 BIGINT 라 큰 값이 넘쳐 409.
- 확인: `MyBatisGenerationTest`(추상 부모·쓴 사람, 선언 쿼리, JPQL 거절, 구체 부모 거절), 샘플의 `DeclaredQueryTest`·
  `ApiContractTest`(감사)·`TypeRoundTripTest`.

#### MyBatis 도메인의 dbms 포트를 MyBatis 매퍼와 어댑터로 생성한다
- 왜: 포트는 런타임 프록시가 JPA `EntityManager` 로 SQL 을 실행했다 - JPA 없는 MyBatis 앱에서는 실행기 빈조차 만들 수 없었다.
  MyBatis 를 쓰는 팀은 SQL 을 매퍼 XML 에서 보고 고친다.
- 무엇이: `mybatis/MyBatisPortRules` - 포트 SQL 을 담은 `<Port>Mapper`(+ XML)와 포트를 구현하는 `<Port>Adapter`(빈 - 런타임
  프록시는 비켜섬). SQL 은 DSL 그대로, `:name`→`#{}`, `/* if:name */` 줄·블록→`<if>`, 페이지는 count 와 한 페이지, 정렬
  코드는 런타임과 같이 검사해 ORDER BY 로. 결과는 런타임과 같은 규칙(`PortRows` - 런타임 게이트웨이와 같은 변환).
  조건에 JEXL 식을 쓴 포트는 거절.
- 런타임: `DbTransactionGateway` 를 `SqlPortGateway`(SQL 처리·결과 변환)와 JPA 실행으로 나누고, JPA 없는 앱을 위한
  `JdbcDbmsGateway` 를 더함(`@EnableElastiCore` 는 `DbmsGatewayRegistrar` 로, 자동 구성은 `EntityManagerFactory` 가 없을 때).
  페이지 count 의 `AS X` 를 `X` 로 - Oracle 은 파생 테이블 별칭에 `AS` 를 받지 않는다.
- 확인: `MyBatisGenerationTest.aDatabasePortOfAMyBatisDomainIsAMapperAndAnAdapter`·`aPortConditionThatIsAnExpression...`,
  샘플 `HexagonalPortTest`(조건부 SQL, 페이지, 쓰기, 어댑터가 빈), 런타임 `JdbcDbmsGatewayTest`, 기존 `DbTransactionGateway*Test`
  그대로 통과, JPA 샘플(`elasticore-template-ddd` 93/93, 펫스토어) 그대로.
- 관련 파일: `PortSpec.java`(`sql`, `mode`), `SpecFactory.java`, `mybatis/MyBatisPortRules.java`(새),
  `v3/mybatis_port{Mapper,Xml,Adapter}.tmpl`(새), 런타임 `bean/SqlPortGateway.java`·`JdbcDbmsGateway.java`·
  `DbmsGatewayRegistrar.java`·`PortRows.java`(새), `DbTransactionGateway.java`, `EnableElastiCore.java`,
  `ElastiCoreAutoConfiguration.java`.

- 2.x 출력 그대로: `elcoreRulesCheck -Pstrict` 157/0/2, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
