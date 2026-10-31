# ElastiCORE YAML DSL Guide

## 1. YAML DSL 개요

ElastiCORE DSL은 YAML 기반의 도메인 모델 정의 언어입니다.
Entity, DTO, Enumeration, Repository, Transaction Port, Domain Service를 선언적으로
정의하면 빌드 시 Java 또는 Kotlin 코드가 자동 생성됩니다.

| 최상위 키 | 뜻 | 이 문서의 장 |
|---|---|---|
| `entity:` | 엔티티 | 2장 |
| `dto:` | DTO | 4장 |
| `enumeration:` | 열거형 | 5장 |
| `repository:` | 리포지터리 쿼리 | 6장 |
| `port:` | 트랜잭션 포트 | 7장 |
| `domainService:` | 어느 애그리거트에도 속하지 않는 연산 | **8장** |
| `processManager:` | 이벤트를 받아 다음 단계를 진행 (다른 컨텍스트의 이벤트 포함) | 8장 |
| `metrics:` | 업무 지표 (매출 = 취소 제외 합계 ...) | 2장 "업무 지표" |

엔티티 안에서는 `fields:` 외에 `commands:` (업무 명령), `states:` (생명주기) 가 같은 들여쓰기로 옵니다 (2장).

### 1.1 문법은 여섯 가지 원칙이 전부입니다

개발자가 손으로 쓰고 리뷰어가 읽는 형식입니다. 새 기능이 생겨도 아래 원칙 밖의 모양은 만들지 않습니다.

| # | 원칙 | 예 |
|---|---|---|
| 1 | **한 줄이 한 필드** — 저장·검증·검색·노출을 그 줄이 정합니다 | `name: string(30)! @search(like)` |
| 2 | **흔한 것은 타입 옆 기호** | `(30)` 길이, `!` 필수, `(10.2)` 정밀도 |
| 3 | **엔티티 단위 규칙은 `meta:` 한 줄** | `meta: entity @root @editable @expose(20) @access(read=public)` |
| 4 | **기본값이 있다** — 다른 것만 적습니다 | `@expose(20)` 만 적어도 생성·조회·검색·페이지·REST (수정은 DDD 번들에서 `@editable` 루트·필드에만) |
| 5 | **YAML 섹션이 개념** | `entity`, `dto`, `enumeration`, `states`, `metrics`, `port` |
| 6 | **`--` 뒤는 설명** — 모델이 곧 문서 | `-- 판매 단위 (Large, Male Puppy ...)` |

한 줄을 뜯어 보면 (pet store 의 `Item`):

```yaml
unitCost:  money(10.2)!  @min(0.01, 'price must be positive')  @terms(price, 가격)
```

| 조각 | 뜻 |
|---|---|
| `unitCost` | 이름 |
| `money(10.2)` | 타입과 정밀도 (전체 10자리, 소수 2자리) |
| `!` | 필수 |
| `@min(0.01, 'price must be positive')` | 규칙과 그 메시지 |
| `@terms(price, 가격)` | 사람들이 이 값을 부르는 말 (BI·AI 가 찾는 동의어) |

이 한 줄이 생성한 것 (실제 생성물 그대로):

| 어디에 | 생성물 |
|---|---|
| JPA 엔티티 | `@Column(name = "unit_cost", nullable = false, precision = 10, scale = 2)` + `Money` 변환기 |
| 요청 DTO (400 검증) | `@NotNull` `@DecimalMin(value = "0.01", message = "price must be positive")` |
| TypeScript 검증 | `else if (value.unitCost != null && Number(value.unitCost) < 0.01) errors.unitCost = "price must be positive";` |
| 의미 모델 (Apache Ossie) | `unit_cost` 필드, `synonyms: ["price", "가격"]` |

손으로 쓰면 네 곳에 같은 사실을 네 번 적고, 하나를 빠뜨려도 빌드는 됩니다. 여기서는 한 줄을 고치면 네 곳이 함께 바뀝니다.

**새 기능도 같은 모양입니다.** 배울 문법이 기능마다 늘어나지 않습니다.

| 기능 | 모양 | 예 (pet store) |
|---|---|---|
| 생명주기 | `명령: 시작 -> 도착` 한 줄 | `ship: PLACED -> SHIPPED` |
| 지울 수 있는 상태 | 같은 `states:` 안의 한 줄 | `delete: CANCELLED` |
| 업무 지표 | `이름: 식 @표시 -- 설명` 한 줄 | `revenue: sum(PurchaseOrder.total) where status != CANCELLED @terms(sales, 매출액) -- 매출 ...` |
| 권한·소유 | `meta:` 줄의 표시 | `@owner(customerId) @access(read=owner, create=user, cancel=owner)` |
| 다른 컨텍스트의 타입·이벤트 | `컨텍스트:이름` | `catalog:Product`, `on: [shop:OrderPlaced]` |

DSL 이 잘못되면 생성기가 **한 문장으로** 무엇이 틀렸고 어떻게 고치는지 말합니다
(`... names SHIPED, which OrderStatus does not have (it has PLACED, SHIPPED, CANCELLED)`). 사람이든 AI 든 그 문장을
보고 바로 고칩니다.

- 파일 위치: `src/main/resources/blueprint/{domain}/`
- 파일 확장자: `.yml`
- 파일 구성: 모델 타입별 분리 권장 (entity.yml, dto.yml 등)
- 빌드 명령: `./gradlew build` 또는 `./gradlew elcore`
- 환경설정: `env.yml` (상세는 elasticore-env-guide.md 참조)

---

## 2. Entity 정의

### 기본 구조
```yaml
entity:
  EntityName:
    meta: entity [@annotations]
    fields:
      fieldName: fieldType [@annotations] -- comment
      fieldName: fieldType [@annotations] -- label | description  # 확장형: "|" 로 label 과 description 분리
```

**`--` 주석 파서 동작**
- 단순형 (`-- text`) → `@label(text)` 어노테이션 생성
- 확장형 (`-- label | desc`) → `@label(label)` + `@desc(desc)` 두 어노테이션 생성

**런타임에서 주석 문자열 조회**
- `field.getDescription()` → `--` 주석 텍스트 반환 (label 우선, 없으면 desc 로 폴백). `v2.2.260421-2+` 부터 지원
- `field.getAnnotationValue("label")`, `field.getAnnotationValue("desc")` → 각 어노테이션 값 직접 조회 (기존 방식, 계속 유효)

### Meta 어노테이션
| 어노테이션 | 설명 |
|-----------|------|
| `@abstract` | 추상 엔티티 (상속 전용, 테이블 생성 안됨) |
| `@extend(Parent)` | 부모 엔티티 상속 |
| `@expose(n)` | REST Controller + Service + SearchDTO 생성. n = 페이지 크기 |
| `@service(n)` | Service + SearchDTO 생성 (Controller 제외). n = 페이지 크기 |
| `@pageable` | 페이징 지원 |
| `@audited` | JPA Envers 감사 대상 |
| `@dto` | DTO 생성 가능 표시 |
| `@embeddable` | JPA Embeddable 타입 |
| `@table(name)` | 테이블명 직접 지정 |
| `@schema(name)` | DB 스키마 지정. `@Table(schema="name")` 생성. Since: 2.1.260409 |
| `@java(fullClass)` | 생성될 Java 클래스 FQCN 지정 |
| `@Index(name="idx", columnList="col1,col2")` | 테이블 인덱스 정의 |

#### DDD 룰셋의 meta 어노테이션

아래는 `ddd-springboot` / `hexagonal-springboot` 계열 번들을 선택한 프로젝트에서만
동작합니다. 파라미터와 생성물 예시는 `elasticore-annotation-guide.md` 를 보세요.

| 어노테이션 | 한 줄 뜻 |
|-----------|---------|
| `@root` | 이 엔티티가 애그리거트 루트다 (다른 루트의 목록에 담기는 루트에만 필수) |
| `@aggregate(Root)` | 이 엔티티는 그 루트의 멤버다 (보통 불필요 - `@root` 의 `@OneToMany` 목록에서 추론) |
| `@event(A, B)` | 루트에 일어날 수 있는 일. 이벤트 클래스 + 발생 메서드 생성 |
| `@invariant(조건, '메시지')` | 루트가 항상 참으로 유지하는 것. 행이 쓰일 때 검사 |
| `@factory(name)` | 유효한 애그리거트를 만드는 자리 (generate-once) |
| `@behavior` | 필드에 붙이면 요청으로 보낼 수 없고 업무 메서드로만 바뀜. (루트는 적지 않아도 `XxxBase` + `Xxx` 로 분리됨. 나뉘지 않는 `@editable` 루트·멤버에서는 경고만 - 거기서는 `@readonly`) |
| `@specification(name, 조건)` | 이름을 가질 만한 도메인 규칙. `Predicate` 로 생성 |

`@invariant` 와 `@specification` 의 조건 문법은 **필드·비교·리터럴**로 제한됩니다
(`items.size > 0`, `totalAmount > 100`, `notes notNull`, `items notEmpty`).
`>=` 와 `:` 는 쓸 수 없습니다 — 빠뜨린 것이 아니라 각각 DSL 애노테이션 파서와
YAML 이 다르게 읽기 때문입니다. 자세한 이유는 annotation guide 에 있습니다.

### 필드 어노테이션
| 어노테이션 | 설명 |
|-----------|------|
| `@id` | 기본키 |
| `@sequence` | 자동 증가 시퀀스 |
| `@genid` | 사용자 정의 ID 생성기 |
| `@GeneratedValue` | JPA 자동 생성값 |
| `@length(n)` | 문자열 최대 길이 |
| `@default(value)` | 기본값 |
| `@search(like\|eq\|between\|in\|isnull)` | 검색 조건 (annotation-guide 참조). `isnull` 은 비어 있는지 검색 - 검색 속성 `<필드>Empty: Boolean` (v3 번들) |
| `@updatable(false)` | 수정 불가 필드 |
| `@readonly` / `@noinput` | 서버가 정하는 필드: 응답에는 나가고 요청으로는 받지 않음. v3 번들은 매퍼도 DTO -> 엔티티로 쓰지 않고 DTO 의 `@NotNull`·`@NotBlank` 를 뺌 (annotation-guide §2.2) |
| `@ref(path)` | 다른 필드 참조 |
| `@dtype(type)` | DTO 변환 타입 지정 |
| `@cascade(ALL\|PERSIST\|MERGE\|REMOVE)` | 연관관계 cascade |
| `@relation(mappedBy="field")` | 연관관계 매핑 |
| `@fetchjoin` | JOIN FETCH 자동 적용 (N+1 방지) |
| `@notaudited` | 감사 제외 |
| `@Column(name="col", length=n)` | JPA 컬럼 매핑 |
| `@env:annotationName` | env.yml 정의 어노테이션 참조 |

### 관계 어노테이션

Entity 타입 필드는 **기본적으로 `@ManyToOne`이 자동 적용**됩니다. `@OneToOne`, `@ManyToMany`를 사용하는 경우에만 명시적으로 지정하면 됩니다.

```yaml
fields:
  parent: ParentEntity                          # @ManyToOne 자동 적용 (생략 가능)
  parent: ParentEntity @ManyToOne               # 위와 동일 (명시적 지정)
  children: List<Child> @OneToMany(mappedBy="parent")
  profile: Profile @OneToOne
  tags: List<Tag> @ManyToMany
```

#### FK 컬럼명 지정

Entity 참조 필드에 `@column` (또는 `@col`)을 사용하면 `@JoinColumn(name=...)`이 생성됩니다. 미지정시 JPA 기본 규칙(필드명 + `_id`)이 적용됩니다.

```yaml
fields:
  comment: Comment                              # JPA 기본: comment_id
  comment: Comment @column(parent_comment_id)   # @JoinColumn(name="parent_comment_id")
  comment: Comment! @col(parent_comment_id)     # @JoinColumn(name="parent_comment_id", nullable=false)
```

`!`가 붙은 관계는 자동 생성 DTO(`@expose`/`@service`)에서 **FK id 필드가 필수**입니다. 저장 요청은 이 필드를 보냅니다.
이름은 `필드명 + 대상 @id` (`post: Post!`, 키 `postId` -> `postPostId`; 키가 `id`이면 `topic: Topic!` -> `topicId`).
응답용 중첩 DTO(`post`)는 요청에서 선택입니다. v3 · Kotlin · TypeScript 생성 기준(3.0.261022-rc1부터)이고, 2.x와
`jpa-springboot` 호환 출력은 이전처럼 중첩 DTO에 `@NotNull`이 붙습니다 -
`elasticore-annotation-guide.md` "필수 관계 (`!`): 저장 요청은 FK id 필드를 보낸다".

### Entity 예시
```yaml
entity:
  AuditEntity:
    meta: entity @abstract
    fields:
      createdBy: string(20) @updatable(false) @jpa:org.springframework.data.annotation.CreatedBy
      createDate: datetime @updatable(false) @jpa:org.springframework.data.annotation.CreatedDate
      lastModifiedBy: string(20) @jpa:org.springframework.data.annotation.LastModifiedBy
      lastModifiedDate: datetime @jpa:org.springframework.data.annotation.LastModifiedDate

  Board:
    meta: entity @expose(50) @extend(AuditEntity) @Index(name="idx_board_type", columnList="board_type")
    fields:
      bid: long @id @sequence -- Board ID
      name: string(100)! @search(like) -- Board name
      boardType: BoardType -- Board type enum
      articles: List<Article> @dtype(List<ArticleDTO>) @OneToMany(mappedBy="board")

  Article:
    meta: entity @expose(50) @extend(AuditEntity)
    fields:
      aid: string @id @genid -- Article ID
      title: string(200)! @search(like) -- Title
      content: text -- Body
      price: bigdecimal(10.2) @search(between) -- Price (precision 10, scale 2)
      board: Board @fetchjoin -- Parent board (@ManyToOne 자동 적용)
```

### 업무 명령 (`commands:`) - DDD 번들

CRUD 는 "이 필드들을 바꿔라" 이고, 도메인은 "이 수주를 확정해라" 라고 요청받습니다. 엔티티에
`commands:` 를 쓰면 명령 하나가 **엔드포인트부터 규칙까지 한 길**로 생성됩니다.

```yaml
SalesOrder:
  meta: entity @root @expose(20)
  fields:
    orderId: string(36) @id @genid
    status: OrderStatus @behavior
  commands:
    confirm:                     # 파라미터 없음
    ship:                        # 파라미터는 필드처럼 쓴다 (검증 어노테이션도 그대로)
      warehouse: string(20)!
    cancel:
```

| 생성물 | 내용 | 파일 성격 |
|---|---|---|
| `POST /api/erp/salesOrder/{orderId}/ship` | 컨트롤러 메서드 (camelCase 명령은 kebab-case 경로: `shipPartially` -> `/ship-partially`) | 새 컨트롤러에 생성 |
| `SalesOrderShipCommand` | 파라미터가 있는 명령의 요청 DTO (`@NotNull`, `@Size` 등) | 재생성 |
| `SalesOrderUseCase.ship(orderId, command)` | 인바운드 포트 | 재생성 |
| `SalesOrderCoreService.ship(...)` | 불러오기 -> `entity.ship(warehouse)` -> 저장, 트랜잭션 | 재생성 |
| `SalesOrderBase`: `abstract void ship(String warehouse)` | 약속 - 행위 클래스가 이미 있으면 규칙을 쓸 때까지 **컴파일 에러** | 재생성 |
| `SalesOrder.ship(String)` | 규칙 (처음 한 번 예외를 던지는 틀로 생성) | 개발자 소유 |

- 규칙은 생성하지 않습니다. 상태 검사, 값 변경, 이벤트 발생은 `SalesOrder` 에 직접 씁니다.
- 명령이 애그리거트 밖의 일(재고 차감처럼 도메인 서비스·다른 포트)을 필요로 하면 확장 서비스
  (`SalesOrderService`)에서 `ship(orderId, command)` 를 override 합니다.
- 파라미터는 **값**입니다. 다른 애그리거트는 식별자로 받고(`itemCode: string(20)`) 서비스에서 불러옵니다
  - 엔티티 타입 파라미터는 DSL 경고.
- 명령은 행위 클래스가 있는 루트(= `@editable` 이 아닌 단일 키 루트)에만 생성됩니다. 그 밖에는 DSL 경고 후 무시.
- **`@editable` 필드가 하나도 없는 루트**는 PUT(`update`, `batchUpdate`)을 만들지 않습니다: API 는
  생성·조회·삭제와 명령입니다.

### 생명주기 (`states:`) - DDD 번들

"주문은 접수(PLACED)로 시작해서, 배송하면 SHIPPED, 취소하면 CANCELLED" 처럼 **상태가 어디서 어디로만
바뀌는 규칙**은 코드 대신 DSL 에 씁니다. 명령 메서드(`ship()`, `cancel()`)의 본문까지 생성되므로
행위 클래스에 손으로 쓸 것이 없습니다.

#### 1. 세 곳이 이렇게 연결됩니다

```yaml
enumeration:
  OrderStatus:                         # (1) 상태 목록 = enum 의 상수 이름
    meta: enum @db(code) @json(code)
    fields:
      code:  string(3) @code
      label: string(20)
    enum:
      PLACED:    PLC, Placed
      SHIPPED:   SHP, Shipped
      CANCELLED: CAN, Cancelled

entity:
  PurchaseOrder:
    meta: entity @root @expose(20) @event(Placed(total), Shipped, Cancelled)
    fields:
      orderId: string(36) @id @genid
      status:  OrderStatus             # (2) 상태를 담는 필드. 타입이 (1) 의 enum
    states:
      status:                          # (3) 키 = (2) 의 "필드 이름"
        initial: PLACED                #     새 주문의 시작 상태 (예약어 initial)
        ship:    PLACED -> SHIPPED     #     명령 이름: 시작 상태 -> 도착 상태
        cancel:  PLACED -> CANCELLED
```

- **어느 필드의 생명주기인가**: `states:` 바로 아래의 키(`status`)가 **필드 이름**입니다.
  타입 이름(`OrderStatus`)이 아닙니다. 그래서 같은 enum 을 쓰는 필드가 둘이어도 헷갈리지 않습니다.
- **상태 이름은 어디서 오나**: 그 필드의 타입인 enum 의 **상수 이름**(`PLACED`)입니다.
  DB/JSON 코드값(`PLC`)이 아닙니다. enum 에 없는 이름을 쓰면 빌드 경고가 나고 생명주기 전체가 빠집니다
  (아래 "빌드 경고" 표).
- **`initial` 말고 나머지 키는 모두 명령**입니다. `ship:` 이라고 쓰면 `ship()` 명령과
  `POST /api/.../{id}/ship` 이 생깁니다. `commands:` 에 따로 적지 않아도 됩니다
  (명령에 파라미터가 필요할 때만 `commands:` 에 같은 이름으로 적습니다).

#### 2. 그림으로 보면

```
          ship
  PLACED ------> SHIPPED      (끝)
     |
     |  cancel
     +---------> CANCELLED    (끝)
```

- **화살표가 나가지 않는 상태는 끝(최종) 상태**입니다. 위 예에서 `SHIPPED` 와 `CANCELLED` 에서는
  어떤 명령도 받지 않습니다. 예를 들어 배송된 주문에 `cancel` 을 보내면 409 (지금 상태와 충돌):
  `PurchaseOrder cannot cancel when status is SHIPPED; it must be [PLACED]` (Kotlin: `... it must be PLACED`)
- SHIPPED 다음 단계가 필요하면 enum 에 상태를 더하고 명령 한 줄을 더합니다:

```yaml
    enum:
      PLACED:    PLC, Placed
      SHIPPED:   SHP, Shipped
      DELIVERED: DLV, Delivered      # 추가
      CANCELLED: CAN, Cancelled
  ...
    states:
      status:
        initial: PLACED
        ship:    PLACED -> SHIPPED
        deliver: SHIPPED -> DELIVERED          # SHIPPED 에서 나가는 길
        cancel:  PLACED, SHIPPED -> CANCELLED  # 시작 상태가 여럿이면 쉼표
```

- 생성된 `XxxBase` 클래스의 주석에도 이 그림이 글로 남습니다 (DSL 을 열지 않아도 읽을 수 있게):

```java
/**
 * Moves status to the next state, or refuses: the command was given in a state it does not start in.
 *
 * Lifecycle of status (OrderStatus), from the DSL's states:
 *   start:  PLACED
 *   ship:   PLACED -> SHIPPED
 *   cancel: PLACED -> CANCELLED
 *   end:    SHIPPED, CANCELLED - no command leaves them
 */
```

  enum 에 있지만 어떤 명령으로도 갈 수 없는 상태가 있으면 `unused: ... - no command moves a row there`
  줄이 붙습니다 (데이터 이관 등으로만 들어오는 상태인지 확인하세요).

#### 3. 생성되는 것

| 생성물 | 내용 |
|---|---|
| `private OrderStatus status = OrderStatus.PLACED` | 시작 상태. `initial` 이 없으면 새 행의 상태는 비어 있고(null), 어떤 명령도 통과하지 못합니다 |
| `PurchaseOrderBase.ship()` | 지금 상태 확인 -> 이동 -> `raiseShipped()` (도착 상태와 같은 이름의 `@event` 가 있을 때). 시작 상태가 아니면 **409** |
| `PurchaseOrderBase.canShip()` | 지금 이 명령을 할 수 있는지 (`status == PLACED`) |
| `commands:` 항목, 엔드포인트, 유스케이스, 서비스 | `commands:` 와 같음 (`POST /{id}/ship`) |
| TypeScript `PurchaseOrderApi.canShip(dto)` | 화면에서 "배송" 버튼을 보일지 정할 때 |
| DTO 의 `status` | **읽기 전용**. 생성·수정 요청에 `"status": "SHP"` 를 넣어도 무시됩니다 - 상태는 명령으로만 바뀝니다 |

#### 4. 이동에 일을 더 하고 싶을 때

이동 자체는 생성 코드가 하고, 더 할 일(배송 번호 기록 등)은 행위 클래스에서 override 합니다:

```java
public class PurchaseOrder extends PurchaseOrderBase {
    @Override
    public void ship() {
        // 이동 전에 확인할 것이 있으면 여기서
        super.ship();               // 상태 확인 + 이동 + 이벤트
        // 이동 후에 할 일
    }
}
```

#### 5. 상태 필드가 여러 개일 때 (한 명령이 여러 생명주기를 바꾸는 경우)

주문 상태와 결제 상태처럼 **enum 필드가 둘 이상**이면 `states:` 아래에 필드마다 생명주기를 따로 씁니다.
각 생명주기는 독립적으로 움직이고, **같은 명령 이름이 두 생명주기에 있으면 그 명령은 둘을 함께 바꿉니다.**

```yaml
enumeration:
  ShipmentStatus:
    enum:
      READY:     RDY, Ready
      SENT:      SNT, Sent
      CANCELLED: CAN, Cancelled
  FeeStatus:
    enum:
      DUE:      DUE, Due
      PAID:     PAD, Paid
      REFUNDED: REF, Refunded

entity:
  Shipment:
    meta: entity @root @expose(20) @event(Sent, Cancelled, Refunded)
    fields:
      shipmentId: string(36) @id @genid
      status:     ShipmentStatus
      fee:        FeeStatus
    states:
      status:                       # 생명주기 1: 배송
        initial: READY
        send:    READY -> SENT
        cancel:  READY -> CANCELLED
      fee:                          # 생명주기 2: 배송비
        initial: DUE
        pay:     DUE -> PAID
        cancel:  PAID -> REFUNDED   # 같은 명령 cancel: 두 생명주기를 함께 바꿈
```

| 명령 | 바꾸는 필드 | 할 수 있는 때 (`canX()`) |
|---|---|---|
| `send()` | `status` 만 | `status == READY` |
| `pay()` | `fee` 만 | `fee == DUE` |
| `cancel()` | `status` **와** `fee` | `status == READY` **이고** `fee == PAID` |

생성되는 `cancel()`:

```java
public void cancel() {
    requireStatus("cancel", ShipmentStatus.READY);   // 1) 모든 생명주기를 먼저 확인
    requireFee("cancel", FeeStatus.PAID);
    this.status = ShipmentStatus.CANCELLED;          // 2) 모두 통과해야 함께 이동
    this.fee = FeeStatus.REFUNDED;
    raiseCancelled();                                // 3) 도착 상태마다 같은 이름의 @event 발행
    raiseRefunded();
}
```

- **모두 아니면 아무것도**: 하나라도 시작 상태가 아니면 거부하고(409) **어느 필드도 바뀌지 않습니다.**
  예) 배송비를 안 낸(`DUE`) 배송을 취소하면 `Shipment cannot cancel when fee is DUE; it must be [PAID]`,
  `status` 는 `READY` 그대로.
- TypeScript `ShipmentApi.canCancel(dto)` 도 두 조건을 모두 봅니다.
- 생성 클래스의 생명주기 주석에 `cancel: READY -> CANCELLED  (with fee)` 처럼 함께 바뀌는 필드가 표시됩니다.
- 두 생명주기 중 하나라도 잘못되면(enum 에 없는 상태 등) 그 명령은 생성되지 않고 추상 메서드로 남습니다.

**이 문법으로 안 되는 것** (행위 클래스에서 override 로 씁니다):
- "배송비를 냈으면 환불, 안 냈으면 그대로 두고 취소" 처럼 **한 생명주기 안에서 상태에 따라 도착 상태가 다른 경우**
  (`PAID -> REFUNDED` 와 `DUE -> DUE` 를 한 명령에). 이런 때는 `fee` 쪽 `cancel` 을 빼고, `Shipment` 에서
  `cancel()` 을 override 해서 `super.cancel()` 뒤에 `fee` 를 직접 처리합니다.
- 다른 애그리거트의 상태를 함께 바꾸는 것 (예: 주문 취소 시 재고 복원) - 애그리거트 하나 안의 규칙만 씁니다.
  그런 흐름은 도메인 이벤트(`@event`)를 받아 처리합니다.

#### 6. 지울 수 있는 상태 (`delete:`)

생명주기에 `delete:` 를 적으면 **그 상태일 때만** 행을 지울 수 있습니다. `delete` 는 명령이 아니라 상태 목록입니다
(`POST /{id}/delete` 같은 엔드포인트는 생기지 않음).

```yaml
    states:
      status:
        initial: PLACED
        ship:    PLACED -> SHIPPED
        cancel:  PLACED -> CANCELLED
        delete:  CANCELLED          # 취소된 주문만 지울 수 있음 (여러 개면 CANCELLED, LOST 또는 [CANCELLED, LOST])
```

| 생성물 | 내용 |
|---|---|
| `PurchaseOrderBase.canDelete()` | 지금 지울 수 있는지 (`status == CANCELLED`) |
| `PurchaseOrderBase.checkDeletable()` | 다른 상태면 **409** (`state.PurchaseOrder.status.delete`), 행은 그대로. JPA `@PreRemove` 로 불리므로 id 로 삭제·조건 삭제·cascade 등 **모든 삭제 경로**에 걸림 |
| TypeScript `canDelete(dto)` | 삭제 버튼 표시용, 같은 조건 |

Pet Store 에서 (`ShopFlowTest.onlyACancelledOrderIsDeleted`):

```http
DELETE /api/petstore/purchaseOrder/{배송된 주문}
HTTP/1.1 409
{ "code": "state.PurchaseOrder.status.delete",
  "detail": "PurchaseOrder cannot be deleted when status is SHIPPED; it must be [CANCELLED]" }
```

- 상태 필드가 여러 개면 **모두** 허용 상태여야 지워집니다 (`canDelete()` 는 AND). 예) 티켓은 `status: CLOSED` 이고
  `billing: UNBILLED, VOID` 일 때만 (Kotlin 샘플 `ticket.yml`).
- `delete:` 가 없으면 삭제는 생명주기와 무관합니다 (예전과 같음). `delete: []` 는 어떤 상태에서도 지울 수 없음입니다.
  누가 지울 수 있는지는 `@access(delete=admin)` 이 정합니다.
- `delete` 는 이제 생명주기의 예약어입니다 (예전에 `delete: A -> B` 를 명령으로 쓰던 모델은 이름을 바꾸세요 - 경고가 나옴).
- 행위 클래스가 있는(나뉜) 루트에만 생성됩니다. `@editable` 루트처럼 나뉘지 않으면 DSL 경고 후 검사 없음.
- JPA 콜백이므로 JPQL 일괄 삭제(`DELETE FROM ...`), 네이티브 SQL, DB 의 `ON DELETE CASCADE` 는 거치지 않습니다.
- 루트에 불변식(`@invariant`, `@min` 등)이 있으면 JPA 가 클래스당 `@PreRemove` 하나만 허용하므로 검사가
  불변식 쪽 `markBeingRemoved()` 안에서 먼저 불립니다. 동작은 같습니다.

#### 7. 빌드 경고 (생명주기가 빠지는 경우)

| 경고 | 원인 | 결과 |
|---|---|---|
| `states.status names SHIPED, which OrderStatus does not have (it has PLACED, SHIPPED, CANCELLED)` | enum 에 없는 상태 이름 (오타, 코드값 `SHP` 사용 등) | 생명주기 생략. 명령은 추상 메서드로 남음 |
| `states names status, which PurchaseOrder has no field for` | `states:` 아래 키가 필드 이름이 아님 (타입 이름을 쓴 경우 등) | 생명주기 생략 |
| `states names status, which is not an enum of the model` | 필드 타입이 enum 이 아님 (`string` 등) | 생명주기 생략 |
| `states.status.ship: '...' is not FROM -> TO` | `->` 가 없음 | 그 이동만 생략 |
| `states.status.delete: '...' is not a list of states` | `delete:` 에 `->` 를 쓰거나 비움 | 삭제 검사 생략 |
| `has @default and states.status.initial` | 상태 필드에 `@default` 도 있음 | `initial` 이 이김 |
| `still has the stub that throws` | 예전에 만든 행위 클래스에 예외를 던지는 틀이 남아 이동을 덮음 | 틀을 지우세요 |

경고는 `elcore3` 출력 끝의 `[elcore3] DSL validation warnings:` 아래에 모여 나옵니다. 빌드는 멈추지 않으므로
생성 후 이 목록을 꼭 확인하세요. 생명주기가 빠지면 명령이 추상 메서드로 돌아가서, 행위 클래스에 구현이 없으면
`PurchaseOrder is not abstract and does not override ship()` 같은 컴파일 오류로 이어집니다.

---

### 업무 지표 (`metrics:`) 와 업무 용어 (`@terms`)

"매출이 무엇인가", "주문 수에 취소도 세나" 는 핵심 도메인 정보인데 코드·SQL·BI 도구마다 따로 정의되면 서로 다른 숫자가
나옵니다. DSL 에 **한 번** 적고, [Apache Ossie](https://github.com/apache/ossie) 시맨틱 모델(`ossie-export`)의 `metrics` 로
내보내면 BI·AI 도구가 같은 정의를 씁니다. 애플리케이션 코드는 생성하지 않습니다 (조회 API 없음).

```yaml
# model/metrics.yml (모델 파일 어디든, 최상위 metrics:)
metrics:
  revenue:         sum(PurchaseOrder.total) where status != CANCELLED @terms(sales, 매출액) -- 매출 (취소 제외)
  orderCount:      count(PurchaseOrder) where status != CANCELLED -- 주문 수
  averageOrder:    revenue / orderCount @terms(AOV, 객단가) -- 주문 1건 평균 금액
  shippedOrders:   count(PurchaseOrder) where status = SHIPPED -- 배송된 주문 수
  customerCount:   count(Customer) where role = USER -- 고객 수 (관리자 제외)
```

| 쓰는 법 | 뜻 | Ossie SQL |
|---|---|---|
| `sum(엔티티.필드)` / `avg` / `min` / `max` | 한 엔티티의 필드 집계 | `SUM(PurchaseOrder.total)` |
| `count(엔티티)` / `count(엔티티.필드)` | 행 수 / 값 있는 행 수 | `COUNT(PurchaseOrder.order_id)` (키 컬럼 - BI 도구가 어느 dataset 을 세는지 알도록) / `COUNT(PurchaseOrder.total)` |
| `… where 필드 != 값 and 필드 >= 값` | 조건 (`=`, `!=`, `<`, `<=`, `>`, `>=`, `and`) | `SUM(CASE WHEN … THEN … END)` |
| 값 | 숫자, `'글자'`(안의 `'` 는 `''`), `true`/`false`, **enum 상수 이름** (저장된 값으로 바뀜: `@db` 코드 `'CAN'`, `@jpa:Enumerated(STRING)` 면 이름, 둘 다 없으면 순번 `2`) | |
| `지표A / 지표B` (`+ - * /`) | 앞에서 선언한 지표끼리 계산. 나누기는 0 으로 나누지 않고, 개수 / 개수는 소수로 | `(…) / NULLIF((…), 0)` |
| `@terms(sales, 매출액)` | 업무 용어·동의어 | `ai_context.synonyms` |
| `-- 매출` | 설명 | `description` |

- 데이터 타입: `count` 는 `Integer`, `sum`·`min`·`max` 는 그 필드의 타입, `avg`·나누기는 `Decimal`(`Float` 필드면 `Float`),
  `+ - *` 는 둘 다 `Integer` 면 `Integer`, 아니면 `Decimal`. `sum`·`avg` 는 숫자 필드에만.
- 줄에 ` #` 나 `: ` 가 들어가면 YAML 이 줄을 자르거나 파일을 못 읽으므로 줄 전체를 따옴표로 감쌉니다:
  `ratio: "shippedOrders / orderCount -- 배송 비율: 배송 / 전체"`.
- 못 읽는 줄(없는 엔티티·필드, enum 에 없는 상수, 뒤에 선언된 지표, enum 이 아닌 필드에 따옴표 없는 값)은 생성할 때
  `[ossie] [도메인] metric 이름: … ; it is left out.` 경고 후 빠집니다 - 추측해서 내보내지 않습니다.
- **한 엔티티 안에서만** 셉니다. "취소되지 않은 주문의 팔린 수량" 처럼 다른 엔티티의 조건(주문 줄 → 주문 상태)이 필요한 지표는
  아직 쓸 수 없습니다 - 억지로 쓰면 취소분까지 세게 되므로 넣지 마세요.
- 기간(월별 매출)은 지표에 넣지 않습니다. 시간 축은 BI 도구가 날짜 필드(`orderDate`, Ossie 의 time dimension)로 나눕니다.

`@terms` 는 엔티티 meta·필드·지표에 붙여 업무에서 부르는 이름과 동의어를 적습니다. 코드 생성에는 영향이 없고 Ossie 의
`ai_context.synonyms` 로 나갑니다 - "지난달 구매 금액" 이라고 물으면 AI 가 `PurchaseOrder` 와 `revenue` 를 찾게:

```yaml
PurchaseOrder:
  meta: entity @root @expose(20) @terms(order, purchase, 구매) -- 주문
Item:
  meta: entity @root @editable @terms(pet, 반려동물) -- 판매 품목
  fields:
    unitCost: money(10.2)! @terms(price, 가격)
```

검증: Pet Store `MetricsTest` 가 주문을 접수·배송·취소한 뒤, 내보낸 지표 SQL 을 실제 DB 에서 실행한 값과 엔티티로 직접 계산한 값
(매출·주문 수·객단가·배송·취소·고객 수)이 같은지 확인합니다.

### 권한·소유·자격 증명·파생 값 - 어노테이션 요약

| 어디에 | 어노테이션 | 뜻 |
|---|---|---|
| 엔티티 meta | `@access(read=owner, create=user, ship=admin)` | 연산별 허용 수준 (public/user/owner/admin/none, 안 적으면 admin) |
| 엔티티 meta | `@owner(customerId)` | 행의 주인을 담는 필드 |
| 엔티티 meta | `@signin` | 로그인 API: `me()`, 컨트롤러의 `signin`·`signout`·`me`, TypeScript `signIn`·`signOut`·`me`. `@login`·`@password`·`@expose`, 키 하나, 본인·관리자만 수정(`update=owner` + 키의 `@owner`, 또는 `update=admin`), 요청이 정하는 키면 삭제도 본인·관리자만, 애플리케이션에 하나일 때만 - 아니면 DSL 경고. 로그인 컬럼 unique 는 `@signin` 만으로 (거절되어도) |
| 필드 | `@access(admin)` | 관리자만 쓰는 필드 |
| 필드 | `@password` | 해시로만 저장, 응답·검색에 없음 |
| 필드 | `@login` | `@password` 와 함께 `authenticate(login, password)` 생성. 유일해야 함 (`@signin` 이면 자동, 아니면 `@unique` - 없으면 DSL 경고) |
| 필드 | `@role` | `@signin` 의 로그인이 세션에 넣는 역할 (없으면 `role` 필드). `@access(admin)` 인 enum·문자열만 - 아니면 역할 없이 로그인하고 DSL 경고 |
| 필드 | `@snapshot(item.unitCost)` | 참조한 행의 값을 한 번 복사 |
| 필드 | `@compute(unitCost * quantity)`, `@compute(sum(lines.subTotal))` | 저장할 때마다 계산 |

상세: `elasticore-annotation-guide.md` 의 "@access / @owner", "@password / @login", "@snapshot / @compute".

## 3. 필드 타입

### 값 타입 한눈에 (DSL → Java → JSON → TypeScript → Apache Ossie)

필드 타입으로 쓸 수 있는 값 타입 전부입니다. 각 타입은 생성되는 Java 필드 타입, 요청·응답 JSON 에서의 모양,
TypeScript 타입, 그리고 [Apache Ossie](https://github.com/apache/ossie/blob/main/core-spec/spec.md) 시맨틱 모델로
내보낼 때의 **DataType** 이 정해져 있습니다 (`ossie-export`, 규칙 가이드 참고).
Ossie 의 DataType 은 10개뿐입니다: `String`, `Integer`, `Decimal`, `Float`, `Boolean`, `Date`, `Time`,
`DateTime`(시간대 없음), `DateTimeTz`(한 순간 - 시간대/오프셋 포함), `Opaque`(어휘 밖).

| DSL 타입 | Java 타입 | JSON | TS | Ossie DataType | 비고 |
|---|---|---|---|---|---|
| `string` / `string(n)` | `String` | `"text"` | `string` | `String` | n = 최대 길이, `!` = NOT NULL |
| `text` / `longtext` | `String` | `"text"` | `string` | `String` | `columnDefinition = "TEXT"` / `"LONGTEXT"` |
| `clob` | `String` | `"text"` | `string` | `String` | `@Lob` (큰 문자열) |
| `json` | `String` | `"{...}"` (문자열) | `string` | `Opaque` | `columnDefinition = "json"` + `@JdbcTypeCode(SqlTypes.JSON)` |
| `int` / `Integer` | `Integer` | `42` | `number` | `Integer` | |
| `long` / `Long` | `Long` | `42` | `number` | `Integer` | |
| `biginteger` | `BigInteger` | `123456789012345678901` | `number` | `Integer` | |
| `quantity` | `Quantity` | `5` | `number` | `Integer` | 3.0 값 타입: 음수 없는 개수 (아래) |
| `decimal` / `decimal(p.s)` | `BigDecimal` | `12.34` | `number` | `Decimal` | `bigdecimal` 와 같음. `(p.s)` = precision, scale |
| `bigdecimal` / `bigdecimal(p.s)` | `BigDecimal` | `12.34` | `number` | `Decimal` | |
| `money` / `money(p.s)` | `Money` | `99.95` | `number` | `Decimal` | 3.0 값 타입: 금액 (아래), 기본 (19,2) |
| `float` / `double` | `Float` / `Double` | `1.5` | `number` | `Float` | 근사값 - 금액에 쓰지 마세요 |
| `boolean` | `Boolean` | `true` | `boolean` | `Boolean` | |
| `date` / `LocalDate` | `LocalDate` | `"2026-10-03"` | `string` | `Date` | DTO 에 `@JsonFormat("yyyy-MM-dd")` |
| `time` / `LocalTime` | `LocalTime` | `"09:30:00"` | `string` | `Time` | |
| `datetime` / `LocalDateTime` | `LocalDateTime` | `"2026-10-03 09:30:00"` | `string` | `DateTime` | 시간대 없음. DTO 에 `@JsonFormat("yyyy-MM-dd HH:mm:ss")` |
| `timestamp` | `java.time.Instant` | `"2026-10-03T00:30:00Z"` | `string` | `DateTimeTz` | 한 순간. `+09:00` 을 붙여 보내도 같은 순간으로 저장, UTC 로 돌려줌 |
| `blob` | `byte[]` | `"AQID/w=="` (base64) | `string` | `Opaque` | `@Lob` |
| `EnumName` | 그 enum | 코드 (`"PLC"`) | enum | `String` | `@db(code)` 의 값이 저장됨. Ossie 에는 코드와 라벨을 `ai_context` 로 |
| `EntityName` | 엔티티 참조 | (식별자 필드) | - | 관계 | `@ManyToOne` 자동. Ossie `relationships` 로 |

- **어느 시간 타입을 쓰나**: 사람이 보는 날짜·시각(영업일, 예약 시각)은 `date`·`datetime`, "언제 일어났나"
  (결제 승인 시각, 이벤트 발생, 감사)는 `timestamp`. 서버·DB 의 시간대가 달라도 `timestamp` 는 같은 순간입니다.
- `datetime` 의 JSON 은 ISO 형식(`T`)이 아니라 `yyyy-MM-dd HH:mm:ss` 입니다 (ElastiCORE DTO 규약).
- **표에 없는 타입은 쓰지 않습니다.** 같은 뜻을 이미 표의 타입이 말합니다: `short`·`byte` 는 `int`, `char` 는 `string(1)`,
  `OffsetDateTime`·`ZonedDateTime` 은 `timestamp`, UUID 키는 `string @id @genid` (길이를 주지 않으면 `UUID.randomUUID()`).
- 위 표의 모든 타입은 `elasticore-template-ddd` 의 `catalog/model/reading.yml` 에서 생성·저장·HTTP 왕복으로
  검증됩니다 (`TypeRoundTripTest`). 이전 버전에서는 `timestamp`, `clob`, `json`, `blob`, `decimal`(크기 없이)이
  엔티티에 DSL 단어 그대로 쓰여(`private timestamp at;`) 컴파일되지 않았습니다 - 3.0.260925 에서 고쳤습니다.
- 표는 3.0 규칙 엔진(`elcore3`) 기준입니다. 2.x 생성기(`elcore`)의 출력은 바뀌지 않았습니다
  (`timestamp` 는 2.x 에서도 `Instant`, `@Lob` 은 3.0 만).

### 값 타입 (Value) - 3.0

금액과 수량을 원시 타입 대신 **값 객체**로 씁니다. 컬럼과 JSON 모양은 그대로(decimal·integer, 숫자)이고,
그 사이의 코드가 `Money`·`Quantity` 를 다룹니다. 런타임 `io.elasticore.springboot3.domain` 에 있습니다.

| DSL 타입 | Java 타입 | 컬럼 | 비고 |
|---------|----------|------|------|
| `money` / `money(p.s)` | `Money` | `decimal(p,s)` (기본 19,2) | `plus`, `minus`, `times(Quantity/long/BigDecimal)`, `rounded`, `isNegative` … 10 과 10.00 은 같은 금액 |
| `quantity` | `Quantity` | `integer` | 음수가 없음. `minus` 로 모자라면 422 (`not enough`). `plus`, `covers` |

```yaml
SalesOrderLine:
  fields:
    qty: quantity! @min(1)
    unitPrice: money(12.2)! @min(0.01, '단가는 0보다 커야 한다')
    amount: money(14.2)
```

```java
// 행위 클래스에서
line.setAmount(line.getUnitPrice().times(line.getQty()));
total = total.plus(line.getAmount());
stock.setOnHand(stock.getOnHand().minus(qty));   // 모자라면 422
```

- 엔티티에는 `@Convert(converter = Money.JpaConverter.class)` 와 `@Column(precision, scale)` 가 붙습니다.
- `@min`/`@max` 는 BigDecimal 과 같은 `@DecimalMin`/`@Min` 으로 DTO 에 붙고, 런타임이 값 타입용 검증기를
  서비스 로더로 등록합니다 (추가 설정 없음). DDD 루트에서는 저장 시점 검사도 같습니다.
- `@default(0)` 은 `Money.of("0")` / `Quantity.of(0)` 이 됩니다.
- 부호가 있는 변화량(입고 +, 출고 -)은 수량이 아니므로 `int` 로 둡니다.
- 3.0 규칙 엔진(elcore3) 전용입니다. 2.x 생성기(elcore)에는 변환기가 붙지 않습니다.

### 컬렉션/배열 타입

| DSL 타입 | Java 타입 | 비고 |
|---------|----------|------|
| `List<T>` | `List<T>` | Collection |
| `Set<T>` | `Set<T>` | Collection |
| `Map<K,V>` | `Map<K,V>` | Map |
| `HashMap` | `java.util.HashMap` | HashMap 직접 사용 |
| `int[]` / `string[]` | Array | 배열 타입 |

### 참조/기타 타입

| DSL 타입 | Java 타입 | 비고 |
|---------|----------|------|
| `Object` | `Object` | 범용 객체 타입 (Ossie: `Opaque`) |
| `EntityName` | Entity reference | @ManyToOne 자동 적용 (생략 가능) |
| `EnumName` | Enum reference | 열거형 참조 |

---

## 4. DTO 정의

### 기본 구조
```yaml
dto:
  EntityNameDTO:
    meta: dto @template(EntityName)
    fields:
      additionalField: string -- extra field
      excludedField: -- -- removes from template

  EntityNameSrchDTO:
    meta: dto @searchable(entity=EntityName, pageSize=20)
    fields:
      name: string @search(like)
      status: List<Status> @search(in)

  CustomDTO:
    meta: dto
    fields:
      field1: string
      field2: int
```

### Meta 어노테이션
| 어노테이션 | 설명 |
|-----------|------|
| `@template(Entity)` | 엔티티 필드 상속. 다중 가능: `@template(A,B)` |
| `@searchable(entity=E, pageSize=n)` | 검색 DTO 생성 (Specification 자동 변환) |
| `@jsonview` / `@jsonview(auto)` | JsonView 기반 필드 가시성 제어 활성화 |

### 필드 어노테이션
| 어노테이션 | 설명 |
|-----------|------|
| `@ref(field.path)` | 중첩 엔티티 필드 참조 |
| `@view(cru)` | JsonView 필드 가시성 지정 (c=create, u=update, r=read) |
| `--` (value) | 템플릿에서 해당 필드 제외 |

### DTO 예시
```yaml
dto:
  ArticleDTO:
    meta: dto @template(Article)
    fields:
      boardName: string @ref(board.name) -- Board name via ref
      boardType: BoardType @ref(board.boardType) -- Board type via ref
      createDate: -- -- Excluded from template

  ArticleSrchDTO:
    meta: dto @searchable(entity=Article, pageSize=50)
    fields:
      title: string @search(like)
      boardType: List<BoardType> @search(in) @ref(board.boardType)
      createDateFrom: date @search(between)
      createDateTo: date @search(between)
```

---

## 5. Enumeration 정의

### 기본 구조
```yaml
enumeration:
  EnumName:
    meta: enum @db(field) @json(field)
    fields:
      field1: type [@annotations]
      field2: type
    enum:
      VALUE1: val1,val2
      VALUE2: val1,val2
```

### Meta 어노테이션
| 어노테이션 | 설명 |
|-----------|------|
| `@db(field)` | DB 저장시 사용할 필드 |
| `@json(field)` | JSON 직렬화시 사용할 필드 |

### 필드 어노테이션
| 어노테이션 | 설명 |
|-----------|------|
| `@code` | DB 저장 + JSON 직렬화 필드 (db + json 동시 지정) |
| `@length(n)` | 문자열 길이 |

### Enumeration 예시
```yaml
enumeration:
  BoardType:
    meta: enum @db(code) @json(code)
    fields:
      code: string(2) @length(3)
      name: string(50)
    enum:
      PUBLIC: PB,Public
      PRIVATE: PV,Private
      NOTICE: NT,Notice

  UserStatus:
    meta: enum
    fields:
      status: string(1) @code
      description: string(20)
    enum:
      ACTIVE: A,Active
      INACTIVE: I,Inactive
      SUSPENDED: S,Suspended
```

---

## 6. Repository 정의

엔티티의 Spring Data 리포지터리에 쿼리 메서드를 추가합니다. **키는 엔티티 이름**이고,
메서드는 `methods` **목록**으로 적습니다.

### 기본 구조
```yaml
repository:
  EntityName:                 # 엔티티 이름 (EntityNameRepository 아님)
    methods:
      - id: methodName        # 메서드 이름
        params:               # 파라미터 (이름: DSL 타입)
          paramName: string
        return: ReturnType    # List<Entity>, Optional<Entity>, Entity, Page<Entity>, long ...
        query: "JPQL"         # 생략하면 Spring Data 파생 쿼리 (메서드 이름으로 해석)
        nativeQuery: false    # true 면 Native SQL
        pageable: false       # true 면 마지막 파라미터로 Pageable 추가
```

> **주의**: `methods` 목록이 없는 항목(예: `ProductRepository:` 아래에 메서드를 키로 적는 형식)은
> 읽히지 않습니다. 생성기가 경고를 출력하고 그 항목을 건너뜁니다.

### Repository 예시
```yaml
repository:
  Stock:
    methods:
      # 파생 쿼리 - query 없이 이름만으로
      - id: findByItemItemCodeAndWarehouse
        params:
          itemCode: string
          warehouse: string
        return: Optional<Stock>

      # JPQL
      - id: findLowStock
        params:
          threshold: int
        return: List<Stock>
        query: "SELECT s FROM Stock s WHERE s.onHand < :threshold ORDER BY s.onHand"

      # 페이징
      - id: findByWarehouse
        params:
          warehouse: string
        return: Page<Stock>
        pageable: true
```

### DDD / 헥사고날 번들에서 (포트로 노출)

`ddd-repository-port` 가 있는 번들에서는 선언한 메서드가 포트에도 나타나고,
어댑터가 Spring Data 메서드로 위임합니다.

| 선언 | 놓이는 곳 | 이유 |
|---|---|---|
| 조회 + 반환 `Root` / `Optional<Root>` / `List<Root>` + 페이징 아님 | `RootRepositoryPort` (도메인) | 도메인 서비스가 애그리거트를 찾는 질문 |
| 그 외 (`Page`, DTO 투영, count, 수정 쿼리) | `RootQueryPort` (애플리케이션) | 화면·조회용이거나 애그리거트를 거치지 않는 일괄 쓰기 |

```java
// StockRepositoryPort (생성)
Optional<Stock> findByItemItemCodeAndWarehouse(String itemCode, String warehouse);

// ShippingDomainService (개발자)
Stock stock = stockRepositoryPort.findByItemItemCodeAndWarehouse(code, "MAIN").orElseThrow(...);
```

---

## 7. Port 정의 개요

Transaction Port는 외부 시스템 연동을 위한 인터페이스 정의입니다.

### 기본 구조
```yaml
port:
  ServiceName:
    methodName:
      "@": "@native @pageable"
      param1: type
      "->": ReturnType
      "--": |
        SQL or URL definition
```

### 포트 타입
| 타입 | 설명 |
|------|------|
| `dbms` | 데이터베이스 직접 연동 (Native SQL) |
| `http` | HTTP REST API 연동 |
| `grpc` | gRPC 서비스 연동 |

상세 문법 및 예시: `elasticore-dsl-port-guide.md` 참조

---

## 8. Domain Service 정의

어느 애그리거트에도 속하지 않는 연산입니다. **엔티티가 아니라 도메인에 선언**하는
것이 이 섹션의 요점입니다 — 다른 모든 어노테이션은 엔티티에 쓰는데, 도메인 서비스는
"누구의 것도 아니다"가 정의이기 때문입니다. 루트 하나를 골라 매다는 순간 그 정의와
어긋납니다.

```yaml
# blueprint/{domain}/model/domainservice.yml
domainService:

  Pricing:
    desc: What an order costs once the products it names are priced
    uses: [Order, Product]
```

| 키 | 필수 | 설명 |
|---|------|------|
| `uses` | O | 이 연산이 걸치는 애그리거트들. 도메인 포트(`XxxRepositoryPort`)로 주입됩니다 |
| `desc` | X | 생성 클래스의 주석 |

### 생성물 — 두 파일

`namespace.domainService` 패키지에 두 파일이 생깁니다. 엔티티의 `XxxBase` + `Xxx` 와 같은 나눔입니다.

| 파일 | 성격 | 내용 |
|---|---|---|
| `PricingDomainServiceBase` | **매번 재생성** | `uses` 의 포트 (주입되는 필드) |
| `PricingDomainService` | 한 번 생성, 이후 개발자 소유 | 연산을 쓰는 자리 |

```java
public abstract class PricingDomainServiceBase {       // 재생성 - 고치지 않음

	@Autowired
	protected OrderRepositoryPort orderRepositoryPort;

	@Autowired
	protected ProductRepositoryPort productRepositoryPort;
}

@Service
public class PricingDomainService extends PricingDomainServiceBase {   // 개발자
	// The operation goes here. ...
}
```

**DSL 을 고치면 소스가 따라옵니다.** `uses` 에 애그리거트를 더하면 재생성되는 `PricingDomainServiceBase` 에 포트
필드가 생기고, 개발자 클래스는 고치지 않고 바로 씁니다. 포트를 생성자가 아니라 필드로 주입하는 이유가 이것입니다 —
생성자였다면 개발자 클래스의 생성자도 함께 고쳐야 합니다.

연산 자체를 생성하지 않는 것은 `@factory` 와 같은 이유입니다: 모델은 데이터를 선언하므로 생성할 수 있는 것은
행위가 아니라 행위를 쓸 자리와 연결입니다. 개발자 파일을 아직 고치지 않았다면 그것도 DSL 을 따라 다시 생성됩니다.

### 주의

- `uses` 가 비었거나 하나뿐이면 `DSL_WARN` 이 납니다. 애그리거트 하나에 대한 연산은
  보통 그 애그리거트의 메서드입니다.
- `namespace.domainService` 를 `namespace.service`(응용 서비스)와 같은 패키지로
  두지 마세요. 도메인 서비스는 도메인의 것이고 응용 서비스는 그 위 계층이라, 같은
  자리에 두면 의존 방향이 무너집니다.
- `ddd-domain-service` 규칙이 있는 번들에서만 동작합니다.

---

### 프로세스 매니저 (`processManager:`)

도메인 서비스와 같은 파일에 선언합니다. 이벤트에 반응해 다음 단계를 진행하는 자리입니다.

```yaml
processManager:
  Billing:
    desc: 출고된 수주마다 청구서를 한 장 만든다
    on: [SalesOrderShipped]
    uses: [Invoice]
```

생성물은 도메인 서비스처럼 두 파일입니다.

| 파일 | 성격 | 내용 |
|---|---|---|
| `BillingProcessManagerBase` | **매번 재생성** | `uses` 의 포트, `on` 의 이벤트마다 리스너(커밋 후, 별도 트랜잭션)와 추상 메서드 `handle(...)` |
| `BillingProcessManager` | 한 번 생성, 이후 개발자 소유 | 이벤트마다 `handle(...)` — 다음 단계를 쓰는 자리 |

**DSL 을 고치면 소스가 따라옵니다.**
- `on` 에 이벤트를 더하면 재생성되는 Base 에 리스너와 추상 `handle(새 이벤트)` 가 생깁니다. 개발자 클래스에 그 단계를
  쓰기 전까지 컴파일러가 정확히 알려 줍니다: `LoyaltyProcessManager is not abstract and does not override abstract
  method handle(OrderCancelledEvent)` (`elasticore-template-ddd` 에서 실제로 확인한 메시지). 업무 명령(`commands:`)과
  같은 방식이라, 단계가 조용히 빠지지 않습니다.
- `uses` 에 애그리거트를 더하면 Base 에 포트 필드가 생기고, 개발자 클래스는 고치지 않습니다.
- `on` 에서 이벤트를 빼면 개발자 클래스의 `@Override handle(그 이벤트)` 가 덮어쓸 것이 없어 컴파일 오류가 납니다 —
  지울 코드를 컴파일러가 가리킵니다.
- 개발자 파일을 아직 고치지 않았다면 그것도 DSL 을 따라 다시 생성됩니다.
- 이 나눔 전에 만들어 이미 고친 파일은 그대로 두고 Base 만 새로 생깁니다. 생성기가 경고합니다
  (`... does not extend XxxBase, which is regenerated from the DSL ...`): `extends XxxBase` 를 붙이고, 리스너를
  `protected void handle(...)` 로 바꾸고(어노테이션 제거), 생성자를 지우세요. 파일을 지우면 새로 생성됩니다.
- `handle` 은 Base 의 리스너가 연 트랜잭션(`REQUIRES_NEW`) 안에서 실행됩니다. 같은 객체 안의 호출이라 `handle` 에
  붙인 `@Transactional` 은 적용되지 않습니다 — 필요 없습니다.
- 포트는 주입되는 필드라, Spring 없이 단위 테스트하려면 필드를 직접 채웁니다 (Java 는 같은 패키지에서 `protected`
  필드에 대입, Kotlin 은 `lateinit var` 에 대입 — 채우지 않고 쓰면 `UninitializedPropertyAccessException`).

자세한 것은 `elasticore-rule-guide.md` 의 프로세스 매니저 절.

**다른 컨텍스트의 이벤트**는 필드 타입에서 다른 도메인 모델을 `catalog:Product` 로 적듯이 `컨텍스트:루트이벤트` 로 적습니다.

```yaml
# blueprint/billing/model/domainservice.yml
processManager:
  Receipting:
    desc: 주문이 들어오면 영수증을 한 장 만든다
    on: [shop:OrderPlaced]       # shop 컨텍스트(폴더 이름 또는 domainName)의 Order 루트, Placed 이벤트
    uses: [Receipt]
```

- 생성물은 같은 두 파일이고, 이벤트 클래스만 `shop` 의 것입니다 (`shop` 의 `event` 네임스페이스에서 import).
  `ReceiptingProcessManagerBase` 의 리스너 `on(OrderPlacedEvent event)` 가 받아 개발자 클래스의
  `handle(OrderPlacedEvent event)` 로 넘기고, 거기서 이벤트에 실은 값(`@event(Placed(totalAmount))`)을 그대로 읽습니다.
- 어느 한쪽이라도 `context:` 를 선언했다면 경계 검사를 받습니다: `billing` 이 선언했다면 그 `uses` 에 `shop`, `shop` 이
  `publishes` 를 선언했다면 거기 `OrderPlaced` (`elasticore-env-guide.md` 6.1.4). 빠지면 생성 전에 빌드 실패.
- `OrderPlacedEvent` 처럼 클래스 이름으로 적어도 됩니다 (`publishes` 도 마찬가지).
- 받지 못하는 이름은 DSL 경고 후 그 핸들러만 빠집니다:
  - 컨텍스트 없이 `OrderPlaced` 만 적었는데 다른 컨텍스트의 이벤트일 때 — `... OrderPlaced is an event of shop - write shop:OrderPlaced`
  - 없는 이벤트(`shop declares no event OrderShiped`), 없는 컨텍스트(`there is no context nowhere`)
  - 상대 컨텍스트의 규칙이 이벤트 클래스를 만들지 않을 때 (예: `jpa-springboot-v3` 만 쓰는 컨텍스트, `publisher.suppress`
    로 `ddd-event` 를 끈 컨텍스트 — `ddd-event` 가 든 ddd·헥사고날 번들이어야 함)
  - 멤버 엔티티(다른 루트에 속한 것)나 키가 없는 엔티티의 `@event` 는 이벤트를 만들지 않으므로 받을 수 없습니다.
- 같은 이벤트를 두 번 적으면 핸들러는 하나만 생기고 DSL 경고. 엔티티가 하나도 없는 컨텍스트는 아무것도 생성되지 않으므로
  (DSL 경고) 받는 쪽에도 자기 엔티티가 하나는 있어야 합니다.
- 전달은 Spring 의 애플리케이션 이벤트라서 **같은 애플리케이션 안**(모듈러 모놀리스)에서 이루어집니다. 컨텍스트를 따로
  배포한다면 내보내는 쪽은 Outbox(`outbox: true`)로 브로커에 보내고, 받는 쪽은 그 메시지를 직접 구독해야 합니다 — 이
  부분은 생성하지 않습니다.
- 예시: `elasticore-template-ddd` 의 `billing` (`CrossContextEventTest` 가 shop 에서 주문을 커밋하면 billing 이 받고,
  롤백하면 받지 않는 것을 확인).

## 9. 고급 기능

### 복합 키
여러 `@id` 필드 정의 시 자동으로 KeyDTO 클래스가 생성됩니다.
```yaml
entity:
  OrderItem:
    meta: entity
    fields:
      orderId: string @id
      itemSeq: int @id
      quantity: int
```

### 임베디드 엔티티
```yaml
entity:
  Address:
    meta: entity @embeddable
    fields:
      postNo: string(5) @Column(name="post_no", length=5)
      baseAddr: string(200) @Column(name="base_addr", length=200)

  User:
    meta: entity
    fields:
      userId: long @id @sequence
      address: Address -- Embedded address
```

### 상속
```yaml
entity:
  Vehicle:
    meta: entity @abstract
    fields:
      id: string @id
      brand: string(50)

  Car:
    meta: entity @extend(Vehicle)
    fields:
      doors: int
      fuelType: FuelType
```

### 다중 템플릿 상속
```yaml
dto:
  MergedDTO:
    meta: dto @template(EntityA, EntityB)
    fields:
      extraField: string
```

---

## 10. 주의사항 및 해결방법 (Known Limitations & Workarounds)

### 10.1 잘못된 DSL 타입 (Invalid DSL Types)

필드 타입은 "3. 필드 타입" 의 표에 있는 것만 씁니다. 표에 없는 단어는 엔티티에 그대로 쓰여(`private uuid id;`) 컴파일되지 않습니다.

| 잘못된 타입 | 대신 |
|-----------|------|
| `uuid` | `string(36)` - 키면 `string @id @genid` (UUID 생성) |
| `short`, `byte` | `int` |
| `char` | `string(1)` |
| `OffsetDateTime`, `ZonedDateTime` | `timestamp` |

`json`, `clob`, `blob`, `timestamp`, 크기 없는 `decimal` 은 3.0.260925 부터 표대로 생성됩니다 (그 전에는 컴파일되지 않았음).

```yaml
entity:
  Product:
    fields:
      productId: string @id @genid       # UUID 키 (UUID.randomUUID())
      metadata:  json                    # String + JSON 컬럼
      document:  clob                    # String + @Lob
      createdAt: timestamp               # Instant (한 순간)
```

### 10.2 @min/@max와 실수 값 (Decimal Values)

Jakarta Bean Validation의 `@Min`, `@Max` 어노테이션은 `long` 타입만 허용합니다. 실수 값(0.0, 5.0 등)을 사용하면 컴파일 오류가 발생합니다.

#### 예시
```yaml
# WRONG - decimal values cause compile errors
fields:
  rating: double @min(0.0) @max(5.0)   # Generates @Min(0.0) - compile error
  temperature: float @min(-273.15)     # Generates @Min(-273.15) - compile error

# CORRECT - use integer values
fields:
  rating: double @min(0) @max(5)        # Generates @Min(0) @Max(5) - valid
  temperature: float @min(-273)         # Generates @Min(-273) - valid
```

#### 해결방법
- 정수값만 사용 (0.0 → 0, 5.0 → 5)
- 실수 범위 검증이 필요한 경우 커스텀 Bean Validation 어노테이션 사용 권장

### 10.3 @ManyToOne @id 필드의 SQL 예약어 (Reserved SQL Keywords)

`@ManyToOne` 필드가 `@id`(복합키)로 사용될 때, 필드명이 그대로 SQL 컬럼명이 됩니다. SQL 예약어(order, group, select 등)를 사용하면 테이블 생성이 실패합니다.

#### 예시
```yaml
# WRONG - 'order' is a SQL reserved keyword
entity:
  OrderItem:
    meta: entity
    fields:
      order: Order @id @ManyToOne       # SQL: order_id - reserved keyword error
      itemSeq: int @id

# SOLUTION 1 - Use non-reserved field name
entity:
  OrderItem:
    meta: entity
    fields:
      orderRef: Order @id @ManyToOne    # SQL: order_ref_id - safe
      itemSeq: int @id

# SOLUTION 2 - Manually add @Column annotation after code generation
# Generated Java code after build:
# @Column(name = "\"order_id\"")  // Add quotes manually for reserved keywords
```

#### 일반적인 SQL 예약어
`order`, `group`, `select`, `from`, `where`, `table`, `index`, `key`, `value`, `user`, `date`, `time`, `timestamp`

#### 해결방법
1. **권장**: SQL 예약어가 아닌 필드명 사용 (order → orderRef, user → userRef)
2. 생성된 코드에서 `@Column(name = "\"field_id\"")`로 수동 추가 (따옴표로 감싸기)

### 10.4 @audited와 연관관계 (Relationships with Audited Entities)

엔티티가 `@audited` (Hibernate Envers)인 경우, 감사되지 않는 엔티티에 대한 모든 관계 필드는 `@org.hibernate.envers.NotAudited` 어노테이션이 필요합니다.

#### 예시
```yaml
# Entity with @audited
entity:
  Order:
    meta: entity @audited
    fields:
      orderId: long @id @sequence
      customer: Customer @ManyToOne      # Customer is NOT @audited
      orderItems: List<OrderItem> @OneToMany(mappedBy="order")  # OrderItem is NOT @audited

# Code generator automatically adds @NotAudited for relationships to non-audited entities
# Generated Java code:
# @NotAudited
# @ManyToOne
# private Customer customer;
#
# @NotAudited
# @OneToMany(mappedBy = "order")
# private List<OrderItem> orderItems;
```

#### 주의사항
- 코드 생성기가 대부분의 경우 자동으로 `@NotAudited` 추가
- 생성 후 검증 필요: 감사되지 않는 엔티티와의 관계에 `@NotAudited` 확인
- 누락된 경우 수동으로 추가하거나 YAML에 `@notaudited` 명시

```yaml
# Explicitly mark in YAML if needed
fields:
  customer: Customer @ManyToOne @notaudited
```

### 10.5 권장사항 요약

1. **타입 선택**: "3. 필드 타입" 표에 있는 타입만. `uuid` 대신 `string(36)`, UUID 키는 `string @id @genid`
2. **검증 범위**: `@min/@max`에 정수값만 사용. 실수 검증은 커스텀 어노테이션 고려
3. **필드명**: SQL 예약어 회피. 특히 `@id` 복합키 필드
4. **감사 설정**: `@audited` 엔티티의 관계 필드는 생성 후 `@NotAudited` 검증
5. **코드 리뷰**: 생성된 Java 코드 컴파일 후 오류 확인 필수
