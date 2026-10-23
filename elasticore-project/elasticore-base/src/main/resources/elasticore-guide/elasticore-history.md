## 3.0.261023

펫스토어에 팀 규칙 모듈(`elasticore { rules = [...] }`)을 붙여 rc1 로 생성·빌드·실행하며 규칙 가이드의 예제를 하나씩 다시
확인했다. 규칙이 쓴 것이 실행 중인 애플리케이션에 닿지 않는 두 자리를 고쳤다. 런타임 라이브러리는 Spring Boot 자동 구성을
갖게 되어 포트에 `@EnableElastiCore` 가 필요 없다. 그것을 실제 포트로 확인하다 찾은, 값 하나를 받거나 돌려주는 DBMS 포트
메서드(`Long countProducts()`, `nameOf(Long id)`, `return: decimal`)의 세 버그를 고쳤다. 2.x 생성기와 `jpa-springboot` 번들의
출력은 그대로다(컴파일되지 않던 `return: decimal` 포트만 바뀐다).

### Bug Fixes (팀 규칙)

#### v3 검색 엔드포인트가 요청 본문을 검증한다 - 규칙이 검색 DTO 에 붙인 제약이 동작
- 규칙이 검색 DTO 에 `@Size(min = 2)` 같은 제약을 붙여도(rule dev guide 6장의 "LIKE 검색은 2글자 이상") 생성된
  `search(@RequestBody XxxSrchDTO searchDTO)` 에 `@Valid` 가 없어 검사되지 않았다. 한 글자 검색이 200 으로 테이블을 훑었다
- 이제 v3·DDD·헥사고날(`v3/control.tmpl`)과 Kotlin(`kt/control.tmpl`)의 검색 엔드포인트가 `@Valid @RequestBody` 로 받는다.
  위반은 생성(`create`)과 같은 400 문제 응답이다(`code: "invalid"`, `fieldErrors[].code: "Size"`) - 펫스토어에서
  `{"name":"a"}` 로 확인
- 동작 변경 없음: 지금까지 생성되는 검색 DTO 에는 검증 어노테이션이 없다(이 저장소의 v3 샘플과 펫스토어의 모든
  `*SrchDTO`, 그리고 `srchdto` target 의 모든 내장 규칙을 확인). 응답이 달라지는 것은 검색 DTO 에 제약을 붙이는 규칙,
  손으로 고친 검색 DTO, 그리고 `env.yml` 의 `sortableInterface`/`pageableInterface` 로 지정한 프로젝트 인터페이스의 getter 에
  검증 어노테이션(예: `@Max(100) int getPageSize()`)이 있을 때뿐이다 - 그때는 그 제약이 이제 400 으로 검사된다
- 컨트롤러는 개발자 파일이라 이미 고친 컨트롤러는 그대로다. 한 번도 고치지 않은 컨트롤러는 다음 생성에 맞춰진다
- 2.x 출력(`control.tmpl`)은 그대로
- 파일: `elasticore-template/v3/control.tmpl`, `elasticore-template/kt/control.tmpl`, 테스트 `ProjectRuleReachTest`

#### `addMethod` 가 메서드 없는 클래스에서 버려지던 것
- `ctx(e).addMethod(...)` 는 클래스의 닫는 중괄호 앞에 메서드를 더하지만, 메서드 구역(`@Operation` 이나 `/**` 로 시작하는
  메서드)을 하나도 찾지 못한 클래스 - 필드만 있는 엔티티(펫스토어의 `Category`, `Country`, `Customer`) - 에서는 아무 말 없이
  버렸다
- 이제 구역이 없어도 마지막 멤버 뒤, 닫는 중괄호 앞에 쓴다. 더하는 것도 빼는 것도 없는 파일은 이전과 바이트까지 같다
- enum·interface·record(Kotlin `object` 포함)에서도 버려졌다 - 선언 줄을 `class` 로만 찾았기 때문. 이제 그 선언도 찾는다
  (ElastiCORE 의 enum 스켈레톤은 상수 목록 뒤에 늘 `;` 와 생성자가 있어, 더한 메서드가 그 뒤에 온다)
- 두 파일로 나뉜 루트에서는 프로젝트의 엔티티 규칙이 개발자 클래스(target `behavior`)에도 돈다 - 클래스 어노테이션을
  위해서다. 그렇게 빌려 도는 규칙의 `addMethod` 는 개발자 클래스에 쓰지 않는다 - 생성기 쪽 `XxxBase` 에 있고 개발자
  클래스가 상속받는다. `target = "behavior"` 로 쓴 규칙은 그대로 개발자 클래스에 더한다(처음 생성될 때)
- 파일: `MethodSections.java`, `SourceCode.java`, 테스트 `ProjectRuleReachTest`. 펫스토어에서 `Category`·`Country`·
  `Customer`·`Item`·`Product`·`PurchaseOrderBase` 에 한 번씩 생기는 것을 확인

### Documentation (규칙 API)

- 공개 규칙 API(`io.elasticore.base.model.pub.rules`)의 javadoc 에 남아 있던 "PROTOTYPE - part of the 3.0 publisher extension
  experiment" 문구를 지웠다(30개 파일). `Rule` 은 메서드 모양 여섯 가지와 팀 규칙이 기본 규칙 뒤에 돈다는 것을, `EntitySpec`·
  `SourceCode` 는 실제로 하는 일을 적는다
- rule dev guide 6장: `-PlistRules` 로 확인할 규칙 이름 중 없는 `acme-example` 을 `acme-internal` 로, 없는 메서드
  이름에 대한 로그를 실제 문장(`… is not declared in SalesOrderRepository (repository file); its annotations were not written`)으로
- 사이트의 팀 규칙 문서(https://www.elasticore.io/docs/guides/team-rules)를 펫스토어 예제로 다시 썼다 - 11개 페이지, 모든
  예제를 펫스토어 사본에서 생성·빌드·실행해 확인
- rule dev guide 10장 "규칙 API 레퍼런스": `@Rule` 속성과 메서드 모양, `EntitySpec`·`FieldSpec`·`DomainSpec`·`EnumSpec`·
  `PortSpec`, `SourceCode`(`addMethod`, `excludeMethod` 와 엔드포인트 이름, `removeField`, `dontMake`, `var`), `FieldCode`,
  `AnnoCode`, `ModelCheck`, 새 파일(`ArtifactContributor`), `Syntax`. 사이트에만 있던 것을 프로젝트에 복사되는 가이드에도
  두었다 - 프로젝트 안에서 일하는 코딩 에이전트는 사이트를 읽지 않는다. `excludeMethod` 의 이름과 엔드포인트 표, v3 와 2.x 호환
  번들의 차이(v3 는 `deleteBatch`·`batchUpdate` 를 함께 뺀다)는 이전에 어느 가이드에도 없었다

### New Features (가이드 색인)

#### `blueprint/elasticore-guide-index.md` - 가이드의 절마다 한 줄
- 생성할 때 가이드를 복사한 뒤, 복사된 가이드에서 색인을 쓴다: 절마다 `<가이드>:<첫 줄>-<끝 줄>  <제목>  [다루는 단어]`.
  제목은 `##`~`####`, 코드 블록 안의 `#` 줄은 제목이 아니다. 단어는 절의 코드 표기(`@search(like)`, `publisher.rules`,
  `excludeMethod`)와 예제의 DSL 어노테이션(코드 블록 안의 `@Override` 같은 Java 어노테이션은 빼고)이고, 경로·명령·JSON·
  파일 이름은 넣지 않는다(단어 목록은 160자 안). 코드 블록은 연 표시(백틱 또는 물결, 길이)와 같은 표시로만 닫힌다고 본다.
  줄 끝은 운영체제와 상관없이 `\n`
- 왜: 가이드 아홉 개는 합쳐 50만 바이트가 넘고 그중 규칙 가이드만 1,889줄이다. 가이드를 단어로 검색하면 줄 번호는 나오지만 그 절이
  어디서 끝나는지는 모른다 - 코딩 에이전트가 너무 많이 읽거나 다시 읽는다. 색인을 단어로 검색하면 읽을 범위가 나온다
  (`@access` → `elasticore-annotation-guide.md:1864-2005  ## @access / @owner — 누가 무엇을 할 수 있는가`)
- 색인은 검색용이다(약 570줄). 줄 번호가 옆의 가이드와 같은 버전에서 나오므로 낡지 않는다
- 새 파일 하나가 `blueprint/` 에 더해질 뿐, 생성 코드는 바뀌지 않는다. 쓰지 못하면 경고만 남긴다
- 파일: `GuideIndex.java`, `GuideDeployer.java`, 테스트 `GuideIndexTest`

### Improvements (런타임 - `@EnableElastiCore` 없이)

#### 런타임 라이브러리의 Spring Boot 자동 구성 - 포트가 어노테이션 없이 빈이 된다
- 런타임 프록시 포트(HTTP `@ExternalService`, DBMS `@DbmsService`)는 애플리케이션 클래스에 `@EnableElastiCore` 가 있어야
  빈이 되었다. 빠뜨리면 기동이 `No qualifying bean of type 'ShopReportPort'` 로 멈췄다 - 엔티티·로그인·오류 응답은 생성
  코드가 스스로 등록하는데 포트만 한 줄이 더 필요했고, 사람도 코딩 에이전트도 그 한 줄을 빠뜨렸다
- 이제 `elasticore-springboot3`·`elasticore-springboot4` 가 `META-INF/spring/...AutoConfiguration.imports` 로
  `io.elasticore.springboot.autoconfigure.ElastiCoreAutoConfiguration` 을 낸다. 의존성에 있으면 Spring Boot 가 기동할 때 읽고,
  `@SpringBootApplication` 클래스의 패키지와 그 아래(Spring Boot 의 auto-configuration packages - 엔티티·리포지터리를 찾는
  곳)에서 포트를 찾는다. **포트가 있을 때만** `@EnableElastiCore` 가 하던 것을 한다: 포트 등록, `EntityManagerFactory` 가 하나(또는
  `@Primary` 하나)이면 `DbTransactionGateway`, `ApplicationContextProvider`, 기동 배너. 포트가 없는 애플리케이션에는 아무 빈도
  더하지 않는다
- 이미 있는 것이 이긴다: `@EnableElastiCore` 가 함께 있어도 포트·빈은 한 번씩만(같은 포트는 처음 등록한 것), `@ComponentScan
  ("io.elasticore.springboot3")` 나 `@Bean` 으로 등록한 `DbTransactionGateway`(`DbmsSqlExecutor`)·`ApplicationContextProvider`
  가 있으면 자동 구성은 만들지 않고(`@ConditionalOnMissingBean`), 포트에 직접 선언한 `@Bean` 은 예전처럼 프록시를 대신한다.
  자동 구성 클래스는 `io.elasticore.springboot3` 밖의 패키지에 둔다 - 그 패키지를 컴포넌트 스캔하는 프로젝트가 자동 구성을 자기
  구성으로 읽지 않게
- 끄기: `elasticore.autoconfigure.enabled=false`(그때는 `@EnableElastiCore` 로 등록한다). 배너만 끄기:
  `elasticore.banner.enabled=false` - `@EnableElastiCore` 로 등록한 배너에도 적용되고, `false` 가 아닌 값은 무시한다(기동을
  멈추지 않는다)
- 기본으로 켠 이유(opt-in 원칙의 예외, 저장소 주인이 정함): 생성 코드는 한 줄도 바뀌지 않는다(`elcoreRulesCheck`·
  `elcoreTreeCheck` 그대로). 포트가 없는 애플리케이션 - 2.x 로 생성한 대부분의 프로젝트 - 에 생기는 것은 기동 때 루트 패키지를
  한 번 훑는 스캔(Reflections, 결과는 자동 구성과 등록기가 함께 쓴다)뿐이다. 포트가 있는 애플리케이션에서 달라지는 것:
  - 어노테이션을 빠뜨려 `No qualifying bean` 으로 멈추던 애플리케이션이 뜬다
  - `@EnableElastiCore` 가 애플리케이션 클래스보다 아래 패키지의 클래스에 붙어 있었다면, 이제 애플리케이션 클래스 패키지의
    포트도 등록된다
  - `EntityManagerFactory` 가 둘 이상이고 `@Primary` 가 없으면 `DbTransactionGateway` 를 만들지 않는다(어느 쪽인지 정할 수 없다)
    - DBMS 포트를 쓰려면 하나를 `@Primary` 로 하거나 `DbmsSqlExecutor` 를 직접 등록한다. `@EnableElastiCore` 로는 그 구성이
    기동부터 실패했다(`DbTransactionGateway` 생성자가 하나를 요구)
- 함께 고친 것(어노테이션으로 등록할 때도 같다):
  - 단순 이름이 같은 포트 둘(`shop.NotificationPort`, `billing.NotificationPort`)은 기동이 `BeanDefinitionOverrideException` 으로
    멈췄다. 이제 그런 포트는 모두 전체 이름으로 등록하고 경고를 남긴다(타입으로 주입하거나 `@Qualifier("<전체 이름>")`). 어느 하나만
    짧은 이름을 갖게 하지 않는 것은 그것이 클래스 경로를 읽는 순서로 정해지기 때문이다. 포트는 이름 순으로 등록한다
  - 포트 프록시는 빈(DBMS 실행기, HTTP 인증·변환기)을 자기가 만들어진 빈 팩토리에서 찾는다. 전에는 정적
    `ApplicationContextProvider` 에서 찾아, 마지막으로 뜬 다른 컨텍스트를 보거나 닫힌 컨텍스트에 물어 `has been closed already` 로
    실패할 수 있었다(테스트가 컨텍스트를 여럿 띄울 때). 정적 참조는 빈 팩토리 없이 만든 프록시만 쓰고, 자기 컨텍스트가 닫히면
    놓는다(그 사이 다른 컨텍스트가 들어오지 않았다면)
  - 자동 구성의 조건과 등록기가 함께 쓰는 스캔 결과는 등록기가 쓴 뒤 버린다 - 남겨 두면 포트 클래스와 그 클래스 로더를 붙잡아,
    devtools 로 재시작할 때마다 이전 로더가 메모리에 남는다
- 확인: 포트 두 개(DBMS `countProducts()`, HTTP `getProduct(id)` - 같은 서버를 부름)가 있는 헥사고날 프로젝트를 로컬 빌드로 생성해,
  `@EnableElastiCore` 없이 Spring Boot 4.0.8 과 3.5.16 에서 기동·호출(`{"countBySql":2,"nameByHttp":"Pen"}`), 3.5.16 의
  `@SpringBootTest` 에서 두 포트 주입과 호출, `elasticore.autoconfigure.enabled=false` 이면 `No qualifying bean` 으로 실패,
  어노테이션과 함께면 배너 한 번·응답 그대로. 단위 테스트는 Boot 3·4 런타임 모두에서: 어노테이션 없이 포트 등록, 포트 없으면 빈
  없음, `EntityManagerFactory` 없음·하나·둘·`@Primary` 하나, 어노테이션·기본 필터의 컴포넌트 스캔·직접 선언한 빈과 함께일 때
  하나씩, 같은 단순 이름의 포트 둘, 끄기, 닫힌 컨텍스트, imports 파일
- 남은 것: 포트를 애플리케이션 클래스 패키지 밖에 두는 구성(`@SpringBootApplication(scanBasePackages = ...)` 로 다른 패키지를
  스캔하는 경우 포함)은 엔티티 스캔처럼 찾지 못한다 - `@AutoConfigurationPackage(basePackages = ...)` 나 그 패키지 클래스의
  `@EnableElastiCore`, 또는 `@Bean` 으로 등록한다(포트 가이드 5장)
- 파일: `ElastiCoreAutoConfiguration.java`, `AutoConfiguration.imports`, `PortServiceRegistrar.java`(포트 스캔을 한 번,
  찾는 패키지를 하위 클래스가 정함, 이름 충돌), `ApplicationContextProvider.java`, `HttpServiceProxyFactory.java`·
  `DbmsServiceProxyFactory.java`(자기 빈 팩토리에서 찾기), `ElastiCoreBannerPrinter.java`,
  `EnableElastiCore.java`(javadoc), 테스트 `ElastiCoreAutoConfigurationTest`, `ElastiCoreBannerPrinterTest`. 포트 가이드 2장 머리·5장,
  규칙 가이드의 "어댑터는 빈이어야 합니다"

### Bug Fixes (DBMS 포트 - 값 하나를 받고 돌려주는 메서드)

#### `long countX()`·`String`·`BigDecimal` 을 돌려주는 DBMS 포트 메서드
- 포트 가이드 3.3 은 `return: long`·`string`·`boolean` 을 단건 조회로 적고 있지만, 런타임은 결과 행을 늘 DTO 로 만들었다
  (`outputType.getDeclaredConstructor().newInstance()` 에 열 이름의 필드를 채움). 그래서 숫자·불린은 호출마다
  `Failed to map tuple to DTO: Long` 으로 실패했고, `String` 은 빈 문자열(`new String()`), `java.util.Date` 는 호출한 시각을
  돌려주었다. 샘플은 그런 메서드(`ShopReportService.countOrdersByStatus`)를 선언만 하고 부르지 않아 드러나지 않았다
- 이제 반환 타입이 값 하나(기본형과 그 래퍼, `Number` - `BigDecimal`·`BigInteger` 포함 -, `String`, `Boolean`, `Character`,
  `java.time` 의 날짜·시간, `java.util.Date`)이면 행의 첫 열을 그 타입으로 바꿔 돌려준다. 숫자 열은 `BigDecimal`·`BigInteger` 로도,
  한 글자 열은 `char`·`Character` 로도 바꾼다. 열 값이 NULL 이면 `null`, 행이 없으면 단건 조회는 `null`
- 생성되는 포트 인터페이스는 래퍼 타입(`Long countProducts()`)이다. 손으로 기본형(`long`)으로 선언하면 결과가 없을 때 프록시가
  돌려주는 `null` 이 `NullPointerException` 이 된다 - 래퍼로 선언하거나 SQL 에서 `COALESCE` 로 값을 정한다
- 바꾸지 못하는 것(전처럼 DTO 로 만들려다 실패): enum, `UUID`, `byte[]`. 시각 열(`Timestamp`)을 `LocalDate`·`OffsetDateTime` 으로
  받는 것도 아직이다
- 단건 조회가 첫 값을 `stream().findFirst()` 로 꺼내, 값이 NULL 이면(`SUM` 이 빈 집합) `NullPointerException` 이었다. 이제 `null`
- DTO 와 `Map` 을 돌려주는 메서드는 그대로
- 파일: `DbTransactionGateway.java`, 테스트 `DbTransactionGatewayValueTest`(H2 에서 COUNT → `Long`·`long`·`Integer`·`BigDecimal`,
  이름 → `String`(빈 문자열이 아님), 합계 → `BigDecimal`, 불린, `Double`, 한 글자 → `Character`·`char`, NULL 값 → `null`, 행 없음 →
  빈 목록)

#### 값 하나를 받는 DBMS 포트 메서드 - `nameOf(Long productId)`
- 파라미터가 하나이면 프록시는 기본형이나 `String` 만 값으로 보고, 나머지는 객체로 보아 getter 로 SQL 의 `:이름` 을 채웠다. 생성되는
  포트는 DSL 의 `long` 을 `Long` 으로 선언하므로, `long` 파라미터 하나짜리 메서드는 `Long.getProductId()` 를 찾다 아무것도 묶지
  못했다 - 오류 없이 결과가 비었다(`WHERE product_id = NULL`)
- 이제 값 타입(Spring 의 `BeanUtils.isSimpleValueType` - 기본형과 래퍼, `String`, 숫자, 날짜·시간, enum, `UUID` 등)은 파라미터
  이름으로 묶는다. DTO 하나, `Map`, `List`(배치)는 그대로
- 달라지는 경우 하나: enum 파라미터 하나를 그 getter 로 묶던 SQL(`:code` 에 `Status.getCode()`)은 이제 파라미터 이름으로 묶인다 -
  그런 SQL 은 `:status` 로 쓰거나 DTO 로 감싼다. 이름으로 묶으려면 클래스에 파라미터 이름이 남아 있어야 한다(`-parameters`,
  Spring Boot 의 Gradle·Maven 플러그인이 켠다)
- 파일: `DbmsServiceProxyFactory.java`, 테스트 `DbmsPortInputTest`

#### 포트의 `return: decimal` 이 Java 에 없는 타입 `decimal` 로 생성되던 것 (생성기)
- 포트 메서드의 반환 타입은 `long`·`string` 같은 일곱 단어만 래퍼 타입으로 바꾸고 나머지는 그대로 썼다. DSL 가이드가 필드 타입으로
  적는 `decimal`·`bigdecimal` 을 반환 타입에 쓰면 `decimal totalPrice();` 가 생성되어 컴파일되지 않았다
- 이제 `java.math.BigDecimal` 로 쓴다(포트 인터페이스는 `java.math` 를 import 하지 않아 전체 이름). `List<decimal>`·`Page<decimal>`
  안의 것도. 2.x 생성기와 규칙 엔진이 같은
  로더를 쓴다. 이전 출력이 컴파일되지 않던 경우만 바뀐다 - `elcoreRulesCheck`·`elcoreTreeCheck` 그대로
- 파일: `PortModelLoader.java`, 테스트 `PortReturnTypeTest`(생성해서 `Long`·`java.math.BigDecimal`·`String` 확인)
- 확인(위 세 가지): 같은 헥사고날 프로젝트에 `totalPriceAbove(minPrice: long): decimal`, `nameOf(productId: long): string` 을 더해
  Boot 4.0.8 에서 호출 - `{"totalAbove2":3.00,"totalAbove100":null,"name":"Pen"}`, 없는 id 는 `"name":null`

## 3.0.261022-rc1

같은 요구사항(도서·회원·대출 API)을 에이전트가 ElastiCORE 로 만들게 하고 어디서 Java 를 손으로 쓰는지 보았다. 네 군데였다:
서버가 정하는 값(반납일)을 클라이언트가 보내도 막을 수 없었고, "아직 반납하지 않은" 검색을 DSL 로 쓸 수 없었고,
ISBN 13자리 같은 정규식 검사는 `@pattern` 이 컴파일되지 않는 코드를 냈고, `member: Member!` 는 키(`memberId`)가 아니라
중첩 객체를 요구했다. 아래 네 항목이 그 자리를 DSL 로 메운다. 네 항목 모두 그 문법을 쓰지 않는 프로젝트의 생성 결과는
바뀌지 않고, 2.x 생성기와 2.x 와 같은 출력을 내는 `jpa-springboot` 번들은 그대로다(`elcoreRulesCheck -Pstrict` 150 MATCH /
0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra).

### New Features (DSL - 요구사항을 손코딩 없이)

#### 필드 `@readonly` 는 서버가 정하는 값 - v3 매퍼가 DTO 에서 엔티티로 쓰지 않는다
- 필드 `@readonly`(별칭 `@noinput`)는 지금까지 DTO 에 `@JsonProperty(access=READ_ONLY)` 만 붙였다. JSON 요청의 값은 Jackson
  이 버렸지만, v3 매퍼는 서버 코드가 DTO 에 넣은 값(`dto.setX(..)` 후 `service.save(dto)`)을 그대로 엔티티에 썼고, `!` 가
  붙은 `@readonly` 필드는 DTO 의 `@NotNull` 이 남아 그 값을 보낼 수 없는 생성 요청을 모두 400 으로 거절했다. DDD 의 나뉜
  루트에서는 매퍼가 요청 값을 `to.creation()::setX` 로 새 루트에 넣었다
- 이제 v3 번들(`jpa-springboot-v3`·DDD·헥사고날·Kotlin)에서 `@readonly`/`@noinput` 필드는
  - 매퍼의 DTO -> 엔티티 방향(`toEntity`, `mapping(dto, entity, ...)`, EntityManager 판 포함)이 쓰지 않는다. 엔티티 -> DTO
    방향은 그대로라 응답에 나간다. 엔티티 필드에 붙이면 그 엔티티의 모든 DTO 에, `dto.yml` 의 DTO 필드에 붙이면 그 DTO 의
    매핑에만 적용된다. `@id` 에 붙은 `@readonly` 는 식별자 쓰기를 그대로 둔다
  - DTO 의 `@NotNull`·`@NotBlank` 를 뺀다. 엔티티 컬럼(`nullable = false`)은 그대로라 서버가 채운다
  - DTO 의 `@Schema` 에 `accessMode = Schema.AccessMode.READ_ONLY` 를 더한다. springdoc 은 `@JsonProperty` 만으로도
    `readOnly: true` 를 냈다 - `@Schema` 만 읽는 도구를 위해 적는다
  - 나뉜 루트에서는 여전히 잠긴 필드다: `protected` setter 와 `creation()` setter 가 남아 서버 코드가 새 루트에 넣는다
- v3 동작 변경: 필드에 `@readonly` 를 붙이고 서버 코드에서 DTO 에 값을 넣어 저장하던 코드는 그 값이 더는 저장되지 않는다.
  엔티티에 넣는다(확장 서비스의 `beforeSave`, 루트의 메서드). HTTP 동작은 같다 - JSON 의 값은 이전에도 버려졌다. 이유:
  `@readonly` 는 "요청으로 받지 않는다" 는 선언인데 매퍼가 그 선언을 무시해, 서비스가 `creation().setX(null)` 같은 우회를
  쓰고 있었다(새 루트에서는 아무 일도 하지 않는다). `@readonly` 가 없는 프로젝트의 v3 출력은 바뀌지 않는다
- 가이드: annotation guide 에 `@readonly / @noinput` 절(생성물 표, `@behavior` 와의 비교, `creation().setX(null)` 은 새 루트에서
  아무 일도 하지 않는다는 것), §2.8 감사 필드, jsonview 표, `@behavior` 는 나뉜 루트에서만 효과가 있다는 것. dsl guide 필드 표와
  `@behavior` 행, rule guide 의 잠긴 루트 표와 '새 루트의 null'
- 확인: 규칙 단위 테스트(Java·Kotlin 매퍼, DTO, 2.x 호환 출력, 나뉜 루트, `@editable` 루트), hexagonal Java·Kotlin 도서관 모델을
  `RulesGenRunner` 로 생성해 매퍼·DTO·`LoanBase` 를 읽는 테스트. `elasticore-template-ddd` 의 catalog `Book` 에 `shelfCode:
  string(10)! @readonly` 와 그 값을 채우는 `beforeSave` 를 임시로 넣고 Spring Boot 3.3.2 에서 실행해 확인했다: 클라이언트가
  값을 보낸 POST·PUT 은 200 이고 `beforeSave` 의 값이 남는다. `service.save(dto)` 도 DTO 에 넣은 값 대신 `beforeSave` 의 값을
  저장한다. `/v3/api-docs` 는 `readOnly: true` 이다. 변경 전 출력에서는 POST 가 400(`shelfCode: must not be null`)이었고,
  DTO 에 넣은 값이 저장되었다
- 남은 것: DTO 의 `@ref(...)` 필드에 붙인 `@readonly` 는 아직 관계 쓰기(`reverseRefPass`)를 막지 않는다. 나뉜 루트의 필드
  `@behavior` 는 `@JsonProperty` READ_ONLY 만 붙고 `@Schema accessMode` 는 붙지 않는다
- 관련 파일: `WriteGuardRules.java`, `DtoBaseRules.java`, `MapperBaseRules.java`, `ServerSetFieldTest.java`, `SealedRootTest.java`,
  `ServerSetFieldGenerationTest.java`

#### `@search(isnull)` - 값이 비어 있는지로 검색한다
- "아직 반납하지 않은 대출", "아직 입고된 적 없는 상품"처럼 값이 비어 있는지로 찾으려면 Service 에
  `Q.Loan.returnedDate(Op.IS_NULL, null)` 를 손으로 쓰거나 Repository 에 JPQL 을 따로 두어야 했다. `@search(isnull)` 은
  2.x 와 규칙 엔진 모두에서 경고 없이 같은 값 비교(`c.equal`)로 만들어졌고, 가이드는 `ISNULL`/`ISNOTNULL` 을 미구현으로
  적고 있었다
- 이제 `returnedDate: date @search(isnull)` 은 검색 DTO 에 `Boolean returnedDateEmpty` 하나를 만든다. 보내지 않거나
  `null` 이면 조건 없음, `true` 면 `c.isNull(r.get("returnedDate"))`, `false` 면 `c.isNotNull(...)`. `@Schema` 설명은
  `... Field is empty (true) or set (false). field:returnedDate` 이고, 보내는 값이 날짜가 아니므로 `@DateTimeFormat`·
  `@JsonFormat`·`example` 은 붙지 않는다. `@ref(order.shippedDate)` 와 쓰면 `r.get("order").get("shippedDate")` 를
  검사하고, 쉼표 뒤 두 번째 값은 다른 조건처럼 엔티티 필드 이름이다(`@search(isnull,open)` -> `openEmpty`, `r.get("open")`)
- Kotlin(`val xEmpty = searchDTO.xEmpty`, `if(xEmpty!!)`, `Specification<E> { r,q,c -> c.isNull(...) }`)과
  TypeScript(`xEmpty?: boolean`)도 같은 규칙에서 나온다
- 필드 하나에 조건 하나: `isnull` 을 적은 필드는 `between` 의 From/To 를 만들지 않는다. `@expose` 의 타입별 자동
  할당(날짜 -> `between`)은 바뀌지 않는다
- v3 번들(`jpa-springboot-v3`·DDD·헥사고날·Kotlin·`ts-model`) 전용이다. 2.x 생성기와 2.x 호환 번들(`jpa-springboot`)은
  이전처럼 같은 값 비교를 만들고 `@Schema` 문구도 그대로다 - `StringUtils.getOperatorDescription(op)` 는 바뀌지 않고 새
  문구는 `getOperatorDescription(op, true)` 에만 있다
- v3 번들에서 이미 `@search(isnull)` 을 적어 둔 필드는 검색 속성이 `<필드>`(필드 타입, 같은 값 비교)에서
  `<필드>Empty`(Boolean)로 바뀐다. 미구현으로 문서화된 키워드가 조용히 같은 값 비교가 되던 것을 바로잡는 것이며,
  저장소의 블루프린트 중 쓰는 곳은 없다
- 확인: 단위 테스트로 Java·Kotlin·TypeScript 출력과 2.x 호환 출력이 그대로인 것을 확인했다. `elasticore-template-ddd`
  의 `Product.lastRestocked` 에 잠시 `@search(isnull)` 을 붙여 생성·컴파일하고, H2 위에서
  `POST /api/v1/shop/product/search` 가 `true` 는 입고일이 없는 상품만, `false` 는 있는 상품만, 생략과 `null` 은 둘 다
  돌려주는 것을 확인했다. `elasticore-template-kt` 의 `Project.endDate` 로 Kotlin 을 생성해 `compileKotlin` 이 통과하고
  TypeScript 에 `endDateEmpty?: boolean;` 이 나오는 것을 확인했다(샘플 변경은 되돌렸다)
- 남은 것: List 필드나 `!` 필드에 `isnull` 을 적어도 경고하지 않는다. v3 의 별칭 구분자는 쉼표인데 가이드 §3.1 은 `$` 로
  적고 있다(별개 버그)
- 관련 파일: `SearchDtoBaseRules.java`, `MapperSpecRules.java`, `StringUtils.java`, `SearchDtoTest.java`,
  `MapperSpecTest.java`, `TypeScriptModelTest.java`, `OperatorDescriptionTest.java`,
  `elasticore-annotation-guide.md`(3.2, 3.4.1, 3.8, 8.5), `elasticore-dsl-guide.md`, `elasticore-dev-guide.md`

### Bug Fixes (생성기)

#### `@pattern` 이 컴파일되는 `@Pattern` 을 쓰고, `@min` 처럼 값 다음에 메시지를 받는다
- 규칙 엔진의 v3·Kotlin 번들에서 `@pattern(regexp="...", message="...")` 이 컴파일되지 않았다. 네 줄짜리
  `@Pattern( regexp="...", msg="..." )` 를 썼는데 Jakarta `@Pattern` 에는 `msg` 속성이 없다. 이제 한 줄
  `@Pattern(regexp = "...", message = "...")` 을 쓴다
- 위치 형식 `@pattern('^[0-9]{13}$', 'ISBN must be 13 digits')`, `@pattern('^[A-Z]+$')` 도 읽는다. 이전에는 아무것도
  생성하지 않았다. 메시지는 따옴표와 괄호 밖의 첫 쉼표 뒤라서 `{10,13}` 은 정규식에 남는다. 메시지를 생략하면 TypeScript
  클라이언트와 같은 `"<라벨> is not in the expected form"` 을 쓴다. TS 의 `validate...()` 도 같은 값을 읽는다.
  이전에 아무 검사도 없던 필드(위치 형식, 메시지 없는 이름 형식)에 요청 검사(400)가 새로 생긴다
- Kotlin 은 문자열 템플릿이 되는 `$`(영문자·`_`·`{` 앞)를 `\$` 로 쓴다. `@Pattern` 은 문자열 필드에만 붙는다. 날짜의
  `@pattern(yyyyMMdd)` 는 이전처럼 날짜 형식이다. clean 출력은 문자열 필드의 `@pattern` 을 Swagger `example` 로 쓰지 않는다
  (위치 형식에서는 `^[0-9]{13}$', 'ISBN must be 13 digits` 같은 깨진 값이 나왔다)
- 정규식은 Java 문자열 리터럴의 내용으로 그대로 들어가므로 백슬래시는 `\\d` 처럼 두 번 쓴다. `\d` 처럼 하나만 쓰면
  규칙 엔진이 DSL 경고를 낸다(2.x 추출기는 경고에 실패하므로 규칙 엔진에서만)
- 2.x 생성기(`elcore`)와 2.x 호환 번들(`jpa-springboot`)의 출력은 그대로다(`msg=`, 이름 형식에 메시지가 있을 때만) -
  2.x 의 `@pattern` 은 여전히 컴파일되지 않는 코드를 내며, 2.x 호환을 위해 손대지 않았다
- 확인: 도서관 모델(`isbn: string(13)! @id @pattern('^[0-9]{13}$', 'ISBN must be 13 digits')`)을 layered·Kotlin·TS 로
  생성했다. Java DTO 는 javac, Kotlin DTO 는 kotlinc 1.9.24 로 컴파일했고, Hibernate Validator 8 이 `12345` 를
  "ISBN must be 13 digits" 로 거부하는 것을 확인했다. TS 의 `validateBook()` 이 node 에서 같은 메시지를 내는 것도 확인했다
- 남은 것: `=` 가 든 정규식은 이름 형식으로만 쓸 수 있다. 따옴표 하나로 감싼 이름 형식의 값은 공백에서 잘린다(기존 파서).
  DDD 루트의 저장 시점 검사(`@min` 처럼)는 하지 않는다
- 관련 파일: `FieldSpec.java`, `DtoBaseRules.java`, `FieldDoc.java`, `TypeScriptDtoFileRules.java`, `AbstractModelLoader.java`,
  `PatternAnnotationTest.java`, `DtoGapCoverageTest.java`, `elasticore-annotation-guide.md`

#### 필수 관계(`post: Post!`)는 저장 요청에서 FK id 필드로 요구한다
- `@expose`/`@service` 엔티티가 자동 생성하는 DTO 에서 `!` 가 붙은 to-one 관계는 중첩 DTO(`PostDTO post`)에 `@NotNull`·
  `requiredMode=REQUIRED` 가 붙고 FK id 필드(`postPostId`)에는 붙지 않았다. 매퍼는 저장/수정할 때 `postPostId` 만 읽으므로
  (`em.getReference`), 키만 보낸 요청은 `@Valid` 검증에서 거절되고 중첩 객체만 보낸 요청은 검증을 통과해도 관계를 연결하지
  못했다. 요청은 둘 다 보내야 했다
- 이제 v3·Kotlin·TypeScript 생성(`jpa-springboot-v3`·DDD·헥사고날·Kotlin·TypeScript 번들)에서는 FK id 필드가 `@NotNull`·
  `requiredMode=REQUIRED` 이고, 응답용 중첩 DTO(`accessMode = READ_ONLY`)는 선택이다. TypeScript 는 `post?: PostDTO;`·
  `postPostId: number;` 로 선언하고 `validateXxx` 가 `post` 를 요구하지 않는다. `ddd-reference` 를 쓰는 번들은 ID 필드(`memberId`)가
  이미 필수였고, 중첩 DTO 만 선택이 된다. 키 이름 규칙(`필드 + 키`, 키가 `id` 면 `topicId`)은 그대로다
- **동작 변경 (v3·Kotlin·TypeScript 사용자)**: 중첩 `post` 만 보내던 클라이언트는 이제 `@Valid` 검증에서 거절된다 - FK id 필드를
  보내야 한다(그 요청은 이전에도 관계를 연결하지 못했다). `postPostId` 를 함께 보내던 클라이언트는 그대로 동작한다. OpenAPI 의
  required 가 `post` 에서 `postPostId` 로 옮겨 간다
- 바뀌지 않는 것: 2.x 생성기(`elcore`)와 2.x 와 같은 출력을 내는 `jpa-springboot` 번들(중첩 DTO 에 `@NotNull` 그대로), 검색 DTO
  (`XxxSrchDTO` 의 `postPostId` 는 검색 조건이라 필수가 아니다), Entity(`@JoinColumn(nullable = false)`), 매퍼, `dto.yml` 에 직접
  선언한 DTO. `!` 가 붙은 엔티티 참조가 없는 프로젝트의 생성 결과는 바뀌지 않는다
- 확인: `Post`·`Comment`(`post: Post!`) 게시판을 v3 layered·Kotlin·TypeScript·호환 모드·2.x 로, `Member`·`Book`·`Loan`
  (`member: Member!`, `book: Book!`, Book 의 키 `isbn`)을 헥사고날로 생성해, v3 `CommentDTO` 의 `postPostId` 에 `@NotNull`·`REQUIRED`
  가 붙고 `post` 에는 없는 것, 키가 `id` 인 `topic: Topic!` 의 `topicId` 가 필수인 것, 선택 관계의 `postPostId` 와 `CommentSrchDTO` 의
  `postPostId` 가 그대로인 것, 헥사고날 `LoanDTO` 의 `memberId`·`bookIsbn` 이 필수이고 `member`·`book` 이 선택인 것, 호환 모드 출력이
  2.x 와 같은 것을 테스트로 확인했다. DTO 와 검색 DTO 가 함께 쓰는 키 필드는 복사본에만 `@notnull` 을 붙여, 어느 DTO 를 먼저
  만들어도 검색 DTO 가 바뀌지 않는 것도 확인했다
- 남은 것: TypeScript 의 `XxxFields` 는 FK id 필드를 여전히 `readOnly: true` 로 표시하고 `validateXxx` 가 검사하지 않는다
- 관련 파일: `SpecFactory.java`, `RulesGenRunner.java`, `RulesVerifyRunner.java`, `RequiredReferenceDtoTest.java`,
  `elasticore-annotation-guide.md`, `elasticore-dsl-guide.md`, `elasticore-rule-guide.md`

### Improvements (런타임 - 손으로 쓴 규칙의 409)

#### `ConflictException` - 손으로 쓴 규칙이 409 를 자기 메시지로 낸다
- "빌려줄 권수가 없다", "이미 반납한 대출이다" 같은 규칙을 `beforeSave` 나 루트 메서드에 쓸 때 409 로 답할 예외가 없었다.
  `DataIntegrityViolationException` 을 던지면 409 는 나가지만 `detail` 이 데이터베이스 제약 문구("A unique value is already
  taken, or the row is still referenced by another.")로 바뀌어 이유를 알 수 없었다
- `io.elasticore.springboot3.validation.ConflictException` (`BadRequestException` 과 같은 자리)을 던지면 생성된
  `XxxApiErrors` 가 409, `code: conflict`, 던진 메시지를 `detail` 로 답한다. Java 와 Kotlin 의 `apiErrors` 템플릿에 처리기를
  더했다. 2.x 와 `jpa-springboot` 번들에는 API 오류 처리기가 없으므로 바뀌지 않는다
- `XxxApiErrors.java` 는 한 번만 생성되는 파일이라 이미 생성한 프로젝트는 그 처리기 다섯 줄을 손으로 더하거나 파일을 지우고
  다시 생성한다. 저장소의 샘플에는 더해 두었다
- 확인: 도서 대출 프로젝트의 `LoanService.beforeSave` 와 반납 메서드에서 던져 `{"status":409,"code":"conflict","detail":"No
  copies of book ... left: 1 of 1 are on loan"}` 이 나오는 것을 확인했다
- 관련 파일: `ConflictException.java`, `elasticore-template/v3/apiErrors.tmpl`, `elasticore-template/kt/apiErrors.tmpl`,
  `elasticore-rule-guide.md`(쓰기 경로가 지키는 것)

### Bug Fixes (Ossie 내보내기 - dialect)

#### 식을 `OSSIE_SQL_2026` 으로 내보낸다. 서브쿼리 `@formula` 만 `ANSI_SQL`
- `OssieSemanticModelExporter` 가 모든 필드 식과 지표 식을 `ANSI_SQL` 로 내보냈다. Ossie core spec 은 `OSSIE_SQL_2026` 만
  의미를 정의하고(`spec.md` "Dialects": 나머지 이름은 벤더 언어, 소비자가 무시해도 됨) 그것을 기본 dialect 로 삼으므로,
  `ANSI_SQL` 로 내보낸 모델은 읽는 쪽이 식을 전부 건너뛰어도 스펙에 맞는 것이었다
- 컬럼 이름인 필드 식과 `metrics:` 의 지표 식(집계, `CASE WHEN`, `CAST`, `NULLIF`, 비교 - 모두 `expression_language.md`
  의 지원 구문)은 `OSSIE_SQL_2026` 으로 내보낸다. `@formula` 는 `OssieSql` 이 가른다: 문자열 리터럴 밖에 SELECT 같은 질의
  단어가 없으면 `OSSIE_SQL_2026`, 서브쿼리·CTE 처럼 그 언어가 지원하지 않는 식이면 `ANSI_SQL` 항목 하나로 둔다. `@formula`
  필드의 `custom_extensions` 에 `"formula": "database"` 를 더했다
- 확인: `OssieSqlTest`(식 16개·질의 5개), `OssieFormulaDialectTest`(@formula 둘을 가진 프로젝트를 생성해 내보낸 파일의
  dialect 확인), `OssieExportTest`(ddd 샘플의 모든 필드가 `OSSIE_SQL_2026`); `elasticore-template-ddd` 와
  `elasticore-sample-petstore` 의 `semantic_model.yml` 을 다시 생성했다
- 관련 파일: `OssieSql.java`, `OssieSemanticModelExporter.java`, `OssieSqlTest.java`, `OssieFormulaDialectTest.java`,
  `OssieExportTest.java`, `elasticore-rule-guide.md`(dialect 단락), `dsl-guide.html`(펫스토어 안내 페이지의 예)

### New Features (로그인 API 생성)

#### 엔티티 줄의 `@signin` - signin·signout·me 가 생성된다
- 펫스토어를 에이전트(agent kit)로 세 번(Java hexagonal, Java ddd, Kotlin hexagonal) 새로 만들게 했더니 셋 다,
  그리고 저장소의 펫스토어 샘플도 같은 로그인 컨트롤러(로그인·로그아웃·지금 누구인지)를 손으로 썼다. 세 에이전트 모두
  `SessionCurrentUser` 의 사용법을 찾으려고 jar 를 `javap` 로 열었다
- `@login`·`@password` 가 있고 `@expose` 된 엔티티의 줄에 `@signin` 을 쓰면 그 컨트롤러에 `POST /signin`
  (`{"<login 필드>", "<password 필드>"}`, 틀리면 401 `Wrong <login> or password`), `POST /signout` (204), `GET /me`
  (방문자 401) 가 붙는다. CoreService·유스케이스에 `me()`, TypeScript 클라이언트에 `signIn`·`signOut`·`me()`(방문자면 null).
  `@login`·`@password` 만으로는 전처럼 `authenticate` 만 생긴다 - 계정 상태 확인 같은 자체 로그인을 하는 애플리케이션에
  두 번째 로그인 경로가 저절로 생기지 않게 **요청할 때만** 만든다 (리뷰에서 기본으로 켜는 안을 되돌렸다)
- 세션에는 행의 키와 역할이 들어간다. 역할은 `@role` 필드, 없으면 `role` 필드인데 **관리자만 쓰는 필드
  (`@access(admin)`) 이고 enum 이나 문자열일 때만** 쓴다 - 아니면 가입자가 자기 역할을 ADMIN 으로 보내고 로그인해
  관리자가 될 수 있으므로, DSL 경고와 함께 역할 없이 로그인한다
- 애플리케이션에서 `@signin` 엔티티는 하나만: 세션은 맨 키만 들고 있어 둘이면 서로의 행으로 읽힌다. 둘 이상이면 어느
  쪽에도 만들지 않고 DSL 경고. 키가 둘 이상이거나 `@expose` 가 없을 때도 경고 (애플리케이션을 어떻게 세는지, 그리고
  비밀번호를 누가 바꿀 수 있어야 하는지는 이 섹션 끝의 "로그인 API - 리뷰" 항목)
- 손댄 컨트롤러는 생성기가 건너뛰므로 엔드포인트가 들어가지 않는다. 이제 생성기가 그 컨트롤러에 없는 엔드포인트
  (`POST /signin` 등, 새 명령도)를 DSL 경고로 알린다 - TypeScript 클라이언트는 늘 다시 생성되어 그것을 부르기 때문
- 펫스토어 샘플의 `Customer` 에 `@signin` 을 붙이고 `AccountController` 는 가입하고 바로 로그인하는 것만 남겼다.
  화면(`web/src/shop.ts`)은 생성된 `CustomerApi.signIn/signOut/me` 를 쓴다
- 확인: `SignInAndReferencesTest`(Java·Kotlin·TypeScript, 역할 거부, 두 엔티티, 키 둘), 펫스토어 `ShopFlowTest`(생성된
  `/customer/signin`, 로그아웃 뒤·방문자의 `/customer/me` 401), `webBuild` 타입 검사, `elasticore-template-kt` 의
  `AgentController`(역할 포함) 컴파일·테스트, agent kit 의 상점 레시피 계약(S3)
- 관련 파일: `ServiceAccessRules.java`(`signInControl`, `me`, `signsIn`, `whyNoSignIn`, `roleField`), `HexUseCaseRules.java`,
  `TypeScriptApiRules.java`, `RulesGenRunner.java`(`warnIfEndpointsMissing`), `SpecFactory.java`, `SignInAndReferencesTest.java`,
  `elasticore-annotation-guide.md`, `elasticore-rule-guide.md`

### Improvements (오류 응답 - 상태 충돌 409, 없는 참조 404)

#### 생명주기가 거절한 명령은 409, 요청이 가리킨 행이 없으면 404
- 세 에이전트 모두 생성된 `XxxApiErrors` 를 고쳐 "배송된 주문 취소"를 422 에서 409 로 바꿨다. RFC 9110 의 409 가
  "대상의 현재 상태와 충돌"이므로 그쪽이 맞다. 다른 불변식 위반은 그대로 422 다
- `apiErrors` 템플릿(Java, Kotlin)의 `refused(...)`: 코드가 `state.` 로 시작하면 409 (제목 코드 `state`, "Not allowed in
  this state"), `reference.` 로 시작하면 404, 나머지는 422. 공통 코드에 `error.state.title` 을 더했다
- 이미 있는 프로젝트: `XxxApiErrors` 를 **손댄 적이 없으면** 다시 생성되어 409·404 로 답한다 - 422 로 분기하던
  클라이언트는 함께 고친다. **손댄 적이 있으면** 그대로라 생명주기 거절도, 없는 참조(아래)도 422 다. 그 파일의
  `refused(...)` 를 고친다. 생성 코드의 주석("Refuses a delete in any other state: 409")도 바뀌었다.
  펫스토어 샘플은 다시 생성하고 테스트를 409·404 로 바꿨다(`petstore-messages_ko.properties` 에 새 제목)
- 관련 파일: `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ErrorCatalog.java`, `ShopFlowTest.java`, `ShopModelTest.java`,
  `elasticore-rule-guide.md`, `elasticore-dsl-guide.md`, `elasticore-annotation-guide.md`, `elasticore-ddd-customize-guide.md`

### Improvements (DDD 번들 - 참조한 행이 있는지 저장 전에 확인)

#### `requireReferences(lookup)` - 없는 id 를 가리키는 참조는 404 로 거절
- 다른 애그리거트를 id 로 참조하는 필드(`loan.memberId`, 주문 줄의 `itemId`)에 없는 id 를 보내면 외래 키 위반이 409
  "Conflicts with stored data" 로 나갔다. 그래서 도서 대출 레시피와 펫스토어를 만든 세 에이전트가 모두 저장 전에
  `entityManager.find` 로 확인하는 코드를 손으로 썼다
- 그런 참조가 있는 엔티티(직접, 또는 소유한 멤버를 통해)에 `requireReferences(Lookup)` 이 생기고, CoreService 가 생성·
  수정·일괄 수정·조건 수정에서 `beforeSave` 전에 부른다. 없으면 `InvariantViolationException("reference.<Entity>.<relation>",
  "<Target> 99 does not exist")` - API 는 404. `@snapshot` 이 원본 행을 못 찾을 때도 같은 코드·문구(전에는
  "OrderLine.item 99 does not exist", 422)
- 생성되는 Kotlin 서비스 테스트(`testCode`)는 모의 `entityManager.find` 가 요청한 클래스의 모의 객체를 돌려주게 한다(lenient)
- 이미 있는 프로젝트에서 달라지는 것: (1) `beforeSave` 에서 손으로 하던 같은 확인은 생성된 확인이 먼저 하므로 그 코드·
  메시지는 나가지 않는다 - 지우거나, 참조를 객체로 두려면 `@objectref`; (2) 손댄 `XxxApiErrors` 는 이 거절을 422 로 답한다
  (전에는 외래 키 409); (3) 손댄 Kotlin 서비스 테스트는 다시 생성되지 않으므로 참조 id 를 넣는 저장 테스트가 실패한다 -
  `setUp` 에 `Mockito.lenient().\`when\`(entityManager.find(any<Class<Any>>(), any<Any>())).thenAnswer(Answer<Any> { c ->
  Mockito.mock(c.getArgument<Class<Any>>(0)) })` 를 넣는다. 저장할 때마다 참조마다 조회 한 번(바뀌지 않은 참조도)
- 3단계 애그리거트(루트 - 멤버 - 멤버의 멤버)도 컴파일되도록, 참조가 있다고 등록된 엔티티는 확인할 것이 없어도
  `requireReferences` 를 가진다 (리뷰에서 찾음) - 단 `@extend` 부모가 이미 가지면 물려받는다(아래 "리뷰 - 상속" 항목).
  참조하는 엔티티 계산은 생성하는 모델마다 한 번 하고 그 모델의 엔티티들이 나눠 쓴다 (id 참조로 필드가 바뀌면 다시).
  선택으로 담은 값 객체는 실행마다 한 번, 모든 도메인에 걸쳐 센다(아래)
- 관련 파일: `DddReferenceRules.java`(`requireReferences`, `-service`, `-test`), `DddDeriveRules.java`, `DddRepositoryPortRules.java`,
  `EntitySpec.java`(`knownReferencing`), `SpecFactory.java`(`registerReferencing`), `SignInAndReferencesTest.java`

### Bug Fixes (DTO - 최소 개수)

#### `@minsize(1)` 컬렉션을 아예 보내지 않으면 400 (전에는 엔티티 불변식의 422)
- `@Size` 는 null 을 통과시키므로 `lines` 를 뺀 주문이 DTO 검증을 지나 저장 시점 불변식에서 422 가 났다. 빈 배열은 400
  이었다(채점 에이전트가 찾음)
- 정리된 출력(v3)에서 `@minsize(n)` 의 n 이 1 이상이면 DTO 필드에 같은 메시지의 `@NotNull` 을 더한다. 2.x 호환 출력은 그대로
- TypeScript 클라이언트의 `validate<Name>()` 도 같은 메시지로 거절한다: `if (!partial && (value.lines == null ||
  value.lines.length < 1)) errors.lines = "an order needs a line";` (부분 수정 `partial` 에서는 묻지 않음). 이미 있는
  화면에서 달라지는 것: 품목을 넣기 전의 주문 초안을 `validatePurchaseOrder` 로 검사해 빈 결과로 저장 버튼을 켜던 화면은
  이제 `lines` 메시지를 받는다. 처음 커밋의 이 검사는 목록 필드를 검사 대상에서 빼 두어 생성되지 않았다(리뷰에서 찾음,
  아래 "리뷰 - TypeScript" 항목)
- 관련 파일: `DtoBaseRules.java`, `TypeScriptDtoFileRules.java`, `SignInAndReferencesTest.java`, `ShopFlowTest.java`,
  `elasticore-annotation-guide.md`

### Bug Fixes (선택으로 담은 값 객체의 컬럼)

#### 어느 엔티티가 값 객체를 선택으로 담으면 그 값 객체의 컬럼은 NULL 을 허용한다
- agent kit 의 상점 레시피를 처음 빌드해 보니, 배송지(`shipTo: Address @Embedded`, `!` 없음)를 보내지 않은 주문이
  `NULL not allowed for column "ZIPCODE"` - 400 `required` 로 저장되지 않았다. 값 객체의 `!` 필드가 그 값 객체를 담는
  모든 테이블의 컬럼을 NOT NULL 로 만들었기 때문이다. 펫스토어 샘플은 손으로 쓴 코드가 배송지를 늘 채워서 드러나지 않았다
- 정리된 출력(v3)에서, 어느 도메인의 어느 엔티티든 값 객체를 필수 표시(`!`·`@required`·`@nn`) 없이 담으면 그 값 객체의
  필드는 `nullable = false` 를 붙이지 않는다. 선택으로 담은 값 객체 안의 값 객체도 그렇다. 값이 오면 DTO 가 필수 필드를
  검사한다(400). 늘 필수로만 담는 값 객체는 그대로 NOT NULL. 2.x 호환 출력은 그대로 (다른 도메인·안쪽 값 객체·`@required`
  는 리뷰에서 고침 - 아래 "리뷰 - 값 객체" 항목)
- 관련 파일: `JpaEntityBaseRules.java`, `EntitySpec.java`(`knownOptionalEmbeddable`), `SpecFactory.java`
  (`registerOptionalEmbeddables`), `SignInAndReferencesTest.java`, `elasticore-annotation-guide.md`

### Improvements (읽지 못한 모델 파일은 빌드를 멈춘다)

#### `LOAD ERROR` 가 나면 `elcore3` 가 실패한다
- `-- 설명` 에 `: ` 가 들어가 YAML 이 깨지면 로더가 `* LOAD ERROR` 를 찍고 그 파일을 뺀 채 생성이 성공했다. 엔티티가
  통째로 없어진 것은 손으로 쓴 코드의 컴파일 오류("package ...dto does not exist")로만 드러났다 - 레시피를 쓰다 직접 겪었다
- 로더가 읽지 못한 파일을 `ModelCheck.failSaid` 로 기록하고, 규칙 엔진은 생성을 마친 뒤 `[elcore3] the model check
  failed (1) - fix these and build again:` 와 파일·파서 메시지를 찍고 1 로 끝난다. 2.x 생성기는 `ModelCheck` 를 보지
  않으므로 그대로다
- 주석이나 빈 줄만 있는 파일은 선언한 것이 없으므로 빈 파일처럼 건너뛰고, 멈춘 실행은 생성 파일을 지우지 않는다
  (리뷰에서 고침 - 아래 "리뷰 - 모델 파일" 항목)
- 관련 파일: `YamlFileMainModelLoader.java`, `ModelCheck.java`, `RulesGenRunner.java`, `LoadErrorFailsTest.java`,
  `elasticore-dev-guide.md`

### Bug Fixes (`@default(now)`)

#### `timestamp` 필드의 `@default(now)` 가 `Instant.now()` 를 쓴다, 그리고 "만들 때의 시각"을 문서에
- `@default(now)` 는 2.x 부터 `date`·`datetime`·`time` 에 `LocalDate.now()` 같은 초기값을 썼지만 가이드 어디에도 없었고,
  규칙 엔진의 `timestamp`(Instant) 에는 `now` 를 그대로 써서 컴파일되지 않았다. 엔티티에는 `java.time.Instant.now()` 를 쓴다
- DTO 에는 `date`·`datetime`·`time` 처럼 `timestamp` 의 **모든** 기본값을 붙이지 않는다 - `now` 만이 아니다.
  `listedAt: timestamp @default(java.time.Instant.EPOCH)` 의 DTO 는 전에 `@Builder.Default private Instant listedAt =
  java.time.Instant.EPOCH;` 였고 이제 `private Instant listedAt;` 이라, `builder()` 로 만든 DTO 의 값은 null 이다. 엔티티의
  초기값(`= java.time.Instant.EPOCH`)은 그대로다. v3 번들만 해당한다(`timestamp` 는 규칙 엔진의 타입이고 2.x 에는 없다)
- 세 에이전트 모두 "서버가 정하는 주문 시각"을 `beforeSave` 에 손으로 썼다. `orderedAt: datetime @readonly @default(now)`
  한 줄이면 된다는 것(만들 때 그 시각, 요청 값 무시, 수정해도 그대로)을 `elasticore-annotation-guide.md` 의 `@default` 에 적었다
- 확인: `SignInAndReferencesTest.aTimeSetWhenTheRowIsMade` - `joinedAt: timestamp @readonly @default(now)` 가 Java 엔티티의
  `joinedAt = java.time.Instant.now();`, Kotlin 엔티티의 `java.time.Instant.now()`, DTO 에는 초기값 없음;
  `listedAt` 은 엔티티에 `= java.time.Instant.EPOCH`, DTO 에는 없음. 이 커밋 전의 `DefaultValueLiteral.java` 로 같은 모델을
  생성해 DTO 가 `= java.time.Instant.EPOCH` 였음을 확인했다
- 관련 파일: `DefaultValueLiteral.java`(`forDto`, `of`), `SignInAndReferencesTest.java`(`aTimeSetWhenTheRowIsMade`),
  `elasticore-annotation-guide.md`

### Improvements (DSL 경고는 한 번)

#### 같은 DSL 경고가 한 실행에 한 번만 나온다
- "Ticket.agent is left out: AgentDTO is read under @access(read=owner) ..." 처럼 엔티티의 산출물(DTO, 검색 DTO)마다 도는
  규칙의 경고가 두 번씩 찍혀 문제가 둘인 것처럼 읽혔다. `SourceCode.dslWarnOnce` 가 실행마다(`resetWarnings`) 한 번만 말한다.
  `@signin` 의 경고도 같은 방식이다
- 관련 파일: `SourceCode.java`, `ServiceAccessRules.java`

### Bug Fixes (로그인 API - 리뷰)

#### `@signin` 은 본인과 관리자만 비밀번호를 바꿀 수 있을 때만, 로그인은 유일하게, `me()` 는 `@signin` 에만
- **누가 비밀번호를 바꿀 수 있는가.** `@access` 가 없는 `@signin` 엔티티(수정이 `public`)나 `update=user` 에서는 아무나
  `PUT /member {"memberId":1,"password":"x"}` 로 관리자의 비밀번호를 바꾸고 관리자로 로그인할 수 있었다 - 역할을
  `@access(admin)` 으로 막아도 관리자 행 자체를 가져가면 그만이었다. 이제 `update` 수준(없으면 `*`, 그것도 없으면 `admin`,
  `@access` 가 아예 없으면 `public`)이 `admin`·`none` 이거나, `owner` 이면서 `@owner` 가 키일 때만 로그인 API 와 `me()` 를
  만들고, 아니면 이유와 고칠 문장(`@owner(<키>) @access(read=owner, create=public, update=owner)` 등)을 DSL 경고로 낸다.
  그 엔티티의 서비스로 행을 쓰는 경로는 모두 이 수준을 묻는다(다른 엔티티 DTO 의 `@ref` 로 쓰는 길은 아래 "리뷰 2" 가
  막았다): `PUT`·`PUT /batch` 는 행마다 소유자를 확인하고, 조건 수정은 `owner` 수준이면
  관리자만(`AccessPolicy.requireAll`). 펫스토어 `Customer`, Kotlin 샘플 `Agent`, 가이드 예제는 이미 `update=owner` +
  `@owner(키)` 라 그대로 생성된다
- **로그인은 유일.** `@signin` 엔티티의 `@login` 컬럼에 `unique = true` 를 붙인다(이미 `@unique` 이거나, 키이거나, 그 컬럼
  하나만의 unique `@Index` 면 그대로). 같은 로그인이 두 행이면 `authenticate` 가 둘 다 거절하므로, 누구든 남의 로그인으로
  가입해 그 계정(관리자 포함)을 못 들어오게 할 수 있었다. 이제 두 번째 행은 다른 제약 위반처럼 409. `@signin` 없는
  `@login`·`@password` 엔티티의 로그인이 유일하지 않으면 DSL 경고. Kotlin 샘플의 `Agent.login` 컬럼이 unique 가 되었다.
  이미 운영 중인 DB 는 마이그레이션으로 unique 제약을 더한다(중복이 있으면 먼저 정리)
- **`me()` 는 `@signin` 으로 로그인하는 엔티티에만.** 앞 커밋은 `@login`·`@password` 가 있으면 모두 CoreService·유스케이스에
  `me()` 를 만들어, 손으로 `me()` 를 쓴 프로젝트가 컴파일되지 않을 수 있었다(이 섹션의 로그인 항목이 말한 "`@login`·
  `@password` 만으로는 전처럼 `authenticate` 만" 과도 달랐다). 이제 그 문장 그대로다
- **경고는 엔티티마다.** `@signin` 이 막힌 이유를 컨트롤러 규칙이 아니라 모든 엔티티에 도는 규칙(`access-signin-check`)이
  말한다 - 컨트롤러가 없는 엔티티의 "`@expose` 가 없음" 경고가 전에는 나오지 않았다. 역할 경고의 끝은 "The generated
  sign-in puts no role in the session" 으로 바꿨다(손댄 컨트롤러는 다음 줄)
- **손댄 컨트롤러에 남은 로그인.** DSL 이 더는 만들지 않는데 손댄 컨트롤러(`SessionCurrentUser` 를 쓰는 파일)가 아직
  `POST /signin`·`GET /me` 를 가지고 있거나, 세션에 역할을 넣으면(`SessionCurrentUser.signIn` 의 세 번째 인자) DSL 경고 -
  그 로그인은 생성된 검사가 믿는 세션을 계속 만들기 때문이다(`@signin` 을 옮기거나 역할 필드의 `@access(admin)` 을 지운 뒤)
- **애플리케이션마다 하나.** 한 번의 생성이 멀티 모듈(`elasticore.modules`)로 여러 애플리케이션을 만들 수 있으므로
  `@signin` 을 애플리케이션마다 센다: 단일 모듈 프로젝트는 모든 도메인을 함께, 멀티 모듈은 컨트롤러가 생기는 모듈(`control`
  의 `모듈명://` 이 가리키는 모듈, 없으면 그 blueprint 가 있는 모듈)마다. 두 모듈이 한 애플리케이션으로 함께 실행되는지는
  생성기가 알 수 없어 가이드에 "한쪽에만" 이라고 적었다. 세는 일은 실행마다 한 번, 엔티티 줄의 `@signin` 만 본다 - 전에는
  도메인마다 모든 모델의 참조 계산을 다시 해서 셌다
- **같은 세션 키.** 로그인 API 가 생기는 `@signin` 엔티티의 애플리케이션에 다른 `@login`·`@password` 엔티티가 있으면 DSL
  경고 - 그 엔티티를 손으로 `SessionCurrentUser.signIn` 하면 그 키가 `@signin` 엔티티의 키로 읽혀(직원 7 = 고객 7) `me()` 와
  소유자 검사를 통과한다
- **문자열 키.** 요청이 정하는 문자열 키(`@genid`·`@sequence` 가 아님)의 `@signin` 엔티티는 DSL 경고 - 키가 `me` 인 행은
  `GET /{id}` 가 아니라 `GET /me` 가 답한다. 가이드의 "`/me` 라는 키" 문구도 고쳤다
- 이 경고들은 모두 `SourceCode.dslWarnOnce` 로 실행마다 한 번 나온다(앞 항목). 2.x 생성기와 `jpa-springboot` 번들은
  `@signin` 을 처리하지 않으므로 그대로다
- 확인: `SignInAndReferencesTest`(수정 수준 셋과 `@expose` 없음의 경고와 생성 안 함, `@signin` 없는 엔티티에 `me()` 없음,
  로그인 unique 와 경고, 같은 세션 키 경고, 멀티 모듈의 두 애플리케이션이 각자 로그인, 문자열 키 경고, 손댄 컨트롤러의 역할·
  엔드포인트 경고), `elasticore-template-kt` 의 `ShopApiContractTest.aTakenLoginIsAConflict`(같은 로그인으로 두 번째 가입
  409, 첫 행은 그대로 로그인됨, 로그인한 상담원이 자기 로그인을 남의 것으로 바꾸면 409), 펫스토어 `ShopFlowTest`, 2.x 호환
  검사(`elcoreRulesCheck -Pstrict` 150 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra)
- 관련 파일: `ServiceAccessRules.java`(`signsIn`, `whyNoSignIn`, `updateLevel`, `credentialsGuarded`, `signInCheck`,
  `uniqueLogin`, `me`), `HexUseCaseRules.java`, `RulesGenRunner.java`(`countSignIns`, `sayApplication`, `warnIfSignInLeft`),
  `SpecFactory.java`(`signInEntities`, `otherLoginEntities`), `EntitySpec.java`(`knownOtherLoginEntities`),
  `SignInAndReferencesTest.java`, `ShopApiContractTest.kt`,
  `elasticore-annotation-guide.md`, `elasticore-rule-guide.md`, `elasticore-dsl-guide.md`

### Bug Fixes (리뷰 - 상속한 참조 확인, 다른 도메인의 값 객체, 모델 파일, TypeScript 최소 개수, 엔드포인트 경고)

#### 리뷰 - 상속: `@extend` 하위 클래스가 부모의 `requireReferences` 를 지우지 않는다
- 위 "확인할 것이 없어도 `requireReferences` 를 가진다" 뒤로, 참조를 확인하는 엔티티를 `@extend` 한 하위 클래스(가이드의
  `Album: meta: entity @extend(Item) @rollup`)가 빈 `requireReferences` 를 가졌다. 모델은 물려받은 필드까지 보고 하위
  클래스를 "참조한다"로 세지만, 본문은 자기 필드만 읽기 때문이다. Java 는 부모의 확인을 빈 메서드로 말없이 덮어썼고(하위
  클래스 객체에서 부르면 아무것도 확인하지 않음), Kotlin 은 `'requireReferences' hides member of supertype 'Item' and needs
  'override' modifier` 로 컴파일되지 않았다
- 이제 자기 확인이 없고 `@extend` 부모가 확인하면 메서드를 쓰지 않고 물려받는다. 자기 확인이 있으면 부모의 것을 먼저 부른다:
  Java 는 `@Override` 와 `super.requireReferences(lookup);`, Kotlin 은 `override fun` 과 `super.requireReferences(lookup)`
  (all-open 플러그인이 부모의 메서드를 연다). 부모가 다른 도메인에 있으면 그 도메인의 모델로 판단한다
- 확인: `SignInAndReferencesTest.aSubclassKeepsItsParentsReferenceCheck` - Java·Kotlin 에서 자기 참조가 없는 `SeAlbum`
  (`@rollup`)과 `SeDiscBase`(나뉜 루트)에는 메서드가 없고, 자기 참조가 있는 `SeBook` 은 override 하고 super 를 먼저 부른다.
  `aSubclassInAnotherDomainKeepsItsParentsReferenceCheck` - 부모(`rpa` 의 `RpItem`)와 다른 도메인(`rpb`)의 하위 클래스
  `RcAlbumBase` 에도 메서드가 없다(다른 도메인의 사실을 보지 않으면 빈 메서드가 생기는 것도 확인했다). 같은 모양의
  모델을 Kotlin 샘플(`elasticore-template-kt` 의 shop)에 잠시 넣어 `compileKotlin` 으로 컴파일했다 - 고치기 전에는 세
  하위 클래스에서 위 오류, 고친 뒤에는 없음. 확인한 뒤 모델은 뺐다
- 관련 파일: `DddReferenceRules.java`(`requireReferences`, `inheritsReferences`), `SpecFactory.java`(`registerParentReferencing`),
  `RulesGenRunner.java`, `SignInAndReferencesTest.java`

#### 리뷰 - 값 객체: 다른 도메인에서, 안쪽에서, `@required` 로 담을 때
- 선택으로 담은 값 객체를 그 값 객체를 선언한 도메인의 엔티티에서만 찾았다. `common` 의 `Address`(`zipcode: string(10)!`)를
  `shop` 의 주문이 `shipTo: Address @Embedded` 로 담아도 `Address` 의 컬럼은 `nullable = false` 그대로였다 - 위 항목이
  고친 `NULL not allowed for column "ZIPCODE"` 거절이 다른 도메인에서는 남았다. 이제 실행의 모든 도메인을 한 번 본다
- 선택으로 담은 값 객체 안에 `!` 로 담긴 값 객체(`contact: Contact @Embedded` 의 `place: Place! @Embedded`)도 선택이다 -
  바깥 값이 없으면 안쪽 컬럼도 빈다
- 필수 표시로 `!` 만 셌다. `card: Card @Embedded @required` 로만 담는 값 객체도 선택으로 읽혀 NOT NULL 이 빠졌다. 이제
  `FieldSpec.required()` 처럼 `!`·`@required`·`@nn` 을 센다
- 확인: `SignInAndReferencesTest.aValueOfAnotherDomainEmbeddedAsOptionalHasNullableColumns` - 두 도메인(`scommon` 이 값 객체를
  선언, `sdshop` 이 담음)에서 `ScAddress`·`ScContact`·`ScPlace` 는 `nullable = false` 가 없고, `@required` 로만 담는
  `ScCard` 는 있다
- 관련 파일: `SpecFactory.java`(`optionalEmbeddables(List)`, `knownOptionalEmbeddables`), `RulesGenRunner.java`,
  `SignInAndReferencesTest.java`, `elasticore-annotation-guide.md`

#### 리뷰 - 모델 파일: 주석만 있는 파일은 빌드를 멈추지 않고, 멈춘 실행은 생성 파일을 지우지 않는다
- 위 "`LOAD ERROR` 가 나면 실패" 뒤로, 모델 파일을 통째로 주석 처리해 꺼 두거나 빈 줄만 남은 `.yml` 도
  `No content to map due to end-of-input` 으로 빌드를 멈췄다. 0 바이트 파일만 건너뛰었기 때문이다. 이제 YAML 토큰이 하나도
  없는 파일은 `* EMPTY:` 로 건너뛴다. 최상위가 목록인 파일처럼 읽을 수 없는 파일은 그대로 빌드를 멈춘다
- 읽지 못한 파일 때문에 멈추는 실행도 끝에서 "DSL 이 더 만들지 않는 파일"을 지웠다. 그 파일이 선언한 엔티티의 DTO·매퍼·
  리포지토리가 `REMOVED` 되어, YAML 오타 하나에 생성 파일 삭제가 따라왔다. 이제 모델 검사가 실패한 실행은 지우지 않고
  `[elcore3] generated files the DSL no longer produces are kept until the model check passes` 를 찍는다
- 2.x 생성기(`elcore`)도 같은 로더를 쓰므로 주석만 있는 파일에 `LOAD ERROR` 와 스택 대신 `* EMPTY:` 를 찍는다. 생성 결과는
  그대로다
- 확인: `LoadErrorFailsTest.aFileOfOnlyCommentsOrBlankLinesDeclaresNothing`(주석만 있는 파일, 빈 줄만 있는 파일 - 0 으로 끝남),
  `aStoppedRunKeepsTheFilesOfWhatItCouldNotRead`(생성한 뒤 그 파일을 깨면 1 로 끝나고 `LdOrderDTO.java` 가 남으며
  `REMOVED(` 가 없음)
- 관련 파일: `YamlFileMainModelLoader.java`(`holdsNothing`), `RulesGenRunner.java`, `LoadErrorFailsTest.java`,
  `elasticore-dev-guide.md`

#### 리뷰 - TypeScript: `@minsize` 목록 검사가 실제로 생성된다
- 앞 커밋이 `validate<Name>()` 에 넣은 목록 `@minsize` 검사는 생성되지 않았다. 검사할 필드 목록이 `...Fields` 상수와 같아서
  목록 필드를 빼 두었기 때문이다(고치기 전 코드로 생성한 `validateSrOrder` 에 `lines` 검사가 없음을 확인)
- 이제 `@minsize(n)`(n 이 1 이상)인 목록은 `validate` 가 검사하고 상수에는 여전히 넣지 않는다(위 "DTO - 최소 개수" 항목)
- 확인: `SignInAndReferencesTest.theBrowserClientRefusesAnOrderWithoutLines` - `SrOrderDTO.ts` 에
  `if (!partial && (value.lines == null || value.lines.length < 1)) errors.lines = "an order needs a line";`, `SrOrderFields` 에
  `lines` 없음
- 관련 파일: `TypeScriptDtoFileRules.java`(`meta`, `minSize`), `SignInAndReferencesTest.java`, `elasticore-annotation-guide.md`

#### 리뷰 - 엔드포인트 경고: 같은 엔드포인트를 다르게 쓴 컨트롤러, 그리고 2.x 호환 모드
- 손댄 컨트롤러가 DSL 이 주는 엔드포인트를 갖고 있는지 경로 문자열 그대로 비교해서, 경로 변수 이름만 다른
  `@GetMapping("/{id}")`, Kotlin 의 `@GetMapping(value = ["/me"])`, `@RequestMapping(value = "/me", method = RequestMethod.GET)`
  으로 쓴 컨트롤러도 `... does not serve GET /{customerId} ...` 경고를 받았다
- 이제 매핑을 읽어서 비교한다. `{...}` 는 이름과 상관없이 같고 끝의 `/` 는 무시한다. `value`·`path`·위치 인자, `"..."`·
  `{...}`·`[...]`, 메서드 수준의 `@RequestMapping`(method 가 없으면 모든 메서드)을 읽고, 클래스의 `@RequestMapping` 과 주석은
  읽지 않는다. 상수나 이어 붙인 문자열처럼 읽지 못한 매핑이 있으면 그 HTTP 메서드의 엔드포인트는 경고하지 않는다(틀린 경고보다
  말하지 않는 쪽). 인자 없는 `@PostMapping`·`@PutMapping`(생성·수정)도 이제 비교하므로, 그것을 지운 컨트롤러는
  `POST (base path)` 로 경고를 받는다. 문구의 "which the DSL now gives it" 은 "which the DSL gives it" 이 되었다
- 2.x 호환 모드(`jpa-springboot`)에서는 이 경고를 하지 않는다. 그 모드는 한 번 생성한 파일을 다시 만들지 않고 TypeScript
  클라이언트도 없는데, 엔드포인트를 지운 2.x 컨트롤러에 "DSL 이 준다"·"TypeScript 클라이언트가 부른다" 는 경고가 실행마다 났다
- 확인: `SignInAndReferencesTest.endpointsServedAnotherWayAreNotMissing` - 경로 변수를 `{id}` 로 바꾸고 삭제를
  `@RequestMapping(value = "/{id}", method = RequestMethod.DELETE)` 로 쓴 컨트롤러에 경고가 없다(고치기 전에는
  `does not serve GET /{userId}, DELETE /{userId}`). Java·Kotlin 형식, 주석, 상수 경로를 읽는 단위 검사도 있다.
  `theCompatibleModeSaysNothingOfTheDevelopersController` - 호환 모드에서 `@DeleteMapping` 을 지운 컨트롤러에 경고가 없다
  (고치기 전에는 `does not serve DELETE /{userId}`)
- 관련 파일: `RulesGenRunner.java`(`warnIfEndpointsMissing`, `missingEndpoints`, `Endpoints`, `warnIfSignInLeft`),
  `SignInAndReferencesTest.java`, `elasticore-annotation-guide.md`

#### 리뷰 - 문서: 3.0.261022-rc1 전의 응답
- 규칙 가이드가 "전에는 생명주기 거절도 없는 참조도 422" 라고 했다. 없는 행을 id 로 참조한 저장은 외래 키 위반의 409
  "Conflicts with stored data" 였고 `@snapshot` 원본이 없을 때만 422 였다(위 `requireReferences` 항목). 그렇게 고쳤다
- 관련 파일: `elasticore-rule-guide.md`

### Bug Fixes (리뷰 2 - 로그인 API, 참조 확인의 도메인 순서, 상속 엔티티)

#### 리뷰 2 - 다른 엔티티의 `@ref` 로 로그인·비밀번호를 쓰지 못한다
- 레이어드 v3(`jpa-springboot-v3`, `jpa-springboot-kt`)에서 다른 엔티티의 DTO 가 `@ref(assignee.login)`·
  `@ref(assignee.password)` 로 `@signin` 엔티티를 읽으면, 매퍼가 수정 때 `to.getAssignee().setLogin(...)`·
  `setPassword(...)` 로 담당자의 행에 썼다. 티켓의 수정 수준(`update=user`)만 묻고 담당자 엔티티의 검사는 건너뛰므로, 로그인한
  아무나 관리자에게 배정된 티켓으로 관리자의 로그인을 바꾸거나 직접 계산한 PBKDF2 문자열을 넣고 관리자로 로그인할 수 있었다.
  조회에서는 티켓을 읽는 사람 모두에게 담당자의 비밀번호 해시가 나갔다. DDD·헥사고날 번들은 다른 애그리거트로 가는 `@ref` 를
  원래 읽기 전용으로 두어 그대로다
- 이제 `jpa-service-access`(2.x 번들에는 없음)의 매퍼 규칙(`access-mapper-ref`)이, 누가 쓸 수 있는지 정한 엔티티(`@access`·
  `@owner`·`@password`·필드의 `@access(admin)`)로 가는 `@ref` 를 읽기 전용으로 둔다 - 관계의 키를 가리키는 `@ref` 는 전처럼
  관계를 바꾼다. `@password` 를 읽는 `@ref` 는 DTO·매퍼·TypeScript 에서 빠지고(`access-dto-ref-password`) DSL 경고가 난다.
  그런 `@ref` 를 가진 v3 프로젝트의 매퍼에서 그 쓰기 줄이 사라진다(샘플에는 없음)
- 확인: `SignInAndReferencesTest.aRefIntoTheSigningInEntityDoesNotWriteItsCredentials` - Java·Kotlin 레이어드 v3 에서 매퍼에
  담당자의 로그인·비밀번호 쓰기와 비밀번호 읽기가 없고, 로그인 표시(`assigneeLogin`)와 아무도 지키지 않는 엔티티로 가는
  `@ref(tag.label)` 쓰기는 남고, DTO·TypeScript DTO 에 `assigneePassword` 가 없다(고치기 전 코드에서는 실패)
- 관련 파일: `ServiceAccessRules.java`(`refsIntoGuardedRowsAreReadOnly`, `noPasswordThroughRef`, `refEntity`, `guarded(Entity)`),
  `SourceCode.java`(`refHidden`), `MapperBaseRules.java`, `elasticore-annotation-guide.md`

#### 리뷰 2 - 참조 확인이 도메인을 생성하는 순서에 따르지 않는다
- 위 "상속: 부모의 `requireReferences`" 는 다른 도메인의 부모를 그 도메인의 모델로 판단하는데, id 참조로 바꾸는 일
  (`IdReferences`)을 도메인을 생성할 때마다 했다. 하위 클래스의 도메인이 먼저 생성되거나(`caa` 가 `czz` 보다 앞) `-Pdomain` 으로
  하위 클래스의 도메인만 생성하면 부모가 아직 참조하지 않는 것으로 읽혀, Kotlin 은 `'requireReferences' hides member of
  supertype` 으로 컴파일되지 않고 Java 는 부모의 확인을 덮어썼다
- 이제 어떤 도메인도 생성하기 전에 실행의 모든 모델을 각 도메인의 규칙 선택대로 한 번 바꾼다(`shapeReferences`, 컨텍스트 맵
  검사 뒤). 도메인 필터가 있어도 같다
- 확인: `SignInAndReferencesTest.aSubclassKeepsItsParentsCheckWhicheverDomainComesFirst` - 자기 참조가 있는 하위 클래스의 도메인이
  먼저인 모델에서 Java `CaBookBase` 는 `@Override` 와 `super.requireReferences(lookup);`, Kotlin 은 `override fun` 과 `super`,
  `-Delasticore.domain=caa` 로 하위 도메인만 생성해도 같다(고치기 전 코드에서는 실패)
- 관련 파일: `RulesGenRunner.java`(`shapeReferences`), `SignInAndReferencesTest.java`

#### 리뷰 2 - 로그인 API 의 남은 조건과 문서
- **요청이 정하는 키의 삭제.** 키가 생성되지 않는 `@signin` 엔티티(`handle: string(20) @id`)를 아무나 지울 수 있으면, 누가
  `alice` 를 지우고 `alice` 로 다시 가입해 `alice` 가 주인이던 모든 행을 가졌다. 이제 `delete` 수준(없으면 `*`, 그것도 없으면
  `admin`)이 `admin`·`none` 이거나 키를 가리키는 `@owner` 의 `owner` 일 때만 로그인 API 를 만들고, 아니면 고칠 문장(`delete=owner`
  와 `@owner(<키>)`, `delete=admin`, 또는 키를 생성되게)을 DSL 경고로 낸다. 키를 생성하게 하라고만 요구하지 않은 것은, 이름 키
  (`handle`)도 정상인 모델이고 `delete` 를 적지 않으면 `admin` 이라 흔한 선언은 그대로 통과하기 때문이다
- **`*` 는 다른 항목과 함께 써도 센다.** 로더가 `@access(read=public, create=public, *=owner)` 의 `*=owner` 를 키로 읽지 못해
  (`AbstractModelLoader.parseParameters` 의 키 패턴 `\w+`) 버렸다 - 수정이 `admin` 으로 읽혀 본인의 비밀번호 변경이 막혔고,
  `*=user` 는 남이 바꿀 수 있는데도 로그인 API 가 생겼다. 이제 키는 단어 또는 `*` 하나다. 2.x 에는 영향이 없다: 저장소의 어느
  blueprint 도 어노테이션 인자에 `*=` 를 쓰지 않고, `*=x` 하나만 쓴 경우는 전에도 같은 글자(`*=x`)로 읽혔으며, 2.x 호환 검사
  (`elcoreRulesCheck -Pstrict` 150 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra)가 그대로다
- **로그인 unique 는 `@signin` 만으로.** 코드가 하던 대로 문서를 맞췄다: 로그인 API 가 거절되어도 `@signin` 엔티티의 로그인
  컬럼은 unique - `@access` 를 고칠 때 테이블이 바뀌지 않는 쪽이 낫다
- **여러 모듈이 각자 `@signin`.** 멀티 모듈 실행에서 둘 이상의 모듈이 `@signin` 엔티티를 가지면 `@signin is counted per module:
  ... if these modules run as one application, only one of them may say @signin.` 을 한 번 낸다
- 확인: `SignInAndReferencesTest.whoMayChangeThePasswordDecidesTheSignIn` - 모듈마다 하나씩, `update=admin`·`update=none`·`*=owner`
  하나·`*=owner` 와 다른 항목·삭제가 본인뿐인 문자열 키는 로그인 API 가 생기고, `*=user` 하나·`*=user` 와 다른 항목·`delete=user`
  인 문자열 키는 생기지 않으며 그 경고와 모듈 경고가 나온다(고치기 전 코드에서는 실패).
  `theBrowserClientOfARefusedEntityDoesNotSignIn` - 거절된 `@signin` 엔티티의 TypeScript 에 `signIn`·`me` 가 없다
- 관련 파일: `ServiceAccessRules.java`(`keyReusable`, `level`, `whyNoSignIn`), `AbstractModelLoader.java`,
  `RulesGenRunner.java`(`countSignIns`), `SignInAndReferencesTest.java`, `elasticore-annotation-guide.md`,
  `elasticore-rule-guide.md`, `elasticore-dsl-guide.md`

#### 리뷰 2 - `@extend` 하위 엔티티: 없는 리포지토리, 자기 테이블
- v3·Kotlin 실행은 서비스(`@expose`·`@service`)와 자기 키가 있는 엔티티에만 리포지토리를 쓰는데, 도메인의
  `RepositoryHelper` 는 물려받은 키가 있는 `@extend` 하위 엔티티와 서비스가 없는 엔티티의 리포지토리도 주입했다
  (`SeAlbumRepository` - 없는 클래스라 컴파일되지 않음). 이제 v3·Kotlin 의 헬퍼는 실행이 쓰는 리포지토리만 주입한다. 2.x 호환
  출력은 그대로다
- `@extend(X) @rollup` 하위 엔티티가 v3 에서 자기 `@Table(name = "se_book")` 을 가져, 가이드("부모 테이블로 컬럼 통합")와
  2.x 출력과 달리 자기 테이블에 매핑되었다. 이제 v3 에서도 `@Table` 없이 `@DiscriminatorValue("SeBook")`(`@rollup(discriminator=...)`
  이 있으면 그 값)를 붙여 부모의 테이블(JPA 기본 단일 테이블)에 들어간다
- 확인: `SignInAndReferencesTest.theRepositoryHelperNamesOnlyRepositoriesThatExist`, `aRolledUpSubclassHasNoTableOfItsOwn`
  (Java·Kotlin 헥사고날, 고치기 전 코드에서는 실패). 샘플에는 `@rollup` 이 없어 실행으로는 확인하지 않았다
- 관련 파일: `RepositoryHelperBaseRules.java`, `JpaEntityAnnotationRules.java`, `V3SkeletonCoverageTest.java`,
  `SignInAndReferencesTest.java`

#### 리뷰 2 - 문서
- 위 "로그인 API - 리뷰" 의 "행을 쓰는 경로는 모두 이 수준을 묻는다" 를 그 엔티티의 서비스로 쓰는 경로로 좁혔고, 세 템플릿의
  이 문서 사본을 다시 복사해 원본과 같게 했다(`cmp`)
- 관련 파일: `elasticore-history.md`

### Improvements (3.0 전에 - 검색, 쓰기 경로, Ossie, TypeScript 클라이언트)

#### v3 의 `@search(like)` 는 대소문자를 무시한다
- "이름 일부로 검색" 은 모두가 `@search(like)` 로 쓰는데, 생성된 `c.like(r.get("name"), "%" + trim(name) + "%")` 는 H2·
  PostgreSQL·Oracle 에서 대소문자를 구분해 `{"name":"gold"}` 가 Goldfish 를 찾지 못했다(펫스토어 실험의 세 팀 중 둘이 그대로
  냈다). 대소문자를 무시하는 `%__%` 는 Kotlin 에 `x!!.toLowerCase()` 를 써서 Kotlin 2.1 부터 컴파일 오류였다(Boot 4 는 Kotlin
  2.2). 3.0 뒤에 바꾸면 모든 v3 프로젝트의 동작이 바뀌므로 지금 바꾼다
- v3 모드(헥사고날·DDD, `-Pv3`, 모든 Kotlin 빌드)에서 문자열 필드의 `like` 는 `%__%` 와 같다: `c.like(c.lower(...), "%" +
  trim(v).toLowerCase(java.util.Locale.ROOT) + "%")`, Kotlin 은 `trim(v)!!.lowercase()`. 검색 DTO 의 `@Schema` 도 대소문자
  무시 문구다. `%_%`·`%%` 는 모든 모드에서 대소문자를 구분하고, 기호들의 뜻은 그대로다. 소문자 변환은 `Syntax.lowerCase` 를
  지나 언어마다 맞게 쓰이고(로캘 무관), 대소문자 무시 조건 `%__%`·`%__`·`__%` 는 `like` 처럼 값의 앞뒤 공백을 지운다
- 모르는 조건(`@search(ilike)`, `gt` 등)은 전처럼 정확 일치지만 v3 모드에서는 DSL 경고를 낸다. 가이드가 조건 없는
  `@search` 를 정확 일치(`eq`)라 했던 것은 `auto` 로, 구분자 `$` 는 실제 코드대로 `,` 로 고쳤다
- 2.x 호환 출력(레이어드 Java)은 그대로다 - 같은 `@search(like)` 가 그곳에서는 구분한다(마이그레이션 가이드에 적음).
  펫스토어·`elasticore-template-ddd`·`elasticore-template-kt` 의 `like` 검색 매퍼 줄이 바뀐다(생성 파일, 커밋 안 됨)
- 확인: `MapperSpecTest.likeIgnoresCase_v3`·`ignoreCase_kotlin`·`anUnknownConditionIsSaid`, `SearchDtoTest.
  likeCondition_cleanOutputIgnoresCase`(고치기 전 코드에서는 실패), 2.x 문구는 `likeVariants` 가 그대로 지킨다. 펫스토어
  `ShopFlowTest.aVisitorBrowsesTheCatalog` 에 `{"name":"gold"}` -> Golden Retriever, Goldfish, `{"name":"  BULL "}` -> Bulldog 추가.
  같은 모양(`like`, `%__%`, `__=`, `__%`, `%__`)을 샘플 두 개에 잠시 넣어 Java(헥사고날)·Kotlin(헥사고날, Kotlin 1.9.24)으로
  컴파일하고 Java 쪽은 H2 에서 `"  gold "` 로 찾았다 - 확인 뒤 뺐다. Kotlin 2.2 컴파일은 하지 않았다(`lowercase()` 는 1.5 부터 있음)
- 관련 파일: `MapperSpecRules.java`(`lowered`, `knownCondition`), `SearchDtoBaseRules.java`(`effectiveCondition`),
  `Syntax.java`(`lowerCase`), `elasticore-annotation-guide.md`, `elasticore-dev-guide.md`, `elasticore-migration-guide.md`

#### `PUT /batch` 는 행마다 `update(dto)` 를 부른다
- v3 `batchUpdate`(Java·Kotlin 서비스 템플릿)가 `update()` 의 본문을 복사해 돌아서, 확장 서비스에서 `update()` 를 override
  한 규칙("쓴 사람만 고친다", "리뷰의 상품은 바뀌지 않는다")을 `PUT /review/batch` 가 건너뛰었다(실험에서 관리자가 남의 리뷰를
  고치고 리뷰를 다른 상품으로 옮김). 이제 한 트랜잭션 안에서 `for (dto : dtoList) update(dto);` - override, `readBack`, 409 변환이
  일괄 수정에도 적용된다. `update()` 를 override 한 기존 v3 프로젝트는 다시 생성하면 그 규칙이 일괄 수정에도 걸린다(그것이 고침)
- `@restExclude(update)` 는 이제 `batchUpdate` 도 함께 뺀다(서비스·컨트롤러·유스케이스·TypeScript) - 남으면 없는 `update` 를
  부르기 때문이다. 같은 이유로 `deleteById` 는 `delete`(목록)와 `POST /delete` 를, `delete` 는 `POST /delete` 를, `save` 는
  `POST` 를 데려간다(`ControllerBaseRules.restExcluded`). 2.x 호환 컨트롤러는 적힌 대로만 뺀다
- 확인: `WritePathsTest.aBatchUpdateGoesThroughUpdate`, `excludingUpdateTakesTheBatchWithIt`(Java 헥사고날·레이어드 v3, Kotlin;
  고치기 전 코드에서는 실패). 임시 도메인으로 `elasticore-template-ddd` 에서 실행: `update()` 를 override 한 서비스의
  `batchUpdate` 가 그 override 에 거절된다
- 관련 파일: `v3/service.tmpl`, `kt/service.tmpl`, `JpaServiceFileRules.java`, `ControllerBaseRules.java`,
  `TypeScriptApiRules.java`, `elasticore-rule-guide.md`

#### `@restExclude` 가 헥사고날 빌드를 깨지 않는다
- 서비스에서 뺀 메서드를 유스케이스 인터페이스가 그대로 약속해 `@restExclude(batchUpdate)` 가 `XxxService is not abstract
  and does not override abstract method batchUpdate(...) in XxxUseCase` 로 컴파일되지 않았다(`save`·`delete`·`deleteById` 도
  같음). 이제 유스케이스도 같은 목록으로 뺀다. Kotlin 서비스는 원래 메서드를 빼지 않으므로(아래 3.1 목록) 유스케이스·
  컨트롤러만 따르고 컴파일된다
- 확인: `WritePathsTest.theUseCaseDropsWhatTheServiceDropped`(Java·Kotlin), 임시 도메인을 `elasticore-template-ddd`(Java)·
  `elasticore-template-kt`(Kotlin)에 넣어 `@restExclude(update)`·`@restExclude(batchUpdate, deleteById)` 를 컴파일
- 관련 파일: `HexUseCaseRules.java`

#### `@readonly` 소유자 필드가 소유자를 잃지 않는다
- `@owner(customerId)` 의 `customerId` 가 `@readonly` 면, 생성 시 서비스가 DTO 에 넣은 소유자를 매퍼가 쓰지 않아(서버가 정하는
  필드) 행이 소유자 없이 저장되었고, 주인은 자기 행을 수정하려다 404 를 받았다. 이제 그 경우 서비스가 생성 시 엔티티에 직접
  넣는다(`claimOwner(entity)`, 관리자 포함 로그인한 누구든 - 요청이 그 값을 보낼 수 없으므로. `runAsSystem` 안에서는 손대지
  않음. 나뉜 루트는 `creation()` 으로). 수정에서는 매퍼가 쓰지 않으므로 누구의 수정이든 저장된 값이 남는다
- 확인: `WritePathsTest.aReadonlyOwnerIsClaimedOnCreateAndKept`(고치기 전 코드에서는 실패). 임시 도메인을 H2 에서 실행: 회원이
  만든 리뷰의 `writerId` 가 그 회원, 수정 뒤에도 그대로, 관리자가 만들면 관리자, 관리자의 수정 뒤에도 회원의 것,
  `@behavior` 로 나뉜 루트도 `creation()` 으로 들어간다
- 관련 파일: `ServiceAccessRules.java`, `elasticore-annotation-guide.md`

#### 참조의 `@updatable(false)` 가 동작한다
- DDD·헥사고날에서 다른 루트로 가는 참조는 식별자 필드(`itemId`)로 쓰이는데, `item: Item @updatable(false)` 의 표시가 그
  필드로 가지 않아 수정 요청이 리뷰를 다른 상품으로 옮겼다. 이제 식별자 필드가 물려받아 `@Column(name = "item_item_id",
  updatable = false)` 이고, 매퍼는 행에 값이 없을 때(생성)만 쓴다(`&& to.getItemId() == null`)
- 확인: `WritePathsTest.aReferenceThatMayNotChangeKeepsItsRow`(Java·Kotlin, 고치기 전 코드에서는 실패), 임시 도메인을 H2 에서
  실행: 다른 `itemId` 로 수정한 리뷰가 처음 상품에 남는다
- 관련 파일: `IdReferences.java`, `MapperBaseRules.java`, `elasticore-annotation-guide.md`

#### Ossie: `count(엔티티)` 가 어느 dataset 을 세는지 말한다
- `count(Customer)` 가 dataset 을 이름하지 않는 `COUNT(*)` 로 나가, BI 도구가 어느 테이블을 세는지 알 수 없었다. 이제 키
  컬럼을 센다: `COUNT(Customer.customer_id)`(키는 비지 않으므로 행 수와 같다). 조건이 있는 `count(...) where ...` 는 조건이
  이미 dataset 의 컬럼을 이름하므로 그대로다
- 확인: `OssieMetricsTest.countsRowsOrValuesAndDividesSafely`(고치기 전 코드에서는 실패). 샘플의 지표는 모두 조건이 있어
  내보낸 `semantic_model.yml` 은 바뀌지 않았다
- 관련 파일: `OssieMetrics.java`(`Model.keyColumn`), `OssieSemanticModelExporter.java`, `elasticore-dsl-guide.md`

#### TypeScript 클라이언트를 빌드와 함께 (`typescriptClient`)
- `src/main/ts` 는 `elcore3ts` 만 쓰고 빌드는 그 태스크를 부르지 않아, 모델을 바꾼 뒤 클라이언트에 새 필드·엔티티가 없었다
  (실험의 2단계 세 팀 모두). 플러그인의 `elasticore { typescriptClient = true }`(기본 false)는 `elcore3` 뒤, 컴파일 전에
  `elcore3ts` 를 실행한다. 설정이 없는데 생성된 `.ts`(첫 줄 `//ecd:`)가 있으면 `elcore3` 가 끝에 태스크와 설정 이름을 말하는
  경고를 한 번 낸다
- 확인: `ElastiCorePluginTest.theTypescriptClientRunsWithTheBuildWhenAsked`(설정 없이 `compileJava` 가 `elcore3ts` 를 부르지
  않음, 설정하면 `elcore3` -> `elcore3ts` -> `compileJava` 순서, `elcore3` 에 `elasticore.rules.tsWithBuild=true`),
  `StaleClientTest`(생성된 클라이언트만 경고, 설정·TypeScript 실행에서는 경고 없음)
- 관련 파일: `ElastiCoreExtension.java`, `ElastiCorePlugin.java`, `RulesGenRunner.java`(`staleClient`),
  `elasticore-rule-guide.md`, `elasticore-env-guide.md`

### Bug Fixes (리뷰 3 - cascade 로 담은 로그인 행, 생성기의 남은 차이)

#### 리뷰 3 - cascade 로 DTO 에 담은 행으로 로그인·비밀번호를 쓰지 못한다
- `QmProfile @access(update=user)` 의 `member: QmMember @OneToOne @cascade(merge) @dtype(QmMemberDTO)` 처럼 `@signin`
  엔티티를 DTO 로 통째로 담으면, 역매핑이 요청의 `member` 를 관계에 붙이고(`toEntity` 는 `login`·`password` 를 보낸 그대로
  복사) 저장이 cascade 로 회원 행에 merge 했다. 로그인한 아무나 `PUT /qmProfile` 로 회원 1 의 로그인과 (직접 만든 PBKDF2)
  비밀번호를 바꿀 수 있었다 - v3 의 네 번들(레이어드·헥사고날, Java·Kotlin) 모두. `@ref` 의 같은 문제는 리뷰 2 에서 막았다
- 이제 `jpa-service-access`(2.x 번들에는 없음)의 매퍼 규칙 `access-mapper-nested` 가, 누가 쓸 수 있는지 정한 엔티티
  (`@access`·`@owner`·`@password`·필드의 `@access(admin)`, `@ref` 와 같은 기준)로 가는 **cascade 관계**를 역매핑에서 쓰지
  않는다. 매퍼가 다시 쓰는 다른 도메인의 `List<...> @OneToMany @cascade` 도 같다. DTO 가 함께 보내는 키
  (`memberMemberId`)는 전처럼 관계를 가리키고, 응답(정방향)에는 그대로 담긴다. 빌드가 필드를 짚어 DSL 경고를 낸다
  (`QmProfile.member is not written from the DTO: QmMember says who may write it ...`). 수정만이 아니라 생성도 같다 - 요청에
  담아 보낸 그 행은 저장되지 않는다(3.0 전이라 v3 번들의 기본값을 바꾼다 - 검사를 건너뛰는 쓰기 경로를 닫으려고). cascade 가 없는 관계(저장이 그 행을
  쓰지 않음)와 검사가 없는 엔티티로 가는 cascade 는 그대로다. 직접 쓴 `@jpa:OneToOne(cascade = ...)` 도 cascade 로 본다
  (관계 어노테이션 안의 `cascade =` 만, 낱말이 들어 있다고 cascade 가 아님)
- DDD·헥사고날에서 같은 애그리거트의 멤버(`QdAccount @aggregate(QdCustomer)`)는 루트와 함께 오는 것이 설계라 위 규칙에서
  빠지고(`ddd-mapper-member-marks`) 루트가 쓴다. 대신 멤버에는 자기 서비스가 없어 루트의 서비스가 해시·검사하지 못하던 멤버의
  `@password` 와 `@access(admin)` 필드를 DTO 에서 쓰지 않는다(`ddd-mapper-member-guards`, DSL 경고
  `QdAccount.secret is not written from a DTO: QdAccount is a member of QdCustomer's aggregate ...`) - 전에는 루트의 소유자가
  관리자만 쓰는 필드를 바꾸고 해시되지 않은 비밀번호를 저장할 수 있었다. 그런 필드는 루트에 둔다
- `@signin` 을 거절(DSL 경고)하는 대신 이렇게 한 까닭: 모델은 맞다 - 프로필이 회원을 담아 보여 주는 것은 정상이고, 틀린 것은
  생성된 매퍼가 그 행의 검사를 건너뛰는 쓰기 경로다. DSL 은 한 줄 그대로 두고 생성 코드가 안전한 쪽을 기본값으로 한다
  (`@ref` 를 읽기 전용으로 둔 리뷰 2 와 같은 원칙). `@signin` 이 아닌 `@owner`·`@access` 엔티티도 같은 구멍이었다
- 확인: `NestedGuardedRowTest`(리뷰의 모델 + 다른 도메인의 cascade 목록 + 검사 없는 엔티티로 가는 cascade·목록 + 같은
  애그리거트의 멤버, 레이어드·헥사고날 Java·Kotlin, 고치기 전 코드에서는 실패 - 리뷰의 시험 출력이 쓰기를 보였다),
  `aNativeCascadeIsACascade`
- 관련 파일: `ServiceAccessRules.java`(`access-mapper-nested`), `DddMapperRules.java`, `SourceCode.java`(`writtenByRoot`),
  `elasticore-annotation-guide.md`

#### 리뷰 3 - 프로젝트 규칙이 `update` 만 빼도 `batchUpdate` 가 함께 빠진다
- `batchUpdate` 가 `update(dto)` 를 부르게 된 뒤, 프로젝트 규칙이 `ctx(e).excludeMethod("update")` 만 하면 Java 서비스가
  없는 `update` 를 불러 컴파일되지 않았다(`@restExclude` 로 뺄 때만 함께 빠졌다). 이제 v3·Kotlin 출력의 `SourceCode` 가 뺀
  메서드를 부르는 것도 함께 뺀다(`@restExclude` 와 같은 짝: `update` -> `batchUpdate`, `deleteById` -> `delete`·`deleteBatch`,
  `delete` -> `deleteBatch`, `save` -> `create`). 2.x 호환 출력은 적힌 대로만 뺀다
- 확인: `ServiceFileV3Test.excludingUpdateByARuleTakesTheBatch`
- 관련 파일: `SourceCode.java`(`withCallers`), `ControllerBaseRules.java`, `elasticore-rule-guide.md`

#### 리뷰 3 - TypeScript 클라이언트가 서버의 모드를 따른다
- 클라이언트는 언제나 v3 서버처럼 `@restExclude(update)` 에 `batchUpdate` 까지 뺐다. 2.x 호환 서버는 `PUT /batch` 를
  남기므로 클라이언트에 그 메서드가 없었다. 이제 TypeScript 실행이 서버의 모드(v3 스위치·아키텍처, Kotlin, v3 에만 있는
  규칙, 그리고 서버를 만든 마지막 `elcore3` 가 `build/elasticore/server.mode` 에 남긴 값 - `-Pv3` 를 `elcore3` 에만 준
  경우)를 보고, 2.x 서버면 적힌 것만 뺀다
- 확인: `WritePathsTest.theClientOffersWhatItsServerKeeps`(헥사고날 서버의 클라이언트에는 없고, 2.x 서버의 클라이언트에는
  `batchUpdate` 가 있음)
- 관련 파일: `TypeScriptApiRules.java`, `RulesGenRunner.java`(`serverIsClean`), `SpecFactory.java`(`compatibleServer`)

#### 리뷰 3 - `@rollup` 판별값이 2.x 와 같다
- v3(`JpaEntityAnnotationRules`)와 2.x 호환 규칙(`JpaEntityClassRules`)이 `@rollup(Item)` 의 값을 판별값으로 써
  `@DiscriminatorValue("Item")` 을 냈다 - 부모의 값과 겹칠 수 있다. 2.x 생성기(`EntitySrcFilePublisher`)는 그 값을 읽지 않고
  `discriminator=`, `target=`, 엔티티 이름 순서로 정한다. 이제 두 규칙 모두 그렇다. `@rollup(X)` 를 쓴 프로젝트는 `jpa-springboot`(2.x 호환) 출력의 판별값도
  바뀌며, 2.x 생성기와 같아지는 쪽이다(템플릿에는 `@rollup` 이 없어 `elcoreRulesCheck` 가 덮지 않으므로, 임시 모델을 2.x
  생성기·`jpa-springboot`·v3·Kotlin 으로 생성해 `@DiscriminatorValue` 가 같음을 확인 - 엔티티 파일이 첫 줄 `//ecd:` 말고
  같았다)
- 확인: `EntityClassAnnotationTest.rollUp_ownValueIsNotTheDiscriminator`(고치기 전 코드에서는 실패),
  `NestedGuardedRowTest.aRolledUpEntityIsToldApartByItsName`(v3 네 가지), `SignInAndReferencesTest.aRolledUpSubclassHasNoTableOfItsOwn`
- 관련 파일: `JpaEntityAnnotationRules.java`, `JpaEntityClassRules.java`, `elasticore-annotation-guide.md`

#### 리뷰 3 - 경고 문구, javadoc, `*` 키
- `@search(%)` 는 끝 일치(`like '%' + 값`)를 만드는데 경고는 "searches by equality" 라고 했다. 이제
  `@search(%) searches by the end of the value (like '%' + value) - say %_ for that, _% for the start or %_% for any part.`
  (`MapperSpecTest.aLonePercentIsSaidAsTheEndsWithItIs`)
- 생성된 TypeScript 클라이언트가 있으면 `elcore3` 가 매번 경고했다 - 빌드 스크립트의 다른 태스크로 `elcore3ts` 를 부르는
  펫스토어·`elasticore-template-kt` 에서도. 이제 `elcore3ts` 가 만든 모델의 지문(블루프린트 도메인 폴더의 `.yml`(줄바꿈 무시),
  `architecture`·`language`·`rules` 설정, ElastiCORE 버전)을
  `build/elasticore/ts-client.model` 에 남기고, `elcore3` 는 클라이언트가 지금 모델보다 오래되었을 때만
  `... holds a generated TypeScript client made from an older model ...` 을 낸다 (`StaleClientTest`)
- `MapperSpecRules.nn()`·`SearchDtoBaseRules.isNullCheck()` 의 javadoc 이 새 메서드 위로 밀려나 있던 것을 제자리로
- 로더가 `*=` 를 키로 읽는 것(`@access(read=public, *=owner)`)을 `@access` 안에서만 한다. 다른 어노테이션, `--` 주석이
  만드는 `@label` 까지 2.x 의 패턴 그대로라 2.x 가 읽는 값은 바뀌지 않는다(전에는 `-- x *= 2` 같은 주석의 라벨이 달라졌음)
  (`StarParameterTest.aStarOutsideAccessIsReadAs2xDid`)
- 관련 파일: `MapperSpecRules.java`, `SearchDtoBaseRules.java`, `RulesGenRunner.java`(`modelDigest`, `writeStamp`),
  `AbstractModelLoader.java`, `elasticore-annotation-guide.md`, `elasticore-rule-guide.md`

### Bug Fixes (런타임 - 오류 응답의 언어)

#### 한 응답은 한 언어 - 기본은 `spring.web.locale`, 서버 머신의 언어는 쓰지 않는다
- 한국어 OS 의 서버에서 v3 앱의 400 이 두 언어로 나갔다. Bean Validation 의 기본 메시지(`Size`, `NotNull`, `Min` ...)는
  서버 머신의 언어(`크기가 0에서 10 사이여야 합니다`), DSL 문장과 제목은 영어였다. `@Valid` 가 돌 때 Spring 의 resolver 가
  헤더 없는 요청에 컨테이너의 언어(JVM 의 ko_KR)를 주었고, `ErrorMessages` 는 코드로만 찾고 못 찾으면 그 문장을 그대로
  썼다. 번역이 없는 `sv` 도 JDK 의 bundle fallback 으로 한국어였고, 애플리케이션의 `ValidationMessages_ko` 나
  `messages_ko` 가 있으면 `en` 요청까지 한국어가 섞였다. 가이드는 반대를 약속하고 있었다
- 이제 `ErrorMessages` 가 언어를 한 번 정하고 코드 찾기와 기본 메시지에 함께 쓴다(Spring Boot 3·4, Java·Kotlin 공통 런타임):
  - Spring MVC 의 기본 resolver(`AcceptHeaderLocaleResolver`)면 `Accept-Language` 의 언어 중 애플리케이션이 번역한 언어
    (`messages_<언어>`, 도메인 번들 옆 `_<언어>`, `ValidationMessages_<언어>` 파일), 기본 언어, 영어 중 처음 것. 없으면 기본
    언어 = `spring.web.locale`, 설정이 없으면 번들의 기본 문구(Locale.ROOT)
  - 영어는 자기 `_en` 파일이 없으면 Locale.ROOT 로 바꾼다 - 다른 영어 locale 은 `_en` 이 없는 bundle 이 머신의 언어로
    fallback 한다
  - 번역 파일 판단은 fallback 없는 `ResourceBundle.Control` 로, 스레드 class loader 로 한다(언어별 캐시)
  - 코드 번역이 없으면 Hibernate Validator 의 기본 메시지 템플릿(`{jakarta.validation.constraints.*}`)을 그 언어로 숫자와
    함께 다시 말한다(`BeanValidation.messageIn`, Spring 이 구성한 validator 의 interpolator). DSL 문장은 그대로다
  - 세션·쿠키·고정 등 다른 resolver 는 전의 규칙 그대로(헤더 없고 resolver 가 컨테이너 언어를 돌려준 요청만 기본 문구)
  - 서비스 측 검증용 `violation(code, violation, message)`: 코드(`Size.lines`)를 Spring 과 같은 값(`{0}` 필드, 속성 이름순)으로
    찾고, 없으면 같은 방식으로 다시 말한다
- v3 기본 출력 변경: `v3/apiErrors.tmpl`·`kt/apiErrors.tmpl` 의 서비스 측 검증 한 줄이 `messages.violation(...)` 이 된다.
  이유: generate-once 파일이라 3.0 이후에는 바꿀 수 없고, 이 줄 없이는 서비스 측(`PUT`) 400 만 머신의 언어로 남는다.
  `@Valid` 경로(`messages.text(f)`)는 런타임만 올려도 고쳐진다. 런타임 동작 변경(이유: 가이드가 약속한 동작): 한국어 머신에서
  헤더 없는 400·`en`·번역 없는 언어(`fr`, `sv`)의 기본 메시지가 영어(또는 `spring.web.locale`)로 나간다
- 바뀌지 않는 것: 2.x 생성기와 `jpa-springboot` 번들(`spring-api-errors` 가 없음 - `elcoreRulesCheck -Pstrict` 150 MATCH /
  0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra). 런타임 POM - `spring-webmvc` 는 `compileOnly`
- 확인: `ErrorLanguageOnAKoreanServerTest` (`-Duser.language=ko` JVM 에서만 도는 `koreanServerTest` 작업, 두 런타임 모두):
  헤더 없음·`sv`·`fr`·`en` 영어, `spring.web.locale: ko` 한국어(그래도 `en` 은 영어), `de;q=0.9, ko;q=0.8` 한국어, 애플리케이션의
  `ValidationMessages_ko`·`_ko` 만 있는 MessageSource 키가 `en` 에 새지 않음, 코드 번역(`Size.login=...{1}...`) 우선, 서비스 측
  위반, 세션 resolver. Pet Store `ShopFlowTest`(테스트 JVM 이 ko_KR). Pet Store 를 `-Duser.language=ko -Duser.country=KR` 로
  띄워 가입(`@Valid`)·고객 수정(서비스 측)에 헤더 없음/`en`/`sv` 는 `size must be between 0 and 10`, `ko`·`de;q=0.9, ko;q=0.8` 은
  `크기가 0에서 10 사이여야 합니다` 와 `잘못된 요청` 인 것을 확인했다
- 남은 것: `ValidationMessages_<언어>` 만 두고 기본 파일이 없는 애플리케이션은 그 파일이 없는 언어에서 머신의 언어로
  fallback 한다(Hibernate Validator 의 locator, Boot 버전마다 다른 설정 - 가이드에 "쓰지 마세요"). `spring.messages.basename` 으로
  바꾼 번들에만 있는 언어는 번역으로 보지 않는다. DSL 의 `@email`·날짜 제약 문장은 영어 그대로(TS 클라이언트와 같은 문장)
- 관련 파일: `ErrorMessages.java`, `BeanValidation.java`, `elasticore-springboot3/build.gradle`, `elasticore-springboot4/build.gradle`
  (`koreanServerTest`), `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ErrorLanguageOnAKoreanServerTest.java`, `ErrorMessagesTest.java`,
  `elasticore-sample-petstore/build.gradle`, `ShopFlowTest.java`, `elasticore-rule-guide.md`

### Improvements (오류 응답 - 컨트롤러에 닿기 전의 거절)

#### Spring MVC 가 거절한 요청도 `code` 가 있는 같은 본문으로 - `RequestShapeErrors`
- JSON 이 아닌 본문, 타입이 받지 않는 값(`quantity: -1`, 없는 enum 코드, 숫자 자리의 글자), 없는 파라미터, 파라미터 제약,
  없는 경로·메서드(404·405), 406·415 는 생성된 `XxxApiErrors` 에 처리기가 없어 Spring Boot 의 `{timestamp, status, error,
  path}` 로 나갔다. 가이드와 kit 은 모든 거절에 `code` 가 있다고 했고, 에이전트가 이것을 디버깅하느라 한 단계를 썼다
- 런타임에 `io.elasticore.springboot3.validation.RequestShapeErrors` (처리기 `RequestShapeAdvice` 를 등록하는 클래스 - 처리기는
  애플리케이션 전체에 답하는 `@RestControllerAdvice`, `@Order(LOWEST_PRECEDENCE)`)와 `RequestErrors`, Jackson 2/3 로 두 벌인 `JsonPaths`(springboot3 `src/jackson2/java`, springboot4
  `src/jackson3/java`)를 더했다. 400 `unreadable`, 400 `invalid` + `fieldErrors[].code` `typeMismatch`(필드는 Jackson 경로:
  `lines[0].quantity`), `NotNull`, 제약 이름, 404 `not-found`, 405 `method-not-allowed`(`Allow` 그대로), 406 `not-acceptable`,
  415 `unsupported-media-type`. 손으로 쓴 컨트롤러의 `@Valid` 본문도 같은 모양. 브라우저(`Accept: text/html`)의 404·405 는
  Spring Boot 의 오류 페이지를 둔다. 메시지는 모든 도메인 번들(`i18n/*-messages.properties`)과 그 옆 번역에서 찾는다
- Jackson 2 의 생성 enum(`CustomDeserializer` -> `fromCode`)이 거절한 값은 `ValueInstantiationException` 이 아닌
  `JsonMappingException` 으로 와서, 그 원인의 메시지(`Unknown PaymentMethod code: XX`)를 쓴다. JSON 이 깨진 위치가 중첩 객체 안이어도
  필드 오류가 아니라 `unreadable` 이다(Jackson 이 경로를 붙여도)
- v3 기본 출력 변경: `v3/apiErrors.tmpl`·`kt/apiErrors.tmpl` 에 `@Import(RequestShapeErrors.class)` 한 줄. 이유: 처리기 본문을
  개발자 파일에 얼리지 않고 런타임에 두어, 이후 고침(색인 없는 키, 예외 종류)이 라이브러리로 간다. 공통 코드(`ErrorCatalog.shared()`)에
  `unreadable`, `method-not-allowed`, `unsupported-media-type`, `not-acceptable`, `upstream`, `no-such-path`, `missing-value`,
  `type.*` 가 더해져 모든 v3 도메인 번들에 나온다
- 자동 구성이 아니다 - 생성된 `XxxApiErrors` 가 가져올 때만 생기므로 같은 런타임의 2.x 애플리케이션은 그대로다. 고친 적이 있는
  `XxxApiErrors` 는 다시 생성되지 않으니 그 한 줄을 붙인다(가이드). Spring Framework 6.1(Boot 3.2) 이상
- 확인: `RequestShapeErrorsTest`(springboot3 은 Jackson 2, springboot4 는 Jackson 3 으로 같은 단언), `Jackson2CodedEnumTest`,
  `elasticore-template-ddd` `ApiContractTest`(Boot 3.3.2, 생성 enum 의 `Unknown PaymentMethod code: XX`, 다른 패키지의 손으로 쓴
  컨트롤러), `elasticore-template-kt` `ShopApiContractTest`, Pet Store `ShopFlowTest`, 그리고 한국어 JVM 으로 띄운 Pet Store 에
  curl: `quantity -1` -> 400 `lines[0].quantity: a quantity cannot be negative: -1`(ko: `수량은 0 이상의 정수입니다`), `DINERS` ->
  `creditCard.creditCardType: Unknown CreditCardType code: DINERS`, `{"lines":` -> `unreadable`, `GET /item/abc` -> `itemId: must be a
  number`, 주문 `PUT` -> 405 `Allow: POST`, `text/plain` -> 415, `Accept: image/png` -> 406, 없는 경로 -> 404 `not-found`, 브라우저 ->
  Whitelabel, 모두 `application/problem+json`
- 관련 파일: `RequestShapeErrors.java`, `RequestErrors.java`, `JsonPaths.java`(두 벌), `ErrorCatalog.java`, `ApiErrorRules.java`,
  `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ProjectRuleSetSupportTest.java`, `ShopModelTest.java`, `SignInAndReferencesTest.java`,
  `ApiContractTest.java`, `ShopApiContractTest.kt`, `petstore-messages_ko.properties`, `build_guide.py`, `elasticore-rule-guide.md`,
  `elasticore-ddd-customize-guide.md`, `elasticore-env-guide.md`

#### 생성된 `findById`·`delete` 의 빈 404 -> 같은 모양의 404
- `GET /order/{id}` 와 `DELETE /order/{id}` 가 없는 행에 본문 없는 404 를 답해 클라이언트가 `code` 를 읽을 수 없었다
- `v3/control.tmpl`·`kt/control.tmpl` 이 런타임의 새 `NotFoundException("Order not found")` 를 던진다. `EntityNotFoundException`
  이라 생성된 `XxxApiErrors` 가 `not-found` 로 답하고, `@ResponseStatus(404)` 라서 `spring-api-errors` 를 끈 앱도 500 이 아닌 404 다.
  v3 기본 출력 변경(이유: 다른 404 와 같은 본문). 2.x 의 `control.tmpl` 은 그대로
- 확인: `NotFoundExceptionTest`, `ShopModelTest`, `SignInAndReferencesTest`(Kotlin), ddd·Kotlin 샘플의 `findByIdOfNothingIsAProblem`,
  Pet Store `GET /item/99999` -> 404 `{"detail":"Item not found","code":"not-found"}`
- 관련 파일: `NotFoundException.java`, `v3/control.tmpl`, `kt/control.tmpl`

### New Features (런타임 - 손으로 쓴 거절)

#### 손으로 쓴 거절도 메시지 코드로 번역된다
- `ConflictException` 은 메시지 하나만 받아 `<도메인>-messages_ko` 로 번역할 수 없었다. 이제 `BadRequestException`,
  `NotFoundException`, `ConflictException`, `security.ForbiddenException`, `security.NotSignedInException`, `http.PortCallException` 이
  `(code, message, args...)` 를 받는다. 생성된 거절과 같은 순서로 찾고, 없으면 기본 문구(값을 넣은 `MessageFormat`), 응답의 `code` 는
  그 코드다. 예전 생성자는 그대로다(코드 없음 = 예전 응답)
- v3 기본 출력 변경: 생성된 처리기가 `coded(problem(..., messages.text(ex.getCode(), ex.getMessage(), ex.getArgs())), ex.getCode())`
  로 답한다. 이유: 손으로 쓴 규칙도 생성된 규칙처럼 번역되도록
- 확인: `ErrorMessagesTest.aRefusalWithACodeIsTranslatedLikeAGeneratedOne`, Pet Store `ShopFlowTest.aRefusalHandCodeThrowsIsSaidByItsCode`
  (테스트용 컨트롤러가 던진 `loan.no-copy-left`: 헤더 없으면 기본 문구, `ko` 면 `messages_ko` 의 번역)
- 관련 파일: `ConflictException.java`, `BadRequestException.java`, `NotFoundException.java`, `ForbiddenException.java`,
  `NotSignedInException.java`, `Refusals.java`, `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `elasticore-rule-guide.md`

#### `PortCallException` - 부른 시스템이 실패하면 502
- 포트는 상대의 오류 상태를 값으로 돌려주고(런타임 프록시와 `httpPort: spring` 모두), 502 로 답할 예외가 없어 에이전트가 택배사
  호출용 예외와 처리기를 손으로 썼다. 런타임에 `io.elasticore.springboot3.http.PortCallException`(`ResponseStatusException`,
  502)을 더하고 두 `apiErrors` 템플릿에 처리기(502, `code: upstream`)를 더했다. `ResponseStatusException` 이라 이 처리기가 없는 예전
  `XxxApiErrors` 에서도 502 다. 실패 정책 설정(`{id}.failOnError`)은 3.1
- 확인: `PortCallExceptionTest`(처리기 없이 502), `PortFailureContractTest`(오류 본문은 값, 읽을 수 없는 본문·답 없음은 예외, 멀티파트는
  `HttpStatusCodeException`), Pet Store `ShopFlowTest` 의 502 `upstream`
- 관련 파일: `PortCallException.java`, `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ApiErrorRules.java`

### Bug Fixes (포트 등록기)

#### `@EnableElastiCore` 가 붙은 클래스의 패키지를 스캔한다
- `PortServiceRegistrar` 는 호출 스택에서 `@SpringBootApplication` 을 찾고, 못 찾으면 내부 패키지 이름으로 대신했다. 그래서
  `@SpringBootTest` 에서는 포트를 하나도 등록하지 않았고(테스트마다 손으로 `@Bean`), 공개 저장소에 내부 이름이 있었다
- 이제 `@EnableElastiCore` 가 붙은 클래스의 패키지와(테스트가 띄우는 그 클래스) 스택의 `@SpringBootApplication` 패키지를 함께 보고,
  같은 인터페이스는 한 번만 등록한다(예전에는 겹치면 `BeanDefinitionOverrideException`). 내부 패키지 fallback 은 지웠다. 직접 선언한
  `@Bean` 이 있으면 그 포트는 등록하지 않는다 - 예전 안내대로 테스트에 `@Bean` 을 적어 둔 프로젝트가 같은 타입 빈 둘로 깨지지 않게.
  `beanFactory` 를 static 에 두지 않는다(두 테스트 컨텍스트가 서로의 것을 쓰지 않게). 생성 결과는 바뀌지 않는다
- 확인: `PortServiceRegistrarTest`(가져온 클래스의 패키지, 두 번 가져와도 하나, 직접 선언한 빈이 우선; Spring Boot 처럼 덮어쓰기 금지)
- 관련 파일: `PortServiceRegistrar.java`, `elasticore-dsl-port-guide.md`, `elasticore-rule-guide.md`

### Documentation (포트 가이드 - 실패 처리)

#### 2.8 실패 처리, base URL 이 정해지는 때, 가짜 상대로 테스트
- 2장 머리에 두 방식(런타임 프록시 / `httpPort: spring`)과 `@EnableElastiCore` 가 필요한 쪽. 2.8: 두 방식의 실패 표(오류 본문은 값,
  읽을 수 없는 본문·답 없음은 예외, 멀티파트), 502 + 되돌림 패턴(`PortCallException`), 테스트(`@MockitoBean`, 같은 서버의 가짜는
  `DEFINED_PORT`). 2.7: `httpPort: spring` 의 base URL 은 기동 때 한 번 정해진다(`RANDOM_PORT` 테스트는 포트 0 을 부름 - 조사에서
  실행으로 확인), Boot 4 키의 그룹 이름은 `erp--credit-check-port`(두 줄)
- 5장: `elasticore 1.8.x` 와 포트를 등록하지 않던(`ApplicationContextProvider`·`DbTransactionGateway` 만 등록하던)
  `@ComponentScan("io.elasticore.springboot3")` 대신 `@EnableElastiCore`,
  등록기가 보는 패키지
- 관련 파일: `elasticore-dsl-port-guide.md`

#### 개발 가이드의 예제 값에서 내부 이름을 뺐다
- `Op.ILIKE` 예제의 검색어가 내부 이름이었다. `"acme"` 로 바꿨다(설명도 실제로 맞는 값으로: `acme`, `ACME`, `Acme Supply`)
- 관련 파일: `elasticore-dev-guide.md`

### Bug Fixes (런타임 - 오류 응답과 포트 등록기 리뷰)

#### 한 도메인의 번역이 다른 도메인의 영어에 가려지지 않는다
- 도메인이 여럿인 앱(예: `billing`, `catalog`, `shop`)에서 `shop-messages_ko` 에 둔 `error.unreadable` 번역이 `ko` 요청에도
  영어로 나갔다. 모든 도메인의 기본 파일에 공통 코드(`ErrorCatalog.shared()`)가 들어 있고, Spring 의
  `ResourceBundleMessageSource` 는 코드를 가진 첫 번들을 쓰며, `_ko` 가 없는 번들은 자기 기본 파일로 답하기 때문이다
  (이름순으로 앞선 `billing-messages.properties`)
- `ErrorMessages` 가 두 번 찾는다: 먼저 모든 번들의 그 언어 파일(`_ko_KR`, `_ko`)에서, 없으면 예전처럼 기본 파일까지.
  `messages_ko` 가 도메인의 `_ko` 보다 앞서는 것은 그대로다. 단일 도메인 앱에서 달라지는 것은 `messages.properties`(기본 파일)에
  있는 코드를 도메인의 `_ko` 가 번역한 경우뿐이다 - 이제 번역이 나간다
- 확인: `RequestShapeErrorsTest.aTranslationInOneDomainIsNotHiddenByAnotherDomainsEnglish`,
  `ErrorMessagesTest.aTranslationInTheSecondBundleWinsOverTheFirstBundlesEnglish`(테스트 번들 `billing-messages.properties`,
  번역 없음) - 고치기 전에는 둘 다 영어로 실패
- 관련 파일: `ErrorMessages.java`, `elasticore-rule-guide.md`

#### 2.x 앱의 컴포넌트 스캔이 API 오류 처리기를 가져오지 않는다
- 처리기가 `@RestControllerAdvice` 라서 예전 포트 가이드대로 `@ComponentScan("io.elasticore.springboot3")` 를 둔 2.x 앱이
  런타임만 올려도 그것을 가져왔다 - 깨진 JSON, 404, 405, 406, 415 와 앱 컨트롤러의 `MethodArgumentNotValidException` 이 모두
  `problem+json` 으로 바뀐다
- 처리기는 `RequestShapeAdvice` 로 옮기고 결코 맞지 않는 `@Conditional` 을 붙였다(스캔도, 그 클래스의 `@Import` 도 지나친다).
  `RequestShapeErrors` 는 그 정의를 직접 등록하는 `ImportBeanDefinitionRegistrar` 가 되었다 - 생성된 `@Import(RequestShapeErrors.class)`
  는 그대로라 생성 결과는 바뀌지 않는다. 여러 도메인이 가져와도 처리기는 하나다. 처리기의 빈 이름은
  `io.elasticore.springboot3.validation.RequestShapeAdvice` 로 바뀌었다(전에는 `...RequestShapeErrors`)
- 확인: `RequestShapeErrorsRegistrationTest`(런타임 패키지 스캔에 `RequestShape*` 없음, 스캔한 컨텍스트에 `@ControllerAdvice` 없음 -
  고치기 전에는 둘 다 실패; 두 도메인이 가져온 MVC 컨텍스트에 처리기 하나, `unreadable` 로 답함)
- 관련 파일: `RequestShapeErrors.java`, `RequestShapeAdvice.java`, `elasticore-dsl-port-guide.md`, `elasticore-rule-guide.md`

#### 포트 프록시를 감싼 `@Primary` 빈이 다시 동작한다
- 직접 선언한 빈이 있으면 포트 프록시를 지웠으므로 `@Bean @Primary CourierPort retrying(@Qualifier("CourierPort") CourierPort raw)`
  처럼 프록시를 감싸는 앱이 `NoSuchBeanDefinitionException` 으로 뜨지 않았다(공유 런타임의 2.x 앱 포함)
- 이제 다른 빈 중 `@Primary` 가 없을 때만(타입 주입이 모호할 때만) 프록시를 지운다. 또 스캔한 패키지에서 포트 인터페이스를
  구현한 클래스(가짜, 래퍼)도 Reflections 가 돌려줘 프록시로 등록하려다 `IllegalArgumentException` 으로 기동이 실패했다(예전
  등록기도 같았다) - 이제 `@ExternalService`/`@DbmsService` 가 직접 붙은 인터페이스만 등록한다. 붙은 인터페이스를 상속하기만
  한 인터페이스도 이제 등록하지 않는다(예전에는 어노테이션이 null 인 프록시가 되어 첫 호출에서 실패했다)
- `@Primary` 인 가짜(포트를 통째로 바꾸는 테스트 빈)도 이제 프록시를 남기므로 `List<CourierPort>` 는 둘을 받는다(가이드)
- 확인: `PortServiceRegistrarTest.aPrimaryBeanThatWrapsTheProxyKeepsIt`(고치기 전 `NoSuchBeanDefinitionException`),
  `aClassImplementingAPortIsNotMadeAProxy`(고치기 전에는 이 패키지의 모든 등록기 테스트가 `IllegalArgumentException`)
- 관련 파일: `PortServiceRegistrar.java`, `elasticore-dsl-port-guide.md`

#### Boot 4 에서 웹 계층을 Jackson 2 로 되돌린 앱의 필드 오류
- springboot4 의 `JsonPaths` 는 Jackson 3 예외만 알아, `MappingJackson2HttpMessageConverter` 를 쓰는 Boot 4 앱에서 타입이 틀린 값이
  필드 오류가 아니라 `unreadable` 로 나갔다. Jackson 2 가 클래스 경로에 있으면 그 예외도 springboot3 처럼 읽는다
- 확인: springboot4 의 `Jackson2WebLayerOnBoot4Test`(`count: must be a number`, `lines[0].amount` 의 생성자 거절, 깨진 JSON 은
  `unreadable`; 고치기 전에는 첫 테스트가 실패)
- 관련 파일: springboot4 `src/jackson3/java/.../JsonPaths.java`, `src/jackson2test/java/.../Jackson2WebLayerOnBoot4Test.java`, `build.gradle`,
  `elasticore-env-guide.md`

#### 테스트: 한국어 JVM, 공유 테스트 자원
- `elasticore-template-ddd`, `elasticore-template-kt` 의 테스트도 Pet Store 처럼 `-Duser.language=ko -Duser.country=KR` 로 돈다.
  새 테스트(`errorsAreNotSaidInTheMachinesLanguage`, `aFieldErrorIsNotSaidInTheMachinesLanguage`)가 헤더 없음·`en`·`sv` 의 400
  본문에 한글이 없음을 확인한다
- 런타임 테스트의 `ValidationMessages*.properties`(앱이 덮어쓴 `NotBlank` 문구)는 `src/koreanServerTest/resources` 로 옮겨
  `koreanServerTest` 에만 둔다 - 다른 모든 테스트의 기본 문구를 바꾸지 않게
- 관련 파일: 두 샘플과 두 런타임의 `build.gradle`, `ApiContractTest.java`, `ShopApiContractTest.kt`

#### 남은 경우 (리뷰에서 코드를 읽어 찾음, 실행으로 재현하지 않음)
- `messages_en` 은 있고 `ValidationMessages` 는 `_ko` 파일만 있는(기본 파일 없는) 앱: `en` 요청은 `en` 으로 정해지고(`Locale.ROOT`
  가 아님), 기본 제약 메시지를 다시 말할 때 Hibernate Validator 가 앱의 `ValidationMessages` 기본 파일을 찾지 못해 JDK 의 bundle
  fallback 으로 머신 언어 파일을 읽을 수 있다. 피하려면 `ValidationMessages.properties` 기본 파일도 둔다(가이드는 이 파일들로
  언어를 맞추지 말라고 한다)
- `spring.web.locale` 로 `Locale.ROOT` 가 아닌 기본 언어를 둔 앱: 코드가 런타임의 번들에 없어 애플리케이션 `MessageSource` 로 가면,
  Spring Boot 의 `spring.messages.fallback-to-system-locale`(기본 `true`) 때문에 그 언어 파일이 없는 번들은 머신 언어로 답할 수
  있다. 피하려면 `spring.messages.fallback-to-system-locale=false`
- 파라미터 제약 처리기(`HandlerMethodValidationException`)를 Spring Framework 6.1 에서 부르는 테스트는 없다.
  `RequestShapeErrorsTest` 는 springboot3(Spring 6.2)과 springboot4(Spring 7)에서 돌고, Spring 6.1 인 Boot 3.3.2 샘플의 계약
  테스트는 그 처리기에 닿지 않는다

## 3.0.261020-alpha2

### Bug Fixes (생성기)

#### 도메인에 `Comment` 엔티티가 있어도 컴파일된다
- 같은 도메인에 `Comment` 라는 엔티티나 enum 이 있으면 생성된 코드가 컴파일되지 않았다. `-- 설명` 이 붙은 필드마다
  Hibernate 의 `@Comment` 가 붙는데
  - 규칙 엔진(`elcore3`)은 `import org.hibernate.annotations.Comment;` 를 써서 같은 패키지의 `Comment` 엔티티를
    가렸다. `PostBase` 의 `List<Comment>` 가 애노테이션을 가리켜 컴파일 오류가 났다
  - 2.x 생성기(`elcore`)는 `org.hibernate.annotations.*` 를 import 하는데, 같은 패키지의 `Comment` 엔티티가 이름을
    가져가 `@Comment("...")` 가 엔티티를 가리켰다
- 이제 도메인에 그런 엔티티나 enum 이 있으면 `@Comment` 를 import 하지 않고 `@org.hibernate.annotations.Comment("...")`
  처럼 패키지까지 붙여 쓴다. 규칙 엔진의 v3 번들(`jpa-springboot-v3`·DDD·헥사고날)과 Kotlin 번들은 규칙이 만들어
  쓰는 다른 애노테이션도 도메인의 타입과 이름이 겹치면 같게 한다. 2.x 와 같은 출력을 내는 `jpa-springboot` 번들은 2.x
  생성기처럼 `@Comment` 만 바꾼다. 규칙이 정해진 한 줄로 쓰는 애노테이션(DTO 의 `@Size`·`@Schema`,
  `@Setter(AccessLevel.PROTECTED)` 등), 같은 파일에서 그런 한 줄로도 쓰이는 애노테이션, 다른 애노테이션 안이나
  `@jpa:` 로 쓰는 애노테이션(`@Table` 의 `@Index` 등)은 단순 이름과 import 를 그대로 둔다. 그런 이름이 없는 도메인의
  생성 결과는 바뀌지 않는다(`elcoreRulesCheck`·`elcoreTreeCheck` 결과 그대로)
- 아직 남은 경우: 다른 도메인의 `Comment` 엔티티를 참조하는 엔티티(그 파일이 그 엔티티를 import 한다), DTO 에서
  `@Size` 와 이름이 같은 타입(`Size` enum 등)을 필드 타입으로 쓰는 경우, MongoDB 출력은 이전처럼 컴파일되지 않는다
- 확인: 2.x 생성기, 규칙 엔진 layered·hexagonal·Kotlin 으로 `Post`·`Comment` 게시판을 생성해 단순 이름 `@Comment` 와
  그 import 가 없는 것을, `Comment` 가 없는 같은 모델은 단순 이름 `@Comment` 와 그 import 를 그대로 쓰는 것을, `Size`
  enum 과 `Schema` 엔티티가 있는 도메인의 DTO 가 `@Size`·`@Schema` 의 import 를, `Setter`·`JoinColumn` 엔티티가 있는
  도메인의 파일이 `@Setter`·`@JoinColumn` 의 import 를 그대로 두는 것을 테스트로 확인했다. hexagonal 게시판은
  Spring Boot 4.0.8 에서 컴파일하고 실행했다
- 관련 파일: `SourceCode.java`, `AnnoCode.java`, `EntitySpec.java`, `EntitySrcFilePublisher.java`,
  `ModelNamedLikeAnAnnotationTest.java`

### Documentation (README)

#### 시작하기를 실제 작업 순서로, build.gradle 은 그대로 동작하게
- 실제 작업 순서로 고쳤다: ③ `./gradlew elcore3` 로 생성 -> ④ 내 작업(애플리케이션 클래스, `ProductService` 의 업무
  로직, 로그인) -> ⑤ `./gradlew bootRun`. 이전에는 `./gradlew build` 한 단계로 적어, 생성된 코드를 보고 그 위에 작업하는
  순서가 드러나지 않았다. `build`·`bootRun` 이 컴파일 전에 `elcore3` 를 먼저 돌리는 것은 그대로다
- ① 의 `build.gradle` 이 그대로 복사해 동작한다. 이전 예시는 `java` 플러그인이 없어 `implementation()` 을 찾지 못하고
  실패했고, 생성된 코드가 쓰는 의존성(Spring Boot 스타터, springdoc, Lombok, DB 드라이버)과 그 버전을 정하는
  `io.spring.dependency-management` 가 빠져 있었다
- Spring Initializr 로 시작했다면 그 `build.gradle` 을 두고 빠진 줄만 더한다고 적었다. 통째로 바꾸면 함께 생성된
  테스트가 쓰는 테스트 의존성이 빠져 `compileTestJava` 에서 실패한다. 애플리케이션 클래스는 Initializr 가 group·artifact
  로 정한 패키지에 만든다는 것도 적었다 - 생성된 패키지가 그 아래여야 Spring 이 찾는다. Initializr 의 기본값인
  Spring Boot 4.1.1 에서도 생성·빌드(함께 생성된 테스트 포함)·실행을 확인했다
- Spring Boot 3 에서 바꿀 것(`elasticore-springboot3`, `spring-boot-starter-web`, springdoc 2.8.17)과, Spring Boot 4 에는
  Gradle 8.14 이상이 필요하다는 것을 적었다
- 관련 파일: `README.md`, `README.ko.md`, `ReadmeTest.java`

#### 소개를 오래 운영할 업무 시스템에 맞추고, 근거와 현재 상태를 적었다
- 첫 문장과 소개를 오래 운영할 업무 시스템에 맞췄다. 업무 규칙(누가 무엇을 하는지, 주문이 어떻게 진행되는지, 값이
  어떻게 정해지는지)은 개발자나 AI 도구가 고치고 리뷰어가 읽는 한 곳에 남고, 빌드가 그 규칙을 서버의 모든 계층에,
  `./gradlew elcore3ts` 가 TypeScript 클라이언트에 반영한다
- 소개 아래에 어떤 시스템을 위한 것인지(주문·정산·계약·백오피스처럼 여러 사람이 몇 년씩 고쳐 가는 업무 시스템)와
  맞지 않는 곳(화면이 대부분인 애플리케이션, 영속이 관계형이나 JPA 가 아닌 시스템, 선언형 모델로 나타내기 어려운
  로직)을 적고, 근거와 현재 상태로 가는 링크를 두었다
- "AI 코딩 시대에" 는 시제품과 오래 운영할 시스템을 나눠서 시작한다
- "믿을 수 있는 출력" 에 근거를 적었다: 풀 리퀘스트마다 CI 가 하는 검사(모든 모듈 빌드, 2.x 출력과의 산출물별 비교와
  기준 프로젝트 229개 파일 비교, 허용한 차이와 비교에서 빼는 것까지), 변경 증폭 벤치마크의 숫자(검색 필드 DSL 1줄 ->
  9개 파일 39줄 변경, 두 번 생성해 130개 파일 중 0개 다름 - 2026-10-16 에 다시 재어 같은 숫자를 확인), Maven Central
  서명 배포, 생성된 코드도 보안 신고 대상이라는 것(SECURITY.md)
- "현재 상태" 를 새로 두었다. 3.0 은 정식 출시 전이고, `elasticore` 블록에 아무것도 선언하지 않은 프로젝트는 2.x
  생성기로 생성하며 규칙 엔진의 `jpa-springboot` 번들은 풀 리퀘스트마다 그 출력과 비교한다. v3 번들
  (`jpa-springboot-v3`·DDD·헥사고날·Kotlin·TypeScript)의 출력은 정식 출시 전까지 바뀔 수 있고, 바뀔 때마다 이유를
  이력에 적는다. 지원 버전은 SECURITY.md, 브랜치와 릴리스는 BRANCHING.md·RELEASING.md 로 안내한다
- 관련 파일: `README.md`, `README.ko.md`

### Documentation (지원 기간)

#### 3.0 은 첫 정식 릴리스부터 3년간 지원한다
- 3.0 은 첫 정식 릴리스(`-alpha`·`-beta`·`-rc` 가 붙지 않은 첫 버전)부터 3년간 버그·보안 수정을 받는다. 대상은
  `elasticore-base`, Gradle 플러그인(`io.elasticore`), `elasticore-springboot3`·`elasticore-springboot4` 이고, 수정은
  `release/3.0` 에서 `3.0.YYMMDD` 버전으로 낸다. 끝나는 날짜는 그 릴리스가 나올 때 BRANCHING.md 에 적는다. 첫 정식
  릴리스 전까지는 `main` 에서 다음 사전 릴리스(pre-release)로 고친다
- `release/3.0` 은 첫 정식 릴리스에서 만든다고 고쳐 적었다. 이전에는 "첫 3.0 릴리스" 라고만 적어, 정식 출시 전
  버전(3.0.261015-alpha1)에서 만들어야 하는 것처럼 읽혔다. 3.0 을 고치는 브랜치도 `release/3.0` 이 생기기 전까지는
  `main` 에서 딴다
- SECURITY.md 의 지원 버전 표는 행이 겹치지 않게 "3.x 최신 버전(`main`)" 과 "첫 정식 릴리스부터의 3.0(`release/3.0`)"
  으로 나눴다. README 의 "현재 상태" 가 같은 내용을 말한다
- 관련 파일: `README.md`, `README.ko.md`, `SECURITY.md`, `BRANCHING.md`

### Documentation (호환 검사 설명)

#### 2.x 와의 호환 검사를 "바이트 단위" 가 아니라 실제로 하는 대로 적었다
- README, BRANCHING.md, CONTRIBUTING.md, 비교 문서가 2.x 출력과 "바이트 단위로" 같다고 적었지만 검사는 그렇게 하지
  않는다. `elcoreRulesCheck` 는 7가지 산출물을 메모리에서 렌더링해 비교하고 엔티티는 필드 부분만 본다
  (`150 MATCH / 0 DIFF / 2 ACCEPTED`). `elcoreTreeCheck` 는 기준 프로젝트에서 2.x 가 만드는 229개 파일을 모두 비교하되,
  2.x 버그 수정으로 달라지는 4개는 이름을 정해 허용하고 1개는 import 순서만 다르다. 두 검사 모두 각 파일의 첫 줄(내용
  해시·시각), 한 번만 생성되는 파일 표시 주석의 표기, 줄바꿈 문자, 파일 끝의 빈 줄은 비교하지 않고, 트리 비교는 줄 끝
  공백도 무시한다
- 그래서 "byte for byte"·"바이트 단위" 를 빼고, 무엇을 비교하는지와 허용한 차이를 그대로 적었다. 사용자 프로젝트의
  `blueprint/` 로 복사되는 규칙 가이드도 `jpa-springboot` 의 출력을 "바이트 단위로 동일" 하다고 적고 있어 같이 고쳤다
- 관련 파일: `README.md`, `README.ko.md`, `BRANCHING.md`, `CONTRIBUTING.md`, `docs/comparison.md`, `docs/comparison.ko.md`,
  `elasticore-rule-guide.md`, `elasticore-template-ddd/README.md`

### Improvements (호환 검사)

#### CI 가 2.x 기준 트리가 커밋된 그대로인지도 확인한다
- 두 호환 검사(`elcoreRulesCheck`, `elcoreTreeCheck`)는 규칙 엔진을 같은 빌드의 2.x 생성기와 비교한다. 둘이 함께 쓰는
  코드(모델 로더 등)를 바꿔 두 생성기가 똑같이 달라지면 두 검사는 통과하고, 2.x 생성기를 쓰는 프로젝트의 코드는
  바뀐다
- CI 와 릴리스 워크플로가 빌드 직후, 빌드가 다시 생성한 기준 프로젝트(`elasticore-project/elasticore-template/src/main/java`)
  가 커밋된 그대로인지 `git status` 로 확인한다. 바뀐 파일, 없어진 파일, 새로 생긴 파일이 하나라도 있으면 그 목록을 실행
  요약에 남기고 실패한다
- 로컬에서 확인했다: 템플릿을 빌드해도 트리는 그대로이고, 파일 하나를 바꾸거나 새 파일 하나를 두면 이 단계가 실패한다.
  기준 트리 248개 파일은 저장소에 LF 로 들어 있고 2.x 생성기는 OS 의 줄바꿈으로 쓰므로 Linux CI 에서도 같다
- 2.x 출력을 일부러 바꾸는 변경(버그 수정)은 다시 생성된 파일을 함께 커밋하고 이유를 이력에 적는다고 CONTRIBUTING.md 에
  적었다. 풀 리퀘스트에 모든 2.x 프로젝트가 받을 차이가 그대로 보인다
- CONTRIBUTING.md, BRANCHING.md, RELEASING.md 의 검사 목록과 README 의 "믿을 수 있는 출력"·"현재 상태" 가 이 검사를
  말한다
- 관련 파일: `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `CONTRIBUTING.md`, `BRANCHING.md`, `RELEASING.md`,
  `README.md`, `README.ko.md`

### Documentation (지원 범위와 릴리스)

#### 3.0 수정이 맞추는 Spring Boot 버전, 풀 리퀘스트로 하는 릴리스, 서명 키 만료
- 3.0 의 수정은 그 시점에 Spring 팀이 오픈소스로 지원하는 Spring Boot 4 버전에서 빌드하고 테스트하고, Spring Boot 3
  런타임 라이브러리(`elasticore-springboot3`)는 마지막 오픈소스 Spring Boot 3 릴리스인 3.5.16 에서 한다. 3.5 의
  오픈소스 지원은 2026-06-30 에 끝났고(Maven Central 의 마지막 3.x 는 3.5.16), Spring Boot 3 에 머무는 팀이 3년 동안
  테스트된 수정을 받게 하려는 것이다
- `main`·`release/**` 은 CI 가 통과한 풀 리퀘스트로만 바뀐다. 그래서 RELEASING.md 의 버전 커밋과 다음 `-SNAPSHOT`
  커밋도 `version/*` 브랜치의 풀 리퀘스트로 넣고, 태그는 그 머지가 `main` 에 만든 커밋에 단다 - squash 머지라도
  맞는 커밋에 달리도록 머지된 커밋을 직접 지정한다
- 릴리스 서명 키는 2029-10-14 에 만료된다. 3.0 지원이 그 뒤까지 가므로, 그 전에 기한을 늘리고 공개 키를 키 서버에
  다시 올리고 다시 내보낸 비밀 키를 `SIGNING_KEY` 에 넣는다고 적었다
- 관련 파일: `README.md`, `README.ko.md`, `BRANCHING.md`, `RELEASING.md`

### Improvements (README 검사)

#### README 의 시작하기를 테스트가 실제로 생성해 본다
- 지금까지 README 는 링크·버전·번들 이름만 검사했고(`ReadmeTest`), 그 안의 DSL 은 아무도 돌리지 않았다. 더는 읽히지
  않는 블록, 처음 실행에서 만나는 경고, README 가 약속하고 생성기는 더 쓰지 않는 파일이 모두 빌드를 통과했다
- `ReadmeQuickStartTest` 가 README.md·README.ko.md 의 시작하기에서 build.gradle 의 `architecture`·`language` 와 Spring
  Boot 버전, env.yml, model/entity.yml 을 꺼내 ② 가 말하는 대로 새 프로젝트에 넣고, 플러그인이 하듯 생성한다. DSL
  경고나 경고 줄이 하나라도 있거나 ③ 에 적은 파일이 하나라도 없으면 실패한다. ④ 가 부르라는 로그인 메서드
  (`SessionCurrentUser.signIn`)가 런타임 라이브러리에 있는지도 본다
- README 를 일부러 세 가지로 틀리게 해 세 검사가 모두 이유를 말하며 실패하는 것을 확인했다: 어디에도 없는 타입의
  필드(DSL 경고 3개), 생성되지 않는 파일, 이름을 바꾼 로그인 메서드
- 규칙 엔진의 `main` 을 `run()` 과 나눴다. `run()` 은 0 이나 1 을 돌려주고 `main` 만 `System.exit` 를 부르므로
  플러그인이 보는 동작은 그대로다. 테스트는 `run()` 을 불러, 생성기가 모델을 거절해도 테스트 JVM 이 끝나지 않고 생성기의
  설명을 담은 실패가 된다
- 관련 파일: `ReadmeQuickStartTest.java`, `RulesGenRunner.java`, `BaseFieldType.java`

## 3.0.261015-alpha1

### New Features (첫 공개)

#### ElastiCORE 3.0
- 이 저장소에서 처음 공개하는 버전이자 3.0 의 첫 알파. 이 저장소의 변경 이력은 이 버전에서 시작한다
- 알파: 정식 3.0 전까지 v3 번들(`jpa-springboot-v3`, `ddd-*`, `hexagonal-*`, `*-kt`, `ts-model`)의 생성 결과는 바뀔 수
  있다 - 바뀌면 그 이유를 여기에 적는다. 선언하지 않은 프로젝트의 생성 결과(2.x 와 같은 `jpa-springboot`)는 알파에서도
  바뀌지 않는다
- Maven Central 에서 받는다: `io.elasticore:elasticore-base`, `io.elasticore.springboot3:elasticore-springboot3`,
  `io.elasticore.springboot4:elasticore-springboot4`, Gradle 플러그인 `id 'io.elasticore'`. 플러그인도 Maven Central 에
  있으므로 `settings.gradle` 의 `pluginManagement` 에 `mavenCentral()` 을 넣는다 (`README.md` 의 설치)
- 기능과 사용법: `README.md`, 그리고 가이드(`elasticore-guide/` - 생성할 때 프로젝트의 `blueprint/` 에 복사됨)

### Bug Fixes (2.x 와 생성 결과가 다른 곳)

#### 2.x 의 결함을 고쳐 생성 결과가 달라지는 곳
생성 코드 밖의 차이(`schema.yml` 의 위치·인코딩, 모듈 배치 등)는 `elasticore-migration-guide.md` 에 있다.
- `string(n)` 과 `@length(min=..)` 를 함께 쓰면 `@Size` 에 최소 길이도 들어간다. 2.x 는 최소 길이를 빠뜨렸다 -
  더 짧은 값은 이제 400
- `@formula` (2.2.261012 에도 들어간 수정 - 그 이전 2.x 와 다름):
  - 식에 `=` 가 있어도 값이 깨지지 않는다. 이전에는 컴파일되지 않는 `@Formula` 를 생성했다
  - Mapper 는 `@formula` 필드를 DTO -> Entity 방향으로 옮기지 않는다 (DB 가 조회할 때 계산하는 값)
  - Entity 의 `@formula` 필드에는 `@Formula` 만 붙고 `@Column`·`@Comment` 는 붙지 않는다. 이전에는 길이·`!`·`@unique`·
    `@updatable(false)`·`@text` 가 있으면 `@Column` 도 붙어 실제 컬럼이 생겼다 - 그 컬럼은 DB 에서 직접 지운다
    (`ddl-auto: update` 는 지우지 않음). `schema.yml` 에는 `@Formula` 로 표시 (`elasticore-annotation-guide.md` 의 `@formula`)
- 검색 DTO: 같은 대상을 가리키는 `@ManyToOne` 관계가 둘이면 둘 다 검색 조건으로 펼친다 (2.x 는 첫 번째만).
  `elcoreRulesCheck` 가 ACCEPTED 로 보고하는 2건이 이것이다
- Mapper: 변환이 필요한 `@forceupdate` 필드의 메소드 호출을 닫는다 (2.x 는 컴파일되지 않는 코드를 생성했다)
- 생성 서비스 테스트: `LocalDateTime`·`BigDecimal` 처럼 이름을 패키지까지 쓰는 타입의 필드에 `null` 대신
  그 타입의 값을 넣는다

### Bug Fixes (운영체제와 무관한 순서)

#### Linux·macOS 에서 도메인과 모델 파일을 읽는 순서가 Windows 와 달랐던 문제
- 폴더 목록을 파일 시스템이 주는 순서대로 읽어서, Windows(이름 순)와 Linux·macOS(파일 시스템마다 다름)의 읽는 순서가
  달랐다. 그 순서를 따르는 모델, DSL 경고의 순서, 여러 엔티티를 모아 만드는 생성물이 운영체제에 따라 달라질 수 있었다
- 이제 어디서나 Windows 와 같은 이름 순(대소문자 구분 없음)으로 읽는다. Windows 에서 만든 결과는 그대로
- 관련 파일: `FileOrder.java`(신규), `ModelExtractor.java`, `FileBasedModelLoader.java`, `YamlFileMainModelLoader.java`,
  `JavaSrcFileMainModelLoader.java`, `BlueprintLoader.java`, `JPACodePublisher.java`, `RulesGenRunner.java`, `FileOrderTest.java`

### Bug Fixes (보안 - 런타임 라이브러리가 2.x 와 다르게 동작하는 곳)

#### `elasticore-springboot3`·`elasticore-springboot4`
- `ignoreSSL` 을 켠 포트의 multipart 호출이 인증서·호스트 이름 검사를 JVM 전체에서 껐다. `HttpsURLConnection` 의
  기본값을 바꿔서, 그 뒤로는 다른 포트와 다른 라이브러리의 HTTPS 연결도 아무 인증서나 받아들였다. 이제 그 호출의
  연결에만 적용된다. 2.x 에서 이 부작용 덕에 통하던 HTTPS 연결이 있었다면 이제 인증서 오류가 난다 - 그 서버의
  인증서를 JVM 신뢰 저장소에 넣거나, 그 포트에도 `ignoreSSL` 을 켠다
- multipart 호출이 요청 헤더를 Authorization 까지 INFO 로그에 남겼다. 이제 DEBUG 로 남기고, 인증 정보를 담을 수 있는
  헤더(이름에 auth·cookie·session·password·token·secret·signature·key 가 든 헤더 - `Authorization`, `api_key`,
  `Ocp-Apim-Subscription-Key` 등)의 값과 URL 의 쿼리 문자열은 `****` 로 가린다
- `string(n) @id @genid` 키의 값(`IdUtils.newId`)을 `SecureRandom` 으로 만든다. 이전에는 호출마다 시각으로 시드한
  `java.util.Random` - 예측할 수 있는 난수 - 이었다. 형식(접두어 3자 + 영숫자)은 그대로
- 관련 파일: `HttpApiClient.java`, `IdUtils.java`, `MultipartTlsTest.java`(신규)

### Improvements (공개 라이브러리가 선언하는 의존성 버전)

#### POM 에 고정돼 있던 오래된 버전
- `elasticore-springboot3` 은 Spring Boot 3.5.16 의 버전 관리를 따른다 (`elasticore-springboot4` 가 4.0.8 을 따르듯).
  POM 에 고정돼 있던 Spring Framework 6.1.2, Netty 4.1.94, Jackson 2.13·2.15, Reactor 3.5.6, commons-lang3 3.12 를
  뺐다. Maven 과, Gradle 의 `io.spring.dependency-management` 를 쓰는 애플리케이션에서는 애플리케이션의 Spring Boot
  버전이 그대로 쓰인다. Gradle 의 `platform()` 으로 버전을 맞추는 애플리케이션에서는 높은 쪽이 이기므로 3.5.16 이
  하한이 된다
- `elasticore-base`: Jackson 2.13.0 -> 2.22.3 (SnakeYAML 1.x -> 2.x). Java 8 에서 그대로 돌고, 생성 결과도 같다
  (`elcoreRulesCheck` 150 MATCH / 0 DIFF, `elcoreTreeCheck` 0 differ). `platform()` 으로 버전을 맞추는 Spring Boot 4
  애플리케이션에서는 Jackson 2 가 Boot 의 2.21 대신 2.22.3 이 된다
- 관련 파일: `elasticore-springboot3/build.gradle`, `elasticore-base/build.gradle`

### Documentation (설치)

#### Maven Central 에서 받는 설치 안내
- `README.md`·`README.ko.md` 와 가이드(`elasticore-rule-guide.md`, `elasticore-rule-dev-guide.md`,
  `elasticore-ddd-customize-guide.md`, `elasticore-migration-guide.md`)가 로컬 설치 대신 Maven Central 과 이 버전을
  안내한다. README 가 안내하는 버전은 `gradle.properties` 의 `elasticoreReleasedVersion` - 마지막 릴리스 - 이고,
  `ReadmeTest` 가 둘을 맞춰 본다. 다음 릴리스를 개발하는 동안에도 README 는 받을 수 있는 버전을 가리킨다
- `elasticore-annotation-guide.md`: 공개된 적 없는 버전 번호(3.0.261005)를 "2.x"·"3.0" 으로 바꿨다
- 릴리스 절차: `RELEASING.md`
