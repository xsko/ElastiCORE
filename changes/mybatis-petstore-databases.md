### New Features (v3 규칙 엔진 - MyBatis)

#### `mode: mybatis` 가 펫스토어를 쓴다 - Money·값 객체·컬렉션·`@joinBy`, Oracle·PostgreSQL·SQL Server
- 왜: 펫스토어 모델을 `mode: mybatis` 로 바꾸자 빌드가 멈췄다. 가격(Money), 주소·카드(값 객체), 주문줄(컬렉션), 수량
  (Quantity) - 쇼핑몰의 핵심이 모두 "아직 쓰지 않는 것" 이었다. 그리고 생성 스키마는 H2 용뿐이라 실제 DB 에서 돌려 본 적이
  없었다.
- 무엇:
  - **Money·Quantity**: 한 컬럼(DECIMAL·INTEGER)에 JPA 변환기와 같은 값. 런타임 라이브러리에 `MoneyTypeHandler`·
    `QuantityTypeHandler` 를 두고 XML 이 결과·파라미터마다 이름으로 쓴다. 검색 DTO 가 다른 타입(BigDecimal)으로 들고 있으면
    그 값은 JDBC 타입으로만 묶는다. Oracle 은 `NUMBER(10,2)` 의 100.00 을 100 으로 돌려주므로(CI 의 Oracle 에서 발견),
    `MoneyTypeHandler` 가 컬럼에 선언된 소수 자릿수로 맞춘다 - 어느 DB 든 같은 금액이 같은 모양(`100.00`)으로.
  - **값 객체**(`@embeddable` + `@Embedded`, `@embedded(prefix)`): 그 필드가 행의 컬럼 - 이름은 JPA 와 같다(각자의 컬럼,
    prefix 면 `prefix_field`). resultMap 의 association 으로 행과 조인한 행 모두에서 읽고, insert·update·정렬(`home.city`)·검색
    경로가 그 컬럼을 쓴다. 선택인 값의 컬럼은 스키마에서 NULL 허용. MyBatis 도메인의 DTO 는 값 객체를 싣는다(레이어드 DTO 는
    빼던 것 - 그러면 주소를 보낼 수 없다). 값 객체 안의 값 객체·관계는 거절.
  - **컬렉션**(`@OneToMany(mappedBy=...)`): 대상 매퍼에 `findAllBy{back}In(keys)`·`deleteAllBy{back}(key)`. 서비스의
    `loaded()` 가 한 행이든 한 페이지든 컬렉션마다 한 문장(키 500 개씩)으로 읽어 나눠 준다 - 주문 페이지와 주문줄이 세 문장.
    **애그리거트의 멤버**(루트가 아닌 대상)는 루트와 함께 쓴다: 매퍼가 DTO 의 줄을 엔티티로 옮기고(`@Valid`), 저장 때 insert
    (`@genid`·생성 시각 포함), 수정 요청이 줄을 보내면 지우고 다시 쓰고, 루트를 지울 때 먼저 지운다. 멤버는 서비스 없이
    매퍼(`*_member` 템플릿)만, 헬퍼에 주입된다. 다른 루트의 컬렉션은 읽기만. `mappedBy` 없는 것·`@ManyToMany` 는 거절.
  - **`@joinBy(idField)`**: 행이 id 를 값으로 갖고 관계는 그 컬럼으로 조인만 - 레이어드에서 `@owner(customerId)` 를 쓰는 모양.
    외래키도 그 컬럼에.
  - **DB 별 스키마**: `{domain}-schema.sql`(H2) 에 더해 `-postgresql`·`-oracle`·`-sqlserver`. 타입은 그 DB 의 Hibernate
    방언이 고르는 것(Oracle `NUMBER(19)`·`VARCHAR2(n CHAR)`·`BINARY_DOUBLE`, SQL Server `NVARCHAR`·`DATETIME2`·`BIT`,
    PostgreSQL `TEXT`·`BYTEA`), 생성 키는 그 DB 의 IDENTITY. `@Index` 를 인덱스로, SQL Server 에서는 NULL 허용 유일 컬럼을
    필터 인덱스로. 어느 DB 의 예약어인 테이블·컬럼 이름은 경고. CLOB·BLOB 파라미터는 VARCHAR·VARBINARY 로 묶는다
    (PostgreSQL 이 대형 객체로 묶인 NULL 을 거절).
  - XML: resultMap 을 DTD 순서(id, result, association)로, 조인한 행의 컬럼 별칭이 PostgreSQL 의 63 자를 넘으면 `jN_` 로.
  - 독립 리뷰가 찾은 것: 멤버 안의 컬렉션(저장할 곳이 없어 조용히 잃던 것)은 거절, 자기 서비스가 있는 엔티티는 루트와 함께
    쓰지 않음(두 번 쓰지 않도록 - 매퍼와 서비스가 같은 판정), `lines: []` 는 줄을 모두 지움, 키와 컬렉션뿐인 엔티티는 UPDATE
    없이 행이 있는지만 확인(전에는 없는 문장을 불러 매번 실패), 큰 값(CLOB·BLOB, 4000 자 넘는 문자열)은 정렬 목록에서 빼고
    LIKE 가 아닌 검색 조건은 경고하고 뺌(Oracle ORA-00932), 같은 이름의 엔티티가 다른 도메인에 있어도 자기 도메인 것을 읽음.
- 새 모듈 `elasticore-sample-petstore-mybatis`: 펫스토어 모델을 레이어드 + MyBatis + Spring Boot 4 로. 가격 계산은 확장
  서비스가 한다(레이어드는 `@compute`·`states:` 를 하지 않는다). 테스트(ShopFlow)는 둘러보기·가입·로그인·주문(값 객체,
  줄, 금액)·소유자 범위·삭제(줄 먼저)·409·400 과, 주문 한 페이지가 세 문장인 것을 본다.
- CI 에 "MyBatis on Oracle, PostgreSQL and SQL Server" 작업: Oracle 23 Free·PostgreSQL 16·SQL Server 2022 서비스
  컨테이너에서 펫스토어 흐름과 template-mybatis 의 API 테스트(모든 검색 조건, 모든 컬럼 타입의 왕복, 버전·외래키·유일 409,
  DB 별 페이지 문법)를 각 DB 의 생성 스키마로 돌린다. 건너뛴 DB 가 있으면 실패.
- 확인: base 의 `MyBatisGenerationTest`(컬렉션·멤버·`@joinBy`·값 객체·DB 별 스키마·필터 인덱스·예약어 경고), H2 의 두 모듈
  테스트, CI 의 세 DB. JPA 쪽 출력은 그대로(`elcoreRulesCheck -Pstrict`, `elcoreTreeCheck`).
- 관련 파일: `rules/mybatis/*`(새 `MyBatisMapperMemberRules`), `v3/mybatis_service.tmpl`·`mybatis_repository*.tmpl`·
  `mybatis_schema.tmpl`, `RulesGenRunner.java`, `RepositoryHelperBaseRules.java`, `RuleCatalog.java`, `DddMapperRules.java`,
  런타임 `mybatis/MoneyTypeHandler.java`·`QuantityTypeHandler.java`, `elasticore-env-guide.md`(2.3 절),
  `elasticore-template-mybatis`, `elasticore-sample-petstore-mybatis`, `.github/workflows/ci.yml`
