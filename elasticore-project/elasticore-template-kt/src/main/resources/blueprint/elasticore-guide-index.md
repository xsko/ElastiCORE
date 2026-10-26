# ElastiCORE guide index

Written with the guides on every generation - do not edit. One line per section:
`<guide>:<first line>-<last line>  <heading>  [words it is about]`.
Search this file for a word - an annotation such as `@access`, a key such as
`publisher.rules`, a Korean term - then read only those lines of that guide,
not the whole guide.

## elasticore-dsl-guide.md

```text
elasticore-dsl-guide.md:1-1179  # ElastiCORE YAML DSL Guide
elasticore-dsl-guide.md:3-80  ## 1. YAML DSL 개요  [entity:, dto:, enumeration:, repository:, port:, domainService:, processManager:, metrics:, fields:, commands:, states:]
elasticore-dsl-guide.md:22-80  ### 1.1 문법은 여섯 가지 원칙이 전부입니다  [@search, meta:, @root, @editable, @expose, @access, @expose(20), entity, dto, enumeration, states, metrics, port, Item, @min, @terms, unitCost, money]
elasticore-dsl-guide.md:82-560  ## 2. Entity 정의
elasticore-dsl-guide.md:84-100  ### 기본 구조  [@annotations, @label(text), @label(label), @desc(desc), field.getDescription, field.getAnnotationValue]
elasticore-dsl-guide.md:102-136  ### Meta 어노테이션  [@abstract, @extend(Parent), @expose(n), @service(n), @pageable, @audited, @dto, @embeddable, @table(name), @schema(name), @Table(schema="name")]
elasticore-dsl-guide.md:118-136  #### DDD 룰셋의 meta 어노테이션  [@root, @aggregate(Root), @OneToMany, @event(A, B), @invariant(조건, '메시지'), @factory(name), @behavior, XxxBase, Xxx, @editable, @readonly]
elasticore-dsl-guide.md:138-157  ### 필드 어노테이션  [@id, @sequence, @genid, @GeneratedValue, @length(n), @default(value), isnull, @search, @updatable(false), @readonly, @noinput, @NotNull, @NotBlank, @ref(path)]
elasticore-dsl-guide.md:159-187  ### 관계 어노테이션  [@ManyToOne, @OneToOne, @ManyToMany]
elasticore-dsl-guide.md:172-187  #### FK 컬럼명 지정  [@column, @col, @JoinColumn(name=...), _id, @expose, @service, postId, postPostId, id, topicId, @id, post, @NotNull]
elasticore-dsl-guide.md:189-216  ### Entity 예시  [@abstract, @updatable, @jpa, @expose, @extend, @id, @sequence, @search, @dtype, @genid, @fetchjoin]
elasticore-dsl-guide.md:218-252  ### 업무 명령 (`commands:`) - DDD 번들  [@root, @expose, @id, @genid, @behavior, shipPartially, SalesOrderShipCommand, @NotNull, @Size, SalesOrderUseCase.ship, SalesOrderCoreService.ship, entity.ship]
elasticore-dsl-guide.md:254-493  ### 생명주기 (`states:`) - DDD 번들  [ship, cancel]
elasticore-dsl-guide.md:260-294  #### 1. 세 곳이 이렇게 연결됩니다  [@db, @json, @code, @root, @expose, @event, @id, @genid, states:, status, OrderStatus, PLACED, PLC, initial, ship:, ship, commands:]
elasticore-dsl-guide.md:296-341  #### 2. 그림으로 보면  [SHIPPED, CANCELLED, cancel, XxxBase]
elasticore-dsl-guide.md:343-352  #### 3. 생성되는 것  [initial, PurchaseOrderBase.ship, raiseShipped, @event, PurchaseOrderBase.canShip, commands:, PurchaseOrderApi.canShip, status]
elasticore-dsl-guide.md:354-367  #### 4. 이동에 일을 더 하고 싶을 때
elasticore-dsl-guide.md:369-436  #### 5. 상태 필드가 여러 개일 때 (한 명령이 여러 생명주기를 바꾸는 경우)  [states:, @root, @expose, @event, @id, @genid, canX, send, status, pay, fee, cancel, DUE, READY, ShipmentApi.canCancel, Shipment, super.cancel]
elasticore-dsl-guide.md:438-475  #### 6. 지울 수 있는 상태 (`delete:`)  [PurchaseOrderBase.canDelete, PurchaseOrderBase.checkDeletable, state.PurchaseOrder.status.delete, @PreRemove, canDelete]
elasticore-dsl-guide.md:477-493  #### 7. 빌드 경고 (생명주기가 빠지는 경우)  [SHP, states:, string, delete:, @default, initial, elcore3]
elasticore-dsl-guide.md:495-544  ### 업무 지표 (`metrics:`) 와 업무 용어 (`@terms`)  [sum, avg, min, max, SUM, count, COUNT, and, @db, @jpa, @terms(sales, 매출액), ai_context.synonyms, description, Integer, Decimal, Float, orderDate, PurchaseOrder]
elasticore-dsl-guide.md:546-560  ### 권한·소유·자격 증명·파생 값 - 어노테이션 요약  [@access, @owner(customerId), @signin, me, signin, signout, signIn, signOut, @login, @password, @expose, @owner, @access(admin), authenticate, @unique, @role]
elasticore-dsl-guide.md:562-656  ## 3. 필드 타입
elasticore-dsl-guide.md:564-604  ### 값 타입 한눈에 (DSL → Java → JSON → TypeScript → Apache Ossie)  [String, Integer, Decimal, Float, Boolean, Date, Time, DateTime, DateTimeTz, Opaque, string, text, longtext, clob, @Lob, json, @JdbcTypeCode(SqlTypes.JSON)]
elasticore-dsl-guide.md:606-636  ### 값 타입 (Value) - 3.0  [Money, Quantity, io.elasticore.springboot3.domain, money, decimal, plus, minus, times, rounded, isNegative, quantity, integer, covers, @min]
elasticore-dsl-guide.md:638-646  ### 컬렉션/배열 타입  [HashMap, java.util.HashMap]
elasticore-dsl-guide.md:648-656  ### 참조/기타 타입  [Object, Opaque, EntityName, @ManyToOne, EnumName]
elasticore-dsl-guide.md:658-715  ## 4. DTO 정의
elasticore-dsl-guide.md:660-680  ### 기본 구조  [@template, @searchable, @search]
elasticore-dsl-guide.md:682-687  ### Meta 어노테이션  [@template(Entity), @template(A,B), @searchable(entity=E, pageSize=n), @jsonview, @jsonview(auto)]
elasticore-dsl-guide.md:689-694  ### 필드 어노테이션  [@ref(field.path), @view(cru)]
elasticore-dsl-guide.md:696-715  ### DTO 예시  [@template, @ref, @searchable, @search]
elasticore-dsl-guide.md:717-768  ## 5. Enumeration 정의
elasticore-dsl-guide.md:719-730  ### 기본 구조  [@db, @json, @annotations]
elasticore-dsl-guide.md:732-736  ### Meta 어노테이션  [@db(field), @json(field)]
elasticore-dsl-guide.md:738-742  ### 필드 어노테이션  [@code, @length(n)]
elasticore-dsl-guide.md:744-768  ### Enumeration 예시  [@db, @json, @length, @code]
elasticore-dsl-guide.md:770-837  ## 6. Repository 정의  [methods]
elasticore-dsl-guide.md:775-790  ### 기본 구조  [methods, ProductRepository:]
elasticore-dsl-guide.md:792-817  ### Repository 예시
elasticore-dsl-guide.md:819-837  ### DDD / 헥사고날 번들에서 (포트로 노출)  [Root, RootRepositoryPort, Page, RootQueryPort]
elasticore-dsl-guide.md:839-864  ## 7. Port 정의 개요
elasticore-dsl-guide.md:843-853  ### 기본 구조  [@native, @pageable]
elasticore-dsl-guide.md:855-864  ### 포트 타입  [dbms, http, grpc]
elasticore-dsl-guide.md:866-997  ## 8. Domain Service 정의  [uses, XxxRepositoryPort, desc]
elasticore-dsl-guide.md:887-917  ### 생성물 — 두 파일  [namespace.domainService, XxxBase, Xxx, PricingDomainServiceBase, uses, PricingDomainService, @factory]
elasticore-dsl-guide.md:919-928  ### 주의  [uses, DSL_WARN, namespace.domainService, namespace.service]
elasticore-dsl-guide.md:930-997  ### 프로세스 매니저 (`processManager:`)  [BillingProcessManagerBase, uses, on, handle, BillingProcessManager, @Override, REQUIRES_NEW, @Transactional, protected, UninitializedPropertyAccessException]
elasticore-dsl-guide.md:999-1054  ## 9. 고급 기능
elasticore-dsl-guide.md:1001-1011  ### 복합 키  [@id]
elasticore-dsl-guide.md:1013-1027  ### 임베디드 엔티티  [@embeddable, @id, @sequence]
elasticore-dsl-guide.md:1029-1043  ### 상속  [@abstract, @id, @extend]
elasticore-dsl-guide.md:1045-1054  ### 다중 템플릿 상속  [@template]
elasticore-dsl-guide.md:1056-1179  ## 10. 주의사항 및 해결방법 (Known Limitations & Workarounds)
elasticore-dsl-guide.md:1058-1079  ### 10.1 잘못된 DSL 타입 (Invalid DSL Types)  [uuid, string, @id, @genid, short, byte, int, char, OffsetDateTime, ZonedDateTime, timestamp, json, clob, blob, decimal]
elasticore-dsl-guide.md:1081-1100  ### 10.2 @min/@max와 실수 값 (Decimal Values)  [@Min, @Max, long]
elasticore-dsl-guide.md:1085-1096  #### 예시  [@min, @max]
elasticore-dsl-guide.md:1098-1100  #### 해결방법
elasticore-dsl-guide.md:1102-1134  ### 10.3 @ManyToOne @id 필드의 SQL 예약어 (Reserved SQL Keywords)
elasticore-dsl-guide.md:1106-1127  #### 예시  [@id]
elasticore-dsl-guide.md:1129-1130  #### 일반적인 SQL 예약어  [order, group, select, from, where, table, index, key, value, user, date, time, timestamp]
elasticore-dsl-guide.md:1132-1134  #### 해결방법  [@Column(name = "\"field_id\"")]
elasticore-dsl-guide.md:1136-1171  ### 10.4 @audited와 연관관계 (Relationships with Audited Entities)  [@org.hibernate.envers.NotAudited, @org]
elasticore-dsl-guide.md:1140-1160  #### 예시  [@audited, @id, @sequence]
elasticore-dsl-guide.md:1162-1171  #### 주의사항  [@NotAudited, @notaudited]
elasticore-dsl-guide.md:1173-1179  ### 10.5 권장사항 요약  [uuid, string, @id, @genid, @min, @max, @audited, @NotAudited]
```

## elasticore-env-guide.md

```text
elasticore-env-guide.md:1-926  # ElastiCORE 환경 설정 가이드 (env.yml)
elasticore-env-guide.md:8-32  ## 1. 기본 구조  [domainName, framework, springboot3, mode, j2ee]
elasticore-env-guide.md:34-108  ## 2. 생성 모드 상세
elasticore-env-guide.md:36-70  ### 2.1 JPA 모드 (기본)  [prefix, @Table, tableSchema, @Table(schema="..."), @schema, autoJoin, findById, @fetchjoin]
elasticore-env-guide.md:72-79  ### 2.2 Multi-Mode
elasticore-env-guide.md:81-88  ### 2.3 MongoDB 모드
elasticore-env-guide.md:90-97  ### 2.4 UML 모드
elasticore-env-guide.md:99-108  ### 2.5 DSL 모드
elasticore-env-guide.md:110-124  ## 3. supportAI 설정  [XxxService, @Tool, @ToolParam, @readonly, @restExclude]
elasticore-env-guide.md:126-212  ## 3-1. Spring Boot 4 / Spring AI 2.0 (ElastiCORE 3.0)
elasticore-env-guide.md:131-155  ### 프로젝트에서 바꿀 것  [io.elasticore.springboot3]
elasticore-env-guide.md:157-185  ### ElastiCORE가 알아서 하는 것  [@json, CustomSerializer, CustomDeserializer, @JsonValue, @JsonCreator, ObjectMapper, JsonMapper, Specification, Page.map, RequestShapeAdvice, typeMismatch]
elasticore-env-guide.md:187-197  ### 직접 고칠 것 (프로젝트 코드)  [org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc, tools.jackson.databind.ObjectMapper, JsonNode.asText, asString, XxxService, supportAI]
elasticore-env-guide.md:199-212  ### Spring AI 2.0에서 도구 쓰기  [@Tool]
elasticore-env-guide.md:214-252  ## 4. 어노테이션 설정  [@entity, @dto, @entity:, @dto:, @genid, @env, @env:, genid, @PrePersist]
elasticore-env-guide.md:254-278  ## 5. jsonview 설정  [@JsonView, auto, @jsonview, @view, @id, @genid, @jpa]
elasticore-env-guide.md:280-417  ## 6. 템플릿/테스트 코드 설정
elasticore-env-guide.md:282-299  ### 6.1 커스텀 템플릿
elasticore-env-guide.md:301-312  ### 6.1.1 트랜잭션 Outbox  [@event, OutboxRepository, OutboxRecorder, OutboxRelay, OutboxPublisher, @EnableScheduling]
elasticore-env-guide.md:314-342  ### 6.1.2 도메인에서 JPA 걷어내기 (`mapping: xml`)  [jakarta.persistence, @Version, persistence, XxxConverter, Proxies.real, SalesOrder, @Entity, @Table, @DynamicUpdate, @Formula, @Audited, @CreatedDate]
elasticore-env-guide.md:344-357  ### 6.1.3 아키텍처 테스트 (`archTest: true`)  [entity, enumeration, event, domainService, domainRepository, service, dto, inboundPort, queryPort, control, persistence, repository, jakarta.persistence]
elasticore-env-guide.md:359-401  ### 6.1.4 바운디드 컨텍스트 (`context:`, 3.0)  [uses, publishes, @embeddable, shop, catalog, ProductDTO, Product, @ManyToOne, OrderPlaced, @template(...), @extend(...), elcore3, elcore, billing]
elasticore-env-guide.md:403-417  ### 6.2 테스트 코드 생성
elasticore-env-guide.md:419-432  ## 6-2. 참조 컬럼 이름·관리자 역할 (3.0)  [@access, customerId, customer_id, adminRole, SessionCurrentUser.signIn, @signin, signin, role, @role, @access(admin)]
elasticore-env-guide.md:434-466  ## 7. apiPath 설정  [@RequestMapping]
elasticore-env-guide.md:468-550  ## 7-1. publisher.rules 설정 (3.0)
elasticore-env-guide.md:479-493  ### 선택 가능한 번들
elasticore-env-guide.md:495-520  ### 우선순위 (4단)  [publisher.suppress]
elasticore-env-guide.md:522-529  ### 주의: 이 설정은 규칙엔진이 읽습니다  [publisher.rules, @ElastiCore]
elasticore-env-guide.md:531-550  ### 언어는 별도 스위치입니다  [elasticore.rules.v3, elcore3ts, build, elcore3]
elasticore-env-guide.md:552-731  ## 8. Namespace 설정  [event, @event(...), entity, domainService, domainService:, service, package]
elasticore-env-guide.md:589-605  ### 8.0 한 줄로: `namespace: base`  [com.acme.erp, entity, enumeration, base.domain.model, event, domainService, domainRepository, base.domain.event, service, dto, base.application, inboundPort]
elasticore-env-guide.md:607-675  ### 8.1 아키텍처 스타일별 Namespace 프리셋
elasticore-env-guide.md:612-629  #### DDD 레이어드 (elasticore-template-ddd 샘플과 동일)
elasticore-env-guide.md:631-653  #### 헥사고날 (ports & adapters)  [publisher.rules, domainRepository]
elasticore-env-guide.md:656-662  #### port가 application 쪽에 놓이는 이유  [port:, HttpServiceProxyFactory, DbmsServiceProxyFactory, infrastructure.port, application.port.out]
elasticore-env-guide.md:664-675  #### 인바운드 포트는 생성된다  [port:, service, application, application.service]
elasticore-env-guide.md:677-731  ### 8.2 Cross-Module Namespace Redirect (멀티 모듈 전용)  [package, elasticore.modules]
elasticore-env-guide.md:733-757  ## 9. Datasource 설정  [orderEntityManagerFactory, order, DbTransactionGateway, EntityManagerFactory, primary]
elasticore-env-guide.md:759-774  ## 10. Resource 설정
elasticore-env-guide.md:776-818  ## 11. 완전한 env.yml 예시  [@entity]
elasticore-env-guide.md:820-904  ## 12. 도메인 범위 지정 코드 생성 (`-Pdomain`)
elasticore-env-guide.md:826-841  ### 12.1 Gradle 설정 필수 단계  [elcore]
elasticore-env-guide.md:843-861  ### 12.2 사용 예시  [hr, bbs]
elasticore-env-guide.md:863-881  ### 12.3 동작 세부  [@extend, @audited]
elasticore-env-guide.md:867-870  #### 1단계: DSL 로딩 (모든 도메인)  [validateGlobalTypeReferences]
elasticore-env-guide.md:872-874  #### 2단계: 필터 적용  [config.resource]
elasticore-env-guide.md:876-881  #### 캐시가 없는 도메인
elasticore-env-guide.md:883-885  ### 12.4 schema.yml 동작  [SchemaCodePublisher]
elasticore-env-guide.md:887-904  ### 12.5 유즈케이스  [hr, config.resource]
elasticore-env-guide.md:906-926  ## 13. Best Practices  [uml, @fetchjoin]
```

## elasticore-dsl-port-guide.md

```text
elasticore-dsl-port-guide.md:1-1154  # ElastiCORE Port DSL Guide
elasticore-dsl-port-guide.md:3-49  ## 1. Port 개요
elasticore-dsl-port-guide.md:9-26  ### 헥사고날에서 이것이 무엇인가  [OrderUseCase, inboundPort, port:, port, OrderRepositoryPort, domainRepository, Page, domainService, uses:]
elasticore-dsl-port-guide.md:28-34  ### 지원 타입  [HttpServiceProxyFactory, DbmsServiceProxyFactory]
elasticore-dsl-port-guide.md:36-49  ### 공통 DSL 구조
elasticore-dsl-port-guide.md:51-536  ## 2. HTTP Port  [@ExternalService, @EnableElastiCore, @HttpExchange, elcore3]
elasticore-dsl-port-guide.md:62-87  ### 2.1 기본 구조  [@url, @auth, @get, @post, @body]
elasticore-dsl-port-guide.md:89-130  ### 2.2 HTTP 어노테이션
elasticore-dsl-port-guide.md:91-113  #### Port 레벨  [@url("baseUrl"), @auth("token"), @timeout, connect, read, write, response, @ExternalService, @post, @body]
elasticore-dsl-port-guide.md:115-119  #### Method 레벨 (단축)  [@get("/path"), @post("/path"), @put("/path"), @delete("/path")]
elasticore-dsl-port-guide.md:121-125  #### Method 레벨 (상세)  [@HttpEndpoint, url, method, contentType]
elasticore-dsl-port-guide.md:127-130  #### 파라미터  [@body]
elasticore-dsl-port-guide.md:132-242  ### 2.3 Runtime Properties (application.yml / application.properties)
elasticore-dsl-port-guide.md:137-150  #### 지원 속성 목록  [url, @url(), authKey, @auth(), apiKey, ignoreSSL, proxyHost, proxyPort, connectTimeout, readTimeout, writeTimeout, responseTimeout]
elasticore-dsl-port-guide.md:152-186  #### application.yml 예시
elasticore-dsl-port-guide.md:188-199  #### application.properties 예시
elasticore-dsl-port-guide.md:201-214  #### 프록시 설정 상세  [ProxyProvider.Proxy.HTTP, SimpleClientHttpRequestFactory.setProxy, proxyHost]
elasticore-dsl-port-guide.md:216-223  #### 속성 우선순위  [apiKey, authKey, HttpAuthProvider]
elasticore-dsl-port-guide.md:225-242  #### 타임아웃 설정 상세  [@timeout]
elasticore-dsl-port-guide.md:244-271  ### 2.4 인증 설정 (상세)  [@url, @auth]
elasticore-dsl-port-guide.md:273-276  ### 2.5 생성 코드  [@ExternalService, HttpServiceProxyFactory, @Autowired]
elasticore-dsl-port-guide.md:278-386  ### 2.6 Multipart Form-Data (파일 업로드)
elasticore-dsl-port-guide.md:280-292  #### 기본 Multipart 요청  [@url]
elasticore-dsl-port-guide.md:294-386  #### Per-Field Content-Type 지정 (Postman 스타일)  [@contentType, @url, @MultipartContentType, MultipartRequest, RestTemplate]
elasticore-dsl-port-guide.md:388-476  ### 2.7 Spring HTTP 서비스 클라이언트로 생성 (`httpPort: spring`, Spring Boot 4)  [@HttpExchange, elcore3, erp, CreditCheckPort, @ImportHttpServices, @url, @timeout, @contentType, PortAuth.passErrorBodies, RestClientException]
elasticore-dsl-port-guide.md:478-536  ### 2.8 실패 처리 - 상대가 실패했을 때  [WebClientRequestException, RestClientException, ResourceAccessException, HttpStatusCodeException, PortFailureContractTest]
elasticore-dsl-port-guide.md:538-880  ## 3. DBMS Port
elasticore-dsl-port-guide.md:540-553  ### 3.1 기본 구조  [@datasource]
elasticore-dsl-port-guide.md:555-562  ### 3.2 DBMS 어노테이션 및 옵션  [@datasource("name"), nativeQuery, pageable, ref]
elasticore-dsl-port-guide.md:564-656  ### 3.3 반환 타입과 실행 방식  [inqueryList, inqueryPage, DTO, inqueryFirst, int, executeUpdate, executeBatchUpdate, long, double, float, Long, Double, Float, decimal, java.math.BigDecimal]
elasticore-dsl-port-guide.md:606-656  #### List 입력 배치 실행 (Multi-row Insert/Update/Delete)  [insert, update, delete, Map]
elasticore-dsl-port-guide.md:658-692  ### 3.4 Dynamic SQL - JEXL 조건부 블록
elasticore-dsl-port-guide.md:662-669  #### 인라인 조건 (한 줄)  [status]
elasticore-dsl-port-guide.md:671-679  #### 블록 조건 (여러 줄)
elasticore-dsl-port-guide.md:681-692  #### JEXL 표현식  [input]
elasticore-dsl-port-guide.md:694-705  ### 3.5 Template Variable Substitution (${...})  [tableName]
elasticore-dsl-port-guide.md:707-735  ### 3.6 sortCode (정렬)  [getSortCode, getSortColumn, getSortAscending, o.created_at, BadRequestException]
elasticore-dsl-port-guide.md:737-746  ### 3.7 SQL 파일 핫리로드  [lastModified]
elasticore-dsl-port-guide.md:748-768  ### 3.8 Query Reference (ref)
elasticore-dsl-port-guide.md:770-788  ### 3.9 다중 데이터소스  [@datasource, EntityManagerFactory, orderEntityManagerFactory, order]
elasticore-dsl-port-guide.md:790-793  ### 3.10 Native Query vs JPQL
elasticore-dsl-port-guide.md:795-808  ### 3.11 결과 매핑  [user_name, userName, Map, snake_case, camelCase, java.sql.Date, LocalDate, java.sql.Timestamp, LocalDateTime, Instant, java.sql.Time, LocalTime]
elasticore-dsl-port-guide.md:810-823  ### 3.12 파라미터 입력 처리
elasticore-dsl-port-guide.md:825-834  ### 3.13 Pageable 자동 추출  [getPageable, pageNumber, page, sizeVal, size]
elasticore-dsl-port-guide.md:836-880  ### 3.14 완전한 DBMS Port 예시  [@datasource]
elasticore-dsl-port-guide.md:882-906  ## 4. gRPC Port  [@server, @client]
elasticore-dsl-port-guide.md:908-1047  ## 5. 환경 설정
elasticore-dsl-port-guide.md:910-925  ### build.gradle  [WebClientException]
elasticore-dsl-port-guide.md:927-968  ### Application 클래스  [ElastiCoreAutoConfiguration, @SpringBootApplication, DbTransactionGateway, EntityManagerFactory, @Primary, ApplicationContextProvider, @EnableElastiCore]
elasticore-dsl-port-guide.md:970-992  ### env.yml (Port 네임스페이스)  [port, domain, application.port.out]
elasticore-dsl-port-guide.md:994-1038  ### 프록시를 빈으로 등록해야 합니다  [@SpringBootApplication, @SpringBootTest, @EnableElastiCore, @Bean, DddSampleApp, ddd, io.elasticore.sample.ddd, @Primary, getBeansOfType]
elasticore-dsl-port-guide.md:1040-1047  ### application.properties
elasticore-dsl-port-guide.md:1049-1154  ## 6. Best Practices
elasticore-dsl-port-guide.md:1051-1063  ### SQL Injection 방지
elasticore-dsl-port-guide.md:1065-1067  ### 핫리로드 활용
elasticore-dsl-port-guide.md:1069-1072  ### 페이징 패턴  [ref, page, size]
elasticore-dsl-port-guide.md:1074-1078  ### sortCode 활용
elasticore-dsl-port-guide.md:1080-1083  ### JEXL 조건 사용 지침  [input.]
elasticore-dsl-port-guide.md:1085-1093  ### 메서드 네이밍 규칙  [find, get, search, count, int, long, exists, check, boolean, update, insert, create, delete, remove]
elasticore-dsl-port-guide.md:1095-1099  ### 동적 SQL 패턴  [update, delete, insert]
elasticore-dsl-port-guide.md:1101-1123  ### HTTP Port - Multipart 요청 패턴  [@contentType, FileSystemResource, Resource, RestTemplate]
elasticore-dsl-port-guide.md:1125-1154  ### HTTP Port - 인증 패턴  [@url, @auth]
```

## elasticore-dev-guide.md

```text
elasticore-dev-guide.md:1-636  # ElastiCORE Developer Guide
elasticore-dev-guide.md:3-64  ## 1. 코드 생성 파이프라인  [@ElastiCore]
elasticore-dev-guide.md:16-28  ### Validation 모드 (`-Pmode=valid`)
elasticore-dev-guide.md:30-64  ### 멀티 모듈 통합 생성  [elasticore.modules]
elasticore-dev-guide.md:66-98  ## 2. 생성 코드 구조
elasticore-dev-guide.md:100-150  ## 3. 코드 수정 규칙
elasticore-dev-guide.md:102-107  ### 재생성 관리 주석
elasticore-dev-guide.md:109-117  ### 수정 가능/불가 파일
elasticore-dev-guide.md:119-129  ### 사용자 코드 보존 블록
elasticore-dev-guide.md:131-150  ### 저장 직전 훅 - `beforeSave` (v3)
elasticore-dev-guide.md:152-184  ## 4. Q 클래스 구조
elasticore-dev-guide.md:186-205  ## 5. FieldInfo 클래스  [resolvePath, rentDriver.driverName]
elasticore-dev-guide.md:207-254  ## 6. Op 열거형  [EQ, equal, NEQ, notEqual, LIKE, like, @search(like), ILIKE, NOT_LIKE, notLike, STARTS_WITH, ENDS_WITH, NOT_ILIKE, ISTARTS_WITH, IENDS_WITH, IEQ, GT]
elasticore-dev-guide.md:256-328  ## 7. JoinSpec 빌더
elasticore-dev-guide.md:260-263  ### 생성
elasticore-dev-guide.md:265-270  ### Join 메서드
elasticore-dev-guide.md:272-277  ### Where 조건
elasticore-dev-guide.md:279-287  ### 정렬
elasticore-dev-guide.md:289-293  ### 프로젝션 (특정 필드만 조회)
elasticore-dev-guide.md:295-301  ### 서브쿼리
elasticore-dev-guide.md:303-308  ### Exists
elasticore-dev-guide.md:310-314  ### Cross Join / Distinct
elasticore-dev-guide.md:316-328  ### 실행 메서드
elasticore-dev-guide.md:330-405  ## 8. UpdateSpec 빌더
elasticore-dev-guide.md:334-337  ### 생성
elasticore-dev-guide.md:339-378  ### SET 오퍼레이션
elasticore-dev-guide.md:341-345  #### 리터럴 값 설정
elasticore-dev-guide.md:347-350  #### NULL 설정
elasticore-dev-guide.md:352-356  #### 필드 간 복사
elasticore-dev-guide.md:358-363  #### Expression 기반 설정
elasticore-dev-guide.md:365-369  #### 숫자 필드 증감
elasticore-dev-guide.md:371-378  #### DTO 기반 일괄 설정
elasticore-dev-guide.md:380-384  ### WHERE 조건
elasticore-dev-guide.md:386-390  ### 전체 테이블 업데이트
elasticore-dev-guide.md:392-398  ### 실행
elasticore-dev-guide.md:400-405  ### 안전 장치  [execute, IllegalStateException, IllegalArgumentException, whereAll]
elasticore-dev-guide.md:407-479  ## 9. DeleteSpec 빌더
elasticore-dev-guide.md:411-414  ### 생성
elasticore-dev-guide.md:416-420  ### WHERE 조건
elasticore-dev-guide.md:422-426  ### 전체 테이블 삭제
elasticore-dev-guide.md:428-434  ### 삭제 전 카운트 미리보기
elasticore-dev-guide.md:436-441  ### 실행
elasticore-dev-guide.md:443-479  ### 안전 장치  [execute, IllegalArgumentException, whereAll, count, Q.Entity]
elasticore-dev-guide.md:481-557  ## 10. 실용 예제
elasticore-dev-guide.md:483-488  ### 기본 조건 조합
elasticore-dev-guide.md:490-497  ### JoinSpec + JOIN FETCH (N+1 prevention)
elasticore-dev-guide.md:499-504  ### 중첩 엔티티 검색
elasticore-dev-guide.md:506-513  ### 프로젝션 조회
elasticore-dev-guide.md:515-523  ### 서브쿼리 활용
elasticore-dev-guide.md:525-533  ### Bulk Update 예제
elasticore-dev-guide.md:535-542  ### Increment 예제
elasticore-dev-guide.md:544-557  ### 조건부 삭제 예제
elasticore-dev-guide.md:559-578  ## 11. SearchDTO와 Specification 통합
elasticore-dev-guide.md:561-571  ### 자동 변환 흐름
elasticore-dev-guide.md:573-578  ### 구성 요소  [BugMapper.toSpec, @search, withJoinFetches, @fetchjoin, @expose(n), @searchable(pageSize=n)]
elasticore-dev-guide.md:580-621  ## 12. 성능 최적화
elasticore-dev-guide.md:582-606  ### N+1 쿼리 방지  [@fetchjoin, JoinSpec.fetchJoin, @expose]
elasticore-dev-guide.md:608-610  ### distinct() 사용 시점  [JoinSpec.distinct, query.distinct]
elasticore-dev-guide.md:612-615  ### 프로젝션 활용  [select, findAllAsMap, findAll]
elasticore-dev-guide.md:617-621  ### 카운트 쿼리 최적화  [withJoinFetches]
elasticore-dev-guide.md:623-636  ## 13. 디버깅 팁  [elcore3, elcore]
```

## elasticore-annotation-guide.md

```text
elasticore-annotation-guide.md:1-2116  # ElastiCORE Annotation Guide
elasticore-annotation-guide.md:5-166  ## 1. Entity Meta Annotations  [meta:]
elasticore-annotation-guide.md:9-14  ### @expose
elasticore-annotation-guide.md:16-21  ### @service  [@expose]
elasticore-annotation-guide.md:23-28  ### @audited  [@Audited]
elasticore-annotation-guide.md:30-35  ### @pageable
elasticore-annotation-guide.md:37-42  ### @abstract
elasticore-annotation-guide.md:44-49  ### @extend(ParentEntity)  [@extend]
elasticore-annotation-guide.md:51-96  ### @embeddable  [@Embeddable, @writeonly, @Embedded, required, common, Address, shop, @required, @nn, @password, findById, update]
elasticore-annotation-guide.md:98-103  ### @table(name) / @tbl(name)  [@table]
elasticore-annotation-guide.md:105-126  ### @schema(name)  [@Table(schema="name"), tableSchema, @schema, @id, @sequence]
elasticore-annotation-guide.md:128-133  ### @Index(name, columnList, unique)  / @idx  [@Table(indexes=...)]
elasticore-annotation-guide.md:135-152  ### @rollup  [@id, @extend, @sequence, @formula, @DiscriminatorValue, @rollup(discriminator=ALB), @rollup(target=...), @rollup(Item)]
elasticore-annotation-guide.md:154-166  ### @terms(용어, 동의어, …)  [metrics:, ai_context.synonyms, @root, @terms]
elasticore-annotation-guide.md:168-781  ## 2. Field Annotations
elasticore-annotation-guide.md:170-196  ### 2.1 기본키
elasticore-annotation-guide.md:172-176  #### @id  [@Id]
elasticore-annotation-guide.md:178-183  #### @sequence  [@GeneratedValue, @id]
elasticore-annotation-guide.md:184-196  #### @genid  [@PrePersist, java.util.UUID.randomUUID.toString, @id, IdUtils.newId, com.acme.Ids.next]
elasticore-annotation-guide.md:198-393  ### 2.2 데이터 제약
elasticore-annotation-guide.md:200-208  #### @length(n) / @len(n) / @size(n)  [@Column(length=n), @Size, @size, @length, string, @length(min=..)]
elasticore-annotation-guide.md:210-231  #### @default(value)  [@default, @readonly, beforeSave, timestamp, now, @default(java.time.Instant.EPOCH), @Builder, @EnableJpaAuditing, @jpa]
elasticore-annotation-guide.md:233-237  #### @notnull / @notblank / @required  [@Column(nullable = false), @id]
elasticore-annotation-guide.md:239-247  #### @updatable(false)  [@Column(updatable = false), @updatable, itemId]
elasticore-annotation-guide.md:249-323  #### @readonly / @noinput - 서버가 정하는 필드  [@root, @editable, @expose, @id, @sequence, @JsonProperty(access=READ_ONLY), @Schema, @NotNull, @NotBlank, @notblank, toEntity, mapping, Member, beforeSave]
elasticore-annotation-guide.md:325-337  #### @min(value) / @max(value) / @min(value, '메시지')  [@Min, @Max, @DecimalMin, @DecimalMax, @min, @max, @invariant]
elasticore-annotation-guide.md:339-348  #### @minsize(n, '메시지') / @maxsize(n, '메시지') - 컬렉션 개수  [@Size(min=n, message=...), @NotNull, validate..., partial, @minsize]
elasticore-annotation-guide.md:350-393  #### @pattern('정규식', '메시지') / @pattern(regexp="...", message="...")  [@Pattern, validate..., @min, @id, @pattern, @pattern(yyyyMMdd), @DateTimeFormat, @JsonFormat, @Column, example, msg, @jakarta]
elasticore-annotation-guide.md:395-396  ### 2.3 검색 어노테이션  [@search(condition)]
elasticore-annotation-guide.md:398-536  ### 2.4 참조 및 타입
elasticore-annotation-guide.md:400-413  #### @ref(fieldPath)  [@ref, @access, @owner, @password, @access(admin), @ref(assignee.memberId)]
elasticore-annotation-guide.md:415-497  #### @fk(Target) / @fk(Target.field) / @fk(Target.field, groupName)  [@ManyToOne, @Column, @fk, @foreignkey, @manytoone, @id, @genid, DSL_WARN, @JoinColumn]
elasticore-annotation-guide.md:499-503  #### @dtype(ClassName)  [@dtype]
elasticore-annotation-guide.md:505-510  #### @fetchjoin  [withJoinFetches]
elasticore-annotation-guide.md:512-536  #### @formula(expression)  [@formula, toEntity, mapping, XxxFields, validateXxx, @abstract, @Formula, @Column, @Comment, string, Nm, Name, Code, Type, Status, Addr, Date, Time, Zip, Tel]
elasticore-annotation-guide.md:538-676  ### 2.5 JPA 관계  [@ManyToOne, @OneToOne, @ManyToMany, @cascade, @relation, ALL, PERSIST, MERGE, REMOVE, @dtype, @OneToMany, @access, @owner, @password, @access(admin), @signin]
elasticore-annotation-guide.md:582-596  #### FK 컬럼명 지정 (@column / @col)  [@JoinColumn(name=...), _id]
elasticore-annotation-guide.md:598-676  #### 관계 필드의 자동 DTO 렌더링 (중첩 DTO + FK 필드)  [@expose, @service, @ManyToOne, @OneToOne, @id, @fetchjoin, batch, batchBatchId, em.getReference, id, batchId, @template, @ref(관계필드.대상필드), EmployeeDTO]
elasticore-annotation-guide.md:678-748  ### 2.6 JPA 컬럼 매핑
elasticore-annotation-guide.md:680-686  #### @Column / @col / @db  [name, length, unique, nullable]
elasticore-annotation-guide.md:688-748  #### @columnDef('xxx') / @jdbcType('XXX')  [string, @Column, columnDefinition, @Column(columnDefinition="xxx"), @columnDef, @jdbcType, @JdbcTypeCode, json, jsonb, JSON, xml, SQLXML]
elasticore-annotation-guide.md:750-755  ### 2.7 커스텀 함수  [@function]
elasticore-annotation-guide.md:757-772  ### 2.8 Spring Data JPA 감사  [@jpa, @NotNull, @readonly, @noinput]
elasticore-annotation-guide.md:774-781  ### 2.9 기타  [@notaudited, @env]
elasticore-annotation-guide.md:783-1005  ## 3. Search Conditions - 완전 참조
elasticore-annotation-guide.md:785-791  ### 3.1 개요  [@search(condition), @s(condition), @srch(condition), @filter(condition)]
elasticore-annotation-guide.md:793-834  ### 3.2 구현된 연산자 전체 목록 (18개)  [eq, equal, like, gt, greaterThan, gte, ge, greaterThanOrEqualTo, lt, lessThan, lte, le, lessThanOrEqualTo, ne, neq, notEqual, in, Join.in, between, isnull]
elasticore-annotation-guide.md:836-849  ### 3.3 auto 조건 동작  [@expose, @search, between, eq, setSpec]
elasticore-annotation-guide.md:851-869  ### 3.4 between 특수 동작  [fieldNameFrom, fieldNameTo, greaterThanOrEqualTo, lessThan, lessThanOrEqualTo, @search]
elasticore-annotation-guide.md:871-940  ### 3.4.1 isnull - 비어 있는지 검색  [Boolean, @expose, @search, ProductSrchDTO, example, between, Op, @search(isnull), @ref(order.shippedDate), @search(isnull,returnedDate), returnedDateEmpty]
elasticore-annotation-guide.md:942-953  ### 3.5 List 필드 + eq = Join 생성  [@search(eq), fieldNameItem, root.join, equal]
elasticore-annotation-guide.md:955-966  ### 3.6 in 연산자와 List 필드
elasticore-annotation-guide.md:968-974  ### 3.7 @ref와 검색 조건 조합  [@ref(parentEntity.childField), @search]
elasticore-annotation-guide.md:976-1005  ### 3.8 실용 예시  [@expose, @pageable, @audited, @id, @search, @length, sortCode, @sort]
elasticore-annotation-guide.md:1007-1081  ## 4. DTO Annotations
elasticore-annotation-guide.md:1009-1023  ### 4.1 @template(EntityName)  [@template, string, @Size(max=n)]
elasticore-annotation-guide.md:1025-1032  ### 4.2 @searchable  [entity, pageSize, pageable, PageableObject, pageNumber, sortCode]
elasticore-annotation-guide.md:1034-1049  ### 4.2.1 @sort - 기본 정렬  [sortCode, @root, @expose, @Builder, ShopFlowTest, elcore3, elcore]
elasticore-annotation-guide.md:1051-1055  ### 4.3 @searchBypass
elasticore-annotation-guide.md:1057-1064  ### 4.4 JSON 어노테이션  [@json, @jsonSerialize, @jsoninclude, @jsonproperty, @includejson]
elasticore-annotation-guide.md:1066-1081  ### 4.5 Bean Validation  [@email, @pattern, @past, @future, @pastorpresent, @futureorpresent]
elasticore-annotation-guide.md:1083-1237  ## 4.6 @jsonview - DTO 필드 가시성 제어 (JsonView)
elasticore-annotation-guide.md:1087-1089  ### 개요  [@JsonView]
elasticore-annotation-guide.md:1091-1110  ### DTO 메타 어노테이션: @jsonview  [@view, @jsonview(auto), @jsonview(false), @template, @expose]
elasticore-annotation-guide.md:1112-1141  ### 필드 어노테이션: @view  [@id, create, @PostMapping, update, @PutMapping, read, @GetMapping, @jsonview]
elasticore-annotation-guide.md:1143-1157  ### 자동 추론 규칙 (@jsonview(auto))  [@id, @genid, @sequence, @updatable(false), @jpa, @formula(...), @readonly, @noinput, @writeonly]
elasticore-annotation-guide.md:1159-1171  ### env.yml 설정  [@jsonview, @view, auto]
elasticore-annotation-guide.md:1173-1218  ### 생성 코드 예시  [@template, @jsonview, @id, @view]
elasticore-annotation-guide.md:1220-1237  ### 주의사항  [@jsonview, @view, jsonview, Jackson2ObjectMapperBuilder, MapperFeature.DEFAULT_VIEW_INCLUSION, @JsonView, @readonly, @writeonly]
elasticore-annotation-guide.md:1239-1255  ## 5. Enumeration Annotations
elasticore-annotation-guide.md:1241-1255  ### @db(field) / @json(field)  [@db, @json]
elasticore-annotation-guide.md:1257-1286  ## 6. Port Annotations
elasticore-annotation-guide.md:1261-1272  ### HTTP Port  [@url, @auth, @get, @post, @put, @delete, @patch]
elasticore-annotation-guide.md:1274-1286  ### DBMS Port  [@datasource, @query, @pageable, @native]
elasticore-annotation-guide.md:1288-1322  ## 7. Best Practices
elasticore-annotation-guide.md:1290-1294  ### 성능  [@fetchjoin, @cascade(ALL), @pageable, @expose]
elasticore-annotation-guide.md:1296-1303  ### 검색 조건 선택  [eq, like, between, in, gte, lte, gt, lt, ne, auto]
elasticore-annotation-guide.md:1305-1309  ### DTO template 활용  [@template(Entity), string, @Size(max=n), @size]
elasticore-annotation-guide.md:1311-1314  ### 인덱스와 검색  [@search, @Index, like, between]
elasticore-annotation-guide.md:1316-1322  ### 기본값 활용  [@table, @Column, @search, @expose, @pageable]
elasticore-annotation-guide.md:1324-1552  ## 8. 주의사항 및 해결방법 (Known Limitations & Workarounds)
elasticore-annotation-guide.md:1326-1365  ### 8.1 @min/@max 어노테이션 제약사항  [@Min, @Max, long]
elasticore-annotation-guide.md:1330-1344  #### 문제 상황  [@min, @max]
elasticore-annotation-guide.md:1346-1360  #### 올바른 사용법  [@min, @max]
elasticore-annotation-guide.md:1362-1365  #### 해결방법
elasticore-annotation-guide.md:1367-1390  ### 8.2 DSL 타입이 아닌 단어 주의 (Invalid Field Types)  [uuid, string, @genid, @id, json, String, clob, @Lob, build, elcore, text, blob, elcore3]
elasticore-annotation-guide.md:1392-1448  ### 8.3 @ManyToOne @id 필드와 SQL 예약어 충돌  [field_name_id]
elasticore-annotation-guide.md:1396-1397  #### 문제가 되는 예약어  [order, group, select, from, where, table, index, key, value, user, date, time, timestamp, status]
elasticore-annotation-guide.md:1399-1411  #### 문제 상황  [@id]
elasticore-annotation-guide.md:1413-1428  #### 해결방법 1: 비예약어 필드명 사용 (권장)  [@id]
elasticore-annotation-guide.md:1430-1448  #### 해결방법 2: 생성 후 수동 수정  [@id]
elasticore-annotation-guide.md:1450-1490  ### 8.4 @audited 엔티티와 연관관계  [@NotAudited]
elasticore-annotation-guide.md:1454-1476  #### 자동 처리  [@NotAudited, @audited, @ManyToOne, @OneToMany, @ManyToMany, @id, @sequence]
elasticore-annotation-guide.md:1478-1484  #### 수동 명시  [@notaudited]
elasticore-annotation-guide.md:1486-1490  #### 검증 필요 사항  [@audited, @NotAudited]
elasticore-annotation-guide.md:1492-1511  ### 8.5 비교 키워드와 같지 않음 (모드에 따라 다름)  [gt, gte, ge, lt, lte, le, ne, neq, notEqual, @search(isnull)]
elasticore-annotation-guide.md:1513-1542  ### 8.6 between 검색과 LocalDate +1일 동작  [@search(between)]
elasticore-annotation-guide.md:1539-1542  #### 주의사항
elasticore-annotation-guide.md:1544-1552  ### 8.7 권장사항 요약  [@min, @max, uuid, @id, @genid, @NotAudited, @audited]
elasticore-annotation-guide.md:1554-1602  ## @root / @aggregate — 애그리거트 경계 (DDD 룰셋)  [@aggregate(Order), Order, @OneToMany, @cascade, @aggregate(X)]
elasticore-annotation-guide.md:1579-1592  ### 왜 추론만으로는 부족한가  [@cascade, @service, @root, @aggregate]
elasticore-annotation-guide.md:1594-1602  ### 생성물에 미치는 영향  [RepositoryHelper, @embeddable]
elasticore-annotation-guide.md:1604-1647  ## domainService — 도메인 서비스 (DDD 룰셋)  [uses, desc, DSL_WARN]
elasticore-annotation-guide.md:1626-1647  ### 생성물  [uses, namespace.domainService]
elasticore-annotation-guide.md:1649-1662  ## @behavior — 엔티티 행위 (DDD 룰셋)  [@editable, XxxBase, Xxx, @MappedSuperclass, @Entity, protected, READ_ONLY, @readonly, @noinput]
elasticore-annotation-guide.md:1664-1721  ## @editable — 요청으로 고치는 필드·루트 (DDD 룰셋)  [@root, @expose, @behavior, @event, order.confirm, recalculate]
elasticore-annotation-guide.md:1723-1780  ## @invariant — 불변식 (DDD 룰셋)  [@min, @minsize, @root, @expose]
elasticore-annotation-guide.md:1750-1780  ### 생성물  [OrderInvariants, @EntityListeners, @genid, @PrePersist, OrderItemRemovalCheck, replaceItems, mappedBy]
elasticore-annotation-guide.md:1782-1806  ## @factory — 팩토리 (DDD 룰셋)  [@root, @invariant]
elasticore-annotation-guide.md:1792-1806  ### 생성물  [OrderFactory.place, @genid]
elasticore-annotation-guide.md:1808-1850  ## @specification — 명세 (DDD 룰셋)  [@root]
elasticore-annotation-guide.md:1822-1830  ### 생성물  [Predicate]
elasticore-annotation-guide.md:1832-1850  ### TypeScript 에서  [Number, number]
elasticore-annotation-guide.md:1852-1869  ## 조건 문법 (`@invariant`, `@specification` 공통)  [Integer, Long, Double, BigDecimal]
elasticore-annotation-guide.md:1871-2012  ## @access / @owner — 누가 무엇을 할 수 있는가 (v3)  [@root, @expose, public, user, owner, admin, ADMIN, none, read, create, update, delete, ship, cancel, @search, @readonly, @noinput, runAsSystem, @access(admin)]
elasticore-annotation-guide.md:2014-2030  ## @password / @login — 자격 증명 (v3)  [@unique, PasswordHasher, @writeonly, authenticate, @signin, @Index, me, signin, signout, @access, @owner]
elasticore-annotation-guide.md:2032-2051  ## @snapshot / @compute — 파생 값 (DDD 룰셋)  [@min, @editable, reference.OrderLine.item, @formula, @formula("...")]
elasticore-annotation-guide.md:2053-2116  ## @event — 도메인 이벤트 (DDD 룰셋)  [@root, @expose]
elasticore-annotation-guide.md:2063-2082  ### 생성물  [OrderPlacedEvent, OrderCancelledEvent, occurredOn, order.raisePlaced, order.raiseCancelled, @DomainEvents, @AfterDomainEventPublication]
elasticore-annotation-guide.md:2084-2096  ### 이벤트 내용 (payload)  [@root, @event, raiseShipped]
elasticore-annotation-guide.md:2098-2102  ### 밖으로 내보내기 - Outbox  [OutboxPublisher]
elasticore-annotation-guide.md:2104-2108  ### 언제 발생하는지는 생성하지 않습니다  [raiseCancelled]
elasticore-annotation-guide.md:2110-2116  ### 제약  [namespace.event]
```

## elasticore-rule-guide.md

```text
elasticore-rule-guide.md:1-1909  # ElastiCORE Rule Guide
elasticore-rule-guide.md:11-30  ## 1. 규칙이란 무엇인가  [@Column(length=200), CodeRules, Syntax]
elasticore-rule-guide.md:32-1228  ## 2. 규칙 고르기 (env.yml)  [rules, suppress]
elasticore-rule-guide.md:52-56  ### 우선순위
elasticore-rule-guide.md:58-64  ### 이름이 가리키는 것  [rules, CodeRules]
elasticore-rule-guide.md:66-1174  ### 기본 제공 번들  [elcoreRulesCheck, elcoreTreeCheck]
elasticore-rule-guide.md:79-99  #### 육각형 번들이 더 만드는 것  [namespace.inboundPort, service, inboundPort, port.in, port.out, port]
elasticore-rule-guide.md:101-148  #### 언어별로 다르게 도달하는 규칙  [MutableList, XxxBase, Xxx, commands:, states:, canX, canShip, @snapshot, @compute, derive, @access, @owner, @password, @login, authenticate, @signin, me]
elasticore-rule-guide.md:150-168  #### 도메인 이벤트 (`ddd-event`)  [@event(Placed, Cancelled), @DomainEvents, @AfterDomainEventPublication, namespace.event]
elasticore-rule-guide.md:170-200  #### 불변식 (`ddd-invariant`)  [@root, @invariant, OrderInvariants, @EntityListeners, @genid, @PrePersist, @PreUpdate, IllegalStateException, @length, OrderItemRemovalCheck]
elasticore-rule-guide.md:202-217  #### 팩토리 (`ddd-factory`)  [@root, @invariant, @factory, OrderFactory]
elasticore-rule-guide.md:219-236  #### 명세 (`ddd-specification`)  [@root, @specification, OrderSpecifications.highValue]
elasticore-rule-guide.md:238-260  #### 조건 문법 (`@invariant` 와 `@specification` 공통)  [notNull, notEmpty]
elasticore-rule-guide.md:262-272  #### 값 객체의 교체 (`ddd-value-object`)
elasticore-rule-guide.md:274-285  #### Kotlin 에서 달라지는 것  [val, private, object, static, fun, XxxBase, @Autowired, order.getItems, @EntityListeners({A.class, B.class}), @EntityListeners(A::class, B::class)]
elasticore-rule-guide.md:287-378  #### 엔티티 행위 (`ddd-behavior`) - Generation Gap  [confirm, @behavior, SalesOrderBase, @MappedSuperclass, SalesOrder, @Entity, @root, @expose, @event, protected, @Setter(AccessLevel.PROTECTED), READ_ONLY]
elasticore-rule-guide.md:380-389  #### 생성기가 더는 만들지 않는 파일 (elcore3)  [@min, XxxInvariants]
elasticore-rule-guide.md:391-403  #### 업무 명령 (`ddd-command`)  [commands:, XxxUseCase, XxxCoreService, XxxBase, Xxx, UnsupportedOperationException, TODO, @editable]
elasticore-rule-guide.md:405-420  #### 생명주기 (`ddd-state`)  [states:, XxxBase, InvariantViolationException, state.PurchaseOrder.status.ship, @event, SHIPPED, raiseShipped, canShip, super.ship, @behavior, moveXxx]
elasticore-rule-guide.md:422-429  #### 파생 값 (`ddd-derive`)  [@snapshot(item.unitCost), @compute(unitCost * quantity), entity.derive, sum]
elasticore-rule-guide.md:431-449  #### 권한·소유·비밀번호 (`jpa-service-access`)  [@owner(customerId), @password, @login, @access, @access(admin), io.elasticore.springboot3.security, CurrentUser, SessionCurrentUser, AccessPolicy]
elasticore-rule-guide.md:451-485  #### 트랜잭션 Outbox (`ddd-outbox`)  [namespace.persistence, repository, @EntityScan, BEFORE_COMMIT, relay, OutboxPublisher, @EnableScheduling, @Scheduled, OutboxEnvelope.getId, pending]
elasticore-rule-guide.md:487-548  #### 프로세스 매니저 (사가) — `processManager:`  [billing, namespace.processManager, XxxBase, Xxx, on, handle, uses, findByOrderId, @event, config.context, publishes]
elasticore-rule-guide.md:550-569  #### 읽기 모델 — 투영 쿼리  [TupleMapper, List, Page, Optional, @immutable(view_name)]
elasticore-rule-guide.md:571-576  #### 값 객체 (`@embeddable`)  [withXxx, OrderId, @Id, @EmbeddedId]
elasticore-rule-guide.md:578-630  #### 다른 애그리거트는 ID 로 참조 (`ddd-reference`)  [partnerId, o.partner.name, findByPartnerPartnerId, @ref, @db, partner, item, itemCode, category, categoryCatId, @NotNull, @Valid, isbn, LoanDTO]
elasticore-rule-guide.md:632-642  #### 도메인에서 JPA 걷어내기 (`mapping: xml`) 와 아키텍처 테스트 (`archTest`)  [OrmMapping, id, basic, version, embedded, transient, XxxInvariants, jakarta.persistence, settleOnLoad, @PostLoad, settleOnPersist, @PostPersist]
elasticore-rule-guide.md:644-648  #### 바운디드 컨텍스트 경계  [port:]
elasticore-rule-guide.md:650-677  #### 도메인 서비스 (`ddd-domain-service`)  [uses, PricingDomainServiceBase, OrderRepositoryPort, ProductRepositoryPort, PricingDomainService, @factory, namespace.domainService]
elasticore-rule-guide.md:679-728  #### TypeScript 에 닿는 것 (`ts-model`)  [number, JSON.parse, @JsonFormat(shape = STRING), Number, customerId, deliveryAddress, @readonly, @restExclude, update, @writeonly, pageNumber, pageSize]
elasticore-rule-guide.md:730-769  #### 포트의 두 방향  [OrderUseCase, inboundPort, service, OrderRepositoryPort, domainRepository, entity, OrderRepositoryAdapter, port:, port, dbms, http, Page, uses:]
elasticore-rule-guide.md:771-865  #### 리포지터리 포트 (`ddd-repository-port`)  [Pageable, Specification, infrastructure.persistence, EntityManager, OrderService, domainRepository, entity, queryPort, service, persistence, JpaRepository]
elasticore-rule-guide.md:867-911  #### 쓰기 경로가 지키는 것 (v3 서비스 공통)  [save, DuplicateKeyException, version, ObjectOptimisticLockingFailureException, @Size, ConstraintViolationException, EntityNotFoundException, loan.memberId]
elasticore-rule-guide.md:913-967  #### API 오류 응답 (`spring-api-errors`)  [ShopApiErrors, @RestControllerAdvice, ProblemDetail, BeanValidation.use, InvalidDataAccessApiUsageException, TransactionSystemException, RequestShapeErrors]
elasticore-rule-guide.md:969-1144  #### 오류 코드와 다국어 (Accept-Language)  [code, spring.web.locale, Size, NotNull, Min, en, sv, ShopFlowTest, fr, LocaleResolver, title, detail, stale, required, unique, referenced, conflict, @Valid]
elasticore-rule-guide.md:1146-1156  #### Kotlin 에서 생성물이 부팅되기 위한 것  [ShopApiContractTest, OrderService, @Transactional, @Repository]
elasticore-rule-guide.md:1158-1174  #### 애그리거트 멤버 — 쓰기는 루트로만  [Collections.unmodifiableList, MemberList.view, order.getItems.add, UnsupportedOperationException, addOrderItem, removeOrderItem, replaceItems]
elasticore-rule-guide.md:1176-1188  ### 잘못 조합하면 실패합니다
elasticore-rule-guide.md:1190-1202  ### 언어별 규칙 선택  [publisher.rules]
elasticore-rule-guide.md:1204-1217  ### 번들 조합
elasticore-rule-guide.md:1219-1228  ### 규칙 하나만 끄기  [suppress, @Rule(value = ...)]
elasticore-rule-guide.md:1230-1297  ## 3. 규칙 작성
elasticore-rule-guide.md:1236-1261  ### 3.1 최소 형태  [@masked, CodeRules, @Rule]
elasticore-rule-guide.md:1263-1273  ### 3.2 `@Rule` 속성  [value, suppress, target, order, phase, HEAD, TAIL, entity, dto, srchdto, enum, repository, service, control, mapper, port]
elasticore-rule-guide.md:1275-1285  ### 3.3 메서드 시그니처가 호출 시점을 정합니다
elasticore-rule-guide.md:1287-1297  ### 3.4 실행 순서  [importList, classDeclaration]
elasticore-rule-guide.md:1299-1338  ## 4. 모델 읽기 — Spec
elasticore-rule-guide.md:1301-1319  ### `FieldSpec` — 자주 쓰는 것  [@id, @length]
elasticore-rule-guide.md:1321-1338  ### `EntitySpec`  [@id, @pk, @extend, Field.getType, FieldSpec]
elasticore-rule-guide.md:1340-1401  ## 5. 코드 쓰기 — `SourceCode` / `FieldCode` / `AnnoCode`  [ctx]
elasticore-rule-guide.md:1344-1362  ### 5.1 필드 단위 — `FieldCode`  [ctx.removeField, ctx.dontMake]
elasticore-rule-guide.md:1364-1380  ### 5.2 애노테이션 — `AnnoCode`  [AnnoCode.AnnoList.of]
elasticore-rule-guide.md:1382-1401  ### 5.3 템플릿 슬롯 — `SourceCode`  [var]
elasticore-rule-guide.md:1403-1470  ## 6. 언어 이음새 — `Syntax`
elasticore-rule-guide.md:1440-1453  ### 이음새가 JVM 모양인 부분  [classLiteral, javaClassLiteral, sam, samBlockOpen, samBlockClose, lambdaReturn, injectedProperty, overriding, supportsAnnotations, importLine]
elasticore-rule-guide.md:1455-1470  ### 이음새로 풀 수 없는 것  [@Builder.Default, @Builder, MapperBaseRules, MapperAssembleRules, KotlinMapperAssembleRules]
elasticore-rule-guide.md:1472-1490  ## 7. 스켈레톤(`.tmpl`)  [target]
elasticore-rule-guide.md:1492-1519  ## 7.5 생성 파일의 소유권 (elcore3)  [@readonly, @behavior, @Entity, @Table, @DynamicUpdate, @Audited, super]
elasticore-rule-guide.md:1515-1519  ### Kotlin 예약어가 든 패키지  [adapter.in.web, in]
elasticore-rule-guide.md:1521-1533  ## 8. 지켜야 할 것  [AnnoCode, @Column, String.format, Formatted, Formatted.text, FileWriter, FileReader, DSL_WARN]
elasticore-rule-guide.md:1535-1826  ## 9. 검증  [RulesVerifyRunner.ACCEPTED_DIFFS]
elasticore-rule-guide.md:1567-1658  ### Gradle 플러그인 (권장)  [elcore, elcore3, compileJava, layered, RulesConfig]
elasticore-rule-guide.md:1606-1615  #### 멀티 모듈
elasticore-rule-guide.md:1617-1620  #### 기존 스위치
elasticore-rule-guide.md:1622-1644  #### TypeScript 클라이언트 (`elcore3ts`, `typescriptClient`)  [build, compileJava, elcore3, architecture, language, rules, webBuild, suppress]
elasticore-rule-guide.md:1646-1658  #### 프로젝트의 규칙 더하기  [elasticore]
elasticore-rule-guide.md:1660-1772  ### Apache Ossie 시맨틱 모델 내보내기 (`ossie-export`)  [name, billing_catalog_shop, @abstract, @embeddable, source, @table, table.prefix, description, @id, primary_key, @unique, @Index(unique="true"), unique_keys]
elasticore-rule-guide.md:1774-1805  ### 플러그인 없이 실행하기 (수동 구성)  [publisher.rules, @ElastiCore]
elasticore-rule-guide.md:1807-1826  ### 생성해보기 (이 저장소의 샘플 모듈에서)  [publisher.rules]
elasticore-rule-guide.md:1828-1893  ## 10. 체크리스트
elasticore-rule-guide.md:1830-1838  ### 새 규칙을 추가할 때  [@Rule(value = ...), target, order, phase, TAIL, Syntax, RuleCatalog.standard]
elasticore-rule-guide.md:1840-1845  ### 새 아티팩트를 추가할 때  [RuleCatalog, RulesGenRunner, ArtifactTarget]
elasticore-rule-guide.md:1847-1880  ### 새 아티팩트 종류를 추가할 때 (확장점)  [ArtifactContributor, artifactsFor, DomainArtifactContributor, orNamespace, SourceCode.artifactName, requireSyntaxAnyOf]
elasticore-rule-guide.md:1882-1893  ### 새 언어를 추가할 때  [Syntax, SourceCode.TemplateSet]
elasticore-rule-guide.md:1895-1909  ## 11. 패키지 구조
```

## elasticore-rule-dev-guide.md

```text
elasticore-rule-dev-guide.md:1-1175  # ElastiCORE 규칙 개발 가이드
elasticore-rule-dev-guide.md:26-116  ## 0. 규칙이 언제, 어디서 동작하나
elasticore-rule-dev-guide.md:28-45  ### 전체 흐름
elasticore-rule-dev-guide.md:47-61  ### 엔티티 하나에서 파일이 여러 개  [Partner, entity]
elasticore-rule-dev-guide.md:63-85  ### 파일 하나를 만들 때 내 메서드가 불리는 순서
elasticore-rule-dev-guide.md:87-116  ### 규칙 메서드 안에서 하는 일: 읽고, 판단하고, 쓴다  [entity.aggregateRoot, field.hasDsl, ctx, ctx.field.ensure, EntitySpec, FieldSpec, Partner:, entity.getName, entity.hasMeta, entity.metaValue, @root, @expose]
elasticore-rule-dev-guide.md:118-195  ## 1. 규칙을 둘 곳 만들기
elasticore-rule-dev-guide.md:197-267  ## 2. 클래스에 어노테이션 붙이기  [@AggregateRoot, AcmeRules, @Rule("acme-aggregate-root"), entity.aggregateRoot, ctx, AnnoCode.of]
elasticore-rule-dev-guide.md:250-267  ### 이미 만들어져 내가 고친 파일에는 직접 붙입니다
elasticore-rule-dev-guide.md:269-302  ## 3. 필드에 어노테이션 붙이기  [@id, @Identity, ctx.ensureClassAnnotation, ctx.field.ensure, field.hasDsl, FieldSpec]
elasticore-rule-dev-guide.md:304-366  ## 4. YAML에 우리만의 표시 만들기  [@secret, @search, bizNo]
elasticore-rule-dev-guide.md:347-366  ### 규칙을 어느 파일에 적용하나 — `target`  [addMethod, control, XxxService]
elasticore-rule-dev-guide.md:368-401  ## 5. 잘못된 모델을 빌드에서 막기  [commands:, @event, entity.commands, entity.events, @event(...), ModelCheck.fail, ModelCheck.warn, SalesOrder]
elasticore-rule-dev-guide.md:403-656  ## 6. 다른 YAML 파일에도 규칙 걸기 (DTO, 검색, SQL, 외부 연동, enum)  [target, @search(...), entity.queries, port.operations, enumeration.constants, EntitySpec, dto.getName, AcmeRules]
elasticore-rule-dev-guide.md:434-473  ### DTO (`dto.yml`) — 내부용 필드를 API 문서에서 숨기기  [@internal, @Schema(description = ...), ensure, merge, @example(A1), @Schema(example="A1")]
elasticore-rule-dev-guide.md:475-503  ### 검색 DTO — LIKE 검색은 2글자 이상  [@search(like), @Valid, fieldErrors, search, @RequestBody]
elasticore-rule-dev-guide.md:505-561  ### SQL (`repository.yml`) — 조회 쿼리는 읽기 전용, `SELECT *`는 금지  [entity.queries, query.name, id:, query.sql, query:, query.isSelect, query.isNative, query.returnType, query.params, return:, params:, ctx.method.ensure]
elasticore-rule-dev-guide.md:563-623  ### 외부 연동 (`port.yml`) — 외부 호출에 표시 달기  [@url, @timeout, @get, @interface, FieldSpec, port.getName, CreditCheckPort, port.protocol, port.url, @url(...), port.operations, methods:, op.name]
elasticore-rule-dev-guide.md:625-650  ### enum (`enum.yml`) — API 문서에 enum을 한 번만  [enumeration.getName, OrderStatus, enumeration.constants, enum:, c.name, c.values, enumeration.fields, fields:, code, label]
elasticore-rule-dev-guide.md:652-656  ### 무엇이 어디로 가나 — 한 번에 확인  [target]
elasticore-rule-dev-guide.md:658-826  ## 7. 규칙 테스트하기  [@root, @id, @secret, @internal, @search, @url, @get, Partner:, nativeQuery, port.operation, engine.run.render, System.out.println]
elasticore-rule-dev-guide.md:828-934  ## 8. 완성된 규칙 클래스  [@secret, @event, @internal]
elasticore-rule-dev-guide.md:936-989  ## 9. 자주 쓰는 것  [entity.getName, Partner, entity.aggregateRoot, entity.hasMeta, entity.metaValue, @expose, entity.fields, entity.commands, entity.events, entity.config]
elasticore-rule-dev-guide.md:991-1170  ## 10. 규칙 API 레퍼런스  [io.elasticore.base.model.pub.rules]
elasticore-rule-dev-guide.md:1001-1022  ### `@Rule` 속성과 메서드 모양  [CodeRules, value, suppress, target, order, phase, HEAD, TAIL, entity, dto, srchdto, repository, control, service, behavior, mapper, apiErrors, enum, port]
elasticore-rule-dev-guide.md:1024-1039  ### `EntitySpec` - 엔티티, `dto.yml` 의 DTO, 검색 DTO  [getName, domainName, domainClassName, hasMeta, metaValue, metaValues, meta:, @x, @x(값), metaText, @access, aggregateRoot, containedIn, XxxBase, fields]
elasticore-rule-dev-guide.md:1041-1060  ### `FieldSpec` - 필드 한 줄  [name, capitalName, typeName, coreItemType, required, hasDsl, hasDslAny, @x, dslValue, dslValueAny, @x(값), dslProperty, @OneToMany(mappedBy="parent")]
elasticore-rule-dev-guide.md:1062-1100  ### `SourceCode` - `ctx(spec)` 이 돌려주는 지금 만드는 파일  [ensureClassAnnotation, removeClassAnnotation, field, FieldCode, removeField, method.ensure, repository, port, addMethod, excludeMethod, addImport, dontMake]
elasticore-rule-dev-guide.md:1102-1112  ### `FieldCode` - 필드 하나  [ensure, add, merge, @Schema, @Column, replace, removeAnnotation, type, rename, initial, comment, before, after]
elasticore-rule-dev-guide.md:1114-1129  ### `AnnoCode` - 어노테이션  [@Column, @X("x"), @X(A.B), jakarta.persistence.Entity, AnnoCode.AnnoList.of]
elasticore-rule-dev-guide.md:1131-1139  ### `ModelCheck` - 검사  [ModelCheck.warn, ModelCheck.fail, elcore3, ModelCheck.failures, ModelCheck.reset]
elasticore-rule-dev-guide.md:1141-1151  ### 새 파일 - `ArtifactContributor`  [artifactsFor, DomainArtifactContributor]
elasticore-rule-dev-guide.md:1153-1170  ### `Syntax` - 언어 이음새  [addMethod, ctx.syntax, s.statement, s.getter, dto.getName, dto.name, s.setter, to.setName, s.localVar, s.newInstance, s.nullableType, String, s.classLiteral]
elasticore-rule-dev-guide.md:1172-1175  ## 더 알아보기
```

## elasticore-ddd-customize-guide.md

```text
elasticore-ddd-customize-guide.md:1-1271  # ElastiCORE DDD 룰셋 커스터마이즈 가이드 (심화)
elasticore-ddd-customize-guide.md:20-31  ## 0. 다 하고 나면  [@AggregateRoot, @Entity, @ValueObject, @Identity, @topic(erp.sales), ErpEventTopics]
elasticore-ddd-customize-guide.md:33-90  ## 1. 규칙이 코드를 만드는 방식  [erp, EntitySpec, FieldSpec, DomainSpec, e.aggregateRoot, e.events, @Rule, @AggregateRoot, CodeRules, AcmeDddRules, architecture, language, entity, behavior]
elasticore-ddd-customize-guide.md:53-73  ### 파일 종류(target) — 헥사고날 ERP에서  [entity, behavior, dto, srchdto, event, repositoryPort, queryPort, usecase, domainServiceBase, processManagerBase, handle, domainService, processManager]
elasticore-ddd-customize-guide.md:75-90  ### 루트와 멤버  [e.aggregateRoot, @root, @aggregate, Partner, Item, SalesOrder, Stock, Invoice, @aggregate(X), @cascade, SalesOrderLine, @OneToMany, @embeddable]
elasticore-ddd-customize-guide.md:92-111  ## 2. 무엇을 어떻게 바꾸나  [suppress, e.metaValue, f.dslValue, ArtifactContributor, DomainArtifactContributor, config:, e.config, ModelCheck.warn, ModelCheck.fail, entity, dto, behavior]
elasticore-ddd-customize-guide.md:113-241  ## 3. 준비: 규칙 모듈 만들기  [architecture, elasticore, elasticore.config.publisher.rules, rules]
elasticore-ddd-customize-guide.md:206-241  ### 지금 어떤 규칙이 도는지 보기: `-PlistRules`  [behavior, entity, order, head, field, tail, once, suppress, AcmeDddRules]
elasticore-ddd-customize-guide.md:243-309  ## 4. 예제 1 — 기본 생성물 끄기  [@RestControllerAdvice, ErpApiErrors, apiErrors, config:, suppress, SKIP, @Comment, code, RequestShapeErrors, @Import, @Order, findById, delete]
elasticore-ddd-customize-guide.md:279-309  ### 규칙이 하는 일 중 하나만 빼기  [suppress, @DynamicUpdate, rules, c.removeClassAnnotation, c.removeField, c.field.removeAnnotation, @dynamicupdate(false)]
elasticore-ddd-customize-guide.md:311-406  ## 5. 예제 2 — 모든 클래스에 DDD 역할 표시하기 (jMolecules)  [@root, @embeddable, @id, @Rule("acme-role"), target, ctx, SourceCode, ensureClassAnnotation, ctx.field.ensure, AnnoCode.of.qualified]
elasticore-ddd-customize-guide.md:360-370  ### 두 파일로 나뉜 루트  [@editable, SalesOrder, Stock, Invoice, Partner, Item, entity, behavior, @behavior, @abstract, @embeddable, @rollup, @discriminator]
elasticore-ddd-customize-guide.md:372-377  ### 순서(order)는 신경 쓰지 않아도 됩니다  [head, field, tail]
elasticore-ddd-customize-guide.md:379-406  ### 이미 있는 내 파일에는 직접 붙입니다  [Shipment]
elasticore-ddd-customize-guide.md:408-694  ## 6. 예제 3 — 우리만의 DSL 단어로 새 파일 만들기 (`@topic`)
elasticore-ddd-customize-guide.md:413-426  ### 6.1 DSL에 쓰기  [SalesOrder, @topic(erp.sales), @root, @expose, @event, e.metaValue, erp.sales, f.hasDsl, f.dslValue, @masked, @topci, @search, @label, @acmeTopic]
elasticore-ddd-customize-guide.md:428-439  ### 6.2 생성될 파일
elasticore-ddd-customize-guide.md:441-488  ### 6.3 파일을 선언하기 — `DomainArtifactContributor`  [topicOf, className, upperSnake, kebab, artifactsFor, ArtifactContributor, target, namespace, com.acme.erp.domain.event, event, entity, enumeration]
elasticore-ddd-customize-guide.md:490-527  ### 6.4 빈칸 채우기 — 규칙  [e.events, @event, e.eventClassName, e.eventPackage, SalesOrderShippedEvent, com.acme.erp.domain.event, c.var, c.varLine, requireSyntaxAnyOf, ERROR]
elasticore-ddd-customize-guide.md:529-580  ### 6.5 스켈레톤  [@topic, c.addImport, c.ensureClassAnnotation, io.elasticore]
elasticore-ddd-customize-guide.md:582-584  ### 6.6 전체 규칙 파일
elasticore-ddd-customize-guide.md:586-636  ### 6.7 변형: 엔티티마다 파일 하나  [SalesOrderEvents, DomainArtifactContributor, ArtifactContributor, artifactsFor, rules, requireSyntaxAnyOf]
elasticore-ddd-customize-guide.md:638-694  ### 6.8 생성된 클래스 쓰기 — Outbox로 토픽에 보내기  [config:, @EnableScheduling, ErpOutboxRelay, OutboxPublisher, @topic, publish]
elasticore-ddd-customize-guide.md:696-749  ## 7. 예제 4 — 모델 검사하기 (팀 설정과 함께)  [@event, @topic, ModelCheck.warn, ModelCheck.fail, e.config, elasticore.config.acme.requireTopic, d.config, e.commands, commands:, name, params, config:]
elasticore-ddd-customize-guide.md:751-763  ## 8. 조립: 전체 빌드
elasticore-ddd-customize-guide.md:765-1026  ## 9. 규칙 테스트하기
elasticore-ddd-customize-guide.md:767-933  ### 9.1 규칙만 테스트 (빠름, 프로젝트 없이)  [@id, @org, @topic(erp.sales), @root, behavior, run.render, code.unfilledSlots, render, ModelCheck.reset]
elasticore-ddd-customize-guide.md:935-1026  ### 9.2 애플리케이션에서 확인 (결과가 실제로 동작하는가)  [theOutboxSendsToTheTopic, relay.relay, TopicOutboxPublisher, @SpringBootTest(properties = ...)]
elasticore-ddd-customize-guide.md:1028-1063  ## 10. Kotlin 프로젝트에서  [ERROR, @AggregateRoot, class, @topic]
elasticore-ddd-customize-guide.md:1065-1079  ## 11. 자주 막히는 곳  [NoSuchMethodError, target, code.unfilledSlots, rules:, rules, elcore3ts, @EnableScheduling, OutboxPublisher, SKIP]
elasticore-ddd-customize-guide.md:1081-1091  ## 12. 체크리스트  [target, entity, AnnoCode, orNamespace, unfilledSlots, ModelCheck]
elasticore-ddd-customize-guide.md:1093-1129  ## 13. 자주 쓰는 API  [e.getName, e.domainClassName, SalesOrder, Erp, e.aggregateRoot, e.containedIn, e.hasMeta, e.metaValue, e.events, e.eventClassName, e.eventPackage, e.commands]
elasticore-ddd-customize-guide.md:1131-1271  ## 부록 A. `AcmeDddRules.java` 완성본  [@topic, @event]
```

## elasticore-migration-guide.md

```text
elasticore-migration-guide.md:1-133  # ElastiCORE 2.x → 3.0 마이그레이션 가이드
elasticore-migration-guide.md:3-30  ## 요약: 고칠 것이 없습니다
elasticore-migration-guide.md:18-30  ### 근거  [_V2.2]
elasticore-migration-guide.md:32-65  ## 그래도 달라지는 것 (전부 버그 수정)
elasticore-migration-guide.md:34-42  ### 1. `schema.yml` 의 위치와 인코딩
elasticore-migration-guide.md:44-49  ### 2. 생성 코드가 모델을 가진 모듈에 놓입니다
elasticore-migration-guide.md:51-57  ### 3. `DTOUtils.getMetaInfo(Class)` → `getMetaInfo(Class<T>)`
elasticore-migration-guide.md:59-65  ### 4. 새 DSL 검증은 2.x 모델에 반응하지 않습니다  [DSL_WARN, domainService, uses:]
elasticore-migration-guide.md:67-105  ## 3.0 기능을 쓰고 싶다면 (선택)
elasticore-migration-guide.md:71-79  ### 무엇이 생기나
elasticore-migration-guide.md:81-99  ### 켜는 법  [@ElastiCore, like, @search(like), @search(%_%)]
elasticore-migration-guide.md:101-105  ### 되돌리는 법  [publisher.rules]
elasticore-migration-guide.md:107-114  ## Spring Boot 4 / Spring AI 2.0으로 올리기 (3.0만)
elasticore-migration-guide.md:116-133  ## 자주 나오는 질문  [publisher.rules, elasticore.modules]
```
