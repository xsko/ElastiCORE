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
  - 새 행 판단(버전 또는 키가 없으면 INSERT), UPDATE 의 버전 비교·증가(낡으면 409, 메모리의 버전도 올림 - 루트도 멤버도,
    새 멤버는 행이 받은 0);
  - 멤버 컬렉션은 저장된 행과 비교 - 빠진 것 DELETE, 남은 것 UPDATE(멤버 키 유지), 새것 INSERT;
  - to-one 멤버(`@OneToOne @cascade`)는 루트보다 먼저 쓰고, 바뀌거나 빠진 이전 멤버를 지움 - 저장된 행은 `findStoredById`
    (`flushCache="true"`)로 읽음: 트랜잭션 안에서 MyBatis 세션 캐시는 저장 중인 바로 그 객체를 돌려줘 이전 멤버를 못 찾았다;
  - `delete()` 는 낡은 버전이면 아무것도 지우기 전에 409(`lockVersion` 이 행을 잠금), 멤버 컬렉션·행·to-one 멤버(저장된
    것과 메모리에 든 것) DELETE 뒤 이벤트 발행.
    도메인 규칙 루트의 파생 `deleteBy...` 는 행을 읽어 하나씩 `delete()`(삭제 가드·멤버 - Spring Data JPA 처럼);
  - 같은 트랜잭션에서 다시 읽은 행(세션이 주는 같은 객체)은 컬렉션을 다시 채우지 않음(런타임 `mybatis/LoadedRows`) -
    메모리의 변경을 덮지 않게;
  - `@PrePersist`·`@PreUpdate`(불변식 리스너)·`@PreRemove`·`@PostLoad`·`@PostPersist` 와 `@DomainEvents` 는 루트가
    구현하는 런타임의 `io.elasticore.springboot3.domain.Lifecycle` 로 옮김(`mybatis/MyBatisDddRules` 가 도메인 규칙이 쓴
    JPA·Spring Data 애노테이션을 지우고 그 메서드를 Lifecycle 에서 부름) - 어댑터가 같은 순간에 부르고, 저장 뒤
    이벤트를 `ApplicationEventPublisher` 로 발행;
  - 감사 필드(시각·`AuditorAware` 의 사람)를 저장 때 넣음 - 멤버가 상속한 것도, 멤버만 가진 것도;
  - `EntityManager.find` 대신 어댑터가 `Lookup.Source` - 런타임의 `ElastiCoreLookupAutoConfiguration` 이 `Lookup` 빈을 만듦
    (참조 검사·`@snapshot`). 어떤 어댑터도 찾지 않는 타입(JPA 도메인의 루트)은 앱의 `EntityManager` 로;
  - 읽기는 조인 한 단계 + 컬렉션은 페이지 단위로 한 문장씩, 읽은 뒤 `afterLoad`.
- 애플리케이션 서비스(`v3/mybatis_port_service.tmpl`)는 포트만 받음 - 도메인 포트, 검색 포트(검색 DTO 를 받음 -
  `findAll(SrchDTO, owner, Pageable)`, `narrows`), `Lookup`. 쓴 뒤에는 포트로 다시 읽어 답함.
- 바운드된 애그리거트의 멤버는 `@service` 를 적어도 자기 서비스·컨트롤러가 없고 루트가 씀 - `@aggregate(X)` 도, 모델이
  멤버로 만든 것(`@cascade`, `@root` 의 `@OneToMany(mappedBy)`)도(`MyBatisSql.aggregateBoundaries` 가 도메인 규칙과 같은
  `SpecFactory.aggregateOfModel` 로 받음). 멤버 매퍼는 헬퍼에 남고 루트의 어댑터만 부름.
- `publisher.rules` 로 `ddd-*`·`hex-*` 룰셋을 직접 고르면서 `mybatis-ddd` 를 빠뜨리면 거절(JPA 콜백이 죽은 애노테이션으로 남아
  불변식·생명주기가 돌지 않았다).
- `Rows` 의 필드 캐시는 `ClassValue` - 클래스 이름으로 잡아 DevTools 재시작 뒤 옛 클래스의 `Field` 를 썼다(모든 읽기·쓰기 실패).
- 아웃박스(`outbox: true`): 메시지 POJO + MyBatis 매퍼(애노테이션 SQL) + 변경을 직접 쓰는 릴레이. 대기 행 잠금은 DB 마다
  - PostgreSQL·Oracle `FOR UPDATE SKIP LOCKED`, SQL Server `UPDLOCK, READPAST`, H2 `FOR UPDATE`. 테이블은 프로젝트 DDL 에.
- 확인: `MyBatisGenerationTest.aHexagonOverMyBatisIsItsPortsAndAnAdapterThatDoesWhatJpaDid`, `PersistenceAxisTest`;
  새 모듈 `elasticore-template-ddd-mybatis` 가 `elasticore-template-ddd` 의 모델(shop 헥사고날, billing ddd, catalog
  레이어드)과 테스트 전부를 MyBatis 로 옮겨 돌림 - 경계, 불변식, 멤버 비교, 낡은 버전, 1:1 멤버, 이벤트·컨텍스트 간 이벤트,
  아웃박스·프로세스 매니저, 선언 쿼리, dbms 포트, 아키텍처 테스트, 모든 값 타입 - 107/107(H2). `DomainOnDatabase` 를
  CI 의 Oracle·PostgreSQL·SQL Server 에서도 돌림(dbTest) - 1:1 멤버 교체·해제, 낡은 삭제, 한 트랜잭션의 두 번 저장,
  파생 쿼리(`%` 그대로, 빈 `In`, `findAllByOrderBy...`, 하나씩 지우는 `deleteBy`), 순서 없는 포트 페이지·NULL 값 포함.
  `MyBatisGenerationTest.membersTheModelMakesAreWrittenByTheirRoot`·`domainRuleSetsWithoutMyBatisDddAreRefused`.
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
- 이름으로 읽는 쿼리: `Containing`·`StartingWith`·`EndingWith` 는 값의 `%`·`_` 를 글자 그대로(`Rows.like` + `ESCAPE '!'`,
  SQL Server 는 `[` 도), 빈 목록의 `In`/`NotIn` 은 `1 = 0`/`1 = 1`(`IN ()` 는 어느 DB 도 읽지 않음), `findAllByOrderByX`
  (By 바로 뒤 OrderBy - 이전엔 빌드가 멈춤), `Stream` 과 조건 없는 `deleteBy`·`existsBy` 는 거절.
- `@audited` 는 거절(행의 이력은 Envers 의 것). 레이어드에서 루트가 to-one 관계로 갖는 서비스 없는 멤버는 거절(이전엔 말없이
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
- 페이지 count 는 SQL 의 마지막 `ORDER BY`(괄호·문자열·주석 밖)를 빼고 셈 - SQL Server 는 파생 테이블 안의 `ORDER BY` 를
  거절한다(CI 의 SQL Server 에서 실패). JPA·JDBC 런타임(`SqlPortGateway.withoutOrderBy`)도 같아 JPA 프로젝트의 포트도
  바뀜: 이전엔 `ORDER BY` 째 셌다(SQL Server 에서 실패, 다른 DB 는 같은 수). 뒤에 `LIMIT`·`OFFSET`·`FETCH` 가 있으면 그대로
  둔다(그 행들만 센다). MyBatis 판은 `ORDER BY` 가 `/* if */` 블록 안이면 블록을 닫아 둔다.
- 순서 없는 SQL 의 페이지는 SQL Server 에서 `ORDER BY (SELECT NULL)`, 값 하나씩의 목록·페이지는 그 값 타입으로 읽어 첫 컬럼
  (NULL 은 null - 이전엔 맵으로 읽어 NULL 컬럼이 빠지면 다음 컬럼을 줬다), 모든 컬럼이 NULL 인 행(MyBatis 는 null 로 줌)은
  필드가 모두 null 인 DTO, `void` 메서드는 문장만 실행.
- 확인: `MyBatisGenerationTest.aDatabasePortOfAMyBatisDomainIsAMapperAndAnAdapter`·`aPortConditionThatIsAnExpression...`,
  샘플 `HexagonalPortTest`(조건부 SQL, 페이지, 쓰기, 어댑터가 빈), 런타임 `JdbcDbmsGatewayTest`, 기존 `DbTransactionGateway*Test`
  그대로 통과, JPA 샘플(`elasticore-template-ddd` 93/93, 펫스토어) 그대로.
- 관련 파일: `PortSpec.java`(`sql`, `mode`), `SpecFactory.java`, `mybatis/MyBatisPortRules.java`(새),
  `v3/mybatis_port{Mapper,Xml,Adapter}.tmpl`(새), 런타임 `bean/SqlPortGateway.java`·`JdbcDbmsGateway.java`·
  `DbmsGatewayRegistrar.java`·`PortRows.java`(새), `DbTransactionGateway.java`, `EnableElastiCore.java`,
  `ElastiCoreAutoConfiguration.java`.

- 2.x 출력 그대로: `elcoreRulesCheck -Pstrict` 157/0/2, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
