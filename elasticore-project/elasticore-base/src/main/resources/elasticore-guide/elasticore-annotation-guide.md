# ElastiCORE Annotation Guide

> **버전 표기 규칙**: 각 어노테이션에 `Since: x.x` 표기가 있는 경우, 해당 버전 이상에서만 사용 가능합니다. 표기가 없는 어노테이션은 ElastiCORE 1.0부터 사용 가능합니다.

## 1. Entity Meta Annotations

엔티티 선언부(`meta:`)에 사용하는 어노테이션. 복수 조합 가능.

### @expose
DTO, Repository, Service, Controller, SearchDTO 자동 생성. 기본적으로 string 필드는 auto(값에 `%` 가 있으면 like, 아니면 정확 일치), datetime 필드는 between 검색 자동 적용.
```yaml
Product:
  meta: entity @expose(30)  # pageSize=30, Controller 포함 전체 CRUD 생성
```

### @service
`@expose`와 동일하나 Controller 생성 제외. 내부 서비스 용도.
```yaml
InternalData:
  meta: entity @service(20)
```

### @audited
JPA Envers `@Audited` 어노테이션 추가. 데이터 변경 이력 추적용 (dependency 설정 필요).
```yaml
Order:
  meta: entity @audited
```

### @pageable
페이징 처리 지원. 기본 pageSize=20.
```yaml
Article:
  meta: entity @pageable(50)
```

### @abstract
추상 엔티티. 테이블 생성 안함, 상속 전용.
```yaml
AuditEntity:
  meta: entity @abstract
```

### @extend(ParentEntity)
부모 엔티티 상속. `extends ParentEntity` 생성.
```yaml
User:
  meta: entity @extend(AuditEntity)
```

### @embeddable
JPA `@Embeddable` 값 객체. 주소, 좌표 등에 사용.
```yaml
Address:
  meta: entity @embeddable
```

v3(DDD·hexagonal) 에서는 DTO 가 값 객체를 **클래스 그대로** 담으므로, DTO 필드에 붙는 규칙이
값 객체의 필드에 붙습니다 (`jpa-value-object-boundary`):

```yaml
CreditCard:
  meta: entity @embeddable
  fields:
    creditCardNumber: string(30)! @writeonly   # 응답에 나가지 않음, 요청에 없으면 400
    creditCardExpDate: string(5)!
```

```java
@NotNull
@Size(max=30)
@JsonProperty(access=JsonProperty.Access.WRITE_ONLY)
@Column(name = "credit_card_number", nullable = false, length = 30)
private String creditCardNumber;

// PurchaseOrderDTO
@Valid
private CreditCard creditCard;
```

값 객체의 컬럼은 그것을 담는 엔티티의 테이블에 생깁니다. 그래서 어느 엔티티가 값 객체를 **선택으로**
담으면(`shipTo: Address @Embedded`, `!` 없음) 그 값 객체의 `!` 필드는 컬럼에 `nullable = false` 를 붙이지
않습니다 (3.0.261021 부터, v3). 값이 오면 DTO 가 필수 필드를 검사하고(400), 값이 없는 행은 그대로 저장됩니다.
전에는 배송지를 보내지 않은 주문이 `NULL not allowed for column "ZIPCODE"` (400 `required`) 로 저장되지 않았습니다.
- 담는 엔티티가 **다른 도메인**에 있어도 같습니다(`common` 의 `Address` 를 `shop` 의 주문이 선택으로 담음).
- 선택으로 담은 값 객체 **안의** 값 객체(`contact: Contact @Embedded` 의 `place: Place! @Embedded`)도 선택입니다 -
  바깥 값이 없으면 안쪽 컬럼도 비기 때문입니다.
- 필수로 담는 표시는 `!`, `@required`, `@nn` 입니다. 늘 필수로만 담는 값 객체(`card: Card! @Embedded`,
  `card: Card @Embedded @required`)는 지금처럼 NOT NULL 입니다.

> **주의**: 값 객체는 통째로 교체됩니다. 응답에 없는 `@writeonly` 필드를 가진 값 객체를 읽어서
> 그대로 다시 저장하면 그 필드는 비어서 옵니다 - 필수면 400, 선택이면 null 로 저장됩니다.
> 카드처럼 **생성할 때만 받는 값**에 쓰고, 수정이 필요하면 값 전체를 다시 받으세요.

v3 매퍼는 `@writeonly` 값을 엔티티 -> DTO 로 복사하지 않습니다(`@password` 와 같음). 서버 코드가
`findById` 한 DTO 를 `update` 에 다시 넘겨도 저장된 해시가 "새 비밀번호"로 들어가지 않습니다.

### @table(name) / @tbl(name)
테이블명 명시적 지정. 미지정시 엔티티명을 snake_case 변환.
```yaml
UserProfile:
  meta: entity @table(user_profile_tbl)
```

### @schema(name)
> **Since: 2.1.260409**

DB 스키마 지정. `@Table(schema="name")` 생성. 엔티티별 개별 지정 또는 env.yml `tableSchema`로 도메인 전체 일괄 지정 가능.

**엔티티별 지정** (우선순위 높음):
```yaml
Customer:
  meta: entity @schema(schema_a)
  fields:
    id: long @id @sequence
    name: string(100)
# 생성: @Table(name="customer", schema="schema_a")
```

**도메인 전체 지정** (env.yml):
```yaml
tableSchema: schema_a
# 해당 도메인의 모든 entity에 schema="schema_a" 적용
```

우선순위: 엔티티 `@schema` > env.yml `tableSchema`

### @Index(name, columnList, unique)  / @idx
데이터베이스 인덱스 정의. `@Table(indexes=...)` 생성.
```yaml
Message:
  meta: entity @Index(name="idx_msg_date", columnList="create_date,status")
```

### @rollup
부모 테이블로 컬럼 통합. 자신의 테이블 생성 안함. `@id`가 있는 상위 entity를 `@extend`한 상태에서 사용.
```yaml
Item:
  meta: entity
  fields:
    id: long @id @sequence
    name: string
    dtype: string @formula("dtype")

Album:
  meta: entity @extend(Item) @rollup
  fields:
    artist: string
```
판별값(`@DiscriminatorValue`)은 `@rollup(discriminator=ALB)`(또는 `value=`·`type=`), 없으면 `@rollup(target=...)`(또는
`entity=`), 그것도 없으면 엔티티 이름(`"Album"`)입니다. `@rollup(Item)` 처럼 이름 없이 쓴 값은 판별값이 아닙니다 - 2.x 와
3.0 의 모든 번들이 같습니다.

### @terms(용어, 동의어, …)
업무에서 이 엔티티를 부르는 이름과 동의어. 필드와 `metrics:` 의 지표에도 씁니다. 코드 생성에는 영향이 없고,
Apache Ossie 시맨틱 모델(`ossie-export`)의 `ai_context.synonyms` 로 나갑니다 (DSL 가이드 "업무 지표와 업무 용어").
```yaml
PurchaseOrder:
  meta: entity @root @terms(order, purchase, 구매) -- 주문
Item:
  meta: entity @root @terms(pet, 반려동물) -- 판매 품목
  fields:
    unitCost: money(10.2)! @terms(price, 가격)
```

---

## 2. Field Annotations

### 2.1 기본키

#### @id
JPA Primary Key. `@Id` 생성.
```yaml
userId: long @id
```

#### @sequence
자동 증가. `@GeneratedValue(strategy = GenerationType.IDENTITY)` 생성.
```yaml
id: long @id @sequence
```
id
#### @genid
저장 직전(`@PrePersist`) 키가 비어 있으면 값을 만들어 넣습니다. 무엇을 만드는지는 길이로 정해집니다.

| DSL | 생성 코드 | 값 |
|---|---|---|
| `string @id @genid` | `java.util.UUID.randomUUID().toString()` | UUID (36자) |
| `string(n) @id @genid` | `IdUtils.newId(n)` | n자 (n ≥ 4): 엔티티 클래스 전체 이름(패키지 포함)에서 딴 3자 + 영숫자 난수 n-3자 (UUID 아님) |
| `string @id @genid(com.acme.Ids.next)` | `com.acme.Ids.next()` | 직접 만든 정적 메서드 |

```yaml
orderId: string(36) @id @genid   # 36자 ElastiCORE ID
id:      string @id @genid       # UUID
```

### 2.2 데이터 제약

#### @length(n) / @len(n) / @size(n)
문자열 길이 제한. `@Column(length=n)` + `@Size` 생성. 타입 괄호 방식 우선 사용 권장.
```yaml
name: string(100)                    # 권장: 타입에 직접 지정
title: string @size(min=5, max=200)  # 복합: min/max 동시 지정
password: string(255)! @length(min=8)   # 타입 길이 + 최소 길이 -> @Size(min=8 , max=255)
```
타입 길이(`string(255)`)와 `@length(min=..)` 를 함께 쓰면 최대는 타입 길이, 최소는 `@length` 의 값입니다.
(2.x 에서는 이 조합에서 최소 길이가 조용히 빠졌습니다.)

#### @default(value)
필드 기본값. 스마트 인용부호 처리: 큰따옴표 유지, 작은따옴표를 큰따옴표 변환, 미지정시 자동 추가.
```yaml
status: string @default("ACTIVE")        # 그대로 유지
category: string @default('GENERAL')     # 큰따옴표로 변환
isActive: boolean @default(true)
processType: ProcessType @default(ProcessType.HANG)
createdAt: datetime @default(now)        # LocalDateTime.now() - date, time, timestamp(Instant) 도 now
```

**서버가 정하는 생성 시각**은 `@readonly` 와 함께 씁니다 - 손으로 `beforeSave` 를 쓰지 않아도 됩니다:

```yaml
orderedAt: datetime @readonly @default(now)   # 만들 때 그 시각, 요청 값은 무시, 수정해도 그대로
joinedOn:  date @readonly @default(now)       # 오늘
```

엔티티 필드의 초기값(`= LocalDateTime.now()`)이라 행이 만들어질 때 정해지고, `@readonly` 라 요청이 보낸 값은
쓰이지 않으며, 수정할 때는 DB 에서 읽은 값이 그대로 남습니다. DTO 에는 기본값이 붙지 않습니다(날짜 기본값은 DTO 에서
뺌). `timestamp` 의 기본값도 `now` 든 다른 값(`@default(java.time.Instant.EPOCH)`)이든 엔티티에만 붙습니다(3.0.261021
부터 - 전에는 DTO 에도 `@Builder.Default ... = java.time.Instant.EPOCH` 가 붙었습니다). 마지막 수정 시각처럼
**매번 바뀌는** 값은 `@jpa:org.springframework.data.annotation.LastModifiedDate` 와 `@EnableJpaAuditing` 을 씁니다.

#### @notnull / @notblank / @required
NOT NULL 제약. `@Column(nullable = false)` 생성. `@id`와 함께 사용 불필요.
```yaml
email: string(100) @notnull
```

#### @updatable(false)
수정 불가 필드. `@Column(updatable = false)` 생성.
```yaml
createdDate: datetime @updatable(false)
item:        Item @ManyToOne @updatable(false)   # 리뷰는 처음 정한 상품에 남는다
```
DDD·헥사고날 번들에서 다른 루트로 가는 참조는 식별자 필드(`itemId`)로 쓰이므로, 그 식별자 필드가 `@updatable(false)` 를
물려받습니다: `@Column(name = "item_item_id", updatable = false)` 이고, 매퍼는 행에 값이 없을 때(생성)만 씁니다 - 수정
요청이 다른 `itemId` 를 보내도 저장된 값과 응답은 처음 상품 그대로입니다.

#### @readonly / @noinput - 서버가 정하는 필드
응답에는 나가고 **요청으로는 받지 않는** 필드입니다. 가입일·등급·처리 번호처럼 서버 코드가 값을 정하는
필드에 씁니다. `@noinput` 은 같은 뜻의 별칭입니다.

```yaml
Member:
  meta: entity @root @editable @expose(20)
  fields:
    memberId: long @id @sequence
    name: string(50)!
    joinedAt: date @readonly          # 서비스의 beforeSave 가 채운다
    grade: string(10)! @noinput       # 필수 컬럼이지만 요청은 보내지 않는다
```

| 생성물 | 2.x 호환 (`jpa-springboot`) | v3 번들 (`jpa-springboot-v3`, DDD·헥사고날, Kotlin) |
|---|---|---|
| DTO `@JsonProperty(access=READ_ONLY)` | 있음 | 있음 |
| DTO `@Schema(... accessMode = READ_ONLY)` | 없음 | 있음 |
| DTO `@NotNull` / `@NotBlank` (`!`, `@notblank`) | 남음 | **빠짐** - 요청이 보낼 수 없는 값이므로 |
| 매퍼 엔티티 -> DTO (응답) | 씀 | 씀 |
| 매퍼 DTO -> 엔티티 (`toEntity`, `mapping(dto, entity, ...)`, EntityManager 판 포함) | 씀 | **쓰지 않음** |
| 엔티티 컬럼 (`nullable = false` 등) | 그대로 | 그대로 - 서버가 채워야 합니다 |

v3 생성 결과 (Java, 위 `Member`):

```java
// MemberDTO.java
@Schema(description = "grade" , requiredMode=Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.READ_ONLY )
@Size(max=10)
@JsonProperty(access=JsonProperty.Access.READ_ONLY)
private String grade;

// LibraryMapper.java - mapping(MemberDTO from, Member to, boolean isSkipNull, MappingContext c)
setVal(from.getMemberId(), to::setMemberId, isSkipNull);
setVal(from.getName(), to::setName, isSkipNull);
// joinedAt, grade 는 없습니다. 반대 방향 mapping(Member from, MemberDTO to, ...) 에는 있습니다.
```

값은 서버 코드가 넣습니다. 확장 서비스의 `beforeSave` 는 생성·수정·일괄 수정·조건 수정 네 경로가 모두 지나갑니다.

```java
@Override
protected void beforeSave(Member entity) {
    if (entity.getJoinedAt() == null) entity.setJoinedAt(LocalDate.now());
}
```

v3 에서 `string(10)! @readonly` 필드에 클라이언트가 값을 보낸 POST 는 200 이고, 응답에는 `beforeSave` 가
넣은 값이 나갑니다. DTO 에 `@NotNull` 이 남아 있으면 같은 요청은 400 (`must not be null`) 입니다 - Jackson 이
값을 버린 뒤에 검사하기 때문입니다.

주의:
- v3 에서는 **서버 코드가 DTO 에 넣은 값도 매퍼가 버립니다.** `dto.setGrade("A")` 다음 `service.save(dto)` 를
  하던 코드는 값이 저장되지 않습니다. 엔티티에 넣으세요 (`beforeSave`, 루트의 메서드).
- `@id` 에 붙은 `@readonly` (`@id @genid @readonly`) 는 JSON 입력만 막습니다. 매퍼는 식별자를 계속 씁니다.
- 엔티티 필드에 붙이면 그 엔티티의 모든 DTO 에 적용됩니다. `dto.yml` 의 DTO 가 템플릿 필드를 다시 적으며
  `@readonly` 를 붙이면 **그 DTO 의 매핑만** 이 필드를 쓰지 않습니다.
- `@jpa:CreatedDate`·`@jpa:Version` 같은 영속성 필드는 `@readonly` 없이도 v3 매퍼가 쓰지 않습니다 (§2.8).
- springdoc 은 `@JsonProperty(access=READ_ONLY)` 만으로도 OpenAPI 에 `readOnly: true` 를 냅니다. v3 의
  `@Schema(accessMode = READ_ONLY)` 는 같은 사실을 `@Schema` 만 읽는 도구에도 적어 둡니다.

DDD 번들에서 나뉜 루트(`XxxBase`)의 `@readonly` 필드는 여전히 잠긴 필드입니다: setter 는 `protected` 이고
`creation()` 에 setter 가 있습니다. 서버 코드는 새 루트에 `entity.creation().setLoanDate(v)` 로 넣고, 그 뒤에는
루트의 메서드로 바꿉니다. **만든 뒤에 바뀌는 서버 값**(반납일, 상태)은 `@behavior` 가 맞습니다 - 요청으로
받지 않는 것은 같고, `creation()` 에도 없어 루트의 메서드만 바꿉니다 ([@behavior](#behavior--엔티티-행위-ddd-룰셋)).

| | `@readonly` | `@behavior` (필드) |
|---|---|---|
| 적용되는 엔티티 | 모든 엔티티, v3 번들 | DDD 번들의 나뉜 루트만 (`@editable` 루트·멤버에서는 경고만) |
| 요청 (JSON, 매퍼) | 받지 않음 | 받지 않음 |
| 나뉜 루트의 `creation()` | 있음 - 새 루트에 서버 코드가 넣음 | 없음 |
| 바꾸는 쪽 | 서버 코드 (`beforeSave`, 루트의 메서드) | 루트의 메서드 |

`creation().setX(null)` 은 새 루트에서 아무 일도 하지 않습니다 (요청이 보내지 않은 값이 `@default` 를 덮지 않게
하는 장치). 값을 지우는 길이 아니므로, 지우려면 루트의 메서드에서 `protected` setter 를 부릅니다.

#### @min(value) / @max(value) / @min(value, '메시지')
숫자 범위 검증. Bean Validation `@Min`/`@Max`(BigDecimal 은 `@DecimalMin`/`@DecimalMax`) 생성.
두 번째 인자로 메시지를 줄 수 있습니다.
```yaml
age: int @min(18) @max(65)
unitPrice: bigdecimal(12.2)! @min(0.01, '단가는 0보다 커야 한다')
onHand: int! @min(0, '재고는 음수가 될 수 없다')
```

**DDD 번들에서는 루트의 도메인 규칙이기도 합니다.** DTO 는 요청이 들어올 때 검사하고(400, 필드별 메시지),
루트 엔티티의 불변식 리스너는 **저장할 때마다** 같은 조건을 검사합니다(422). 도메인 서비스가 재고를 음수로
만드는 것처럼 요청을 거치지 않는 변경도 막힙니다. 그래서 필드 하나에 대한 규칙은 `@invariant` 대신 필드에
쓰면 됩니다. `@invariant` 는 **두 필드 이상을 비교하는 규칙**에만 씁니다.

#### @minsize(n, '메시지') / @maxsize(n, '메시지') - 컬렉션 개수
컬렉션이 담을 멤버 수. DTO 에는 `@Size(min=n, message=...)` (n 이 1 이상이면 `@NotNull` 도 - 목록을 아예 보내지 않아도 400), DDD 루트에는 저장 시점 검사(422)가 됩니다.
최소 개수는 컬렉션이 아예 없을 때(null)도 위반입니다. n 이 1 이상이면 TypeScript 클라이언트의 `validate...()` 도
같은 메시지로 먼저 검사합니다 - `if (!partial && (value.lines == null || value.lines.length < 1)) errors.lines = "..."`
(부분 수정 `partial` 에서는 묻지 않음). `...Fields` 상수에는 컬렉션이 들어가지 않습니다.
```yaml
lines: List<SalesOrderLine> @OneToMany(mappedBy="order") @minsize(1, '수주에는 품목이 하나 이상 있어야 한다')
```
DTO 를 `dto.yml` 에서 직접 선언하고 같은 필드를 다시 적으면 그 필드는 DSL 선언을 따르므로, 요청 단계(400)
검사를 원하면 DTO 필드에도 적습니다. 저장 시점 검사(422)는 엔티티의 선언만으로 항상 걸립니다.

#### @pattern('정규식', '메시지') / @pattern(regexp="...", message="...")
문자열이 정규식과 **전체가** 맞아야 한다는 규칙. DTO 에 Bean Validation `@Pattern` 이 생기고(요청 검사, 400),
TypeScript 클라이언트의 `validate...()` 도 같은 정규식과 메시지로 검사합니다.
`@min` 처럼 값(정규식) 다음에 메시지를 적습니다. 이름을 붙인 형식도 같습니다.
```yaml
isbn:  string(13)! @id @pattern('^[0-9]{13}$', 'ISBN must be 13 digits') -- ISBN, 13 digits
code:  string(10) @pattern('^[A-Z]{3}[0-9]+$') -- Shelf code          # 메시지 생략
range: string(13) @pattern(regexp="^[0-9]{10,13}$", message="10 to 13 digits")
email: string @pattern(regexp="^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message="Invalid email")
```
생성되는 DTO (v3 / Kotlin 번들):
```java
	@Schema(description = "ISBN, 13 digits" , requiredMode=Schema.RequiredMode.REQUIRED )
	@NotNull
	@Pattern(regexp = "^[0-9]{13}$", message = "ISBN must be 13 digits")
	@Size(max=13)
	private String isbn;

	@Schema(description = "Shelf code"  )
	@Pattern(regexp = "^[A-Z]{3}[0-9]+$", message = "Shelf code is not in the expected form")
	@Size(max=10)
	private String code;
```
TypeScript 클라이언트:
```ts
else if (value.isbn && !new RegExp("^(?:^[0-9]{13}$)$").test(String(value.isbn))) errors.isbn = "ISBN must be 13 digits";
```
- **메시지**는 생략할 수 있습니다. 그러면 서버와 클라이언트 모두 `"<라벨> is not in the expected form"` 이라고 합니다
  (라벨은 `--` 주석, 없으면 필드 이름).
- **백슬래시는 두 번** 씁니다. 정규식은 Java 문자열 리터럴의 내용으로 그대로 들어가기 때문입니다: `\\d`, `\\.`.
  `[0-9]` 같은 문자 클래스가 더 쉽습니다. `\d` 처럼 하나만 쓰면 Java 는 컴파일하지 않고(`illegal escape character`)
  TypeScript 는 `d` 로 읽으므로, 생성할 때 DSL 경고가 납니다:
  `Book.digits: @pattern has \d, which a string literal does not read as a backslash. Write it doubled (\\d) or use a class such as [0-9].`
- **Kotlin** 에서는 문자열 템플릿이 되는 `$`(영문자, `_`, `{` 앞의 `$`)를 `\$` 로 바꿔 씁니다. DSL 에는 Java 와 똑같이 적습니다.
  `@pattern('^\\$USD[0-9]+$', 'write it as $USD100')` -> `@Pattern(regexp = "^\\\$USD[0-9]+$", message = "write it as \$USD100")`.
  정규식 끝의 `$` 는 그대로입니다.
- 정규식 안의 **쉼표**는 괜찮습니다(`{10,13}`). 메시지는 따옴표와 괄호 밖의 첫 쉼표 뒤입니다.
  정규식에 `=` 가 들어가면 이름 붙인 형식을 쓰고, 그 값은 큰따옴표로 감쌉니다(작은따옴표 값은 공백에서 끊깁니다).
- **문자열 필드에만** 붙습니다. 날짜 필드의 `@pattern(yyyyMMdd)` 는 지금처럼 날짜 형식(`@DateTimeFormat` / `@JsonFormat`)입니다.
- **DTO 와 TS 클라이언트에만** 들어갑니다. 엔티티(`@Column` 만)와 검색 DTO 에는 없고, `@min` 과 달리 DDD 루트의
  저장 시점 검사(422)도 아닙니다. 정규식은 Swagger `example` 로도 쓰지 않습니다(예시 값이 아니므로).
- 2.x 호환 출력(`jpa-springboot`)은 바뀌지 않았습니다: 이름 붙인 형식에 메시지까지 있을 때만 여러 줄
  `@jakarta.validation.constraints.Pattern( regexp="...", msg="..." )` 를 쓰는데, `msg` 는 Jakarta `@Pattern` 에 없는
  속성이라 컴파일되지 않습니다. 이 수정은 v3 / Kotlin / TypeScript 번들의 출력입니다.

### 2.3 검색 어노테이션
`@search(condition)` - Section 3에서 상세 설명.

### 2.4 참조 및 타입

#### @ref(fieldPath)
DTO에서 다른 엔티티 필드 참조. nested get 생성.
```yaml
boardName: string @ref(board.name)           # r.get("board").get("name")
boardTypeName: string @ref(board.boardType.name)
```

수정(DTO -> 엔티티)에서는 관계 너머의 행에 그 값을 씁니다 (`to.getBoard().setName(from.getBoardName())`). 3.0 규칙
엔진의 v3 번들(`jpa-service-access` 가 든 `jpa-springboot-v3`·`jpa-springboot-kt`)에서는 **누가 쓸 수 있는지 정한
엔티티**(`@access`·`@owner`·`@password`·필드의 `@access(admin)` 이 있는 엔티티)로 가는 `@ref` 는 **읽기 전용**입니다 -
티켓을 고칠 수 있는 사람이 담당자의 로그인이나 비밀번호를 바꾸면 그 엔티티의 검사를 건너뛰기 때문입니다. 관계의 키를
가리키는 `@ref(assignee.memberId)` 는 전처럼 관계가 가리키는 행을 바꿉니다. `@password` 를 읽는 `@ref` 는 DTO 에서 빠지고
DSL 경고가 납니다 (`... is left out: @ref(assignee.password) reads a @password ...`). DDD·헥사고날 번들은 다른 애그리거트로
가는 `@ref` 를 원래 모두 읽기 전용으로 둡니다. 2.x 호환 번들(`jpa-springboot`)은 그대로입니다.

#### @fk(Target) / @fk(Target.field) / @fk(Target.field, groupName)

**Foreign Key 메타데이터** — Primitive 타입 필드에 FK 관계를 선언. JPA `@ManyToOne`을
생성하지 않고 순수 `@Column`으로 유지하므로 **Legacy DB 마이그레이션, 분석 목적,
N+1/lazy loading 회피** 시나리오에 적합.

**별칭**: `@fk`, `@foreignkey`

**필드 타입 요구사항**: 반드시 primitive 타입 (long, int, string 등). Entity 참조
타입에는 `@manytoone` 등 기존 관계 annotation 사용.

##### 단일 FK — Target Entity 참조
```yaml
Article:
  fields:
    id:       long @id @genid
    boardId:  long! @fk(Board)        # Board의 @id 필드 자동 참조
    title:    string(200)!
```

##### 단일 FK — Target Entity.field 명시
```yaml
Article:
  fields:
    id:       long @id @genid
    boardId:  long! @fk(Board.id)     # Board의 id 필드를 명시적으로 참조
    title:    string(200)!
```

##### 복합 FK — 그룹 이름으로 여러 필드를 하나의 논리적 FK로 묶기
```yaml
ArticleAudit:
  fields:
    id:       long @id @genid
    tenantId: long! @fk(Board.tenantId, boardFk)
    boardSeq: long! @fk(Board.seq, boardFk)
    # 두 필드가 'boardFk' 그룹으로 묶여 하나의 복합 FK로 인식됨
```

##### 같은 Target에 여러 FK (그룹으로 구분)
```yaml
Article:
  fields:
    id:               long @id @genid
    # 작성자 FK (writer 그룹)
    writerTenantId:   long! @fk(User.tenantId, writer)
    writerId:         long! @fk(User.id, writer)
    # 최종 수정자 FK (modifier 그룹)
    modifierTenantId: long! @fk(User.tenantId, modifier)
    modifierId:       long! @fk(User.id, modifier)
```

##### 생성되는 Java 코드
`@fk`는 메타데이터이므로 생성 Java 코드는 일반 `@Column`과 동일:
```java
@Column(name = "board_id", nullable = false)
private Long boardId;
```

##### schema.yml 출력
schema.yml에는 FK 메타데이터가 포함됨:
```yaml
Article:
  fields:
    boardId:
      type: long
      annotations: '@Column(name="board_id") @NotNull @fk(Board.id)'
```

##### DSL Validation
- Target entity가 존재하지 않으면 `DSL_WARN` 경고
- `Target.field` 명시 시 해당 필드가 존재하지 않으면 `DSL_WARN` 경고
- `./gradlew elcore -Pmode=valid` 시 빌드 실패로 보고됨

##### @fk vs @manytoone 비교

| 항목 | `board: Board! @manytoone` | `boardId: long! @fk(Board.id)` |
|------|--------------------------|-------------------------------|
| 필드 타입 | Entity 참조 (Board) | Primitive (Long) |
| JPA annotation | `@ManyToOne @JoinColumn` | `@Column` |
| Lazy loading | JPA proxy 관리 | 없음 (명시적 조회 필요) |
| N+1 위험 | 있음 | 없음 |
| 사용 시나리오 | 신규 객체지향 설계 | Legacy DB 마이그레이션/분석 |

#### @dtype(ClassName)
DTO 전환시 사용할 타입 명시.
```yaml
articles: List<Article> @dtype(List<ArticleDTO>)
```

#### @fetchjoin
N+1 방지를 위한 JOIN FETCH. Service의 `withJoinFetches()`에서 적용. count 쿼리에서 자동 제외.
```yaml
postMan: PostMan @fetchjoin
category: Category @fetchjoin
```

#### @formula(expression)
읽기 전용 SQL 표현식 매핑. DB 컬럼이 아닌 계산값.
```yaml
dtype: string @formula("dtype")
orderCount: int @formula("(select count(*) from orders o where o.customer_id = id)")
createdByNm: string(100) @formula("coalesce((select u.name from system_user u where u.user_id = created_by), created_by)")
```
- 표현식은 **큰따옴표**로 감싼다. 따옴표 안의 `=`, `,`, 괄호, SQL 문자열 `'Y'`는 표현식의 일부로 그대로
  쓰인다(2.2.261012, 3.0부터). 표현식 안에는 큰따옴표(`"`)를 쓸 수 없다.
  예: `activeCount: long @formula("(select count(*) from member m where m.team_id = team_id and m.use_yn = 'Y')")`
- DB가 조회할 때 계산하는 값이므로 Mapper의 DTO→Entity 방향(`toEntity`, `mapping(dto, entity)`)에서는
  값을 넣지 않는다. Entity→DTO 방향과 검색 조건에는 일반 필드처럼 포함된다. TypeScript 클라이언트의
  `XxxFields`에서는 `readOnly: true`이고 `validateXxx`가 검사하지 않는다.
- `@abstract` 부모 엔티티에 선언하면 부모 클래스에 생성되고, 상속한 엔티티는 그 필드를 물려받는다.
- Entity에는 필드가 생기지만 테이블 컬럼은 생기지 않는다. 필드에는 `@Formula`가 붙고
  `@Column`·`@Comment`는 붙지 않는다(`-- 설명`은 Java 주석으로 남는다). Hibernate는 `@Column`이 있으면
  `@Formula`를 무시하고 실제 컬럼으로 매핑하기 때문이다.
- 이전 버전은 `@Column` 속성이 하나라도 생기면 `@Formula` 옆에 `@Column`을 붙여 컬럼이 생겼다:
  길이(`string(100)`), 이름으로 추정한 길이(길이 없는 `string` 필드 이름에 `Nm`·`Name`·`Code`·`Type`·`Status`·
  `Addr`·`Date`·`Time`·`Zip`·`Tel`이 들어간 경우),
  `!`, `@unique`, `@updatable(false)`, `@text`. `ddl-auto: update`는 그 컬럼을 지우지 않으므로 DB에서 직접 지운다
  (`!`로 NOT NULL 컬럼이 되었다면 업그레이드 후 INSERT가 실패한다).
- 식은 그 Entity를 조회할 때마다(목록 조회 포함) SELECT 안의 서브쿼리로 실행된다.
  DTO로 바꿀 때만 따로 실행되는 것이 아니다.
- `schema.yml`에는 `@Column` 대신 `@Formula`로 표시된다.

### 2.5 JPA 관계

Entity 타입 필드는 **기본적으로 `@ManyToOne`이 자동 적용**됩니다. `@OneToOne`, `@ManyToMany`를 사용하는 경우에만 명시적으로 지정하면 됩니다.

```yaml
# @ManyToOne (자동 적용 - 아래 두 표현은 동일)
board: Board                    # @ManyToOne 생략 가능
board: Board @ManyToOne         # 명시적 지정 (동일 결과)

# @OneToMany
articles: List<Article> @OneToMany(mappedBy="board")

# @ManyToMany + cascade
roles: List<Role> @ManyToMany @cascade(ALL)

# @OneToOne
profile: UserProfile @OneToOne @cascade(ALL)

# @Embedded
address: Address @Embedded

# @relation (mappedBy 지정)
address: Address @relation(mappedBy="market_post_id")
```
cascade 타입: `ALL`, `PERSIST`, `MERGE`, `REMOVE`

cascade 가 붙은 관계를 DTO 로 통째로 담으면(`member: QmMember @OneToOne @cascade(merge) @dtype(QmMemberDTO)`, 다른 도메인
엔티티의 `List<...> @OneToMany @cascade`), 수정(DTO -> 엔티티) 때 요청이 보낸 행이 관계에 붙고 저장이 cascade 로 그 행까지
씁니다. 3.0 규칙 엔진의 v3 번들(`jpa-service-access` 가 든 `jpa-springboot-v3`·`jpa-springboot-kt`·DDD·헥사고날)에서는
**누가 쓸 수 있는지 정한 엔티티**(`@access`·`@owner`·`@password`·필드의 `@access(admin)` 이 있는 엔티티, `@signin` 엔티티 포함)로
가는 cascade 관계를 DTO 에서 쓰지 않습니다 - 프로필을 고칠 수 있는 사람이 회원의 로그인이나 비밀번호(해시를 직접 만들어)를
바꿀 수 있었기 때문입니다. 그 행은 자기 서비스(자기 API)로 고치고, DTO 가 함께 보내는 키(`memberMemberId`)는 전처럼 관계를
가리킵니다. 응답에는 그대로 담깁니다. 빌드가 필드를 짚어 알려 줍니다:
`[qmshop] QmProfile.member is not written from the DTO: QmMember says who may write it (@access, @owner, @password), and the
cascade would save the row past those checks. ...`. 생성(`POST`)도 같아서, 요청에 담아 보낸 그 행은 저장되지 않습니다.
검사가 없는 엔티티로 가는 cascade, cascade 가 없는 관계(`@jpa:OneToOne(cascade = ...)` 처럼 직접 쓴 cascade 는 cascade 로
봅니다), 2.x 호환 번들(`jpa-springboot`)은 그대로입니다.

DDD·헥사고날 번들에서 **같은 애그리거트의 멤버**는 루트와 함께 오는 것이 설계라 위 규칙에서 빠지고 루트를 통해 쓰입니다.
다만 멤버에는 자기 서비스가 없어 루트의 서비스가 해시·검사하지 못하므로, 멤버의 `@password` 와 `@access(admin)` 필드는
DTO 에서 쓰지 않고 빌드가 알려 줍니다(`QdAccount.secret is not written from a DTO: QdAccount is a member of QdCustomer's
aggregate and arrives with it, and the root's service hashes a @password of only its own fields. Declare it on QdCustomer.`).
그런 필드는 루트에 둡니다.

#### FK 컬럼명 지정 (@column / @col)

Entity 참조 필드에 `@column` (또는 `@col`)을 사용하면 `@JoinColumn(name=...)`이 생성됩니다. 미지정시 JPA 기본 규칙(필드명 + `_id`)이 적용됩니다.

```yaml
# FK 컬럼명 미지정 - JPA 기본 규칙 적용 (comment_id)
comment: Comment

# FK 컬럼명 지정 - @JoinColumn(name="parent_comment_id") 생성
comment: Comment @column(parent_comment_id)
comment: Comment @col(parent_comment_id)

# NOT NULL + FK 컬럼명 지정 - @JoinColumn(name="parent_comment_id", nullable=false) 생성
comment: Comment! @col(parent_comment_id)
```

#### 관계 필드의 자동 DTO 렌더링 (중첩 DTO + FK 필드)
> **Since: 2.2.260703**

`@expose`/`@service` 엔티티가 자동 생성하는 main DTO에서 `@ManyToOne`/`@OneToOne` 관계 필드는
**두 가지 필드로 렌더링**됩니다:

```yaml
# entity
ScheduleInfo:
  meta: entity @expose(20)
  fields:
    scheduleId: string(20) @id
    batch: BatchInfo @ManyToOne @fetchjoin
```
```java
// 자동 생성 ScheduleInfoDTO
@Schema(description = "batch" , accessMode = Schema.AccessMode.READ_ONLY )
private BatchInfoDTO batch;          // 조회 응답용 (중첩 상세)

@Schema(description = "batchBatchId"  )
private String batchBatchId;         // 저장/수정 입력용 (FK id)
```

- **중첩 DTO 필드(`batch`)**: OpenAPI 스키마에 `accessMode = READ_ONLY`로 표시되어
  **Swagger의 저장/수정 request body에서 제외**됩니다 (조회 응답에는 포함).
  문서(스펙) 수준의 표시이며 런타임 직렬화/역직렬화 동작은 변경되지 않습니다. 요청에 중첩 객체를 보내도
  오류는 나지 않지만, 매퍼는 저장/수정할 때 중첩 객체를 읽지 않습니다 — 관계는 FK id 필드로만 연결됩니다.
- **FK id 필드(`batchBatchId`)**: 저장/수정 시 관계를 지정하는 입력 필드입니다
  (`필드명 + 참조엔티티 @id 필드명` 네이밍, 내부적으로 `em.getReference()`로 연결).
  참조 엔티티의 키가 `id`이면 `batchId`가 됩니다.
- 중첩 DTO 자체를 생성에서 제외하려면 `dto.yml`에 `@template`로 DTO를 직접 선언하세요
  (관계 필드는 자동 제외되며, 필요한 필드만 `@ref(관계필드.대상필드)`로 평탄화 선언 —
  blueprint `hr/model/dto.yml`의 `EmployeeDTO` 참고).

##### 필수 관계 (`!`): 저장 요청은 FK id 필드를 보낸다
> **Since: 3.0.261021** (v3 · Kotlin · TypeScript 생성. 2.x와 `jpa-springboot` 호환 출력은 그대로)

관계에 `!`를 붙이면 컬럼이 NOT NULL이 되고(`@JoinColumn(nullable = false)`), 자동 생성 DTO에서는
**FK id 필드가 필수**, 중첩 DTO는 선택입니다.

```yaml
Post:
  meta: entity @root @expose(20) -- post
  fields:
    postId:    long @id @sequence
Comment:
  meta: entity @root @expose(20) -- comment
  fields:
    commentId: long @id @sequence
    post:      Post! -- parent post
```
```java
// 자동 생성 CommentDTO (v3, Kotlin도 같은 어노테이션)
@Schema(description = "parent post" , accessMode = Schema.AccessMode.READ_ONLY )
private PostDTO post;                // 응답용 - 요청에서는 선택

@Schema(description = "postPostId" , requiredMode=Schema.RequiredMode.REQUIRED )
@NotNull
private Long postPostId;             // 저장 요청에 필수
```
```ts
// TypeScript 클라이언트 CommentDTO.ts
post?: PostDTO;
postPostId: number;
```

- 저장 요청(`POST`, `@Valid @RequestBody`)은 `{"postPostId": 1, ...}`처럼 **FK id 필드를 보냅니다**.
  `postPostId`가 없으면 `@Valid` 검증에서 거절됩니다. 중첩 `post`는 보내지 않아도 됩니다.
- 2.x(`build`/`elcore`)와 `jpa-springboot` 호환 출력은 이전과 같습니다: 중첩 `post`에 `@NotNull`과
  `requiredMode=REQUIRED`가 붙고 `postPostId`에는 붙지 않습니다. 이 출력에서는 검증을 통과하는 데 `post`가,
  관계를 저장하는 데 `postPostId`가 필요하므로 요청은 둘 다 보내야 합니다.
- **3.0.261021 이전 v3 출력에서 바뀐 점**: 이전에는 v3도 2.x처럼 중첩 `post`를 요구했습니다. 이제 `postPostId`만
  보낸 요청이 통과하고, 중첩 `post`만 보내던 요청은 `@Valid` 검증에서 거절됩니다 (그 요청은 이전에도 관계를
  연결하지 못했습니다 - 매퍼는 `postPostId`만 읽습니다).
- 검색 DTO(`CommentSrchDTO`)의 `postPostId`는 검색 조건이라 필수가 아닙니다. 중첩 `post`는 검색 DTO에 없습니다.
- 다른 애그리거트를 ID로 참조하는 DDD·헥사고날 번들에서는 FK id 필드가 엔티티의 ID 필드(`memberId`)이고,
  같은 규칙으로 그 필드가 필수입니다 - `elasticore-rule-guide.md` "다른 애그리거트는 ID 로 참조".
- `dto.yml`에 직접 선언한 DTO에는 적용되지 않습니다. 직접 선언한 DTO에서는 `postId: long! @ref(post.postId)`처럼
  필드에 `!`를 씁니다.

### 2.6 JPA 컬럼 매핑

#### @Column / @col / @db
데이터베이스 컬럼 매핑. 미지정시 필드명을 snake_case 자동 변환.
```yaml
postNo: string(5) @Column(name="post_no", length=5)
username: string @col(unique=true)
```
매개변수: `name`, `length`, `unique`, `nullable`

#### @columnDef('xxx') / @jdbcType('XXX')
> **Since: 2.2.260421-4**
> **요구사항: Hibernate 6.2 이상**
> **1차 도입 범위: `string` 타입(BaseFieldType.STRING) 기반. Map / POJO / List<POJO> 는 후속 단계.**

JSON / JSONB / XML 등 임의의 DB 컬럼 정의와 Hibernate JdbcTypeCode 를 DSL 에서 직접 지정.
DB 컬럼 정의는 **단일 `@Column` 안의 `columnDefinition` 속성으로 통합 출력**되며 별도 `@Column` 어노테이션이 추가로 생기지 않는다.

| DSL | 매핑되는 JPA / Hibernate 출력 |
|---|---|
| `@columnDef('xxx')` | `@Column(columnDefinition="xxx")` 속성으로 통합 |
| `@jdbcType('XXX')`  | `@JdbcTypeCode(org.hibernate.type.SqlTypes.XXX)` |

**자동 추론 규칙** (`@jdbcType` 미지정 시 `@columnDef` 값으로부터 추정):

| `@columnDef` 값 (대소문자 무시) | 자동 적용되는 `@jdbcType` |
|---|---|
| `json`, `jsonb` | `JSON` |
| `xml` | `SQLXML` |
| (그 외) | 추론 없음 — `@jdbcType` 명시 시에만 출력 |

`@jdbcType` 가 명시되어 있으면 자동 추론보다 **명시값을 우선**.

```yaml
fields:
  # 자동 추론
  metadataRaw: string @columnDef('jsonb')                              # @JdbcTypeCode(SqlTypes.JSON)
  oldPayload:  string @columnDef('json')                               # @JdbcTypeCode(SqlTypes.JSON)
  customXml:   string @columnDef('xml')                                # @JdbcTypeCode(SqlTypes.SQLXML)

  # 명시적 override - 자동 추론(JSON) 대신 LONG32VARCHAR 사용
  forceLong:   string @columnDef('jsonb') @jdbcType('LONG32VARCHAR')

  # 향후 확장 (DSL 측 코드 변경 없이 임의의 SqlTypes 상수 출력 가능)
  geomCol:     string @columnDef('geometry') @jdbcType('GEOMETRY')
```

**생성 코드 예시**:
```java
@Column(name = "metadata_raw", columnDefinition = "jsonb")
@JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
private String metadataRaw;

@Column(name = "old_payload", columnDefinition = "json")
@JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
private String oldPayload;

@Column(name = "custom_xml", columnDefinition = "xml")
@JdbcTypeCode(org.hibernate.type.SqlTypes.SQLXML)
private String customXml;

@Column(name = "force_long", columnDefinition = "jsonb")
@JdbcTypeCode(org.hibernate.type.SqlTypes.LONG32VARCHAR)
private String forceLong;
```

**주의사항**:
- `@JdbcTypeCode` 는 `org.hibernate.annotations.*` 와일드카드 import 로 자동 해결됨 (별도 import 추가 불필요).
- `org.hibernate.type.SqlTypes` 는 FQCN(`org.hibernate.type.SqlTypes.XXX`) 으로 인라인 출력되므로 import 추가 불필요.
- 현재 1차 단계는 **`string` 타입에서만 검증 완료**. `Map<String,Object>`, POJO, `List<POJO>` 매핑은 별도 단계(BaseFieldType.MAP 활성화, TypeInfo / 관계 분기 처리 추가)에서 진행 예정.
- DB 측에서는 PostgreSQL `jsonb` / `json` 컬럼이 가장 흔한 사용처이며, Hibernate 6.2+ 가 별도 라이브러리 없이 매핑 처리.

### 2.7 커스텀 함수

```yaml
fullName: string @function.get("getFullName")       # 커스텀 Getter
password: string @function.set("setEncryptedPassword")  # 커스텀 Setter
```

### 2.8 Spring Data JPA 감사

```yaml
createDate: datetime @jpa:org.springframework.data.annotation.CreatedDate
createdBy: string(20) @jpa:org.springframework.data.annotation.CreatedBy
lastModifiedDate: datetime @jpa:org.springframework.data.annotation.LastModifiedDate
lastModifiedBy: string(20) @jpa:org.springframework.data.annotation.LastModifiedBy
```

v3 번들에서는 이 네 필드와 `@jpa:Version` 을 매퍼가 DTO 에서 엔티티로 쓰지 않고, DTO 의 `@NotNull` 도
빠집니다 (`jpa-write-guard`). 응답에는 그대로 나갑니다. DTO 와 OpenAPI 에도 읽기 전용으로 적으려면
`@readonly` 를 함께 붙입니다 (§2.2 `@readonly / @noinput`):

```yaml
createdBy: string(20) @jpa:org.springframework.data.annotation.CreatedBy @readonly
```

### 2.9 기타

```yaml
temporaryData: string @notaudited          # Envers 감사 제외
coordinates: int[] @env:integerarray       # env.yml annotations 참조
```

---

## 3. Search Conditions - 완전 참조

### 3.1 개요

`@search(condition)` 또는 `@s(condition)` 또는 `@srch(condition)` 또는 `@filter(condition)` 모두 동일.
조건과 매핑 필드 구분자: `,` (생성기가 쉼표로 나눕니다).
```yaml
customName: string @search(like,comName)   # comName 필드에 like 검색
```

### 3.2 구현된 연산자 전체 목록 (17개)

| DSL 문법 | JPA Criteria | 대소문자 무시 | 설명 |
|----------|-------------|:---:|------|
| `eq`, `=` | `equal` | No | 정확 일치 |
| `like` | `like` (%v%) | **v3: Yes** / 2.x 호환: No | 포함 검색. v3 모드(헥사고날·DDD, `-Pv3`, 모든 Kotlin 빌드)에서는 문자열 필드에 `%__%` 와 같음 - 이름 일부를 대소문자와 관계없이 찾습니다. 레이어드 Java(2.x 호환)는 그대로 구분 |
| `%_%`, `%%` | `like` (%v%) | No | 대소문자를 구분하는 포함 검색 (모든 모드) |
| `%__%` | `like` (%v%) | Yes | 대소문자 무시 포함 검색 |
| `__=`, `=__` | `equal` | Yes | 대소문자 무시 정확 일치 |
| `%_` | `like` (%v) | No | 접미사 검색 (ends-with) |
| `_%` | `like` (v%) | No | 접두사 검색 (starts-with) |
| `%__` | `like` (%v) | Yes | 대소문자 무시 접미사 검색 |
| `__%` | `like` (v%) | Yes | 대소문자 무시 접두사 검색 |
| `>` | `greaterThan` | No | 초과 |
| `>=` | `greaterThanOrEqualTo` | No | 이상 |
| `<` | `lessThan` | No | 미만 |
| `<=` | `lessThanOrEqualTo` | No | 이하 |
| `in` | `.in()` / `Join.in()` | No | 다중 값 포함 |
| `between`, `~` | `between` | No | 범위 검색 |
| `isnull` | `isNull` / `isNotNull` | No | 비어 있는지 검색. 검색 속성 `<필드>Empty: Boolean` (3.4.1, v3 번들 전용) |
| `auto`, (기본값 - 조건 없는 `@search`) | Runtime 결정 | No | String 전용, %포함시 like 아니면 eq |

- v3 모드에서 대소문자를 무시하는 조건(`like`, `%__%`, `%__`, `__%`)은 값의 앞뒤 공백을 지우고(`like` 처럼),
  값을 로캘과 무관하게 소문자로 바꿉니다 (Java `toLowerCase(Locale.ROOT)`, Kotlin `lowercase()` - Kotlin 2.x 에서
  `toLowerCase()` 는 컴파일 오류). 컬럼은 `lower(...)` 로 비교합니다.
- 호출자가 값에 넣은 `%`·`_` 는 그대로 와일드카드입니다 (`{"name":"%Puppy%"}`).
- 인덱스: 포함 검색(`%v%`)은 어느 DB 에서도 B-tree 범위를 쓰지 못하므로 `lower()` 가 비용을 더하지 않습니다. 접두사
  `__%`·정확 일치 `__=` 를 인덱스로 찾으려면 `lower(col)` 식 인덱스가 필요합니다. MySQL·SQL Server 의 기본 CI 콜레이션은
  원래 대소문자를 무시합니다.

**미구현 주의:**
- `!=`, `neq` - 코드에 구현 없음
- `gt`, `gte`, `lt`, `lte`, `ilike` 키워드 - 미구현 (기호 `>`, `>=`, `<`, `<=`만 동작). 모르는 조건은 정확 일치가 되며,
  v3 모드에서는 빌드가 `... @search(ilike) is not a search condition, so it searches by equality ...` DSL 경고를 냅니다
- `%` 하나만 쓴 `@search(%)` 는 끝 일치(`like '%' + 값`, `%_` 와 같음)입니다. v3 모드에서는 빌드가
  `... @search(%) searches by the end of the value (like '%' + value) - say %_ for that, _% for the start or %_% for any part.`
  DSL 경고를 냅니다

### 3.3 auto 조건 동작

`@expose` 엔티티에서 개별 필드에 `@search` 미지정시 타입 기반 자동 할당:
- **String** -> `auto` (런타임에 값에 `%` 포함시 like, 아니면 eq)
- **LocalDate / LocalDateTime / DATE / DATETIME / LocalTime** -> `between`
- **기타 타입** -> `eq`

`auto`의 런타임 로직 (생성되는 `setSpec` 메서드):
```java
if(value.startsWith("%") || value.endsWith("%"))
    return sp.and((r,q,c) -> c.like(r.get(fieldName), value));
else
    return sp.and((r,q,c) -> c.equal(r.get(fieldName), value));
```

### 3.4 between 특수 동작

SearchDTO에 `fieldNameFrom`, `fieldNameTo` 2개 필드 자동 생성.

**LocalDate 특수 처리:**
- From + To 모두 있으면: To에 **+1일** 적용 후 `between(from, to+1)` - 하루 끝까지 포함
- From만 있으면: `greaterThanOrEqualTo(from)`
- To만 있으면: To에 **+1일** 적용 후 `lessThan(to+1)` - 하루 끝 포함

**LocalDate 외 타입:**
- From + To 모두 있으면: `between(from, to)`
- From만 있으면: `greaterThanOrEqualTo(from)`
- To만 있으면: `lessThanOrEqualTo(to)`

```yaml
registDate: localDate @search(between)
# -> SearchDTO: registDateFrom, registDateTo 필드 생성
# -> LocalDate이므로 To+1일 자동 적용
```

### 3.4.1 isnull - 비어 있는지 검색

"아직 반납하지 않은 대출", "아직 입고된 적 없는 상품"처럼 **값이 비어 있는지**로 찾을 때 씁니다.
검색 DTO 에는 필드 값 대신 `<필드>Empty` 라는 `Boolean` 속성 하나가 생기고, 그 값이 조건을 정합니다.

| 요청의 `<필드>Empty` | 조건 |
|---|---|
| 보내지 않음 또는 `null` | 조건 없음 (모두) |
| `true` | `IS NULL` - 비어 있는 행만 |
| `false` | `IS NOT NULL` - 값이 있는 행만 |

```yaml
entity:
  Product:
    meta: entity @expose(20)
    fields:
      lastRestocked: datetime @search(isnull) -- last restocked, empty until the first restock
```

생성되는 `ProductSrchDTO` (날짜 형식 어노테이션과 `example` 은 붙지 않습니다 - 보내는 값이 날짜가 아니므로):
```java
/*
  last restocked, empty until the first restock
*/
@Schema(description = "last restocked, empty until the first restock Field is empty (true) or set (false). field:lastRestocked"  )
private Boolean lastRestockedEmpty;
```

생성되는 Mapper `toSpec(ProductSrchDTO searchDTO, Specification<Product> sp)`:
```java
Boolean lastRestockedEmpty = searchDTO.getLastRestockedEmpty();
if(hasValue(lastRestockedEmpty)){
    if(lastRestockedEmpty){
        sp = sp.and((r,q,c) -> c.isNull(r.get("lastRestocked")));
    }else{
        sp = sp.and((r,q,c) -> c.isNotNull(r.get("lastRestocked")));
    }
}
```

호출: `POST /api/v1/shop/product/search {"name":"kettle","lastRestockedEmpty":true}` -> 이름에 kettle 이 들어가고
아직 입고된 적 없는 상품만. 다른 검색 조건과 AND 로 묶입니다.

Kotlin 과 TypeScript 도 같은 규칙에서 나옵니다:
```kotlin
var endDateEmpty: Boolean? = null                    // ProjectSrchDTO.kt

val endDateEmpty = searchDTO.endDateEmpty            // HrMapper.kt toSpec
if(hasValue(endDateEmpty)){
    if(endDateEmpty!!){
        sp = sp.and(Specification<Project> { r,q,c -> c.isNull(r.get<java.time.LocalDate>("endDate")) })
    }else{
        sp = sp.and(Specification<Project> { r,q,c -> c.isNotNull(r.get<java.time.LocalDate>("endDate")) })
    }
}
```
```typescript
endDateEmpty?: boolean;                              // ProjectSrchDTO.ts
```

- **필드 하나에 조건 하나.** `isnull` 을 적은 필드는 `between` 의 From/To 를 만들지 않습니다. 기간 검색도 필요하면
  지금은 Service 에서 `Q`/`Op` 로 직접 조건을 더합니다 (dev-guide "Op 열거형").
- `@expose` 의 타입별 자동 할당(3.3, 날짜 -> `between`)은 그대로입니다. `@search(isnull)` 을 적은 필드만 바뀝니다.
- `@ref(order.shippedDate)` 와 함께 쓰면 관계 너머의 컬럼을 검사합니다: `c.isNull(r.get("order").get("shippedDate"))`.
- 두 번째 값(쉼표 뒤)은 다른 조건과 같이 **엔티티 필드 이름**입니다: `@search(isnull,returnedDate)` ->
  속성 `returnedDateEmpty`, 조건 `r.get("returnedDate")`.
- `!` / `@notnull` 필드에는 의미가 없습니다 (`true` 는 늘 빈 결과). 경고는 아직 없습니다.
- 3.0 규칙 엔진(`elcore3`)의 v3 번들(`jpa-springboot-v3`, `ddd-springboot`, `hexagonal-springboot`, Kotlin 판,
  `ts-model`)에서 동작합니다. 2.x 생성기(`elcore`)와 2.x 호환 번들(`jpa-springboot`)은 `isnull` 을 모르는 값으로 보고
  예전처럼 필드 타입 그대로의 같은 값 비교(`c.equal`)를 만듭니다 - 2.x 출력은 바뀌지 않습니다.

### 3.5 List 필드 + eq = Join 생성

`List<Entity>` 타입 필드에 `@search(eq)` 적용시:
- SearchDTO에 `fieldNameItem` 필드 생성 (리스트 요소 타입)
- JPA `root.join("field")` + `equal(join, value)` 자동 생성

```yaml
drivers: List<RentDriver> @search(eq)
# -> SearchDTO: driversItem (RentDriver 타입)
# -> Join<Entity, RentDriver> join = root.join("drivers");
#    return criteriaBuilder.equal(join, driversItem);
```

### 3.6 in 연산자와 List 필드

**일반 필드:**
```java
sp.and((r,q,c) -> r.get("status").in(status));
```

**List<Entity> 필드:**
```java
Join<Entity, ItemType> join = root.join("items");
return join.in(items);
```

### 3.7 @ref와 검색 조건 조합

`@ref(parentEntity.childField)` 사용시 nested get 생성:
```yaml
companyName: string @search(like) @ref(company.name)
# -> r.get("company").get("name")
```

### 3.8 실용 예시

```yaml
entity:
  RentCompany:
    "@": "@expose @pageable @audited"
    fields:
      comId: string @id @search(eq)
      comName: string @search(like) @length(100)
      email: string @search(%__%)
      status: string @search(in)
      registDate: localDate @search(between)
      closeDate: localDate @search(isnull)
      score: int @search(>=)
      drivers: List<RentDriver> @search(eq)
```

생성되는 SearchDTO 필드:
- `comId: String` (eq)
- `comName: String` (like)
- `email: String` (%__% 대소문자 무시)
- `status: String` (in)
- `registDateFrom: LocalDate`, `registDateTo: LocalDate` (between)
- `closeDateEmpty: Boolean` (isnull - `true` 영업 중, `false` 폐업, 생략 모두. v3 번들 전용)
- `score: Integer` (>=)
- `driversItem: RentDriver` (Join eq)
- `sortCode: String`, `pageNumber: int`, `pageSize: int` (자동 생성)
- 요청이 `sortCode` 를 보내지 않을 때의 순서는 엔티티(또는 검색 DTO)의 `@sort` 로 정합니다 (아래)

---

## 4. DTO Annotations

### 4.1 @template(EntityName)
엔티티 기반 DTO 생성. 필드 자동 복사. 여러 DTO/Entity 조합 가능.
```yaml
dto:
  UserDTO:
    meta: dto @template(User)
    fields:
      password: --              # 필드 제외
      createDate: --            # 필드 제외

  CarProfileInfoDTO:
    meta: dto @template(CarProfileDTO,CarInfoDTO)  # 다중 템플릿
```

`@template` 사용시 Entity의 `string(n)` 길이가 DTO에 자동 `@Size(max=n)` 반영.

### 4.2 @searchable
검색 전용 DTO 생성. 매개변수: `entity`(필수), `pageSize`, `pageable`.
```yaml
dto:
  UserSearchDTO:
    meta: dto @searchable(entity=User, pageSize=25)
```
pageSize 지정시 `PageableObject` 인터페이스 구현. 자동 생성 필드: `pageNumber`(기본0), `pageSize`(설정값), `sortCode`.

### 4.2.1 @sort - 기본 정렬
요청이 `sortCode` 를 보내지 않을 때의 순서. 엔티티 meta 에 적으면 그 엔티티의 모든 검색 DTO 에, 검색 DTO meta 에 적으면
그 DTO 에만 적용됩니다 (검색 DTO 쪽이 우선). 형식은 `sortCode` 와 같습니다: 속성 + 방향(`+` 오름차순, `-` 내림차순,
생략하면 오름차순), 쉼표로 여러 개.
```yaml
entity:
  Product:
    meta: entity @root @expose(20) @sort(name+)
  PurchaseOrder:
    meta: entity @root @expose(20) @sort(orderDate-, orderId-)
```
생성: `@Builder.Default private String sortCode="name+";` (Kotlin `override var sortCode: String? = "name+"`).
요청이 `sortCode` 를 보내면 그것이 이깁니다. Pet Store 상품 검색이 이름순으로 나오는 것을 `ShopFlowTest` 가 확인합니다.
- 엔티티에 없는 속성을 적으면 DSL 경고(`@sort(...): x is not a field of Product`) 후 그 속성만 빠집니다 (정렬 400 방지).
- `@sort` 가 없으면 예전처럼 정렬 없이 조회합니다.
- 3.0 규칙 엔진(`elcore3`) 기능입니다. 2.x 생성기(`elcore`)는 `@sort` 를 읽지 않습니다.

### 4.3 @searchBypass
검색 DTO 생성시 해당 필드를 검색 조건에서 제외.
```yaml
internalId: string @searchBypass
```

### 4.4 JSON 어노테이션

```yaml
userName: string @json("user_name")            # @JsonProperty("user_name")
amount: BigDecimal @jsonSerialize(using=com.example.MoneySerializer)
optionalField: string @jsoninclude             # @JsonInclude
```
별칭: `@json` = `@jsonproperty`, `@jsoninclude` = `@includejson`

### 4.5 Bean Validation

```yaml
email: string(100) @email                       # @Email
isbn: string(13) @pattern('^[0-9]{13}$', 'ISBN must be 13 digits')   # @Pattern(regexp = ..., message = ...) - 2.2 참조
birthDate: date @past                           # @Past
eventDate: datetime @future                     # @Future
orderDate: datetime @pastorpresent              # @PastOrPresent
checkInDate: date @futureorpresent              # @FutureOrPresent
address: AddressDTO                             # DTO 타입 -> @Valid 자동 추가
```

메시지 패턴: `"{FieldName} required"`, `"{FieldName} max {n}"`, `"Email format invalid"`, `"Date must be past"` 등 간단한 영어.
`@pattern` 은 메시지를 직접 줄 수 있고, 생략하면 `"<라벨> is not in the expected form"` 입니다(TS 클라이언트와 같은 문장).

---

## 4.6 @jsonview - DTO 필드 가시성 제어 (JsonView)

> **Since: 2.1**

### 개요
Jackson `@JsonView`를 활용하여 REST API 작업별(Create/Update/Read) DTO 필드 가시성을 제어합니다.
하나의 DTO 클래스로 Create, Update, Read 작업에서 서로 다른 필드를 Swagger에 노출할 수 있습니다.

### DTO 메타 어노테이션: @jsonview

| 값 | 설명 |
|---|---|
| `@jsonview` | 수동 모드. 필드에 `@view` 지정 필요 |
| `@jsonview(auto)` | 자동 모드. 기존 어노테이션에서 가시성 자동 추론 |
| `@jsonview(false)` | 명시적 비활성화 |

```yaml
dto:
  ProductDTO:
    meta: dto @template(Product) @jsonview(auto)
```

Entity에서 자동 생성되는 DTO에도 적용 가능:
```yaml
entity:
  Product:
    meta: entity @expose @jsonview(auto)
```

### 필드 어노테이션: @view

> **Since: 2.1**

두 가지 문법을 지원합니다:

**단축 문법** (권장):
```yaml
fields:
  id: long @id @view(r)          # Read만
  name: string @view(cru)        # Create, Read, Update 모두
  status: string @view(ur)       # Update, Read만
```

**전체 이름 문법**:
```yaml
fields:
  id: long @id @view(read)
  name: string @view(create,update,read)
```

| 문자 | 전체 이름 | 의미 | Controller 매핑 |
|------|----------|------|----------------|
| `c` | `create` | 생성 작업에서 표시 | `@PostMapping` |
| `u` | `update` | 수정 작업에서 표시 | `@PutMapping` |
| `r` | `read` | 조회 응답에서 표시 | `@GetMapping`, 모든 응답 |

**규칙**:
- `@view` 없는 필드: 모든 View에서 표시 (기본 동작)
- `@jsonview` 없는 DTO: 기존과 100% 동일한 코드 생성

### 자동 추론 규칙 (@jsonview(auto))

| 기존 어노테이션 | Create | Update | Read | 추론 근거 |
|----------------|:------:|:------:|:----:|----------|
| `@id @genid` | ❌ | ✅ | ✅ | 자동 생성 ID |
| `@id @sequence` | ❌ | ✅ | ✅ | DB 시퀀스 ID |
| `@id` (수동) | ✅ | ✅ | ✅ | 사용자 지정 ID |
| `@updatable(false)` | ✅ | ❌ | ✅ | 수정 불가 필드 |
| `@jpa:CreatedDate` | ❌ | ❌ | ✅ | 자동 주입 필드 |
| `@jpa:LastModifiedDate` | ❌ | ❌ | ✅ | 자동 주입 필드 |
| `@jpa:Version` | ❌ | ❌ | ✅ | 낙관적 잠금 |
| `@formula(...)` | ❌ | ❌ | ✅ | 계산 필드 |
| `@readonly` / `@noinput` | ❌ | ❌ | ✅ | 서버가 정하는 필드 (§2.2) |
| `@writeonly` | ✅ | ✅ | ❌ | 쓰기 전용 |
| 일반 필드 | ✅ | ✅ | ✅ | 기본값 |

### env.yml 설정

> **Since: 2.1**

```yaml
elasticore:
  config:
    jsonview: auto  # false(기본) | true | auto
```

- `false`: 기본값. 개별 entity/dto에 `@jsonview` 설정하기 전까지 비활성화
- `true`: 수동 모드. `@view` 지정 필요
- `auto`: 전체 auto 모드. 모든 DTO에 자동 추론 적용

### 생성 코드 예시

**DSL 입력**:
```yaml
dto:
  ProductDTO:
    meta: dto @template(Product) @jsonview(auto)
    fields:
      id: long @id @view(r)
      name: string(100) @view(cru)
      status: string(20) @view(ur)
      createdAt: datetime @view(r)
```

**생성 결과 (DTO)**:
```java
@JsonView(Views.Read.class)
private Long id;

@JsonView({Views.Create.class, Views.Update.class, Views.Read.class})
private String name;

@JsonView({Views.Update.class, Views.Read.class})
private String status;

@JsonView(Views.Read.class)
private LocalDateTime createdAt;

// @view 없는 필드는 @JsonView 미생성 → 모든 View에서 표시
private BigDecimal price;
```

**생성 결과 (Controller)**:
```java
@JsonView(Views.Read.class)
@PostMapping
public ProductDTO create(@JsonView(Views.Create.class) @Valid @RequestBody ProductDTO inputDto) { ... }

@JsonView(Views.Read.class)
@PutMapping
public ResponseEntity<ProductDTO> update(@JsonView(Views.Update.class) @RequestBody ProductDTO updateDto) { ... }

@JsonView(Views.Read.class)
@GetMapping("/{id}")
public ResponseEntity<ProductDTO> findById(@PathVariable Long id) { ... }
```

### 주의사항
- **ElastiCORE 2.1 이상** 필요 (`@jsonview`, `@view`, env.yml `jsonview` 설정 모두 해당)
- **`spring.jackson.mapper.default-view-inclusion=true`를 application.yml에 반드시 명시 설정해야 함**
  - ⚠️ Spring Boot의 기본값은 **false**입니다 (Spring의 `Jackson2ObjectMapperBuilder`가
    `MapperFeature.DEFAULT_VIEW_INCLUSION`을 비활성화)
  - false 상태에서 컨트롤러에 `@JsonView`가 적용되면 `@JsonView`가 없는 모든 필드가
    요청/응답에서 **통째로 제외**되므로, jsonview 기능 사용 시 이 설정은 필수입니다
  ```yaml
  # application.yml (jsonview 기능 사용 시 필수)
  spring:
    jackson:
      mapper:
        default-view-inclusion: true
  ```
- `@view`와 기존 `@readonly`/`@writeonly`는 독립적으로 동작
- springdoc-openapi v2.0+ (Spring Boot 3) 또는 v1.7+ (Spring Boot 2) 필요

---

## 5. Enumeration Annotations

### @db(field) / @json(field)
DB 저장 필드와 JSON 직렬화 필드 지정.
```yaml
enumeration:
  BoardType:
    meta: enum @db(code) @json(code)
    fields:
      code: string(2)
      name: string(20)
    values:
      NOTICE: { code: "NT", name: "Notice" }
      FREE: { code: "FR", name: "Free" }
```

---

## 6. Port Annotations

상세 내용은 `elasticore-dsl-port-guide.md` 참조.

### HTTP Port
```yaml
port:
  ExternalApi:
    meta: http @url("https://api.example.com") @auth(API-KEY)
    methods:
      getUser:
        meta: method @get("/users/{userId}")
      createUser:
        meta: method @post("/users")
```
지원 메서드: `@get`, `@post`, `@put`, `@delete`, `@patch`

### DBMS Port
```yaml
port:
  BoardService:
    meta: dbms @datasource("main")
    methods:
      findByStatus:
        meta: method @query("SELECT b FROM Board b WHERE b.status = :status") @pageable
      deleteOld:
        meta: method @native("DELETE FROM board WHERE created < :date")
```

---

## 7. Best Practices

### 성능
- `@fetchjoin`: 항상 함께 조회하는 연관 엔티티에 사용. count 쿼리에서 자동 제외됨.
- `table.autoJoin=true` (env.yml): 모든 엔티티 참조 필드에 fetchjoin 자동 적용. 개별 지정 불필요.
- `@cascade(ALL)`: 생명주기가 동일한 엔티티에만 사용. 대량 컬렉션에서는 성능 영향 고려.
- `@pageable` / `@expose` pageSize: 과도한 값 설정시 메모리 사용량 증가.

### 검색 조건 선택
- **정확한 코드/ID 검색**: `eq` 또는 `=` (기본값)
- **텍스트 부분 검색**: `like` (v3 모드는 대소문자 무시, 레이어드 Java 는 구분), `%_%` (항상 구분), `%__%` (항상 무시)
- **날짜 범위**: `between` (LocalDate는 자동 +1일 포함)
- **다중 선택 필터**: `in`
- **숫자 비교**: `>=`, `<=`, `>`, `<` (기호만 사용, 키워드 미지원)
- **유연한 검색 UI**: `auto` (사용자가 %를 입력하면 like, 아니면 eq)

### DTO template 활용
- `@template(Entity)` 사용시 Entity의 `string(n)` 길이가 자동으로 `@Size(max=n)` 반영.
- 명시적 `@size` 지정시 우선 적용.
- `--`로 불필요한 필드 제외.
- 여러 DTO/Entity를 조합하여 복합 DTO 생성 가능.

### 인덱스와 검색
- `@search` 사용 필드에는 `@Index` 설정 권장.
- `like` 검색은 접두사(`_%`)만 인덱스 활용 가능. `%_`와 `%_%`는 full scan.
- `between` 검색 대상 컬럼은 인덱스 설정시 범위 스캔 최적화.

### 기본값 활용
- `@table`: 미지정시 엔티티명을 snake_case 자동 변환.
- `@Column`: 미지정시 필드명을 snake_case 자동 변환.
- `@search`: `@expose` 엔티티에서 미지정시 타입별 자동 할당.
- `@pageable`: 기본 pageSize=20.

---

## 8. 주의사항 및 해결방법 (Known Limitations & Workarounds)

### 8.1 @min/@max 어노테이션 제약사항

Jakarta Bean Validation의 `@Min`, `@Max` 어노테이션은 `long` 타입 매개변수만 허용합니다. 실수 값(0.0, 5.0 등)을 사용하면 컴파일 오류가 발생합니다.

#### 문제 상황
```yaml
# WRONG - decimal values cause compile errors
fields:
  rating: double @min(0.0) @max(5.0)   # Generates @Min(0.0) @Max(5.0) - compile error
  temperature: float @min(-273.15)     # Generates @Min(-273.15) - compile error
  price: bigdecimal @min(0.01)         # Generates @Min(0.01) - compile error
```

생성되는 Java 코드:
```java
@Min(0.0)  // Error: incompatible types: possible lossy conversion from double to long
@Max(5.0)  // Error: incompatible types: possible lossy conversion from double to long
private Double rating;
```

#### 올바른 사용법
```yaml
# CORRECT - use integer values only
fields:
  rating: double @min(0) @max(5)        # Generates @Min(0) @Max(5) - valid
  temperature: float @min(-273)         # Generates @Min(-273) - valid
  price: bigdecimal @min(0)             # Generates @Min(0) - valid
```

생성되는 Java 코드:
```java
@Min(0)
@Max(5)
private Double rating;
```

#### 해결방법
1. **정수값 사용**: 실수값을 정수로 변환 (0.0 → 0, 5.0 → 5, -273.15 → -273)
2. **커스텀 검증**: 정밀한 실수 범위 검증이 필요한 경우 커스텀 Bean Validation 어노테이션 구현
3. **서비스 레이어 검증**: 복잡한 비즈니스 규칙은 서비스 계층에서 검증

### 8.2 DSL 타입이 아닌 단어 주의 (Invalid Field Types)

필드 타입에는 DSL 가이드 "값 타입 한눈에" 표에 있는 타입만 씁니다. 표에 없는 단어는 그대로 Java 타입명이 되어
(`private uuid id;`) 컴파일되지 않습니다.

| 쓰고 싶은 것 | 쓰는 법 | 비고 |
|-----------|--------|-----|
| UUID 키 | `string @id @genid` | `uuid` 는 타입이 아닙니다. 길이를 주면(`string(36)`) UUID 가 아니라 ElastiCORE ID 가 만들어집니다 (위 `@genid` 절) |
| JSON 문서 | `json` | `String` + `columnDefinition = "json"` |
| 큰 문자열 | `clob` | `String` + `@Lob` (3.0 규칙 엔진). 2.x(`build`/`elcore`)에서는 `@Lob` 이 붙지 않으므로 `text` 를 씁니다 |
| 바이너리 | `blob` | `byte[]` + `@Lob`, JSON 에서는 base64 (3.0 규칙 엔진 전용) |

`json`·`blob`·`clob` 은 3.0.260925 부터 지원하는 타입입니다. 그 이전 버전에서는 `private json metadata;` 처럼
DSL 단어 그대로 생성되어 컴파일되지 않았으므로 `text` 로 대신했습니다. `@Lob` 은 3.0 규칙 엔진(`elcore3`)만
붙입니다. 타입별 생성 결과는 DSL 가이드의 값 타입 표를 따릅니다.

```yaml
entity:
  Product:
    fields:
      productId:  string @id @genid      # UUID
      metadata:   json
      attachment: blob
```

### 8.3 @ManyToOne @id 필드와 SQL 예약어 충돌

복합키에서 `@ManyToOne` 필드가 `@id`로 사용될 때, 필드명이 SQL 컬럼명(`field_name_id`)이 됩니다. SQL 예약어를 필드명으로 사용하면 테이블 생성이 실패합니다.

#### 문제가 되는 예약어
`order`, `group`, `select`, `from`, `where`, `table`, `index`, `key`, `value`, `user`, `date`, `time`, `timestamp`, `status`

#### 문제 상황
```yaml
# WRONG - 'order' is a SQL reserved keyword
entity:
  OrderItem:
    meta: entity
    fields:
      order: Order @id @ManyToOne       # Generates column: order_id - reserved keyword
      itemSeq: int @id                  # SQL: CREATE TABLE fails

# Database error:
# Syntax error near 'order_id' - reserved keyword
```

#### 해결방법 1: 비예약어 필드명 사용 (권장)
```yaml
# RECOMMENDED - use non-reserved field names
entity:
  OrderItem:
    meta: entity
    fields:
      orderRef: Order @id @ManyToOne    # Generates column: order_ref_id - safe
      itemSeq: int @id

  UserProfile:
    meta: entity
    fields:
      userRef: User @id @ManyToOne      # Not 'user'
      profileSeq: int @id
```

#### 해결방법 2: 생성 후 수동 수정
```yaml
# If you must use reserved keyword, manually add @Column annotation after generation
entity:
  OrderItem:
    meta: entity
    fields:
      order: Order @id @ManyToOne       # Generate first
      itemSeq: int @id
```

생성된 Java 코드를 수동 수정:
```java
// Add quotes to escape reserved keyword
@Id
@ManyToOne
@Column(name = "\"order_id\"")  // Add this manually
private Order order;
```

### 8.4 @audited 엔티티와 연관관계

`@audited` (Hibernate Envers) 엔티티가 감사되지 않는 엔티티와 관계를 가질 때, `@NotAudited` 어노테이션이 필요합니다.

#### 자동 처리
코드 생성기가 다음 경우에 자동으로 `@NotAudited` 추가:
- `@audited` 엔티티의 `@ManyToOne`, `@OneToMany`, `@ManyToMany` 필드
- 참조 대상 엔티티가 `@audited`가 아닌 경우

```yaml
entity:
  Order:
    meta: entity @audited
    fields:
      orderId: long @id @sequence
      customer: Customer @ManyToOne      # Customer is NOT @audited
      items: List<OrderItem> @OneToMany(mappedBy="order")  # OrderItem is NOT @audited

# Generated Java code (automatic):
# @NotAudited
# @ManyToOne
# private Customer customer;
#
# @NotAudited
# @OneToMany(mappedBy = "order")
# private List<OrderItem> items;
```

#### 수동 명시
자동 생성이 누락된 경우 YAML에 명시:
```yaml
fields:
  customer: Customer @ManyToOne @notaudited
  items: List<OrderItem> @OneToMany(mappedBy="order") @notaudited
```

#### 검증 필요 사항
1. 생성된 Java 코드 컴파일 확인
2. `@audited` 엔티티의 모든 관계 필드 검토
3. 비감사 엔티티 참조시 `@NotAudited` 존재 확인
4. 누락 발견시 YAML 재생성 또는 Java 코드 수동 수정

### 8.5 검색 조건 미구현 연산자

Section 3.2의 구현된 연산자 외에는 **사용 불가**합니다. 다음 연산자는 코드에 구현되지 않았습니다.

#### 미구현 연산자
- `!=`, `neq` (부정 equal) - 미구현
- `gt`, `gte`, `lt`, `lte` (키워드 형태) - 미구현

비어 있는지(IS NULL / IS NOT NULL) 검색은 `@search(isnull)` 하나로 합니다 (3.4.1).

#### 올바른 사용
```yaml
# WRONG - keyword form not implemented
fields:
  price: int @search(gt)     # 'gt' keyword not supported
  age: int @search(lte)      # 'lte' keyword not supported

# CORRECT - use symbol operators
fields:
  price: int @search(>)      # Symbol form works
  age: int @search(<=)       # Symbol form works
```

### 8.6 between 검색과 LocalDate +1일 동작

`@search(between)`에서 **LocalDate 타입만** 특수 처리됩니다. To 값에 자동으로 +1일을 적용하여 하루 끝까지 포함합니다.

```yaml
entity:
  Order:
    fields:
      orderDate: date @search(between)       # LocalDate - automatic +1 day to 'To'
      createDateTime: datetime @search(between)  # LocalDateTime - no +1 day
```

생성되는 Specification 로직:
```java
// LocalDate: To+1 applied
if(orderDateFrom != null && orderDateTo != null) {
    LocalDate adjustedTo = orderDateTo.plusDays(1);  // +1 day
    return sp.and((r,q,c) -> c.between(r.get("orderDate"), orderDateFrom, adjustedTo));
}

// LocalDateTime: No adjustment
if(createDateTimeFrom != null && createDateTimeTo != null) {
    return sp.and((r,q,c) -> c.between(r.get("createDateTime"), createDateTimeFrom, createDateTimeTo));
}
```

#### 주의사항
- **LocalDate 타입**: 2024-01-01 ~ 2024-01-31 검색시 자동으로 2024-02-01 00:00:00까지 포함
- **LocalDateTime 타입**: 입력값 그대로 사용, +1일 적용 안됨
- **숫자/기타 타입**: 입력값 그대로 사용

### 8.7 권장사항 요약

1. **@min/@max**: 정수값만 사용. 실수 검증은 커스텀 어노테이션 또는 서비스 레이어
2. **DSL 타입**: DSL 가이드 값 타입 표에 있는 타입만. `uuid` 는 타입이 아님 (UUID 키는 `string @id @genid`)
3. **필드명**: SQL 예약어 회피. 특히 복합키 `@id` 필드
4. **검색 연산자**: 구현된 16개 연산자만 사용. 비교는 기호(`>`, `<=`) 사용
5. **between + LocalDate**: +1일 자동 적용 이해 필요
6. **@audited**: 생성 후 관계 필드 `@NotAudited` 검증 필수
7. **코드 생성 후 검증**: 항상 컴파일 후 오류 확인

## @root / @aggregate — 애그리거트 경계 (DDD 룰셋)

`ddd-aggregate` 규칙을 쓰는 프로젝트에서, 엔티티가 **애그리거트 루트인지 멤버인지**를
명시합니다. 붙이지 않으면 모델에서 추론합니다.

| 형태 | 의미 |
|---|---|
| `@root` | 이 엔티티는 **애그리거트 루트**입니다 |
| `@aggregate(Order)` | `Order` 애그리거트의 **멤버**입니다 |
| `@aggregate` (값 없음) | `@root`와 같은 말 |
| (없음) | `@root` 엔티티가 `@OneToMany` 목록으로 가졌거나 `@cascade` 로 담았으면 그 루트의 멤버, 아무도 안 가졌으면 루트 |

**대부분 아무것도 안 써도 됩니다.** 규칙은 셋입니다.
1. `@root` 가 있으면 루트.
2. `@root` 인 엔티티가 `List<X> @OneToMany(...)` 로 가진 `X` 는 그 루트의 멤버 (`@aggregate` 불필요).
3. 아무도 목록으로 갖지 않는 엔티티는 혼자 쓰이는 루트.

`@root` 가 **꼭 필요한** 곳: 다른 루트가 목록으로 갖고 있지만 자기도 루트인 엔티티
(`Customer.orders: List<Order>` 의 `Order`). `@aggregate(X)` 가 필요한 곳: 두 루트가 같은 엔티티를
목록으로 가져 소속이 모호할 때.

각 애노테이션이 **한 가지만 말합니다.** 함께 써도 됩니다 —
`@root @aggregate(Order)`처럼 자기가 무엇인지와 어느 애그리거트를 이끄는지를
같이 적을 수 있고, 둘은 모순되지 않습니다(`@root`가 이깁니다).

### 왜 추론만으로는 부족한가

`@cascade`는 **영속성 결정**이지 도메인 결정이 아닙니다. 대개 애그리거트 경계와
일치하지만, 어긋나는 경우가 있습니다.

```yaml
  # 두 애그리거트가 함께 저장되지만 각자 루트인 경우
  CustomerProfile:
    meta: entity @service @root           # cascade로 담겨도 루트

  # JPA cascade 없이 코드로 생명주기를 관리하는 멤버
  OrderNote:
    meta: entity @service @aggregate(Order)
```

### 생성물에 미치는 영향

멤버는 **repository / service / controller / service_test를 받지 않습니다.**
애그리거트는 루트를 통해 적재·저장되고, 멤버는 루트에서 탐색해 도달하기 때문입니다.
`RepositoryHelper`도 루트의 리포지터리만 주입합니다.

값 객체는 별도 애노테이션이 없습니다 — **`@embeddable`이 이미 그 뜻**입니다
(자기 식별자가 없고 소유자에 인라인 저장). 자세한 내용은 `elasticore-rule-guide.md`를
보세요.

## domainService — 도메인 서비스 (DDD 룰셋)

**애노테이션이 아니라 DSL 섹션입니다.** 어느 엔티티에도 속하지 않는 연산이라,
엔티티의 meta 줄에 쓸 자리가 없기 때문입니다.

```yaml
# blueprint/<domain>/model/domainservice.yml
domainService:

  Pricing:
    desc: What an order costs once the products it names are priced
    uses: [Order, Product]
```

| 키 | 필수 | 설명 |
|---|---|---|
| `uses` | O | 이 연산이 걸치는 애그리거트들. 재생성되는 Base 에 도메인 포트로 주입됩니다 |
| `desc` | X | 클래스 주석 |

`uses` 가 비었거나 하나뿐이면 `DSL_WARN` 이 납니다(빌드는 계속). 애그리거트 하나에
대한 연산은 보통 그 애그리거트의 메서드입니다.

### 생성물

```java
public abstract class PricingDomainServiceBase {     // 매번 재생성 - uses 가 바뀌면 따라 바뀜

	@Autowired
	protected OrderRepositoryPort orderRepositoryPort;

	@Autowired
	protected ProductRepositoryPort productRepositoryPort;
}

@Service
public class PricingDomainService extends PricingDomainServiceBase {
	// 연산은 여기에. generate-once 이므로 이 파일은 첫 생성 후 당신 것입니다.
}
```

`uses` 에 애그리거트를 더하면 Base 에 포트가 생기고, 당신의 클래스는 고치지 않고 씁니다.

`env.yml` 의 `namespace.domainService` 로 자리를 정합니다. 생략하면 엔티티와 같은
패키지입니다.

## @behavior — 엔티티 행위 (DDD 룰셋)

엔티티에 **업무 메서드를 둘 자리**를 만듭니다. `ddd-behavior` 규칙(`ddd-springboot` /
`hexagonal-springboot` 및 Kotlin 번들에 포함)이 처리합니다. DDD 번들에서 **루트는 적지 않아도
기본으로 나뉩니다** - meta 의 `@behavior` 는 루트가 아닌 엔티티를 나누거나 뜻을 밝힐 때 씁니다.

| 위치 | 파라미터 | 기본값 | 효과 |
|---|---|---|---|
| 엔티티 meta | 없음 | 루트는 분리, `@editable` 이면 분리 안 함 | `XxxBase`(@MappedSuperclass, 재생성) + `Xxx`(@Entity, generate-once) |
| 필드 | 없음 | 잠김(생성 때만 받음) | setter `protected`, 매퍼가 쓰지 않음, DTO `READ_ONLY` |

필드 `@behavior` 는 **나뉜 루트에서만** 효과가 있습니다. `@editable` 루트나 멤버처럼 나뉘지 않는 엔티티에서는
DSL 경고만 남기고 필드는 요청으로 그대로 쓸 수 있습니다. 어느 엔티티에서나 "요청으로 받지 않는 서버 값" 이
필요하면 `@readonly` 를 씁니다 ([§2.2 @readonly / @noinput](#readonly--noinput---서버가-정하는-필드)).

## @editable — 요청으로 고치는 필드·루트 (DDD 룰셋)

DDD 번들에서 루트의 필드는 기본으로 **잠깁니다**: 요청은 루트를 만들 때만 값을 주고, 그 뒤로는
루트의 메서드가 바꿉니다 (PUT 이 잠긴 값을 바꾸려 하면 422). `@editable` 은 이를 엽니다.

| 위치 | 파라미터 | 효과 |
|---|---|---|
| 필드 | 없음 | 공개 setter, PUT 으로 고칠 수 있음 |
| 엔티티 meta | 없음 | 루트 전체가 평범한 데이터 - 분리하지 않고 모든 필드에 공개 setter |

```yaml
Item:                                   # 기준정보: 필드 단위 편집이 본래 모습
  meta: entity @root @editable @expose(20)

SalesOrder:
  meta: entity @root @expose(20)
  fields:
    orderNo: string(20)!                # 잠김: 생성 때만, 이후엔 메서드로
    memo: string(500) @editable         # 언제든 PUT 으로
```

사용 사례:
- **기준정보(마스터 데이터)**: 품목·거래처·코드 - meta `@editable`
- **업무 루트의 메모·연락처** 같은 부가 정보 - 필드 `@editable`
- **초안 편집**: 확정 전까지 요청으로 바꾸는 품목 줄 - 컬렉션 `@editable` (+ 서비스에서 상태 검사)

```yaml
SalesOrder:
  meta: entity @root @behavior @event(Confirmed, Cancelled)
  fields:
    status: OrderStatus @behavior
```

생성 결과 (Java):

```java
// SalesOrderBase.java - 재생성
@MappedSuperclass
@EntityListeners({AuditingEntityListener.class, SalesOrderInvariants.class})
public abstract class SalesOrderBase implements java.io.Serializable {
    @Setter(AccessLevel.PROTECTED)
    private OrderStatus status;
    public void raiseConfirmed() { ... }
}

// SalesOrder.java - 첫 생성 후 개발자 소유
@Entity
@Table(name = "erp_sales_order")
@DynamicUpdate
public class SalesOrder extends SalesOrderBase {
    public void confirm() { ... }
}
```

사용 사례:
- 수주·결재·배송처럼 **상태 전이가 있는 루트**: 전이 규칙을 엔티티 메서드로 (`order.confirm()`)
- **계산 필드**: `recalculate()` 로 합계를 루트가 스스로 유지
- **서비스 슬림화**: 애플리케이션 서비스는 조회·저장·트랜잭션만, 규칙은 엔티티에

## @invariant — 불변식 (DDD 룰셋)

> **어디에 쓸까?** 필드 하나에 대한 규칙은 필드에 씁니다 — `unitPrice: money(12.2)! @min(0.01, '단가는 0보다 커야 한다')`,
> 컬렉션 개수는 `lines: ... @minsize(1, '...')`. DDD 번들에서 루트의 필드 규칙은 저장 시점에도 검사되는 불변식이
> 됩니다. `@invariant` 가 꼭 필요한 것은 **여러 필드를 함께 보는 규칙**뿐입니다:
> - 두 필드 비교: `startDate <= endDate`, `discount <= totalAmount`
> - 다른 필드 값에 따라 필수: 상태가 출고면 출고일 필수
> - 멤버를 모아서 보는 규칙: 품목 금액 합 = 합계, 같은 품목 중복 금지
>
> 조건 문법이 지원하지 않는 규칙(두 필드 비교 등)은 행위 클래스의 메서드나 도메인 서비스에 씁니다.

애그리거트 루트가 **항상 참으로 유지하겠다고 약속한 것**을 선언합니다.
`ddd-invariant` 규칙이 있는 프로젝트(`ddd-springboot` / `hexagonal-springboot`
번들에 포함)에서 동작합니다.

```yaml
  Order:
    meta: entity @root @expose(30) @invariant(items.size > 0, 'an order has at least one line')
```

여러 번 쓸 수 있습니다 — 각 선언이 별개의 약속입니다.

| 파라미터 | 필수 | 설명 |
|---|---|---|
| 조건 | O | 아래 조건 문법 |
| 메시지 | X | 어겼을 때의 문장. 홑따옴표. 생략하면 조건을 그대로 씁니다 |

### 생성물

`OrderInvariants` 리스너 클래스가 생기고 루트의 `@EntityListeners` 에 등록됩니다.

```java
@EntityListeners({AuditingEntityListener.class, OrderInvariants.class})
public class Order extends AuditEntity { ... }
```

```java
public class OrderInvariants {
    @PrePersist
    @PreUpdate
    void check(Order order) {
        if (order == null) return;
        if (order.getItems() == null || order.getItems().size() <= 0) {
            throw new IllegalStateException("an order has at least one line");
        }
    }
}
```

**루트의 메서드가 아닌 이유**: JPA 는 클래스당 라이프사이클 콜백을 하나만
허용하는데, `@genid` 를 쓰는 루트는 이미 `@PrePersist` 를 쓰고 있습니다. 두
개면 경고가 아니라 퍼시스턴스 유닛이 아예 뜨지 않습니다.

**한계**: 마지막 멤버만 지우면 루트의 행이 바뀌지 않아 검사가 실행되지 않습니다.
루트도 함께 바뀌면 다시 걸립니다.

## @factory — 팩토리 (DDD 룰셋)

애그리거트를 **만드는 자리**를 선언합니다. 불변식이 있는 루트는 필드를 하나씩
세팅해서 만들 수 없기 때문입니다.

```yaml
  Order:
    meta: entity @root @invariant(items.size > 0, '...') @factory(place)
```

### 생성물

`OrderFactory.place(...)` 스텁이 생기고, 파일은 **generate-once** 입니다 —
첫 실행 후에는 덮어쓰지 않으므로 본문은 개발자 것입니다.

```java
public static Order place(LocalDateTime orderDate) {
    throw new UnsupportedOperationException(
            "OrderFactory.place is where a valid Order is made; write it.");
}
```

시그니처는 루트의 필수 필드입니다. 생성되는 식별자(`@genid`)와 컬렉션은 빠집니다 —
만들기 전에 식별자를 건네받는 팩토리는 아무것도 만들지 않은 것이고, 멤버는 루트의
add 메서드로 들어갑니다.

## @specification — 명세 (DDD 룰셋)

이름을 가질 만한 도메인 규칙을 선언합니다.

```yaml
  Order:
    meta: entity @root @specification(highValue, totalAmount > 100) @specification(hasLines, items notEmpty)
```

| 파라미터 | 필수 | 설명 |
|---|---|---|
| 이름 | O | 생성될 메서드 이름. 첫 콤마 앞 |
| 조건 | O | 아래 조건 문법 |

### 생성물

```java
OrderSpecifications.highValue()                          // Predicate<Order>
OrderSpecifications.highValue().and(OrderSpecifications.hasLines())
```

boolean 이 아니라 `Predicate` 인 이유는 **조합이 이 패턴의 요점**이기 때문입니다.
쿼리가 아니라 이미 읽어온 것에서 고릅니다.

### TypeScript 에서

`ts-model` 번들을 쓰면 같은 선언에서 브라우저용 파일도 나옵니다.

```ts
// OrderSpecifications.ts
export function highValue(order: OrderDTO | null | undefined): boolean {
    return order != null && (order.totalAmount != null
            && Number(order.totalAmount) > 100);
}

// OrderInvariants.ts — 강제가 아니라 사전 확인입니다
export function violations(order: OrderDTO | null | undefined): string[]
export function isValid(order: OrderDTO | null | undefined): boolean
```

`Number(...)` 는 서버가 소수를 JSON 숫자(Jackson 기본)로 보내든 문자열로 보내든 맞게
비교합니다. 손으로 쓴 `order.totalAmount > 100` 은 문자열로 오는 서버에서 금액과 무관하게
false 입니다. TS 타입 선언은 기본 직렬화에 맞춰 `number` 입니다.

## 조건 문법 (`@invariant`, `@specification` 공통)

| 형태 | 뜻 | 예 |
|---|---|---|
| `필드.size > n` | to-many 멤버 수 | `items.size > 0` |
| `필드 > n` / `필드 < n` | 숫자 비교 (`Integer`/`Long`/`Double`/`BigDecimal` 등) | `totalAmount > 100` |
| `필드 notNull` | 설정되어 있음 | `notes notNull` |
| `필드 notEmpty` | to-many 가 비어있지 않음 | `items notEmpty` |

필드·비교·리터럴만 받습니다. 임의 표현식 파서를 만들지 않는 것이 의도입니다.

> **`>=`, `<=`, `==` 는 쓸 수 없습니다.** DSL 애노테이션 파서가 인자 안의 `=` 를
> 키/값 목록의 시작으로 읽습니다. `:` 도 쓸 수 없습니다 — meta 줄은 YAML 스칼라라
> `이름: ` 이 매핑 시작으로 해석되어 파일 로드가 실패합니다.

읽지 못하는 조건은 **조용히 빠집니다**(빌드는 계속). 오타 하나가 도메인 전체의
생성을 막지 않게 하기 위해서입니다 — 생성된 파일에 해당 검사가 없으면 조건을
확인하세요.

## @access / @owner — 누가 무엇을 할 수 있는가 (v3)

엔티티 meta 에 연산별 허용 수준을, 소유자를 담는 필드를 적습니다.

```yaml
PurchaseOrder:
  meta: entity @root @expose @owner(customerId)
        @access(read=owner, create=user, cancel=owner)   # ship·delete 는 적지 않음 -> admin
```

| 수준 | 누가 |
|---|---|
| `public` | 누구나 (로그인 불필요) |
| `user` | 로그인한 사용자 |
| `owner` | 그 행의 주인(`@owner` 필드 = 로그인 ID)과 관리자 |
| `admin` | 관리자 (역할 `ADMIN`, `env.yml` 의 `config: adminRole:` 로 변경) |
| `none` | 아무도 (애플리케이션 자신은 가능) |

연산 이름: `read`(조회·검색), `create`, `update`, `delete`, 그리고 명령 이름(`ship`, `cancel`...).
**적지 않은 연산은 `admin`** 입니다 (`*=user` 로 기본값 지정). `@access` 가 없으면 아무것도 검사하지 않습니다.

생성되는 동작 (CoreService):

| 상황 | 결과 |
|---|---|
| 로그인 안 함 + `user`/`owner`/`admin` | 401 `Sign in first` |
| 권한 없음 | 403 |
| `owner` 조회·수정·삭제·명령에서 남의 행 | 404 (존재 자체를 알리지 않음) |
| `owner` 검색 | 소유자 필드 **동등 조건**(`=`)을 검색에 덧붙임 - 내 것만 나옴 (필드의 `@search` 방식과 무관) |
| `create` | 소유자 필드를 로그인 ID 로 덮어씀 - 방문자가 보낸 값도 지움 (관리자는 요청 값 그대로). 소유자가 기본키이고 자동 생성이 아니면 기본키를 로그인 ID 로. 소유자 필드가 `@readonly`·`@noinput` 이면(요청이 보낼 수 없는 값) 관리자를 포함해 로그인한 사람이 소유자 - `runAsSystem` 안에서는 손대지 않음 |
| `update` | 소유자 필드와 `@access(admin)` 필드는 **행의 현재 값** 으로 (보낸 값 무시, `@dynamicupdate(false)` 에서도 지워지지 않음). `@readonly` 소유자는 관리자의 수정에서도 그대로 |
| `owner` 의 `findAll`·조건 삭제·조건 수정 | 관리자만 (한 건씩 확인할 수 없으므로) |
| `owner` 수준인데 `@owner` 가 안 풀림 | 빌드 경고(DSL_WARN) + 실행 시 관리자만 (검색은 403) |
| 로그인 안 한 호출 (요청 밖·WebFlux·gRPC·`@Async` 포함) | **방문자** - fail-closed |
| `CurrentUser.runAsSystem(() -> ...)` 안 | 애플리케이션 자신 - 모두 허용 (시작 시 데이터, 배치) |

```java
// 시작 시 관리자 계정 만들기 - 애플리케이션 자신으로 실행했다고 명시
CurrentUser.runAsSystem(() -> customers.save(admin));
```

다른 엔티티의 **행 전체**(중첩 DTO)는 그 엔티티의 읽기 수준이 `public` 일 때만 DTO 에 실립니다.
`read=owner` 인 고객을 주문 DTO 가 통째로 담으면 주문을 읽는 사람에게 고객 정보가 보이므로, 빌드가
그 필드를 빼고 경고합니다 (식별자 `customerId` 는 남음).

`@access` 문법 오류(`read=owners`, 엔티티에 `@access(admin)`)는 빌드 경고로 알려 줍니다 - 그대로 두면
애플리케이션이 시작하지 않습니다. 규칙 엔진(`elcore3`)에서 `jpa-service-access` 가 없는 번들(2.x 호환 번들 등)로
`@access`·`@owner`·`@password` 를 쓰면 아무것도 검사·해시되지 않으므로 빌드 경고가 납니다.
**2.x 생성기(`elcore`, 어노테이션 프로세서)는 이 어노테이션들을 처리하지 않습니다** - 3.0 규칙 엔진을 쓰세요.

생성되는 서비스 테스트(`testCode: true`)는 접근 규칙이 있는 엔티티에 `RunAsSystemExtension` 을 붙여
애플리케이션 자신으로 실행합니다. 권한 자체를 시험하는 테스트는 이 확장 없이 사용자로 로그인해서 씁니다.

필드에 `@access(admin)` 을 붙이면 **관리자만 쓰는 필드** 입니다. 다른 사람이 보낸 값은 생성 시 엔티티
기본값(`@default`)으로, 수정 시 행의 현재 값으로 바뀝니다. 이 필드는 DTO 에 기본값을 두지 않아, 관리자가
다른 필드만 고칠 때 기본값이 실려 와 덮어쓰지 않습니다. 소유자 필드도 수정으로 바꿀 수 없습니다.

```yaml
Customer:
  meta: entity @root @editable @expose @owner(customerId) @access(read=owner, create=public, update=owner)
  fields:
    role: UserRole @default(UserRole.USER) @access(admin)   # 가입자가 스스로 관리자가 될 수 없음
```

로그인 API 는 **엔티티 줄에 `@signin` 을 쓰면 생성됩니다** (세션 기본값, 3.0.261021 부터). `@login` 과
`@password` 가 있고 `@expose` 된 엔티티이며, **그 행의 비밀번호와 로그인은 본인과 관리자만 바꿀 수 있어야** 합니다
(`update=owner` 와 키를 가리키는 `@owner`, 또는 `update=admin`·`none` - 아래 "수정 수준"):

```yaml
Customer:
  meta: entity @root @editable @expose(20) @signin @owner(customerId) @access(read=owner, create=public, update=owner)
```

| 요청 | 답 |
|---|---|
| `POST /api/shop/customer/signin` `{"login": "...", "password": "..."}` (키는 두 필드의 이름) | 200 고객 (비밀번호 없음), 세션 시작. 틀리면 401 `Wrong login or password` |
| `POST /api/shop/customer/signout` | 204, 세션 끝 |
| `GET /api/shop/customer/me` | 200 로그인한 고객, 방문자는 401. 키가 문자열이면 키가 `me` 인 행은 `GET /{id}` 가 아니라 이 엔드포인트(로그인한 사람)가 답합니다 |

CoreService·유스케이스에 `me()` 가, TypeScript 클라이언트에 `signIn(login, password)`, `signOut()`, `me()` (방문자면
null) 가 생깁니다. 쿠키 세션이라 화면과 API 가 같은 출처(origin)일 때 동작합니다. `@signin` 이 없으면 `me()` 도 없고
`authenticate` 만 있습니다 (아래 "@password / @login").

세션에는 행의 키와 **역할**이 들어갑니다. 역할은 `@role` 을 붙인 필드, 없으면 `role` 이라는 필드의 값으로,
**`@access(admin)` 인 enum 이나 문자열 필드만** 씁니다 (enum 이면 상수 이름 - `UserRole.ADMIN` 이면 `ADMIN`,
`adminRole` 과 비교). 관리자만 쓰는 필드가 아니면 가입자가 `"role":"ADMIN"` 을 보내고 로그인해 관리자가 되므로,
빌드가 DSL 경고를 내고 역할 없이 로그인합니다. 역할 필드가 없으면 모두 일반 사용자입니다.

- **수정 수준.** 행을 쓰는 요청(`PUT`, `PUT /batch`, 조건 수정)은 모두 `update` 수준을 묻습니다 - `update`, 없으면 `*`,
  그것도 없으면 `admin`, `@access` 가 아예 없으면 `public`. 이것이 `admin`·`none` 이거나, `owner` 이면서 `@owner` 가 키일
  때만 로그인 API 를 만듭니다. `public`·`user` 이거나 키가 아닌 필드의 `owner` 이면 남이 관리자의 비밀번호를 바꾸고 관리자로
  로그인할 수 있으므로 만들지 않고, 이유와 고칠 문장(`@access` 가 없으면 `@owner(customerId)
  @access(read=owner, create=public, update=owner)`)을 DSL 경고로 알려 줍니다. `@access` 에 적지 않은 연산은 `admin` 이므로
  `@access` 를 처음 쓸 때는 가입(`create=public`)과 조회도 함께 적습니다. `*` 는 다른 항목과 함께 써도 됩니다
  (`@access(read=public, create=public, *=owner)` 의 수정은 `owner`). 다른 엔티티의 DTO 가 `@ref(member.login)`·
  `@ref(member.password)` 로 이 엔티티를 읽어도 그 길로는 쓰지 못합니다 (위 "@ref" - 비밀번호는 읽지도 않음).
- **키를 요청이 정하면 삭제도 본인과 관리자만.** 키가 생성되지 않으면(`@genid`·`@sequence` 가 아님) 누가 `alice` 행을 지운 뒤
  `alice` 로 다시 가입해 `alice` 가 주인이던 모든 행을 가집니다. 그래서 `delete` 수준(없으면 `*`, 그것도 없으면 `admin`)이
  `admin`·`none` 이거나 키를 가리키는 `@owner` 의 `owner` 일 때만 만들고, 아니면 `delete is user and its key handle is one
  the request chooses ...` DSL 경고를 냅니다. 키를 생성되게 해도 됩니다.
- **로그인은 유일.** 엔티티 줄에 `@signin` 이 있으면 `@login` 컬럼에 `unique = true` 가 붙습니다 - 위 조건에 걸려 로그인
  API 를 만들지 않을 때도 붙어서, `@access` 를 고쳐도 테이블은 바뀌지 않습니다 (이미 `@unique` 이거나, 키이거나,
  그 컬럼 하나만의 unique `@Index` 가 있으면 그대로). 이미 있는 로그인으로 가입하거나 로그인을 바꾸면 다른 제약 위반처럼
  409 입니다. 같은 로그인이 두 행이면 `authenticate` 가 둘 다 거절하므로, 남의 로그인으로 행을 만들어 그 사람을 못 들어오게
  하는 일을 DB 가 막습니다. 이미 운영 중인 DB 는 마이그레이션으로 unique 제약을 더합니다 (중복이 있으면 먼저 정리).
- **애플리케이션에 `@signin` 엔티티는 하나만.** 세션은 맨 키만 들고 있어, 둘이면 판매자 7 이 고객 7 로 읽힙니다. 한 번의
  생성 안에서 컨트롤러가 같은 애플리케이션에 생기는 엔티티끼리 셉니다: 단일 모듈 프로젝트는 모든 도메인이 하나의
  애플리케이션이고, 멀티 모듈(`elasticore.modules`)에서는 컨트롤러가 생기는 모듈(`control` 의 `모듈명://` 이 가리키는 모듈,
  없으면 그 blueprint 가 있는 모듈)마다 하나입니다. 둘 이상이면 그 애플리케이션 어디에도 만들지 않고 DSL 경고를 냅니다.
  두 모듈이 한 애플리케이션으로 함께 실행되는지는 생성기가 알 수 없으니, 그런 두 모듈에는 한쪽에만 `@signin` 을 씁니다.
  둘 이상의 모듈이 각자 `@signin` 엔티티를 가지면 빌드가 `@signin is counted per module: Staff and Customer each sign in
  through their own module - if these modules run as one application, only one of them may say @signin.` 을 한 번 알려 줍니다.
- **같은 애플리케이션의 다른 `@login`·`@password` 엔티티.** 그 엔티티를 손으로 `SessionCurrentUser.signIn` 하면 그 키도
  같은 세션 키라, `me()` 와 모든 소유자 검사가 `@signin` 엔티티의 같은 키 행으로 읽습니다 (직원 7 = 고객 7). 빌드가
  `Customer: the session the generated sign-in starts holds only a bare Customer key, and Staff has @login and @password
  too ...` DSL 경고로 알려 줍니다. 그 엔티티는 자기 `CurrentUser` 빈(Spring Security 등)으로 로그인하거나, 로그인은
  `@signin` 엔티티로만 합니다.
- 키가 둘 이상이거나, `@expose` 가 없거나, `@login`·`@password` 가 모자라도 만들지 않고 DSL 경고를 냅니다.
- 키가 요청이 정하는 문자열이면(`@genid`·`@sequence` 가 아님) 키가 `me` 인 행은 `GET /{id}` 로 읽을 수 없으므로(`GET /me`
  가 로그인한 사람을 답함) 빌드가 경고합니다. `beforeSave` 에서 `me` 를 거절하거나 키를 생성되게 합니다.
- 틀린 비밀번호를 몇 번까지 허용할지, 정지된 계정을 막을지 같은 로그인 규칙은 개발자 소유인 `CustomerService` 에서
  `authenticate(login, password)` 를 override 해 씁니다 - 생성된 `signin` 이 그것을 부릅니다.
- 컨트롤러는 개발자 소유라 **손댄 컨트롤러에는 들어가지 않습니다.** 빌드가 `... is yours and does not serve POST /signin,
  POST /signout, GET /me ...` 를 DSL 경고로 알려 주면, 그 파일을 지우고 빌드해(손댄 내용은 옮겨 두고) 생성된 것을 보거나
  세 메서드를 옮겨 적습니다. 이 비교는 경로 변수 이름(`/{id}` 와 `/{customerId}`), 끝의 `/`, Kotlin 의 배열 형식
  (`@GetMapping(value = ["/me"])`), `@RequestMapping(value = "/me", method = RequestMethod.GET)` 을 같은 엔드포인트로
  읽고, 상수로 쓴 경로(`@GetMapping(ME)`)처럼 읽지 못한 매핑이 있으면 그 HTTP 메서드의 엔드포인트는 묻지 않습니다.
  2.x 호환 번들(`jpa-springboot`)은 TypeScript 클라이언트가 없으므로 이 경고를 하지 않습니다.
  거꾸로 손댄 컨트롤러가 DSL 이 더는 만들지 않는 `POST /signin`·`GET /me` 를 아직 가지고 있거나(`@signin` 을
  지웠거나 위 이유로 거절됨), 관리자만 쓰는 필드가 아닌 역할을 세션에 넣으면 `... is yours and still
  serves POST /signin, GET /me ...` / `... still puts a role in the session ...` 경고가 납니다 - 그 로그인은 생성된 검사가
  믿는 세션을 계속 만들기 때문입니다. 그 메서드를 지우거나 역할 인자를 뺍니다.

손으로 세션을 다룰 때 (가입하고 바로 로그인 같은 것):

```java
SessionCurrentUser.signIn(request, customer.getCustomerId(), customer.getRole().name());
SessionCurrentUser.signOut(request);
```

Spring Security 를 쓰면 `io.elasticore.springboot3.security.CurrentUser` 빈을 등록하고 `@signin` 은 쓰지 않습니다
(`authenticate` 는 남음).

## @password / @login — 자격 증명 (v3)

```yaml
login:    string(10)! @login @unique
password: string(255)! @password      # 해시 길이를 담을 만큼 (PBKDF2 약 100자)
```

- 요청으로 받은 비밀번호는 **모든 쓰기 경로에서 해시로만** 저장 (기본 PBKDF2-SHA256, `PasswordHasher` 빈으로 교체).
- 응답·검색·DTO 복사에 나오지 않음 (`@writeonly` 와 같음), TypeScript 에서 optional.
- `@login` 과 `@password` 가 함께 있으면 CoreService·유스케이스에 `authenticate(login, password)` 생성 - 행, 또는
  로그인이 없거나 두 행 이상이거나 비밀번호가 틀리면 비어 있음. 없는 로그인도 같은 해시 비용을 치러 응답 시간으로 계정
  존재를 알 수 없음.
- 로그인은 유일해야 합니다: 같은 로그인이 두 행이면 `authenticate` 가 둘 다 거절하므로, 누가 남의 로그인으로 행을 만들면
  그 사람이 로그인하지 못합니다. `@signin` 없이 쓰는 엔티티의 로그인이 유일하지 않으면(`@unique`, 키, 그 컬럼 하나만의
  unique `@Index` 가 아님) DSL 경고를 냅니다.
- 엔티티 줄에 `@signin` 까지 쓰면 `me()`, 컨트롤러의 `signin`·`signout`·`me` (위 "@access / @owner" 의 로그인 표), 그리고
  로그인 컬럼의 unique (unique 는 로그인 API 가 거절되어도 `@signin` 만으로 붙음).

## @snapshot / @compute — 파생 값 (DDD 룰셋)

```yaml
OrderLine:
  fields:
    item:     Item @ManyToOne
    quantity: quantity! @min(1)
    unitCost: money(10.2) @snapshot(item.unitCost)     # 주문 당시 가격을 한 번 복사
    subTotal: money(12.2) @compute(unitCost * quantity)
PurchaseOrder:
  fields:
    totalWithoutVat: money(12.2) @compute(sum(lines.subTotal))
```

- 둘 다 요청으로 쓸 수 없는 필드입니다 (DTO 에서 읽기 전용, 매퍼가 쓰지 않음 - `@editable` 엔티티에서도).
- `@compute` 가 다른 `@compute` 를 쓰면 그 필드를 먼저 선언하세요 (거꾸로면 빌드 경고).
- `@snapshot` 은 값이 비었을 때만 채웁니다 - 이후 상품 가격이 바뀌어도 주문 줄은 그대로.
  참조한 행이 없으면 404 (`Item 99 does not exist`, 코드 `reference.OrderLine.item`).
- `@compute` 는 저장할 때마다 다시 계산합니다. 피연산자 중 하나가 null 이면 결과도 null.
- **`@formula` 와 다릅니다**: `@formula("...")` 는 DB 가 계산하는 Hibernate SQL 식입니다.

## @event — 도메인 이벤트 (DDD 룰셋)

애그리거트 루트에 **무슨 일이 일어날 수 있는지**를 선언합니다. `ddd-event` 규칙이
있는 프로젝트(`ddd-springboot` / `hexagonal-springboot` 번들에 포함)에서 동작합니다.

```yaml
  Order:
    meta: entity @root @expose(30) @event(Placed, Cancelled)
```

### 생성물

| 생성되는 것 | 내용 |
|---|---|
| `OrderPlacedEvent`, `OrderCancelledEvent` | 루트의 식별자 + 선언한 내용 + `occurredOn`(발생 시각)을 담은 불변 값 (Java: equals/hashCode/toString, Kotlin: data class) |
| `order.raisePlaced()`, `order.raiseCancelled()` | 루트에 생기는 발생 메서드 |
| `@DomainEvents` / `@AfterDomainEventPublication` | Spring Data 가 저장 시점에 읽어가는 접근자 |

```java
// 개발자가 소유한 확장 서비스에서
order.raiseCancelled();
orderRepository.save(order);   // 여기서 발행됩니다
```

- **저장될 때** 발행됩니다. 저장이 실패하면 발행되지 않습니다.
- 발행 후 지워지므로 다음 저장에서 **다시 발행되지 않습니다.**
- 이벤트는 애그리거트가 아니라 **식별자**를 담습니다. 객체를 넘기면 이미 끝난
  트랜잭션을 통해 다른 애그리거트를 쓸 수 있게 되기 때문입니다.
- 이벤트 객체는 발행 시점에 만들어집니다. 새로 삽입되는 행은 발생 시점에 아직
  식별자가 없기 때문입니다.

### 이벤트 내용 (payload)

이벤트 이름 뒤 괄호에 **루트의 필드**를 적으면 이벤트가 그 값을 싣습니다.

```yaml
  SalesOrder:
    meta: entity @root @event(Confirmed(totalAmount), Shipped(orderNo, shipWarehouse, totalAmount), Cancelled)
```

- 값은 `raiseShipped()` 를 **호출한 시점**의 값입니다(저장 시점이 아님). 식별자만 발행 시점에 채워집니다.
- 받는 쪽(리스너, Outbox 소비자)이 수주를 다시 조회하지 않아도 됩니다.
- 실을 수 있는 것: 컬럼, enum, 값 객체. 다른 애그리거트 참조와 컬렉션은 DSL 경고 후 제외됩니다
  (다른 애그리거트는 식별자 컬럼으로 실으세요).

### 밖으로 내보내기 - Outbox

`env.yml` 에 `outbox: true` 를 두면 이벤트가 같은 트랜잭션에서 Outbox 테이블에 기록되고,
생성된 Relay 가 커밋 후 애플리케이션의 `OutboxPublisher`(Kafka 등)로 보냅니다.
자세한 것은 `elasticore-rule-guide.md` 의 `ddd-outbox`.

### 언제 발생하는지는 생성하지 않습니다

주문이 취소될 수 있다는 것은 모델에 있지만 **무엇이 취소인지**는 모델에 없습니다.
추측해서 넣은 발생 호출은 단순히 수정된 행에서도 울립니다. 그래서 루트는
`raiseCancelled()` 를 제공하고, 호출 위치는 도메인이 정합니다.

### 제약

| 항목 | 내용 |
|---|---|
| 대상 | 애그리거트 **루트**만. 멤버의 선언은 무시됩니다 |
| 언어 | Java, Kotlin |
| 위치 | `namespace.event` 로 지정. 없으면 엔티티와 같은 패키지 |
