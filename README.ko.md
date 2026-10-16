<p align="right"><a href="README.md">English</a> · <b>한국어</b></p>

# ElastiCORE

[![Maven Central](https://img.shields.io/maven-central/v/io.elasticore/elasticore-base.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.elasticore/elasticore-base)

**오래 운영할 업무 시스템을 위해: 업무 규칙은 YAML 몇 줄로, Spring Boot 코드는 거기서 생성 - 매번 똑같이.**

ElastiCORE는 도메인 모델과 업무 규칙(누가 무엇을 하는지, 주문이 어떻게 진행되는지, 값이 어떻게 정해지는지)을 짧은
DSL로 받아 Spring Boot 애플리케이션을 생성합니다 - JPA 엔티티, DTO, 서비스, REST API, 권한 검사, 상태 전이, 그리고
화면용 TypeScript 클라이언트까지. 업무 규칙은 개발자나 AI 도구가 고치고 리뷰어가 읽는 한 곳에 남고, 빌드가 그 규칙을
서버의 모든 계층에, `./gradlew elcore3ts` 가 TypeScript 클라이언트에 반영합니다. 생성 방식은 **규칙(ruleset)으로
조립**되어 있어서, 언어·구조·Spring Boot 버전을 바꾸거나 우리 팀의 규칙을 끼워 넣어도 모델은 그대로입니다.

```
  blueprint/shop/model/*.yml  ──►  Java  · Kotlin  · TypeScript
       (업무 규칙 149줄)           레이어드 · DDD · 헥사고날
                                   Spring Boot 3 · Spring Boot 4
                                   + 우리 팀 규칙 (ruleset)
```

**이런 시스템을 위해 만들었습니다.** 주문, 정산, 계약, 백오피스처럼 여러 사람의 손을 거치며 몇 년씩 운영되는 업무
시스템 - 권한과 생명주기가 모든 진입점에서 지켜져야 하고, 어느 개발자가 쓰든 어느 AI 도구가 쓰든 같은 구조가 나와야
하는 곳입니다. 화면이 대부분인 애플리케이션, 영속이 관계형이나 JPA 가 아닌 시스템, 선언형 모델로 나타내기 어려운
로직에는 적합하지 않습니다 ([다른 도구와 비교](docs/comparison.ko.md)). 무엇을 검사하고 무엇이 아직 정식 출시
전인지는 [믿을 수 있는 출력](#믿을-수-있는-출력)에 있습니다.

---

## 5단계로 내 프로젝트에

**① `build.gradle`** - Spring Boot 프로젝트에 플러그인과 설정 두 줄, 그리고 생성된 코드가 쓰는 의존성

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.8'      // 3.x 도 됩니다 - 버전을 읽어 맞춰 생성
    id 'io.spring.dependency-management' version '1.1.7'
    id 'io.elasticore' version '3.0.261015-alpha1'
}

repositories {
    mavenCentral()
}

elasticore {
    architecture = 'hexagonal'    // layered | ddd | hexagonal
    language     = 'java'         // java | kotlin
}

dependencies {
    implementation 'io.elasticore:elasticore-base:3.0.261015-alpha1'
    implementation 'io.elasticore.springboot4:elasticore-springboot4:3.0.261015-alpha1'

    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    runtimeOnly 'com.h2database:h2'               // 바로 실행해 볼 메모리 DB
}
```

Spring Initializr(Spring Web, Spring Data JPA, Validation, Lombok, H2. 기본값인 Boot 4.1.1 도 됩니다)로 시작했다면 그
`build.gradle` 은 그대로 두고(함께 생성된 테스트가 그 파일의 테스트 의존성을 씁니다) 빠진 것만 더합니다: `io.elasticore` 플러그인 줄,
`elasticore` 블록, ElastiCORE 의존성 두 줄과 springdoc. Spring Boot 3 이면 (3.5.16 에서 확인): Boot 플러그인 `3.5.16`,
`…springboot4` 대신 `io.elasticore.springboot3:elasticore-springboot3`, `-webmvc` 대신 `spring-boot-starter-web`,
springdoc `3.0.3` 대신 `2.8.17`.

**② `src/main/resources/blueprint/shop/`** - 도메인 설정과 업무를 적습니다

```yaml
# env.yml
elasticore:
  enable: true
  config:
    domainName: shop
    framework: springboot
    mode: jpa
    j2ee: jakarta
    apiPath: "/api"               # REST 주소: /api/shop/...
  namespace:
    base: com.acme.shop           # 패키지 구조는 이것 하나로
```

```yaml
# model/entity.yml
entity:
  Product:
    meta: entity @root @editable @expose(20) @access(read=public) @sort(name+) -- 상품
    fields:
      productId: long @id @sequence
      name:      string(100)! @search(like)
      price:     money(10.2)! @min(0.01, 'price must be positive')
```

**③ 생성**

```bash
./gradlew elcore3
```

`src/main/java/com/acme/shop/` 아래에 이런 것이 생깁니다:

```
domain/model/Product.java               JPA 엔티티
application/ProductCoreService.java     CRUD·검색·권한 검사 (다시 생성됨)
application/ProductService.java         내 코드를 쓰는 곳 (한 번만 생성, 그 뒤로 내 파일)
application/dto/ProductDTO.java         요청·응답 (+ 400 검증)
application/dto/ProductSrchDTO.java     검색 조건 - 이름 LIKE, 페이지, 정렬 (요청이 말하지 않으면 이름순)
adapter/in/web/ProductController.java   REST API + OpenAPI 문서 (이것도 한 번만 생성)
domain/repository/...                   리포지터리 포트
infrastructure/persistence/...          그 JPA 어댑터
```

**④ 내 작업** - 모델이 말하지 않는 것

- **애플리케이션 클래스**: `com.acme.shop` 이나 그 위 패키지에 `@SpringBootApplication` 클래스를 둡니다. 그래야
  Spring 이 생성된 클래스를 찾습니다. Spring Initializr 는 group 과 artifact 로 정한 패키지에 만들어 줍니다
  (`com.acme` + `shop` 이면 `com.acme.shop`).
- **업무 로직**은 `ProductService.java` 에 씁니다. 컨트롤러처럼 한 번만 생성되는 파일이라, 고치기 전까지는 모델을
  따라 다시 생성되고, 한 번 고치면 그 뒤로는 다시 생성해도 건드리지 않습니다(로그에 `SKIP(user-owned)`).
- **로그인**: 상품을 바꾸려면 `ADMIN` 역할이 필요합니다(`@access(read=public)`: 읽기는 누구나, 나머지는 관리자).
  로그인 처리에서 `SessionCurrentUser.signIn(request, userId, "ADMIN")` 을 부르고(패키지는 Boot 4 에서도
  `io.elasticore.springboot3.security`), Spring Security 를 쓰면 대신 `CurrentUser` 빈을 등록합니다.

**⑤ 실행**

```bash
./gradlew bootRun
```

`POST /api/shop/product/search`, `GET /api/shop/product/{id}` 는 누구나 호출할 수 있고, 로그인 없이 상품을 만들면
401 "Sign in to create Product", 가격 0 은 400 "price must be positive" 로 답합니다. 이후 변경은 모델에서 시작합니다:
`./gradlew build` 와 `bootRun` 은 컴파일 전에 `elcore3` 를 먼저 돌리므로 코드가 모델보다 뒤처지지 않습니다.
처음이라면 [`elasticore-sample-petstore`](elasticore-project/elasticore-sample-petstore/)를 실행해 `/dsl-guide.html`
에서 업무마다 DSL과 생성된 코드를 나란히 보세요.

---

## DSL 읽는 법: 한 줄이 모든 계층을 정한다

문법은 여섯 가지 원칙이 전부입니다. 개발자가 손으로 쓰고, 리뷰어가 한눈에 읽습니다.

| | 원칙 | 예 |
|---|---|---|
| 1 | **한 줄이 한 필드** — 저장·검증·검색·노출을 그 줄이 정함 | `name: string(30)! @search(like)` |
| 2 | **흔한 것은 타입 옆 기호** | `(30)` 길이, `!` 필수, `(10.2)` 정밀도 |
| 3 | **엔티티 단위 규칙은 `meta:` 한 줄** | `meta: entity @root @editable @expose(20) @access(read=public)` |
| 4 | **기본값이 있다** — 다른 것만 적음 | `@expose(20)` 만으로 생성·조회·검색·페이지·REST (수정은 DDD 번들에서 `@editable` 루트·필드에만) |
| 5 | **YAML 섹션이 개념** | `entity`, `dto`, `enumeration`, `states`, `metrics`, `port` |
| 6 | **`--` 뒤는 설명** — 모델이 곧 문서 | `-- 판매 단위 (Large, Male Puppy ...)` |

pet store 의 `Item` 한 줄과, 그 줄이 만든 것 (생성된 파일에서 그대로 옮김):

```
unitCost:  money(10.2)!  @min(0.01, 'price must be positive')  @terms(price, 가격)
```

| 어디에 | 생성물 |
|---|---|
| JPA 엔티티 | `@Column(name = "unit_cost", nullable = false, precision = 10, scale = 2)` + `Money` 변환기 |
| 요청 DTO (400) | `@NotNull` `@DecimalMin(value = "0.01", message = "price must be positive")` |
| TypeScript 검증 | `else if (value.unitCost != null && Number(value.unitCost) < 0.01) errors.unitCost = "price must be positive";` |
| 의미 모델 (Ossie) | `unit_cost` 필드, `synonyms: ["price", "가격"]` |

손으로 쓰면 같은 사실을 네 곳에 적어야 하고, 하나를 빠뜨려도 컴파일됩니다. 업무 규칙도 같은 모양을 지킵니다.

| 업무 규칙 | DSL 한 줄 |
|---|---|
| 생명주기 단계 | `ship: PLACED -> SHIPPED` |
| 업무 지표 | `revenue: sum(PurchaseOrder.total) where status != CANCELLED` |
| 다른 컨텍스트의 이벤트 | `shop:OrderPlaced` |

그래서 기능마다 새 문법이 늘지 않습니다.
[DSL 가이드](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-dsl-guide.md)가 이 원칙으로 시작합니다.

## 무엇이 다른가

비교 대상은 JHipster, Context Mapper, Sculptor, Spring Modulith, Amplication, Jmix, Wasp, Bootify 입니다
([자세한 비교와 출처](docs/comparison.ko.md), 2026-10 확인).

- **업무 규칙이 그 강제 코드를 생성합니다** (3.0 규칙 번들. 생명주기·불변식은 DDD·헥사고날 번들).
  - 생명주기, 동작별 권한, 행 소유, 불변식, 파생 값이 모델의 한 줄이고, 다시 생성되는 코드에서 강제됩니다.
    규칙을 어긴 요청은 정해진 HTTP 응답을 받습니다.
  - JDL 은 필드 검증까지만 다룹니다.
  - Context Mapper 는 생명주기를 모델링하지만 문서로만 남깁니다.
- **생성이 멈추지 않습니다.** 모델의 몫과 개발자의 몫이 다른 파일이라, 다시 생성해도 직접 고친 코드가 위험해지지
  않습니다. 다시 생성하는 것이 평소에 바꾸는 방법입니다.
- **바운디드 컨텍스트를 그림이 아니라 강제로 다룹니다.**
  - 아무도 열지 않은 경계를 넘는 타입이나 이벤트는 생성 단계에서 실패합니다.
  - JHipster 가 생성하는 ArchUnit 테스트는 기술 계층을 검사하고, Spring Modulith 는 테스트 시점에 이미 있는 코드를 검사합니다.
- **모델 하나를 여러 방식으로 읽습니다.** 레이어드·DDD·헥사고날, Java·Kotlin·TypeScript, Spring Boot 3·4 중에서 고릅니다.
- **데이터의 의미까지 내보냅니다.** Apache Ossie 의미 모델에 업무 지표를 SQL 로 담아, BI 도구와 AI 에이전트가 읽게 합니다.
- **같은 DSL 이면 같은 코드가 나옵니다.** 그리고 생성기 자체가 팀이 확장할 수 있는 규칙입니다.

업무 규칙은 언어와 프레임워크보다 오래 갑니다.

- 같은 DSL 로 Java 나 Kotlin 을, Boot 3 나 Boot 4 용으로 생성합니다.
- 생성기를 통째로 다시 쓴 2.x → 3.0 에서도 출력을 재현했습니다. 기준 프로젝트에서 2.x 가 만드는 229개 파일 중
  224개가 같고, 1개는 import 순서만 다르며, 4개는 이력에 적은 2.x 버그 수정만큼 다릅니다.

---

## 하나의 모델, 원하는 코드로

생성은 **규칙(ruleset)의 묶음**입니다. 같은 모델을 어떻게 읽을지 설정 한 줄로 바꿉니다.

| 바꾸고 싶은 것 | 설정 | 결과 |
|---|---|---|
| 언어 | `language = 'kotlin'` | 같은 모델이 Kotlin 엔티티·서비스·컨트롤러로 |
| 화면 코드 | `./gradlew elcore3ts` | TypeScript DTO·enum 라벨·타입 있는 API 클라이언트·검증 함수 |
| 구조 | `architecture = 'ddd'` / `'hexagonal'` | 애그리거트 경계, 불변식, 도메인 이벤트, 포트·어댑터 패키지 |
| Spring Boot | Boot 플러그인 버전 (자동) | Boot 4 면 Jackson 3 용 enum, Spring Data 4 에 맞는 Kotlin 등 |
| 모듈 | `env.yml` namespace 에 `service: app-admin://com.acme.shop.service` | 서비스·컨트롤러만 다른 모듈에 생성 |
| 일부만 | `env.yml` 에 `publisher: rules: [jpa-springboot-kt, ddd-aggregate, …]` | 필요한 규칙만 골라 조합 |

같은 `Item` 모델에서 나온 서버와 화면 코드 (예제 그대로):

```java
// Java - domain/model/Item.java
@Column(name = "unit_cost", nullable = false, precision = 10, scale = 2)
@Convert(converter = Money.JpaConverter.class)
private Money unitCost;
```

```ts
// TypeScript - src/main/ts/ItemDTO.ts
export interface ItemDTO { name: string; unitCost: number; productName?: string; /* … */ }
export function validateItem(value: Partial<ItemDTO>, partial = false): Record<string, string>
//   → { unitCost: "price must be positive" }   서버의 400 과 같은 문구
```

서버와 화면이 **같은 DSL에서** 나오므로, 필드를 바꾸면 화면 코드가 컴파일 오류로 알려 줍니다.

---

## 우리 팀 규칙으로 (ruleset)

"모든 루트 엔티티에 우리 어노테이션을", "개인정보 필드는 응답에서 빼기", "이벤트 없는 명령은 빌드 실패" 같은
**팀의 규칙을 코드로** 만들어 끼웁니다. 생성된 파일을 손으로 고치는 대신 규칙을 한 번 쓰면, 다시 생성할 때마다
모든 엔티티에 적용됩니다.

```java
public class AcmeRules implements CodeRules {

    /** A field marked @secret in the DSL is accepted in requests but never sent back. */
    @Rule(value = "acme-secret", target = "dto")
    void secret(EntitySpec entity, FieldSpec field) {
        if (field.hasDsl("secret")) {
            ctx(entity).field(field).ensure(AnnoCode.of("com.fasterxml.jackson.annotation.JsonProperty")
                    .valRaw("access", "JsonProperty.Access.WRITE_ONLY"));
        }
    }

    /** A root that takes commands must declare its events - otherwise the build fails. */
    @Rule("acme-commands-need-events")
    void commandsNeedEvents(EntitySpec entity) {
        if (!entity.commands().isEmpty() && entity.events().isEmpty()) {
            ModelCheck.fail(entity.getName() + " has commands but no @event.");
        }
    }
}
```

```gradle
elasticore { rules = ['com.acme.rules.AcmeRules'] }
dependencies { elasticore project(':acme-rules') }
```

```yaml
      bizNo: string(12)! @secret      # DSL 에 우리만의 표시
```

ElastiCORE 자신의 Java·Kotlin·TypeScript·DDD·헥사고날 생성도 모두 같은 방식의 규칙입니다.
[규칙 개발 가이드](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-rule-dev-guide.md)는
처음 만드는 사람을 위해 단계마다 결과를 보여 줍니다.

---

## 환경이 바뀌어도 모델은 그대로

- **Spring Boot 3 → 4**: Boot 플러그인 버전을 올리면 생성 코드가 따라갑니다 (Jackson 3 의 enum 직렬화처럼, 바꾸지 않으면
  오류 없이 응답이 달라지는 곳까지). Boot 3·4 예제 프로젝트가 모두 생성·컴파일·테스트됩니다.
- **2.x 에서 3.0**: 아무것도 바꾸지 않으면 2.x 와 **같은 코드**입니다 - 기준 프로젝트에서 2.x 가 만드는 229개 파일을
  풀 리퀘스트마다 비교해 확인합니다
  ([이력](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-history.md)에 적은 버그 수정 제외).
  새 기능은 쓸 때만 켜집니다.
- **고친 코드는 지켜집니다**: 다시 생성되는 파일과 한 번만 생성되는 파일(서비스 확장, 컨트롤러 등)이 나뉘어 있고,
  손으로 고친 파일은 내용 해시로 알아보고 덮어쓰지 않습니다.

---

## AI 코딩 시대에

AI 코딩 도구는 코드를 빨리 쓰고, 시제품이라면 그것으로 충분합니다. 몇 년을 고쳐 가며 운영할 업무 시스템에는 그
다음이 문제입니다 - 수천 줄을 누가 검토하고, 다음에 고칠 때도 같은 규칙을 지키게 할 것인가.

| AI만으로 | ElastiCORE + AI |
|---|---|
| AI가 수천 줄, 사람이 전부 검토 | AI는 **DSL 몇십 줄**, 사람도 그것만 검토 ([측정](docs/benchmarks/change-amplification.ko.md)) |
| 같은 요청에도 매번 다른 코드 | **같은 DSL → 같은 코드.** 다시 생성해도 안전 |
| 엔드포인트 하나에서 권한 검사 누락 | 권한·소유·비밀번호가 **모든 진입점에** 같은 코드로 |
| PR 수백 줄 | PR에는 **바뀐 업무 규칙 한 줄** |
| 틀려도 빌드 통과 | 잘못된 DSL은 **문장으로** 알려 줌 → AI가 바로 고침 |
| 편한 import 하나로 도메인 경계가 무너짐 | 아무도 열지 않은 바운디드 컨텍스트 경계를 넘으면 **빌드가 거절** |
| 데이터의 뜻을 모르는 AI 에이전트·BI | 같은 DSL에서 [Apache Ossie](https://github.com/apache/ossie) 시맨틱 모델 - 조인, 코드의 뜻, "매출"의 정의 |

빌드할 때마다 DSL 가이드가 프로젝트의 `blueprint/` 로 복사되므로, AI는 지금 쓰는 버전의 문법을 그대로 읽습니다.

```
blueprint/petstore/model/entity.yml 에서 배송 뒤 "배송 완료(DELIVERED)" 단계를 추가해 줘.
OrderStatus 에 DELIVERED 를 더하고 states 에 deliver 명령을 넣어. 생성된 코드는 고치지 말고,
./gradlew elcore3 로 경고가 없는지 확인해.
```

---

## 업무 규칙까지 DSL로

구조만이 아니라 **누가 무엇을 하는지, 주문이 어떻게 진행되는지, 값이 어떻게 정해지는지**를 적습니다.

```yaml
PurchaseOrder:
  meta: entity @root @expose(20) @event(Placed(total), Shipped, Cancelled) @owner(customerId)
        @access(read=owner, create=user, cancel=owner) -- 주문
  fields:
    status:   OrderStatus
    total:    money(12.2) @compute(sum(lines.subTotal))
    lines:    List<OrderLine> @OneToMany(mappedBy="order")
  states:
    status:
      initial: PLACED
      ship:    PLACED -> SHIPPED
      cancel:  PLACED -> CANCELLED
      delete:  CANCELLED          # 취소된 주문만 지울 수 있음

OrderLine:
  fields:
    unitCost: money(10.2) @snapshot(item.unitCost)    # 주문 당시 가격
    subTotal: money(12.2) @compute(unitCost * quantity)
```

예제 애플리케이션에 보낸 실제 요청과 응답:

```
주문 (로그인 안 함)                      → 401 "Sign in to create PurchaseOrder"
주문 (단가 0.01, 상태 SHP 를 보내 봄)     → 200 단가 10.00, 상태 PLC - 서버가 정함
배송 (고객)                              → 403 "ship PurchaseOrder is for the administrator"
취소 (배송된 주문)                       → 422 "PurchaseOrder cannot cancel when status is SHIPPED; it must be [PLACED]"
취소 (배송된 주문, Accept-Language: ko)  → 422 "주문 상태가 SHIPPED이면 취소할 수 없습니다 ..."  code: state.PurchaseOrder.status.cancel
삭제 (배송된 주문)                       → 422 "PurchaseOrder cannot be deleted when status is SHIPPED; it must be [CANCELLED]"
남의 주문 조회                           → 404
```

| 업무 정보 | DSL |
|---|---|
| 권한·소유 | `@access(read=owner, create=user)`, `@owner(customerId)` |
| 로그인·비밀번호 | `@login`, `@password` (PBKDF2 해시, 응답·검색에서 제외) |
| 생명주기 | `states:` - 상태 확인·이동·이벤트, 지울 수 있는 상태(`delete:`), 화면용 `canShip(dto)`·`canDelete(dto)` |
| 파생 값 | `@snapshot`, `@compute` |
| 검증 | `!`, `@min(0.01, '...')`, `@email`, `@past` - 서버 400 과 같은 문구의 TS 검증 |
| 업무 지표·용어 | `metrics:` (매출 = 취소 제외 합계), `@terms` |
| 기본 정렬 | `@sort(name+)` - 요청이 정렬을 말하지 않을 때 |
| 오류 메시지 | 모든 오류에 `code`, 문구는 요청 언어로 (`<도메인>-messages_ko.properties` 에 번역만 추가) |
| 외부 연결 | `port:` - HTTP·SQL·gRPC 를 인터페이스로 선언 |

---

## 도메인 경계까지 (바운디드 컨텍스트)

도메인(blueprint 폴더)마다 무엇을 가져다 쓰고 무엇을 내보내는지 적으면, **경계를 넘는 참조를 빌드가 막습니다.**
AI가 DSL을 고치다가 다른 도메인의 엔티티를 끌어다 쓰는 실수도 바로 거절됩니다.

```yaml
# blueprint/catalog/env.yml                  # blueprint/shop/env.yml
config:                                      config:
  context:                                     context:
    publishes: [ProductDTO]                      uses: catalog
```

```
[elcore3] context: [shop] Order.product is catalog's entity Product: another context's entity is no field type
          - hold its id (productId: long), or use a DTO catalog publishes.
```

이벤트도 같은 방식으로 넘깁니다. 한 컨텍스트의 프로세스 매니저가 다른 컨텍스트의 이벤트를 `컨텍스트:루트이벤트`로
적으면, 그 컨텍스트의 트랜잭션이 커밋된 뒤에 실행됩니다. 검사는 타입과 같습니다: 받는 쪽은 상대를 `uses` 에 적고,
`publishes` 를 선언한 컨텍스트는 내보낼 이벤트를 거기 적습니다.

```yaml
# blueprint/billing/model/domainservice.yml  # blueprint/shop/env.yml
processManager:                              config:
  Receipting:                                  context:
    on: [shop:OrderPlaced]                       publishes: [OrderPlaced]
    uses: [Receipt]
```

선언에서 `blueprint/context-map.md` (Mermaid 컨텍스트 지도)가 만들어집니다. 아무것도 선언하지 않으면 예전과 같습니다.

---

## 믿을 수 있는 출력

- 모듈 전체에서 **자동 테스트 1,400개 이상**이 매 빌드에서 돕니다. 예제 프로젝트들은 생성 → 컴파일 → 실행까지 합니다.
- **변경마다 검사**: 풀 리퀘스트마다 CI 가 모든 모듈을 빌드한 뒤 규칙 엔진의 출력을 2.x 출력과 비교합니다 -
  산출물별로(`150 MATCH / 0 DIFF / 2 ACCEPTED`, 엔티티는 필드 부분), 그리고 기준 프로젝트에서 2.x 가 만드는 229개
  파일 전부(`0 differ / 0 missing / 0 extra`: 2.x 버그 수정으로 달라지는 4개는 이름을 정해 허용, 1개는 import 순서만
  다름). 각 파일의 첫 줄(내용 해시·시각), 한 번만 생성되는 파일 표시 주석의 표기, 줄바꿈 문자, 끝 공백은 비교하지
  않습니다.
- **동작으로 확인**: 권한 응답은 HTTP로, 업무 지표의 SQL은 실제 DB로, 시맨틱 모델의 모든 컬럼은 DB 메타데이터로 대조.
- **측정**: 검색 필드 하나를 더하면 DSL 은 1줄, 생성 코드는 9개 파일에서 39줄이 바뀌고, 같은 DSL 로 두 번 생성하면
  130개 파일 중 달라지는 파일이 없습니다(각 파일 첫 줄의 해시·시각 제외) - [변경 증폭 벤치마크](docs/benchmarks/change-amplification.ko.md).
- **서명된 배포**: Maven Central 에 서명해 배포합니다. 라이선스는 Apache License 2.0 입니다.
- **생성된 코드도 보안 신고 대상**: 남의 행을 읽게 하는 권한 검사, 응답에 나오는 비밀번호도 생성기의 결함처럼
  신고받습니다 ([SECURITY.md](SECURITY.md)).

### 현재 상태

- **3.0 은 정식 출시 전(pre-release)입니다.** 이력에 적은 버그 수정과 Spring Boot 4 에서 Jackson 3 에 맞춘 enum 을
  빼면 2.x 가 만들던 코드는 그대로입니다: `elasticore` 블록에 `architecture`·`language`·`rules` 를 적지 않은 프로젝트는
  2.x 생성기가 생성하고, 규칙 엔진의 `jpa-springboot` 번들(레이어드 Java)은 풀 리퀘스트마다 그 출력과 비교됩니다.
- v3 번들의 출력은 정식 출시 전까지 바뀔 수 있습니다 - `jpa-springboot-v3`(레이어드, `-Pv3`), DDD, 헥사고날, Kotlin,
  TypeScript. 바뀔 때마다 그 이유를
  [이력](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-history.md)에 적습니다.
- **3.0 은 첫 정식 릴리스부터 3년간 지원합니다**: 생성기, Gradle 플러그인, 두 런타임 라이브러리의 버그·보안 수정을
  `release/3.0` 브랜치에서 `3.0.YYMMDD` 버전으로 냅니다 ([SECURITY.md](SECURITY.md), [BRANCHING.md](BRANCHING.md)).
  그 전까지는 다음 사전 릴리스(pre-release)에서 고칩니다. 릴리스 방법은 [RELEASING.md](RELEASING.md) 에 있습니다.

## 시작하기 (설치)

내 프로젝트는 **Java 17 이상**, Spring Boot 4 이면 **Gradle 8.14 이상**(Boot 4 Gradle 플러그인의 요구)입니다.
ElastiCORE 는 Gradle 플러그인까지 Maven Central 에 있습니다. 위 `build.gradle` 이 의존성을 Maven Central 에서
받으니, 플러그인도 받도록 내 프로젝트의 `settings.gradle` 에 적습니다:

```gradle
// settings.gradle
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

그다음 위 "5단계"를 따릅니다. 소스에서 직접 빌드하려면 이 저장소를 받아 `./gradlew publishToMavenLocal` 을
실행하고(JDK 17~22, 컴파일에 쓰는 JDK 8·17 툴체인이 없으면 내려받습니다) 두 목록의 맨 앞에 `mavenLocal()` 을 넣습니다.

## 문서

| 문서 | 내용 |
|---|---|
| [다른 도구와 비교](docs/comparison.ko.md) | JHipster, Context Mapper, Sculptor, Spring Modulith, Amplication, Jmix, Wasp, Bootify, AI 가 Spring 을 직접 쓰는 것과 비교 |
| [DSL](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-dsl-guide.md) | 엔티티, DTO, enum, 생명주기, 업무 지표, 값 타입 |
| [어노테이션](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-annotation-guide.md) | 모든 어노테이션과 생성 결과 |
| [설정](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-env-guide.md) | `env.yml`, 바운디드 컨텍스트, 멀티 모듈, Spring Boot 3/4 |
| [규칙 엔진](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-rule-guide.md) | 구조 고르기, 규칙 묶음, 오류 코드와 다국어, Ossie 내보내기 |
| [규칙 개발](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-rule-dev-guide.md) | 우리 팀 규칙 만들기, 테스트 |
| [포트](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-dsl-port-guide.md) | HTTP, DB, gRPC 포트 |

가이드는 빌드할 때 프로젝트의 `blueprint/` 로 복사됩니다 (한국어, 영문판 준비 중).

## 참여하기 · 라이선스

[CONTRIBUTING.md](CONTRIBUTING.md) · [행동 강령](CODE_OF_CONDUCT.md) · [보안 신고](SECURITY.md) · Apache License 2.0 ([LICENSE](LICENSE))
