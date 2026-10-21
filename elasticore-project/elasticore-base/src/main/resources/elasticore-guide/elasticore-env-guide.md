# ElastiCORE 환경 설정 가이드 (env.yml)

`env.yml`은 ElastiCORE 도메인별 환경 설정 파일로, 코드 생성 모드, 프레임워크, 네임스페이스, 어노테이션 등을 정의한다.
각 도메인 디렉토리(`src/main/resources/blueprint/{domain}/`)에 위치하며, DSL 기반 모델링 전에 환경 정보를 기술한다.

---

## 1. 기본 구조

```yaml
elasticore:
  enable: true
  config:
    domainName: myDomain        # 도메인명 (생성될 패키지와 파일명에 사용)
    framework: springboot3      # 프레임워크 (springboot3)
    mode: jpa                   # 생성 모드: jpa, mongo, uml, dsl (쉼표로 복수 지정 가능)
    j2ee: 17                    # Jakarta EE 버전 (17 이상)
  namespace:
    entity: com.example.domain.entity
    dto: com.example.domain.dto
    repository: com.example.domain.repository
    service: com.example.domain.service
    control: com.example.domain.controller
```

**필수 설정 항목:**
- `domainName`: 도메인 식별자. 생성 코드의 패키지명과 API 경로에 사용
- `framework`: 대상 프레임워크 (`springboot3`)
- `mode`: 코드 생성 모드
- `j2ee`: Jakarta EE 버전 (`17` 이상, javax는 deprecated)

---

## 2. 생성 모드 상세

### 2.1 JPA 모드 (기본)

```yaml
config:
  mode: jpa
```

**생성 항목:** Entity, DTO, SearchDTO, Repository, Service, Controller, Mapper

**table 설정:**
```yaml
config:
  table:
    prefix: "tbl_"        # 테이블명 접두사 (@Table(name="tbl_xxx"))
    autoJoin: true         # 자동 JOIN FETCH (기본값: false)
  tableSchema: "schema_a"   # DB 스키마 지정 (@Table(schema="schema_a")) - Since: 2.1.260409
```

- `prefix`: 엔티티의 `@Table` 어노테이션에 적용되는 테이블명 접두사
- `tableSchema`: 도메인 내 모든 엔티티에 `@Table(schema="...")` 적용. 엔티티별 `@schema` 어노테이션이 우선
- `autoJoin`:
  - `true`: 모든 엔티티 참조 필드에 대해 `findById` 시 JOIN FETCH 자동 적용 (N+1 방지)
  - `false` (기본값): `@fetchjoin` 어노테이션이 명시된 필드에만 JOIN FETCH 적용

**`@fetchjoin` vs `autoJoin` 관계:**
- `@fetchjoin`은 필드 단위로 개별 지정하는 방식
- `autoJoin: true`는 도메인 전체에 일괄 적용하는 방식
- 두 방식은 동시에 사용 가능하며, `autoJoin: true`일 때 `@fetchjoin` 없는 필드도 모두 JOIN FETCH 대상이 됨

**JPA 추가 설정:**
```yaml
config:
  jpa:
    useQueryMethod: true   # Repository에 커스텀 쿼리 메서드 생성
```

### 2.2 Multi-Mode

```yaml
config:
  mode: jpa,uml    # JPA 코드 + Mermaid UML 다이어그램 동시 생성
```

쉼표로 구분하여 여러 모드를 동시 실행한다. 각 모드의 산출물이 모두 생성된다.

### 2.3 MongoDB 모드

```yaml
config:
  mode: mongo
```

**생성 항목:** MongoDB Document 엔티티, Spring Data MongoDB Repository, 서비스 클래스

### 2.4 UML 모드

```yaml
config:
  mode: uml
```

**생성 항목:** Mermaid 다이어그램, 엔티티 관계도 HTML 문서

### 2.5 DSL 모드

```yaml
config:
  mode: dsl
```

**생성 항목:** DSL 전용 코드 산출물 (JPA 없이 모델 정의만 사용하는 경우)

---

## 3. supportAI 설정

```yaml
config:
  supportAI: "true"    # AI 지원 서비스 템플릿 사용 (기본값: "false")
```

- `"true"` 설정 시 `service_extend_ai.tmpl` 기반의 확장 서비스 클래스를 생성
- `"false"` (기본값) 설정 시 `service_extend.tmpl` 사용
- AI 기능 통합을 위한 확장 포인트를 제공하며, 서비스 레이어에 AI 관련 메서드 템플릿이 추가됨
- 생성되는 `XxxService`의 메서드에 Spring AI `@Tool`/`@ToolParam`이 붙습니다. Spring AI 1.x와 2.0 모두 같은 어노테이션입니다.
- `@readonly` 엔티티(또는 `@restExclude`로 뺀 메서드)는 조회 도구만 생깁니다 (3.0 규칙엔진). 쓰기 메서드가 없는 서비스에 쓰기 도구를 만들지 않습니다.
- Spring Boot 4 + Spring AI 2.0은 아래 3-1 참고.

---

## 3-1. Spring Boot 4 / Spring AI 2.0 (ElastiCORE 3.0)

ElastiCORE **3.0**은 Spring Boot 3과 4 둘 다 지원합니다. 2.2는 Spring Boot 3만 지원합니다.
확인한 버전: Spring Boot 4.0.8, Spring AI 2.0.1, Hibernate 7.2, Spring Data JPA 4.0, Jackson 3.1.

### 프로젝트에서 바꿀 것

```gradle
plugins {
    id 'org.springframework.boot' version '4.0.8'          // 4.x
    id 'io.spring.dependency-management' version '1.1.7'
    id 'io.elasticore' version '3.0.x'
}

dependencies {
    implementation 'io.elasticore.springboot4:elasticore-springboot4:3.0.x'   // springboot3 대신
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'      // Boot 4: starter-web 의 새 이름
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3'  // Boot 4 용 3.x

    // supportAI 를 쓸 때
    implementation platform('org.springframework.ai:spring-ai-bom:2.0.1')
    implementation 'org.springframework.ai:spring-ai-model'

    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'  // MockMvc 테스트
}
```

- Gradle **8.14 이상** (Spring Boot 4 Gradle 플러그인의 요구)
- Kotlin은 **2.2 이상**, Jackson Kotlin 모듈은 `tools.jackson.module:jackson-module-kotlin`
- `elasticore-springboot4`의 Java 패키지는 `io.elasticore.springboot3` 그대로입니다. 생성된 코드의 import가 바뀌지 않으므로, Boot 3 → 4 전환은 의존성 한 줄입니다.

### ElastiCORE가 알아서 하는 것

플러그인이 빌드에 적용된 Spring Boot Gradle 플러그인의 버전을 읽습니다(루트에 `apply false`로만 선언해도 됨). Boot 4이면 생성 코드가 바뀝니다.

| 생성물 | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| enum (`@json`) | Jackson 2 `CustomSerializer`/`CustomDeserializer` | `@JsonValue` + `@JsonCreator` (Jackson 2·3 공통 `jackson-annotations`) |
| outbox recorder (DDD) | Jackson 2 `ObjectMapper` | Jackson 3 `JsonMapper` (앱이 등록한 것) |
| Kotlin (`language = 'kotlin'`) | 그대로 | `Specification` 타입 파라미터에 `T : Any`, `Page.map`에 `!!` (Spring Data 4의 JSpecify 선언) |
| HTTP 포트 클라이언트 (런타임) | Jackson 2 코덱 | Jackson 3 코덱 (`elasticore-springboot4`) |
| 요청 본문에서 거절된 필드 (`RequestShapeAdvice`, API 오류의 `typeMismatch`) | Jackson 2 의 경로 | Jackson 3 의 경로 (`elasticore-springboot4`; 웹 계층을 Jackson 2 로 되돌린 앱은 Jackson 2 의 경로) - 응답은 같음 |
| HTTP 포트 (선택: `httpPort: spring`) | ElastiCORE 런타임 | Spring이 구현하는 `@HttpExchange` 인터페이스 + `@ImportHttpServices` (`elasticore-dsl-port-guide.md` 2.7) |
| 엔티티, 리포지터리, 서비스, 컨트롤러 | 그대로 | 그대로 (Hibernate 7·Spring Data 4에서 컴파일·실행 확인) |

**왜 enum이 바뀌어야 하나**: Spring Boot 4의 웹 계층은 Jackson 3으로 JSON을 씁니다. Jackson 3은 Jackson 2의 `@JsonSerialize`/`@JsonDeserialize`를 보지 않으므로, 예전 enum은 **오류 없이** 코드(`"DRF"`) 대신 이름(`"DRAFT"`)으로 나갑니다. 컴파일도 되고 기동도 되어서 API 응답이 바뀐 것을 테스트로만 알 수 있습니다.

생성할 때 로그 첫머리에 결정된 버전이 나옵니다. **Boot 4 프로젝트인데 `Spring Boot: 3`이 보이면** enum이 이름으로 나가게 되니, 아래 중 하나로 직접 적습니다:

```
[ElastiCORE] Spring Boot: 4.0.8 (build) - enums and the outbox are written for Jackson 3
```

| 어디에 | 쓰는 법 | 언제 |
|---|---|---|
| `build.gradle` | `elasticore { springBoot = '4' }` | 플러그인을 쓰는데 감지가 안 될 때 (Boot 플러그인 없이 BOM만 쓰는 경우 등) |
| `env.yml` | `elasticore: config: springBoot: 4` | 플러그인 없이 생성할 때 - 애노테이션 프로세서(`./gradlew build`), 직접 만든 JavaExec 태스크 |
| JavaExec | `systemProperty 'elasticore.springBoot', '4'` | 직접 만든 생성 태스크 |

빌드 쪽 값(첫 줄, 셋째 줄)이 `env.yml`보다 우선합니다. 숫자로 읽을 수 없는 값(`boot4`)은 경고를 내고 3으로 생성합니다.

### 직접 고칠 것 (프로젝트 코드)

ElastiCORE가 만들지 않은 코드는 Spring Boot 4 마이그레이션 가이드를 따릅니다. 예제 프로젝트에서 실제로 고친 것:

| 증상 | 수정 |
|---|---|
| `package org.springframework.boot.test.autoconfigure.web.servlet does not exist` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` + `spring-boot-starter-webmvc-test` |
| `No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'` | `tools.jackson.databind.ObjectMapper` (Boot 4는 Jackson 3 매퍼를 등록) |
| `JsonNode.asText()` 경고 | Jackson 3: `asString()` |

이미 생성된 개발자 소유 파일(`XxxService`, 컨트롤러)은 다시 생성되지 않습니다. `supportAI`를 나중에 켰다면 `@Tool`이 없는 서비스는 직접 고치거나, 손대지 않은 파일이면 지우고 다시 생성합니다.

### Spring AI 2.0에서 도구 쓰기

생성된 서비스의 `@Tool` 메서드를 등록합니다:

```java
@Bean
ToolCallbackProvider erpTools(PartnerService partners, ItemService items) {
    return MethodToolCallbackProvider.builder().toolObjects(partners, items).build();
}
```

도구의 인자는 Jackson 3로 읽으므로, 위의 enum 변경이 있어야 도구 호출과 REST API가 같은 값(`"DRF"`)을 씁니다.

---

## 4. 어노테이션 설정

```yaml
config:
  annotations:
    # ID 생성기
    genid: "genid(io.elasticore.springboot3.util.IdUtils.newId)"

    # Entity 코드 생성 시 적용 (@entity: 접두사)
    stringarray: "@entity:Convert(converter = io.elasticore.springboot3.util.StringArrayConverter.class)"
    integerarray: "@entity:Convert(converter = io.elasticore.springboot3.util.IntegerArrayConverter.class)"

    # DTO 코드 생성 시 적용 (@dto: 접두사)
    email: "@dto:Email(message = 'Invalid email format')"
    phone: "@dto:Pattern(regexp = '^[0-9-]+$', message = 'Invalid phone format')"
```

**어노테이션 적용 범위 접두사:**
- `@entity:` - Entity 클래스 생성 시에만 적용
- `@dto:` - DTO 클래스 생성 시에만 적용
- 접두사 없음 - 모든 생성 코드에 적용

**모델 파일에서의 사용:**
```yaml
entity:
  User:
    fields:
      id: string @genid                  # ID 자동 생성
      tags: string[] @env:stringarray     # StringArrayConverter 적용
      email: string @env:email            # Email 검증 어노테이션
```

`@env:` 접두사로 env.yml에 정의된 어노테이션을 참조한다. 빌드 시 실제 어노테이션 코드로 치환된다.

**genid 설정:**
- `genid` 키는 특별 처리되며, 엔티티 필드에 `@genid` 사용 시 지정된 ID 생성기 메서드가 `@PrePersist`에 등록됨
- 형식: `genid(패키지.클래스.메서드)`

---

## 5. jsonview 설정

```yaml
config:
  jsonview: auto  # false(기본) | true | auto
```

- **용도**: Jackson @JsonView 기반 DTO 필드 가시성 제어
- **기본값**: `false`
- **사용 가능 값**: `false`, `true`, `auto`
- **설명**:
  - `false`: 비활성화 (기본). 개별 entity/dto에 `@jsonview` 지정 시에만 활성화
  - `true`: 수동 모드. DTO 필드에 `@view` 어노테이션 필요
  - `auto`: 자동 모드. 기존 어노테이션(@id, @genid, @jpa:CreatedDate 등)에서 가시성 자동 추론

**사용 예시**:
```yaml
elasticore:
  config:
    jsonview: auto
```

상세 설명은 `elasticore-annotation-guide.md` 4.6 섹션 참조.

---

## 6. 템플릿/테스트 코드 설정

### 6.1 커스텀 템플릿

```yaml
config:
  template:
    entity: "custom/entity.tmpl"
    dto: "custom/dto.tmpl"
    service: "custom/service.tmpl"
    repository: "custom/repository.tmpl"
    port: "custom/port.tmpl"
    mapper: "custom/mapper.tmpl"
    enum: "custom/enum.tmpl"
    serviceExt: "custom/service_extend.tmpl"
    serviceTest: "custom/service_test.tmpl"
    templateHelper: "custom/repository_helper.tmpl"
```

각 생성 대상별로 커스텀 `.tmpl` 파일 경로를 지정할 수 있다. 미지정 시 기본 내장 템플릿을 사용한다.

### 6.1.1 트랜잭션 Outbox

```yaml
config:
  outbox: true          # 도메인 이벤트를 Outbox 로 내보냄 (기본값: false)
```

- DDD/헥사고날 번들에서, `@event` 를 선언한 루트가 있을 때 도메인마다
  `<Domain>OutboxMessage` / `OutboxRepository` / `OutboxRecorder` / `OutboxRelay` 생성
- 이벤트가 변경과 **같은 트랜잭션**에 기록되고, 커밋 후 애플리케이션의 `OutboxPublisher` 빈으로 전송
- 전송 주기: `elasticore.outbox.relay-delay-ms` (기본 5000, 앱에서 `@EnableScheduling` 필요)
- 사용 사례: 마이크로서비스 간 이벤트 연동(Kafka/RabbitMQ), 외부 시스템 웹훅, 감사 로그 적재

### 6.1.2 도메인에서 JPA 걷어내기 (`mapping: xml`)

```yaml
config:
  mapping: xml          # JPA 매핑을 META-INF/orm.xml 로 (기본값: 어노테이션)
```

- 헥사고날의 "안쪽은 기술을 모른다" 를 지킵니다: 도메인 클래스(엔티티·Base·행위 클래스·임베더블·
  불변식 리스너)에서 `jakarta.persistence` 와 Hibernate 어노테이션을 **모두 걷어내고**, 같은 매핑을
  `src/main/resources/META-INF/orm.xml` 에 씁니다 (JPA 가 자동으로 읽는 파일 - 설정 불필요).
- 변경 감지, `@Version`, 지연 로딩, 생명주기 콜백은 그대로입니다 (JPA 표준 XML).
- 함께 옮겨지는 것: enum 의 DB 변환기 -> `persistence` 네임스페이스의 `XxxConverter`, 조회 메타 클래스
  `Q` -> `persistence` 네임스페이스. 멤버 삭제 검사의 프록시 풀기는 런타임 `Proxies.real()` 로.
- 개발자 소유 행위 클래스(`SalesOrder`)의 `@Entity`/`@Table`/`@DynamicUpdate` 도 걷어냅니다 (나머지 코드는 그대로).
- Hibernate 전용 기능: `@DynamicUpdate` 는 XML 형태가 없어 빠집니다(모든 컬럼 UPDATE). `@Formula`, Envers
  (`@Audited`) 등은 클래스에 남기고 경고합니다.
- **남는 의존**(메타데이터 수준): Spring Data 의 감사 어노테이션(`@CreatedDate` 등)과 도메인 이벤트 발행
  (`@DomainEvents`), enum 의 JSON 코드 변환(Jackson). 도메인 패키지에 `jakarta.persistence`, `org.hibernate` 는 없습니다.
- **Kotlin**: 클래스를 열어 주는 all-open/no-arg 플러그인은 `@Entity` 를 보고 동작하므로, 이 모드에서는 도메인
  클래스에 대신 붙는 `@io.elasticore.springboot3.entity.PersistentModel` 을 등록해야 합니다 (안 하면 클래스가
  final 이 되어 지연 로딩 프록시가 동작하지 않습니다).
  ```groovy
  allOpen { annotation 'io.elasticore.springboot3.entity.PersistentModel' }
  noArg   { annotation 'io.elasticore.springboot3.entity.PersistentModel' }
  ```
- Envers(`@Audited`)를 쓰는 도메인은 그 어노테이션이 남으므로, 생성되는 아키텍처 테스트가 `org.hibernate.envers` 는 허용합니다.
- 사용자가 만든 `META-INF/orm.xml` 이 있으면 덮어쓰지 않고 `META-INF/elasticore-orm.xml` 에 쓰고
  `spring.jpa.mapping-resources` 설정을 안내합니다.
- 사용 사례: 도메인 모델 단위 테스트를 JPA 없이, 헥사고날 규칙을 아키텍처 테스트로 강제(`archTest`), 규제 산업의 계층 분리 감사

### 6.1.3 아키텍처 테스트 (`archTest: true`)

```yaml
config:
  archTest: true        # 헥사고날 경계를 ArchUnit 테스트로 (기본값: false)
```

- `src/test/java/<기본 패키지>/<Domain>ArchitectureTest.java` (Kotlin 은 `src/test/kotlin`) 를 **처음 한 번** 생성합니다.
  네임스페이스로 계층을 읽습니다: 도메인(`entity`, `enumeration`, `event`, `domainService`, `domainRepository`),
  애플리케이션(`service`, `dto`, `inboundPort`, `queryPort`), 어댑터(`control`), 영속성(`persistence`, `repository`).
- 규칙: 도메인은 바깥 계층에 의존하지 않음 / 애플리케이션은 어댑터·영속성 클래스에 의존하지 않음(포트로만) /
  도메인은 웹을 모름 / (`mapping: xml` 이면) 도메인은 `jakarta.persistence`·`org.hibernate` 를 모름.
- 테스트 의존성이 필요합니다: `testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'`
- 사용 사례: 코드 리뷰 없이도 계층 위반을 빌드에서 차단, 팀 합류자에게 경계를 코드로 전달

### 6.1.4 바운디드 컨텍스트 (`context:`, 3.0)

blueprint 도메인 하나가 바운디드 컨텍스트 하나입니다. `context:` 를 적은 도메인은 **다른 컨텍스트에서 무엇을 가져다
쓰는지(`uses`)**, **다른 컨텍스트가 무엇을 써도 되는지(`publishes`)** 를 선언하고, 빌드가 그 경계를 지킵니다.

```yaml
# blueprint/catalog/env.yml
config:
  context:
    publishes: [ProductDTO, Unit]   # 다른 컨텍스트가 필드 타입으로 써도 되는 것 (DTO, enum, @embeddable)

# blueprint/shop/env.yml
config:
  context:
    uses: catalog                   # 이 컨텍스트가 기대는 상위(upstream) 컨텍스트들
    publishes: [OrderPlaced]        # 이벤트도 내보낼 수 있음 (루트 이름 + 이벤트 이름)
```

| 상황 | 결과 |
|---|---|
| `shop` 의 필드가 `catalog` 의 `ProductDTO` 를 씀 (uses·publishes 둘 다 있음) | 통과 |
| `shop` 의 필드가 `catalog` 의 엔티티 `Product` 를 씀 (`product: Product @ManyToOne`) | **빌드 실패**: 다른 컨텍스트의 엔티티는 필드 타입이 될 수 없음 — `productId: long` 으로 식별자만 들거나 `catalog` 가 내보내는 DTO 를 씀 |
| `uses` 에 없는 컨텍스트의 타입을 씀 | **빌드 실패** (`... does not declare it (config.context.uses: catalog)`) |
| 상대가 `publishes` 를 선언했는데 거기 없는 타입을 씀 (`publishes: []` 는 아무것도 내보내지 않음) | **빌드 실패** (`... which catalog does not publish`) |
| 상대가 `context:` 는 있지만 `publishes` 를 선언하지 않음 | `uses` 에만 있으면 통과 |
| 다른 컨텍스트의 프로세스 매니저가 `on: [shop:OrderPlaced]` 로 이벤트를 받음 | 타입과 같은 검사: 받는 쪽 `uses` 에 `shop`, `shop` 이 `publishes` 를 선언했다면 거기 `OrderPlaced` |
| `publishes` 에 엔티티나 없는 이름을 적음 | DSL 경고 (그 컨텍스트의 이벤트 이름은 정상) |
| 어느 쪽도 `context:` 가 없음 | 예전과 같음 (이름은 모든 도메인에서 찾고, DDD 번들은 경고만) |

- 검사 대상: 엔티티·DTO 필드의 타입(`List<X>` 의 X 포함, `catalog:Product` 처럼 도메인 이름을 붙인 것 포함), DTO 의
  `@template(...)`, 엔티티의 `@extend(...)`, 프로세스 매니저의 `on: [shop:OrderPlaced]` (`elasticore-dsl-guide.md` 의
  "프로세스 매니저"). 두 도메인에 같은 이름이 있으면 어느 쪽인지 정할 수 없어 검사하지 않습니다
  (로더가 따로 경고). 3.0 규칙 엔진(`elcore3`)이 생성 전에 검사하고, 실패하면 모든 문제를 한 번에 말한 뒤 빌드를 멈춥니다.
  2.x 생성기(`elcore`)는 `context:` 를 읽지 않습니다.
- **컨텍스트 지도**: 어느 도메인이든 `context:` 를 적으면 `blueprint/context-map.md` 가 생깁니다 (Mermaid 그림 —
  GitHub 에서 그대로 보임). 화살표는 쓰이는 쪽(upstream)에서 쓰는 쪽으로, 건너가는 타입·이벤트 이름이 붙습니다.
  `elasticore-template-ddd` 의 `catalog`·`shop`·`billing` 이 예시입니다 (`billing` 이 `shop:OrderPlaced` 를 받음).
- 사용 사례:
  - 모놀리식 안에서 도메인끼리 엔티티를 직접 엮어 경계가 무너지는 것을 빌드에서 막기 (나중에 서비스로 떼어낼 때 비용 감소)
  - AI 가 DSL 을 고칠 때 다른 컨텍스트의 엔티티를 끌어다 쓰는 실수를 즉시 거절
  - 컨텍스트끼리는 이벤트로만 이어지게 하기 (주문이 들어오면 청구 컨텍스트가 영수증 발행) — 무엇을 내보내는지가
    `publishes` 한 줄에 남음
  - 팀/조직 경계와 컨텍스트를 맞추고, `context-map.md` 로 의존 관계를 문서화

### 6.2 테스트 코드 생성

```yaml
config:
  testCode: "true"      # 테스트 코드 자동 생성 (기본값: "false")
```

- `"true"` 설정 시 JUnit 5 + Mockito 기반 서비스 단위 테스트 자동 생성
- 복합 키(Composite Key) 엔티티도 자동으로 올바르게 처리
- 모든 필드 타입에 대한 샘플 데이터 자동 생성
- CRUD 및 검색(List/Page) 메서드에 대한 완전한 테스트 커버리지 제공

생성된 테스트 파일은 `src/test/java/` 하위에 서비스 패키지 경로로 배치된다.

---

## 6-2. 참조 컬럼 이름·관리자 역할 (3.0)

```yaml
config:
  referenceColumn: short   # 다른 애그리거트 참조 컬럼: customer_id (기본: 2.x 식 customer_customer_id)
  adminRole: ADMIN         # @access 의 admin 수준이 확인하는 역할 이름 (기본 ADMIN)
```

- `referenceColumn: short` - DDD 번들의 식별자 참조(`customerId`)가 쓰는 컬럼 이름을 필드 이름 그대로
  (`customer_id`) 만듭니다. **새 프로젝트용** 입니다: 이미 있는 DB 의 컬럼 이름이 바뀌므로 2.x 에서
  옮겨오는 프로젝트는 그대로 두거나 마이그레이션과 함께 켭니다.
- `adminRole` - `SessionCurrentUser.signIn(request, id, "ADMIN")` 처럼 로그인 때 넣은 역할 중 이 이름이
  있으면 관리자입니다. 엔티티 줄의 `@signin` 으로 생성되는 `signin` 은 그 엔티티의 `role` 필드(또는 `@role` 필드)가
  `@access(admin)` 인 enum·문자열일 때 그 값을 넣습니다.

## 7. apiPath 설정

```yaml
config:
  apiPath: "/api/v1"    # Controller의 기본 API 경로 (기본값: "/api")
```

생성되는 Controller의 `@RequestMapping` 경로에 적용된다:
```java
@RequestMapping("/api/v1/{domainName}/{entityName}")
```

**활용 사례:**
```yaml
# API 버전 관리
apiPath: "/api/v1"
# 결과: /api/v1/user/profile

# 도메인 분리
apiPath: "/api/admin"
# 결과: /api/admin/user/role

# 마이크로서비스 경로
apiPath: "/services/order-api"
# 결과: /services/order-api/order/create
```

**주의사항:**
- 미설정 시 기본값 `/api` 적용
- 슬래시(`/`)로 시작해야 함
- 이미 생성된 Controller에는 영향 없음 (재생성 시 적용)

---

## 7-1. publisher.rules 설정 (3.0)

같은 모델을 어떻게 읽을지 정합니다. **적지 않으면 2.x 와 동일하게 동작합니다.**

```yaml
elasticore:
  config:
    publisher:
      rules: [ddd-springboot]
```

### 선택 가능한 번들

| 번들 | 내용 |
|---|---|
| `jpa-springboot` | 기존 레이어드 구조 (적지 않은 것과 동일) |
| `ddd-springboot` | 집합체 경계, 불변식, 도메인 이벤트, 값 객체 |
| `hexagonal-springboot` | 위 + 인바운드 포트, 헥사고날 패키지 배치 |
| `jpa-springboot-kt` / `ddd-springboot-kt` / `hexagonal-springboot-kt` | 위 셋의 Kotlin 판 |
| `ts-model` | 브라우저용 DTO, enum, 타입 있는 REST 클라이언트 |

번들은 개별 룰셋의 이름 목록이므로 직접 조합해도 됩니다:

```yaml
      rules: [jpa-springboot-kt, ddd-aggregate, ddd-reference, ddd-value-object-kt]
```

### 우선순위 (4단)

```
1. 도메인    이 env.yml 의 publisher.rules        <- 이 도메인만 예외일 때
2. 프로젝트  -Delasticore.publisher.rules=...      <- 보통 여기서 한 번
3. 내장 기본 jpa-springboot                        <- 아무것도 안 적으면 여기
```

도메인은 프로젝트 기본값에서 벗어날 때만 선언합니다. 도메인이 20개여도
`env.yml` 20개를 고칠 필요가 없습니다.

`publisher.suppress` 는 다르게 동작합니다 - 누적됩니다. 프로젝트 수준의
억제 목록에 도메인 수준의 것이 더해집니다.

**팀의 규칙을 더할 때**는 위 목록을 대체하지 않고 **더하는** 빌드 설정을 씁니다.
위에서 어떤 번들이 골라졌든 그 뒤에 붙습니다 (TypeScript 생성에는 붙지 않음):

```gradle
elasticore {
    architecture = 'hexagonal'
    rules = ['com.acme.rules.AcmeDddRules']   // -Delasticore.publisher.rules.add=...
}
dependencies { elasticore project(':acme-rules') }
```

지금 도는 규칙과 순서는 `./gradlew elcore3 -PlistRules` 로 봅니다. 자세한 것은 `elasticore-ddd-customize-guide.md`.

### 주의: 이 설정은 규칙엔진이 읽습니다

**`publisher.rules` 는 `@ElastiCore` 애노테이션 프로세서가 읽지 않습니다.**
별도 진입점인 규칙엔진이 읽으므로, 번들을 적어두기만 하면 파일은 설정된 것처럼
보이고 빌드는 그것을 무시하며 아무 경고도 나오지 않습니다.

실행 방법은 `elasticore-rule-guide.md` 의 "내 프로젝트에서 규칙엔진
실행하기" 를 보십시오.

### 언어는 별도 스위치입니다

번들 이름은 *어떤 규칙을 쓸지*만 정합니다. *어느 트리에 어떤 확장자로 쓸지*는
러너의 시스템 프로퍼티가 정합니다:

| 언어 | 스위치 |
|---|---|
| Java (호환) | 없음 |
| Java (정리 모드) | `elasticore.rules.v3` |
| Kotlin | `elasticore.rules.kt` |
| TypeScript | `elasticore.rules.ts` |

`rules: [jpa-springboot-kt]` 만 적고 스위치를 켜지 않으면 `.kt` 파일이 나오지
않습니다. 도메인 초기화 마법사는 이 둘을 함께 정해주므로 어긋나지 않습니다.

TypeScript 클라이언트(`src/main/ts`)는 서버 코드와 함께 쓰는 두 번째 출력이라 Gradle 플러그인의 `elcore3ts` 태스크가
씁니다. `build` 는 그 태스크를 부르지 않으므로, 빌드마다 함께 쓰려면 `elasticore { typescriptClient = true }` 를 적습니다
(`elasticore-rule-guide.md` "TypeScript 클라이언트"). 적지 않았는데 생성된 클라이언트가 있으면 `elcore3` 가 경고합니다.

---

## 8. Namespace 설정

```yaml
namespace:
  entity: io.example.domain.entity           # JPA Entity 패키지
  dto: io.example.domain.dto                 # DTO 클래스 패키지
  enumeration: io.example.domain.enums       # Enum 클래스 패키지
  repository: io.example.domain.repository   # Repository 패키지
  service: io.example.domain.service         # Service 패키지
  control: io.example.domain.controller      # Controller 패키지
  port: io.example.domain.port              # Transaction Port 패키지
  proto: io.example.proto                    # Protocol Buffer 패키지
  event: io.example.domain.event             # 도메인 이벤트 패키지 (ddd-event 규칙)
  domainService: io.example.domain.service   # 도메인 서비스 패키지 (ddd-domain-service 규칙)
  inboundPort: io.example.application.port.in          # 인바운드 포트 (UseCase)
  domainRepository: io.example.domain.repository       # 리포지터리 포트 (도메인 소유)
  queryPort: io.example.application.port.out           # 조회 포트 (응용 계층 검색, 기본값 service)
  persistence: io.example.infrastructure.persistence   # 어댑터 + Spring Data
```

`event`는 `@event(...)`를 선언한 애그리거트 루트가 있을 때만 쓰입니다. 선언하지
않으면 이벤트 클래스는 `entity` 패키지에 놓입니다.

`domainService`는 `domainService:` 섹션을 선언한 도메인에서만 쓰입니다. 선언하지
않으면 `entity` 패키지에 놓입니다. **`service`(응용 서비스)와 같은 패키지로 두지
마세요** — 도메인 서비스는 도메인의 것이고 응용 서비스는 그 위 계층이라, 같은 자리에
두면 의존 방향이 무너집니다.

각 생성 클래스의 `package` 선언과 `import` 문에 사용된다.

**네이밍 권장 패턴:** `{기본패키지}.{도메인}.{타입}`
```
io.company.rental.entity
io.company.rental.dto
io.company.rental.repository
```

### 8.0 한 줄로: `namespace: base`

```yaml
namespace:
  base: com.acme.erp
```

아래 헥사고날 배치를 `com.acme.erp` 아래에 채웁니다. 함께 적은 키는 그 값이 우선합니다.

| 키 | 패키지 |
|---|---|
| `entity`, `enumeration` | `base.domain.model` |
| `event` / `domainService` / `domainRepository` | `base.domain.event` / `.domain.service` / `.domain.repository` |
| `service` / `dto` | `base.application` / `.application.dto` |
| `inboundPort` / `queryPort`, `port` | `base.application.port.in` / `.application.port.out` |
| `control` | `base.adapter.in.web` |
| `repository`, `persistence` | `base.infrastructure.persistence` |

### 8.1 아키텍처 스타일별 Namespace 프리셋

Namespace는 패키지 배치만 결정하므로, 아키텍처 스타일(레이어드/DDD/헥사고날)은
**룰 변경 없이 설정만으로** 표현할 수 있다.

#### DDD 레이어드 (elasticore-template-ddd 샘플과 동일)

```yaml
namespace:
  entity:      com.acme.shop.domain.model          # 도메인 모델 (Entity, Enum)
  enumeration: com.acme.shop.domain.model
  repository:  com.acme.shop.domain.repository     # 도메인이 요구하는 저장소
  service:     com.acme.shop.application           # 응용 서비스 (트랜잭션 경계)
  dto:         com.acme.shop.application.dto       # 응용 계층의 입출력 형태
  control:     com.acme.shop.interfaces.rest       # 인바운드 어댑터
  port:        com.acme.shop.application.port.out  # 아웃바운드 포트 (아래 참고)
  event:       com.acme.shop.domain.event          # 도메인 이벤트 (도메인의 것)
  domainService: com.acme.shop.domain.service      # 도메인 서비스 (역시 도메인의 것)
  domainRepository: com.acme.shop.domain.repository        # 도메인이 소유한 포트
  queryPort:   com.acme.shop.application.port.out          # 응용 서비스의 검색 포트
  repository:  com.acme.shop.infrastructure.persistence    # Spring Data 는 인프라에
  persistence: com.acme.shop.infrastructure.persistence    # 어댑터도 함께
```

#### 헥사고날 (ports & adapters)

```yaml
namespace:
  entity:           com.acme.shop.domain.model
  enumeration:      com.acme.shop.domain.model
  event:            com.acme.shop.domain.event
  domainService:    com.acme.shop.domain.service
  domainRepository: com.acme.shop.domain.repository            # <Root>RepositoryPort (도메인 소유)
  repository:       com.acme.shop.infrastructure.persistence   # Spring Data 는 인프라에
  persistence:      com.acme.shop.infrastructure.persistence   # 두 포트를 구현하는 어댑터
  service:          com.acme.shop.application
  dto:              com.acme.shop.application.dto
  inboundPort:      com.acme.shop.application.port.in          # <Root>UseCase
  queryPort:        com.acme.shop.application.port.out         # <Root>QueryPort (검색/페이징)
  port:             com.acme.shop.application.port.out         # driven port (port DSL)
  control:          com.acme.shop.adapter.in.web               # driving adapter
```

> 이 프리셋은 build.gradle 의 `elasticore { architecture = 'hexagonal' }` 만으로
> 동작한다 - env.yml 에 `publisher.rules` 를 적지 않아도 `hexagonal-springboot`
> 번들이 적용된다. `domainRepository` 를 빼면 도메인 포트가 Spring Data 와 같은
> 패키지에 놓여 도메인이 인프라에 의존하게 되므로 반드시 분리한다.


#### port가 application 쪽에 놓이는 이유

ElastiCORE의 `port:`로 생성되는 것은 **인터페이스뿐**이다. 구현(어댑터)은
런타임 프록시(`HttpServiceProxyFactory`, `DbmsServiceProxyFactory`)가 만들며
생성 코드가 없다. 헥사고날에서 포트 인터페이스는 application이 소유하고
어댑터가 infrastructure에 속하므로, 생성되는 유일한 파일인 인터페이스는
`infrastructure.port`가 아니라 `application.port.out`에 두는 것이 맞다.

#### 인바운드 포트는 생성된다

아웃바운드 포트가 `port:` DSL이라면, 인바운드 포트는 `hexagonal-springboot`
번들이 만든다: 노출된 애그리거트 루트마다 `<Entity>UseCase` 인터페이스가 생기고,
확장 서비스가 그것을 구현하고, 컨트롤러가 구현 클래스 대신 그것을 주입받는다.
인터페이스는 `service` namespace에 놓이므로, 위 프리셋에서는
`application` / `application.service`가 그 자리다.

DDD 룰셋(`rules: [ddd-springboot]`)과 조합하면 aggregate 경계까지 함께
강제되고, `hexagonal-springboot`는 거기에 인바운드 포트를 더한다. Kotlin은
`ddd-springboot-kt` / `hexagonal-springboot-kt`를 쓴다. 상세는
`elasticore-template-ddd/README.md`와 `elasticore-rule-guide.md` 참고.

### 8.2 Cross-Module Namespace Redirect (멀티 모듈 전용)

멀티 모듈 프로젝트에서 하나의 도메인(env.yml) 내에서 namespace별로 **서로 다른 모듈**에
코드를 생성할 수 있다. `모듈명://패키지명` 형식으로 prefix를 붙여 지정한다.

```yaml
# 예: core-domain 모듈의 blueprint/account/env.yml
namespace:
  # 현재 모듈(core-domain)에 생성
  entity:      com.acme.shop.domain.account.entity
  dto:         com.acme.shop.domain.account.dto
  repository:  com.acme.shop.domain.account.repository
  enumeration: com.acme.shop.domain.account.enums

  # app-admin 모듈에 생성 (cross-module redirect)
  service:     app-admin://com.acme.shop.domain.account.service
  control:     app-admin://com.acme.shop.domain.account.control
  port:        app-admin://com.acme.shop.domain.account.port
```

**동작 방식:**
- Prefix 없는 namespace → 현재 모듈(blueprint가 위치한 모듈)의 `src/main/java/...`에 생성
- `모듈명://` prefix 있는 namespace → 해당 모듈의 `src/main/java/...`에 생성
- 생성된 Java 파일의 `package` 선언에는 순수 패키지명만 사용 (prefix 제거됨)

**사용 조건:**
- `./gradlew elcore` 실행 시 `-Delasticore.modules=...` 시스템 프로퍼티가 설정된
  **멀티 모듈 모드**에서만 동작
- `모듈명`은 `elasticore.modules`에 전달된 경로의 마지막 디렉토리명과 일치해야 함
  (예: `C:/.../app-admin` → 모듈명 `app-admin`)
- 싱글 모듈 프로젝트에서는 prefix가 있어도 현재 모듈에 생성됨 (하위 호환)

**사용 시나리오:**
- Domain 모델(Entity/DTO/Repository)은 공통 모듈(`core-domain`)에 두고,
  비즈니스 로직(Service/Controller)은 애플리케이션 모듈(`app-admin`, `app-batch`)에 분산
- Multi-tenant SaaS에서 공통 스키마는 core에, 애플리케이션별 API는 개별 모듈에 배포

**Gradle 설정 예시 (root build.gradle):**
```groovy
task elcore(type: JavaExec) {
    classpath = project(':core-domain').sourceSets.main.runtimeClasspath
    mainClass = 'io.elasticore.base.extract.ModelExtractor'

    def modules = ['core-domain', 'app-admin', 'app-batch']
    systemProperty 'elasticore.modules', modules.collect { "$projectDir/$it" }.join(',')
    systemProperty 'elasticore.base.path', "$projectDir"

    if (project.hasProperty('mode')) {
        systemProperty 'mode', project.property('mode')
    }
    standardInput = System.in
}
```

---

## 9. Datasource 설정

```yaml
config:
  datasource:
    name: "orderEntityManagerFactory"   # EntityManagerFactory 빈 이름
```

- 다중 데이터소스 환경에서 도메인이 사용할 특정 EntityManagerFactory를 지정
- 풀네임(`orderEntityManagerFactory`) 또는 접미사 제거 약칭(`order`) 모두 사용 가능
- `DbTransactionGateway`가 빈 이름 매핑 시 `EntityManagerFactory` 접미사를 자동으로 제거하여 약칭도 인식

```yaml
# 풀네임 사용
datasource:
  name: "orderEntityManagerFactory"

# 약칭 사용 (동일하게 동작)
datasource:
  name: "order"
```

단일 데이터소스 환경에서는 설정하지 않으면 기본 `primary` EntityManagerFactory를 사용한다.

---

## 10. Resource 설정

```yaml
config:
  resource:
    - "https://example.com/shared-models.yml"
    - "https://example.com/common-entities.yml"
```

- 외부 URL에서 YAML 모델 파일을 다운로드하여 도메인 모델에 포함
- 다운로드된 파일은 `_resource/model-{N}.yml` 형식으로 로컬 캐시
- 바이트 비교를 통해 내용이 동일하면 파일 쓰기를 생략 (불필요한 I/O 방지)
- HTTP 리다이렉트(301, 302, 307, 308) 자동 처리
- 여러 도메인에서 공통 모델을 공유할 때 유용

---

## 11. 완전한 env.yml 예시

```yaml
elasticore:
  enable: true
  config:
    domainName: rental
    framework: springboot3
    mode: jpa
    j2ee: 17
    apiPath: "/api/v1"
    supportAI: "false"
    testCode: "true"

    table:
      prefix: rental_
      autoJoin: false

    jpa:
      useQueryMethod: true

    annotations:
      genid: "genid(io.example.util.IdGenerator.newId)"
      stringarray: "@entity:Convert(converter = io.example.util.StringArrayConverter.class)"
      audit: "@entity:EntityListeners(AuditingEntityListener.class)"

    datasource:
      name: "primaryEntityManagerFactory"

    resource:
      - "https://shared.example.com/common-models.yml"

  namespace:
    entity: io.example.rental.entity
    dto: io.example.rental.dto
    enumeration: io.example.rental.enums
    repository: io.example.rental.repository
    service: io.example.rental.service
    control: io.example.rental.controller
    port: io.example.rental.port
```

---

## 12. 도메인 범위 지정 코드 생성 (`-Pdomain`)

특정 도메인만 선택적으로 DSL을 갱신하고 Java 소스를 재생성할 수 있는 기능이다.
이를 통해 대규모 프로젝트에서 하나의 도메인만 수정했을 때 빠르게 재생성하거나,
원격 DSL URL 갱신을 특정 도메인만 대상으로 수행할 수 있다.

### 12.1 Gradle 설정 필수 단계

사용자의 프로젝트 `build.gradle` 파일의 `elcore` 태스크에서 `-Pdomain` 옵션을 전달해야 한다.
기존 `-Pmode` 포워딩과 동일한 패턴으로 다음 3줄을 추가한다:

```groovy
task elcore(type: JavaExec) {
    // ... 기존 설정 ...
    
    if (project.hasProperty('domain')) {
        systemProperty 'elasticore.domain', project.property('domain')
    }
}
```

이 설정이 없으면 `-Pdomain` 옵션이 무시되고 전체 도메인 처리가 실행된다.

### 12.2 사용 예시

**단일 도메인 처리:**
```bash
./gradlew elcore -Pdomain=hr
```
`hr` 도메인만 `env.yml`을 로드하고 DSL을 갱신하며, Java 소스를 재생성한다.

**다중 도메인 처리 (콤마 구분):**
```bash
./gradlew elcore -Pdomain=hr,bbs
```
`hr`과 `bbs` 도메인만 처리한다.

**Validation 모드와 조합:**
```bash
./gradlew elcore -Pmode=valid -Pdomain=hr
```
`hr` 도메인만 DSL 검증을 실행하고, 검증 오류 시 빌드 실패한다.

### 12.3 동작 세부

`-Pdomain` 은 **2단계 필터**로 동작한다. Cross-domain 참조(`@extend`, `@audited`, 공용 템플릿 등) 가 깨지지 않도록, DSL 자체는 모든 도메인을 로드한다.

#### 1단계: DSL 로딩 (모든 도메인)
- 모든 도메인의 `env.yml` 과 DSL 을 로드한다
- 전역 타입 참조 검증 (`validateGlobalTypeReferences`) 이 전체 모델을 대상으로 수행된다
- `schema.yml` 도 전체 엔티티·enum 기준으로 정상 재생성된다

#### 2단계: 필터 적용
- **퍼블리시(소스 생성)**: 지정 도메인만 Java 소스를 재생성한다. 나머지 도메인은 이전 생성물이 그대로 보존된다.
- **원격 URL 새로고침**: 지정 도메인의 `config.resource` HTTP(S) URI 만 새로 다운로드한다. 나머지 도메인은 기존 `_resource/model-N.yml` 캐시를 재사용한다 (네트워크 접근 0회).

#### 캐시가 없는 도메인
필터에서 제외된 도메인이 과거에 한 번도 실행된 적이 없어 `_resource/` 캐시가 비어 있다면, 해당 원격 리소스는 로드되지 않고 다음 경고가 출력된다:
```
[DomainFilter] Skipping remote resource (no cache): <url>
```
필요하면 `-Pdomain` 없이 full elcore 를 한 번 실행해 캐시를 초기화한 뒤 다시 필터 모드로 전환한다.

### 12.4 schema.yml 동작

필터 사용 여부와 무관하게 `schema.yml` 은 **전체 도메인 기준으로 재생성**된다. 모든 도메인 DSL 이 로드되어 `SchemaCodePublisher` 가 완전한 엔티티 집합을 집계하기 때문이다.

### 12.5 유즈케이스

**대형 프로젝트 빠른 개발 사이클:**
- 여러 도메인 중 `hr` 도메인만 수정하고 엔티티 추가
- `./gradlew elcore -Pdomain=hr`로 빠르게 생성
- 다른 도메인의 코드는 건드리지 않음

**원격 DSL 선택적 갱신:**
- 프로젝트가 여러 원격 `config.resource` URL을 참조
- 특정 도메인의 URL만 갱신하고 싶을 때 `-Pdomain` 사용
- 불필요한 네트워크 I/O와 시간 절약

**멀티 모듈 프로젝트:**
- `./gradlew elcore -Pdomain=account,order`로 특정 도메인만 처리
- 결과적으로 여러 모듈에 코드가 생성될 수 있음
- 나머지 모듈의 도메인은 완전히 스킵됨

---

## 13. Best Practices

**Namespace 네이밍 규칙:**
- 도메인별로 독립적인 패키지 구조를 유지한다
- `{기본패키지}.{도메인}.{타입}` 패턴을 일관되게 사용한다
- 도메인 간 패키지 충돌을 방지하기 위해 도메인명을 반드시 포함한다

**Multi-Mode 사용 시 주의사항:**
- `jpa,uml` 등 복수 모드 사용 시 모든 산출물이 동시에 생성됨
- 운영 환경에서는 필요한 모드만 지정하여 불필요한 파일 생성을 방지한다
- `uml` 모드는 개발 시 엔티티 관계 확인용으로 활용하고, 운영 빌드에서는 제외를 권장

**autoJoin 성능 영향:**
- `autoJoin: true`는 모든 연관 엔티티에 JOIN FETCH를 적용하므로, 연관 관계가 많은 엔티티에서는 쿼리가 복잡해질 수 있음
- 대부분의 경우 `autoJoin: false` (기본값)로 두고, 필요한 필드에만 `@fetchjoin`을 개별 적용하는 것이 성능상 유리
- 단순 조회가 많은 도메인에서는 `autoJoin: true`가 N+1 문제를 효과적으로 방지

**테스트 코드 생성:**
- `testCode: "true"` 설정 후 첫 빌드에서 테스트 파일이 생성됨
- 생성된 테스트는 기본 CRUD 검증에 유용하며, 비즈니스 로직 테스트는 별도 작성 권장
- 복합 키 엔티티도 자동으로 올바른 테스트 코드가 생성됨
