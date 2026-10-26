# ElastiCORE Rule Guide

코드 생성 규칙(rule)을 **고르고**, **끄고**, **직접 작성하는** 방법.

DSL로 모델을 쓰는 것이 목적이라면 `elasticore-dsl-guide.md`를,
생성된 코드를 쓰는 것이 목적이라면 `elasticore-dev-guide.md`를 보세요.
이 문서는 **생성되는 코드 자체를 바꾸려는 사람**을 위한 것입니다.

---

## 1. 규칙이란 무엇인가

ElastiCORE 3.0의 생성기는 두 부분으로 나뉩니다.

| | 무엇을 결정하나 | 어디에 있나 |
|---|---|---|
| **규칙(rule)** | *무엇을* 낼지 — 이 필드에 `@Column(length=200)`이 붙는가 | Java 클래스 (`CodeRules`) |
| **스켈레톤(template)** | 그것이 *어디에* 놓이는지 — 파일의 뼈대 | `.tmpl` 파일 |

규칙은 모델을 읽고 판단만 합니다. 문자열을 어떻게 쓰는지 — 세미콜론, import 형태,
프로퍼티 선언 — 는 **언어 이음새(`Syntax`)**가 답합니다. 이 분리가 같은 규칙으로
Java와 Kotlin을 모두 생성할 수 있게 하는 이유입니다.

```
모델(DSL) ──▶ Spec ──▶ 규칙들 ──▶ SourceCode ──▶ 스켈레톤 ──▶ 파일
                                      │
                                   Syntax(언어)
```

---

## 2. 규칙 고르기 (env.yml)

규칙 선택은 도메인의 `env.yml`에서 합니다. **이 설정을 쓰지 않으면 기본 번들이 적용되므로,
대부분의 프로젝트는 이 절을 몰라도 됩니다.**

```yaml
elasticore:
  config:
    publisher:
      rules: [jpa-springboot-v3, kr.co.acme.AcmeRules]
      suppress: [jpa-comment]
```

| 키 | 의미 |
|---|---|
| `rules` | 사용할 규칙 집합. **기본값을 대체**합니다 |
| `suppress` | 끌 규칙의 id. **누적**됩니다 |

값은 세 가지 형태를 모두 받습니다: `[a, b]`, `a, b`, `a`.

### 우선순위

```
env.yml publisher.rules  >  시스템 속성 elasticore.publisher.rules  >  기본 번들
```

### 이름이 가리키는 것

`rules`의 각 이름은 셋 중 하나로 해석됩니다.

1. 등록된 **번들** — 여러 규칙 집합의 묶음
2. 등록된 **규칙 집합** — 하나의 `CodeRules` 클래스
3. **완전한 클래스 이름** — 직접 작성한 `CodeRules` 구현체

### 기본 제공 번들

| 번들 | 무엇을 생성하나 |
|---|---|
| `jpa-springboot` | 2.x 파이프라인과 **같은** 출력 (이력에 적은 2.x 버그 수정 제외 - `elcoreRulesCheck`·`elcoreTreeCheck` 가 비교) |
| `jpa-springboot-v3` | 정리된 출력 — 실제 사용하는 import만, 규칙이 만드는 클래스 애노테이션 |
| `jpa-springboot-kt` | Kotlin 출력 |
| `ddd-springboot` | v3 + 애그리거트 경계 — 루트만 repository/service/controller, 값 객체 불변, 도메인 이벤트 |
| `ddd-springboot-kt` | 위와 같은 읽기의 Kotlin 판 |
| `hexagonal-springboot` | `ddd-springboot` + 인바운드 포트(`<Entity>UseCase`) |
| `hexagonal-springboot-kt` | 위와 같은 육각형의 Kotlin 판 |
| `ts-model` | TypeScript — DTO·enum·검색DTO + 컨트롤러 호출 클라이언트 |

#### 육각형 번들이 더 만드는 것

`hexagonal-springboot`는 노출된 애그리거트 루트마다 인바운드 포트를 하나
선언합니다.

```
application/port/in/OrderUseCase   <- 애플리케이션이 밖에서 불리는 면
application/OrderService           <- implements OrderUseCase (확장 서비스)
interfaces/rest/OrderController    <- OrderUseCase 를 주입받음 (구현 클래스가 아니라)
```

놓일 자리는 `namespace.inboundPort` 로 정합니다. 선언하지 않으면 `service` 와 같은
패키지에 놓이므로, **기존 프로젝트는 아무 변화도 겪지 않습니다.** 두 방향을 나누고
싶으면 `inboundPort` 를 선언하세요 — 그러면 `port.in` 과 `port.out` 이 나란히 섭니다.

포트의 메서드 목록은 생성된 서비스의 면을 그대로 옮긴 것이라, "서비스가 포트를
구현한다"는 주장을 **컴파일러가 매번 검사**합니다. 아웃바운드 포트는 이미
`port` DSL 이 하던 일입니다 — 어댑터가 런타임 프록시라 생성할 코드가 없을 뿐입니다.

Kotlin 판은 시그니처의 널 허용까지 서비스와 맞춥니다 (`save(dto: OrderDTO): OrderDTO?`).
그 언어에서는 널 허용이 시그니처의 일부라, 맞지 않으면 애초에 컴파일되지 않습니다.

#### 언어별로 다르게 도달하는 규칙

| 규칙 | Java | Kotlin | TS | 비고 |
|---|---|---|---|---|
| `ddd-aggregate` / `ddd-reference` | O | O | - | 경계는 모델의 사실. TS 에서는 엔드포인트가 없다는 형태로 이미 닿아 있음 |
| `ddd-collection` (cascade·orphanRemoval) | O | O | - | ORM 에게 하는 말이라 브라우저에 대응물 없음 |
| `ddd-collection` (add/remove/replace) | O | O | - | Kotlin 프로퍼티를 `MutableList` 로 선언 |
| `ddd-collection` (setter 봉인) | O | O | - | 아래 참고 |
| `ddd-mapper-ref` (쓰기 차단 + 멤버 왕복) | O | O | - | 매퍼는 서버의 것 |
| `ddd-value-object` / `ddd-value-object-kt` | 각자 | 각자 | - | 불변을 Lombok 으로 만드느냐 언어로 만드느냐의 차이 |
| `ddd-event` | O | O | - | 저장 시점에 매여 있음 |
| `ddd-invariant` | O | O | **O** | TS 에서는 강제가 아니라 사전 확인 |
| `ddd-specification` | O | O | **O** | |
| `ddd-factory` | O | O | - | 애그리거트를 만드는 일은 서버의 것 |
| `ddd-domain-service` | O | O | - | 포트를 주입받음 |
| `ddd-behavior` | O | O | - | `XxxBase`(재생성) + `Xxx`(개발자 소유) 로 분리 |
| `ddd-command` | O | O | - | `commands:` -> 엔드포인트·명령 DTO·유스케이스·서비스·추상 메서드 |
| `ddd-outbox` | O | O | - | `env.yml` 의 `outbox: true` 일 때 |
| `ddd-state` | O | O | **O** | `states:` -> 명령마다 상태 전이·`canX()`·초기값. TS 는 `canShip(dto)` |
| `ddd-derive` | O | O | - | `@snapshot`·`@compute` -> `derive(lookup)`, 서비스가 저장 전에 호출 |
| `jpa-service-access` | O | O | - | `@access`·`@owner`·`@password`·`@login` -> CoreService 검사·해시·`authenticate`, `@signin` 이면 `me()`·컨트롤러의 signin·signout·me·유일한 로그인 컬럼 (v3 번들 전부) |
| `jpa-value-object-boundary` | O | O | - | `@embeddable` 필드에 DTO 의 검증·JSON 규칙, DTO 쪽 `@Valid` (v3 번들 전부) |
| 프로세스 매니저 (`ddd-domain-service` 의 `processManager:`) | O | O | - | 이벤트 커밋 후 별도 트랜잭션 |
| `ddd-repository-port` / `-adapter` | O | O | - | 브라우저에는 리포지터리가 없음 |
| `hex-usecase` | O | O | - | 두 언어의 스켈레톤이 모두 있음 |

> **Kotlin 에서 setter 를 봉인하는 법.** `private set` 만 쓰면 컴파일이
> 거부합니다 — *"Private setters are not allowed for open properties"*. 엔티티
> 프로퍼티가 `open` 인 것은 all-open 플러그인 때문이고, 그게 없으면 Hibernate 가
> 프록시를 못 만듭니다. 한동안 이건 **ORM 의 요구와 캡슐화가 서로를 배제하는**
> 것으로 기록돼 있었습니다.
>
> 배제하지 않습니다. 플러그인은 **기본값이 final 인 것을 열 뿐, 명시적 `final` 은
> 그대로 둡니다.** 그래서 생성물은 이렇습니다:
>
> ```kotlin
> final var items: MutableList<OrderItem>? = null
>     private set
> ```
>
> 프로퍼티는 봉인되고 클래스는 열린 채로 남습니다 — 양쪽이 실제로 필요로 하던
> 것이 각각 그것이었습니다. Hibernate 는 프로퍼티가 아니라 필드에 쓰므로 영향이
> 없습니다.

여기서 X 인 항목은 조용히 빠지지 않습니다. 언어 전용 규칙은 자기가 쓰는 언어를
말하고(`requireSyntax`), 다른 언어에서 선택되면 그 자리에서 멈춥니다.
"자기 언어에서는 할 일이 없다"고 스스로 판단해 넘어가는 규칙(`ddd-collection`의
메서드 절반)만 아무 말 없이 지나갑니다.

#### 도메인 이벤트 (`ddd-event`)

애그리거트 루트가 `@event(Placed, Cancelled)` 를 선언하면:

```java
OrderPlacedEvent, OrderCancelledEvent   // 루트의 식별자와 발생 시각을 담은 클래스
order.raisePlaced()                     // 루트에 생기는 발생 메서드
```

발생한 이벤트는 그 루트가 **다음에 저장될 때** Spring Data 가 읽어서 발행합니다
(`@DomainEvents` / `@AfterDomainEventPublication`). 저장이 실패하면 발행되지
않고, 발행된 뒤에는 지워지므로 두 번 발행되지 않습니다.

**언제 발생하는지는 생성하지 않습니다.** 주문이 취소될 수 있다는 것은 모델에
있지만 무엇이 취소인지는 없기 때문입니다 — 호출은 개발자가 소유한 확장 서비스에서
합니다.

이벤트 클래스가 놓일 자리는 `namespace.event` 로 정합니다. 선언하지 않으면
엔티티와 같은 패키지에 놓입니다.

#### 불변식 (`ddd-invariant`)

애그리거트가 **항상 참으로 유지하겠다고 약속한 것**입니다. 경계 규칙이 "누가 쓸 수
있는가"를 정한다면, 불변식은 "쓰인 뒤에 무엇이어도 되는가"를 정합니다.

```yaml
Order:
  meta: entity @root @invariant(items.size > 0, 'an order has at least one line')
```

여러 번 쓸 수 있습니다. 각 선언이 별개의 약속입니다.

생성물은 `OrderInvariants` **리스너 클래스**이고 루트의 `@EntityListeners` 에
등록됩니다. 루트의 메서드가 아닌 이유는 JPA 가 클래스당 콜백 하나만 허용하는데
`@genid` 루트는 이미 `@PrePersist` 를 쓰고 있기 때문입니다 — 두 개면 경고가 아니라
**퍼시스턴스 유닛이 아예 뜨지 않습니다.**

검사는 `@PrePersist` / `@PreUpdate` 에서, 즉 **행이 쓰일 때** 실행됩니다. 어떤
경로로 저장하든 지나칠 수 없습니다. 어기면 `IllegalStateException` 입니다 —
사용자 입력을 거절하는 것이 아니라 도메인이 "약속한 적 없는 상태가 되었다"고
말하는 것이므로, 검증 메시지(`@length` 등이 만드는 Bean Validation)와는 다릅니다.

> **멤버가 빠지는 것도 루트의 쓰기입니다.** 마지막 멤버를 지우면 멤버의 행만
> 지워지고 루트의 행은 그대로라, 루트의 리스너만으로는 검사가 실행되지 않습니다.
> 그래서 멤버에 `OrderItemRemovalCheck` 리스너가 붙어, 멤버 행이 지워질 때(flush)
> 루트의 불변식을 남은 상태로 검사합니다. remove 메서드 안에서 검사하지 않는 이유는
> replace 메서드가 전체 교체를 위해 먼저 비우기 때문입니다 — flush 때는 이미 다시
> 채워져 있으므로 교체는 허용됩니다. 루트를 지우는 중이면 검사하지 않습니다.
> 멤버에 루트를 가리키는 필드가 없으면 검사를 만들 수 없어 DSL 경고가 납니다.
> 샘플 테스트: `AggregateInvariantTest` (`emptyingAnOrderIsCaught`,
> `replacingEveryLineIsStillAllowed`, `deletingTheRootIsNotRefused`).

#### 팩토리 (`ddd-factory`)

불변식이 있는 루트는 필드를 하나씩 세팅해서 만들 수 없습니다 — 중간이 유효하지
않기 때문입니다. 유효해지는 순간을 책임질 자리가 팩토리입니다.

```yaml
Order:
  meta: entity @root @invariant(items.size > 0, '...') @factory(place)
```

생성물은 `OrderFactory` 이고 **generate-once 마커**를 답니다. 첫 실행이 시그니처를
쓰고 그 뒤로는 파일을 건드리지 않습니다. 본문은 던지는 스텁입니다 — 조용히 무언가
반환하는 스텁은 운영까지 가기 때문입니다.

시그니처는 루트의 필수 필드입니다(생성되는 식별자와 컬렉션은 제외). 모델이 말할
수 있는 최대치이고, 그 다음은 개발자의 파일입니다.

#### 명세 (`ddd-specification`)

이름을 가질 만한 도메인 규칙입니다. 같은 비교가 서비스와 리포트와 테스트에 각각
다시 타이핑되다가 하나만 다르게 타이핑되는 것을 막습니다.

```yaml
Order:
  meta: entity @root @specification(highValue, totalAmount > 100)
```

생성물은 `OrderSpecifications.highValue()` 이고 `Predicate<Order>` 를 돌려줍니다.
boolean 이 아니라 predicate 인 이유는 **조합이 이 패턴의 요점**이기 때문입니다:
`highValue().and(hasLines())` 는 규칙으로 만든 규칙입니다.

**쿼리가 아닙니다.** Spring Data `Specification<Order>` 로 내보내 SQL 로 밀어넣는
편이 더 나은 아티팩트겠지만, criteria API 로 표현되는 규칙에서만 그렇습니다.
어떤 규칙은 DB 에서 어떤 규칙은 메모리에서 동작하는데 **어느 쪽인지 알 수 없는**
것은 생성물이 가질 수 있는 최악의 성질입니다. 이미 읽어온 것에서 고릅니다.

#### 조건 문법 (`@invariant` 와 `@specification` 공통)

두 패턴은 같은 문장을 다르게 쓸 뿐이라 문법을 공유합니다.

| 형태 | 뜻 |
|---|---|
| `items.size > 0` | to-many 의 멤버 수가 그 값보다 큼 |
| `totalAmount > 100` | 숫자가 그 값보다 큼 (`<` 도 가능) |
| `notes notNull` | 필드가 설정되어 있음 |
| `items notEmpty` | to-many 에 무언가 들어 있음 |

필드·비교·리터럴, 그 이상은 없습니다. 임의 표현식을 받으면 파서를 만들어야 하고,
**거의 맞는 파서는 명백히 제한된 형태보다 나쁩니다** — DSL 이 로직을 넣는 자리처럼
보이기 시작하는데, 그것만은 아닙니다.

> `>=` 와 `<=` 는 **쓸 수 없습니다.** DSL 애노테이션 파서가 인자 안의 `=` 를
> 키/값 목록의 시작으로 읽기 때문입니다. 빠뜨린 것이 아니라 쓸 수 없는 것이고,
> 대신 `notNull` / `notEmpty` 같은 낱말 형태를 씁니다. 같은 이유로 `:` 도 쓰지
> 않습니다 — meta 줄은 YAML 스칼라라 `이름: ` 을 매핑 시작으로 읽고 파일이 아예
> 로드되지 않습니다.

값은 항상 **접근자로** 읽습니다. 두 생성물 모두 엔티티 바깥에 있어 필드가 보이지
않고, 지연 로딩 to-many 는 게터로 읽어야 초기화됩니다.

#### 값 객체의 교체 (`ddd-value-object`)

세터를 주지 않는 것은 "값 객체는 바뀌지 않는다"는 말이고, **어떻게 다른 것을
얻는가**에는 답하지 않았습니다. 이제 필드마다 with 메서드가 생깁니다:

```java
Address moved = address.withCity("Rotterdam");   // 원본은 그대로
```

전에는 전체 인자 생성자가 유일한 방법이었는데, 나머지 필드를 호출부에서 위치
순서대로 다시 나열하는 방식이라 둘이 뒤바뀌기 좋았습니다.

#### Kotlin 에서 달라지는 것

규칙이 쓰는 것은 같고, 각 언어가 그것을 말하는 방식만 다릅니다.

| | Java | Kotlin |
|---|---|---|
| 이벤트 클래스 | 필드 + 생성자 + 게터 | 주 생성자의 `val` |
| 명세 클래스 | `private` 생성자 | `object` |
| 팩토리 | `private` 생성자 + `static` | `object` + `fun` |
| 도메인 서비스·프로세스 매니저의 포트 | `XxxBase` 의 `@Autowired protected` 필드 | `XxxBase` 의 `@Autowired protected lateinit var` |
| 조건식 | `order.getItems()` | `order.items!!` |
| 불변식 리스너 | `@EntityListeners({A.class, B.class})` | `@EntityListeners(A::class, B::class)` — 배열 속성을 vararg 로 받으므로 대괄호를 쓰면 컴파일되지 않습니다 |

#### 엔티티 행위 (`ddd-behavior`) - Generation Gap

엔티티 파일은 DSL 이 바뀔 때마다 다시 생성되므로, 그 안에 쓴 `confirm()` 같은 메서드는
사라집니다. 그래서 행위가 서비스로 빠지고 빈약한 도메인 모델이 됩니다. DDD 번들에서
**루트는 기본으로** 둘로 나뉩니다 (`@behavior` 를 적지 않아도 됩니다).

| 파일 | 성격 | 내용 |
|---|---|---|
| `SalesOrderBase` | 재생성 (`@MappedSuperclass`, abstract) | 필드, 이벤트 발생 메서드, 컬렉션·불변식 배관 |
| `SalesOrder` | **generate-once** (`@Entity`, `extends SalesOrderBase`) | 개발자가 쓰는 업무 메서드 |

```yaml
SalesOrder:
  meta: entity @root @expose(20) @behavior @event(Confirmed)
  fields:
    status: OrderStatus @behavior        # 행위로만 바뀐다
    totalAmount: bigdecimal(14.2) @behavior
```

```java
public class SalesOrder extends SalesOrderBase {
    public void confirm() {
        if (getStatus() != OrderStatus.DRAFT) throw new InvariantViolationException("초안만 확정");
        setStatus(OrderStatus.CONFIRMED);   // protected setter
        raiseConfirmed();
    }
}
```

- **구체 클래스가 엔티티 이름을 그대로 가집니다.** 매퍼·리포지터리·포트·DTO 는 바뀌지 않고
  상위 클래스 하나가 추가될 뿐입니다 (Core Data mogenerator, Apache Cayenne 와 같은 분리).
- **필드 `@behavior`**: setter 가 `protected` (Java: `@Setter(AccessLevel.PROTECTED)`,
  Kotlin: `protected set`), 매퍼가 쓰지 않고, DTO 는 `READ_ONLY` 로 받습니다.
  클라이언트는 상태를 보낼 수 없고 서비스도 직접 바꿀 수 없어, 행위 메서드만이 바꿉니다.
  루트는 적지 않아도 나뉘므로 필드 `@behavior` 만 쓰면 됩니다. 나뉘지 않는 엔티티(`@editable` 루트,
  멤버)의 필드 `@behavior` 는 DSL 경고를 내고 무시합니다.
- **테이블 어노테이션**(`@Entity`, `@Table`, `@DynamicUpdate` …)은 JPA 가 읽는 구체 클래스에
  있습니다. 이 파일은 재생성되지 않으므로 DSL 에서 테이블명·스키마·인덱스를 바꾸면
  생성기가 차이와 새 어노테이션을 출력합니다 - 직접 옮겨 적으세요.
- **기존 프로젝트 전환**: `@behavior` 를 추가하고 생성하면, 생성기가 관리하던 `SalesOrder.java`
  가 얇은 구체 클래스로 바뀌고 필드는 `SalesOrderBase.java` 로 옮겨집니다.
  사용자가 수정한 파일이면 `-Pforce` 없이는 건너뜁니다.
- 적용되지 않는 모양: `@embeddable`, `@abstract`, `@rollup`, 판별자(single table) 계층,
  복합 키. 이들은 JPA 가 상속을 다르게 다루므로 분리하지 않습니다.

##### 루트는 기본으로 잠긴다 (sealed) - `@editable` 로 연다

DDD 번들에서 루트의 필드는 **기본으로 잠깁니다**. 공개 setter 가 없는 모델이 기본이고,
빈약한 모델은 `@editable` 로 **명시적으로 고른** 경우에만 생깁니다.

| 필드 종류 | setter | 요청(POST) | 요청(PUT) | 바꾸는 방법 |
|---|---|---|---|---|
| 기본 (잠김) | `protected` | 받음 | 같은 값이면 통과, 다르면 **422** | 루트의 메서드 |
| `@behavior` | `protected` | 받지 않음 (`READ_ONLY`) | 받지 않음 | 루트의 메서드 |
| `@readonly` / `@noinput` | `protected` (`@editable` 루트는 `public`) | 받지 않음 (`READ_ONLY`) | 받지 않음 | 서버 코드: 새 루트는 `creation()`, 그 뒤는 루트의 메서드 (`@editable` 루트는 `beforeSave` 의 setter) |
| `@editable` | `public` | 받음 | 받음 | setter, PUT |
| id, `@jpa:Version`, 감사 필드 | 그대로 | - | - | 영속성 계층 |

```yaml
SalesOrder:
  meta: entity @root @expose(20) @event(Confirmed)
  fields:
    orderId: string(36) @id @genid
    orderNo: string(20)!          # 만들 때 정하고, 그 뒤로는 메서드로만
    status: OrderStatus @behavior # 요청은 보낼 수도 없다
    memo: string(500) @editable   # 아무 때나 고친다
    lines: List<SalesOrderLine> @OneToMany(mappedBy="order") @editable

Item:                             # 기준정보 - 필드 단위로 고치는 게 맞는 루트
  meta: entity @root @editable @expose(20)
```

- **만드는 길은 `creation()`**: 매퍼는 잠긴 필드를 `to.creation()::setOrderNo` 로 씁니다.
  새 루트에는 무엇이든 받고, 저장되었거나 조회된 루트(`@PostLoad`/`@PostPersist` 로 표시)에는
  **지금 값과 같은 값만** 받습니다. PUT 에 객체 전체를 되돌려 보내는 것은 통과하고, 잠긴 값을
  바꾸려 하면 `InvariantViolationException` - 422 입니다 (`FixedAtCreation`).
  같은 값의 판단: `BigDecimal` 은 `compareTo`(10 과 10.00 은 같다), 참조는 식별자, 값 객체는 구성 요소.
- **코드에서 만들 때**도 같은 길입니다: `order.creation().setOrderNo("SO-1")`
  (Kotlin: `order.creation().orderNo = "SO-1"`). 도메인 쪽이라면 구체 클래스에 팩토리를 두는 편이
  더 좋습니다: `Invoice.issueFor(orderId, orderNo, amount)`.
- **새 루트의 null**: 요청이 값을 보내지 않으면(null) 엔티티의 기본값(`@default`)을 덮어쓰지 않습니다.
  잠긴 필드와 `@behavior` 필드의 DTO 는 기본값을 들고 다니지 않습니다 - 들고 다니면 일부만 보내는
  PUT 이 기본값(예: 합계 0)을 함께 보내 잠긴 값을 바꾸려는 요청이 되기 때문입니다.
  같은 이유로 **`creation().setX(null)` 은 새 루트에서 아무 일도 하지 않습니다** (`if (!settled && value == null) return;`).
  서비스의 `beforeSave` 에서 클라이언트가 보낸 값을 지우는 길이 아닙니다. 요청으로 받지 않을 필드는
  `@readonly`(서버가 정함) 나 `@behavior`(루트의 메서드만 바꿈) 로 선언하면 매퍼가 처음부터 쓰지 않고,
  값을 지우는 일은 루트의 메서드가 `protected` setter 로 합니다.
- **컬렉션**: 루트의 멤버 컬렉션도 잠깁니다 (`creation().replaceLines(...)` 는 새 루트에만).
  초안 편집처럼 요청으로 바꿔야 하면 `@editable` 을 붙입니다.
- **생성 서비스 테스트**도 `creation()` 으로 픽스처를 만듭니다.
- **2.x·일반 v3 번들에는 영향이 없습니다.** 루트가 기본으로 나뉘는 것은 `ddd-behavior` 가
  선택된 도메인(= `ddd-springboot`, `hexagonal-springboot` 및 Kotlin 번들)뿐입니다.

#### 생성기가 더는 만들지 않는 파일 (elcore3)

DSL 에서 사라진 것이 만들던 파일 - 마지막 `@min` 이 값 타입으로 옮겨 간 루트의 `XxxInvariants`, 이름이
바뀐 명령의 DTO - 는 예전에는 그대로 남아 모양이 바뀐 상위 클래스를 참조하며 빌드를 깨뜨렸습니다.
이제 **모든 도메인을 생성한 실행**의 끝에서, 생성한 디렉터리에 있지만 이번 실행이 만들지 않은 파일 중
다음을 만족하는 것을 지웁니다 (`REMOVED(the DSL no longer produces it)`).

- 첫 줄이 3.0 해시 줄이고, 넘겨준 파일(`Initially generated ...`)이 아니며, 생성기가 쓴 그대로인 파일.
- 수정된 파일은 지우지 않고 경고만 합니다. `-Pdomain=...` 으로 일부 도메인만 생성할 때와
  실패한 산출물이 있을 때는 정리하지 않습니다.

#### 업무 명령 (`ddd-command`)

엔티티의 `commands:` 에 선언한 명령마다 `POST /{id}/<명령>` -> `XxxUseCase` -> `XxxCoreService`
(불러오기·호출·저장) -> `XxxBase` 의 추상 메서드 -> `Xxx` 의 규칙으로 이어지는 한 길을 만듭니다.
DSL 문법과 생성물 표는 `elasticore-dsl-guide.md` 의 "업무 명령" 을 보세요.

- 추상 메서드이므로, 이미 있는 행위 클래스에 명령을 추가하면 규칙을 쓸 때까지 컴파일되지 않습니다 - 빠뜨릴 수 없습니다.
  행위 클래스를 처음 만들 때는 예외를 던지는 틀이 함께 생기므로, 그 상태로 호출하면 500 입니다.
- 행위 클래스를 처음 쓸 때 각 명령의 틀이 들어갑니다 (Java: `UnsupportedOperationException`,
  Kotlin: `TODO()`). 이미 있는 행위 클래스에는 추가되지 않으므로 직접 씁니다 (컴파일러가 알려 줍니다).
- 컨트롤러는 개발자 소유 파일입니다. 손대지 않은 컨트롤러는 다시 생성되어 명령 엔드포인트가
  들어가고, 고친 컨트롤러는 그대로 둡니다.
- `@editable` 필드가 없는 루트는 PUT 을 만들지 않습니다 (컨트롤러와 유스케이스 모두).

#### 생명주기 (`ddd-state`)

엔티티의 `states:` 가 상태 필드와 명령마다의 이동을 선언하면, 명령은 **추상 메서드가 아니라
`XxxBase` 의 구체 메서드**로 생성됩니다. 문법은 `elasticore-dsl-guide.md` 의 "생명주기" 를 보세요.

- 시작 상태가 아닌데 명령이 오면 `InvariantViolationException` - API 는 409 (지금 상태와 충돌): "PurchaseOrder cannot ship when status is CANCELLED; it must be [PLACED]".
  코드는 `state.PurchaseOrder.status.ship` 이고 요청 언어로 번역할 수 있습니다 (아래 "오류 코드와 다국어").
- 대상 상태와 같은 이름의 이벤트가 `@event` 에 있으면 발행합니다 (`SHIPPED` -> `raiseShipped()`).
- `canShip()` 이 함께 생기고, TypeScript 클라이언트에도 `canShip(dto)` 가 생깁니다 (버튼 표시용).
- 더 할 일이 있으면 행위 클래스에서 override 하고 `super.ship()` 으로 이동시킵니다.
- 상태 필드는 요청으로 쓸 수 없습니다 (`@behavior` 와 같은 취급).
- `states:` 아래 키는 **필드 이름**, 상태 이름은 그 필드 타입인 enum 의 **상수 이름**입니다. enum 에 없는 이름이면
  DSL 경고(`... names SHIPED, which OrderStatus does not have (it has ...)`) 후 생명주기를 생략합니다.
- 나가는 이동이 없는 상태는 끝 상태입니다. `moveXxx` 의 Javadoc 에 시작·이동·끝·도달하지 않는 상태가 정리됩니다.
- 같은 명령이 두 생명주기(두 enum 필드)에 있으면 그 명령은 모든 생명주기의 시작 상태를 먼저 확인(`requireXxx`)한 뒤
  함께 이동합니다. 하나라도 아니면 409 이고 아무 필드도 바뀌지 않습니다. `canX()` 는 조건의 AND.

#### 파생 값 (`ddd-derive`)

`@snapshot(item.unitCost)` 은 참조한 행의 값을 **한 번** 복사하고(없는 행이면 404),
`@compute(unitCost * quantity)`, `@compute(sum(lines.subTotal))` 는 매번 계산합니다.
엔티티에 `derive(lookup)` 이 생기고 CoreService 가 생성·수정·일괄 수정·조건 수정·명령 뒤 저장 전에
`entity.derive(entityManager::find)` 를 부릅니다. 멤버가 먼저 계산되므로 합계는 최신 줄을 더합니다.
식은 `a * b`, `a + b`, `a - b`(필드나 숫자), `sum(멤버목록.필드)` 만 지원하고, 타입별로
`Money.times/plus`, `BigDecimal.multiply/add`, 정수 연산으로 씁니다. 못 읽는 식은 DSL 경고 후 건너뜁니다.

#### 권한·소유·비밀번호 (`jpa-service-access`)

`@access(read=owner, create=user, ship=admin)`, `@owner(customerId)`, 필드의 `@password`·`@login`·
`@access(admin)` 을 **재생성되는 CoreService** 에서 검사합니다 - 개발자 소유인 컨트롤러가 아니라서,
DSL 을 고치면 다음 빌드에 모든 진입점에 반영됩니다. 어노테이션 표는 `elasticore-annotation-guide.md`
의 "@access / @owner" 를 보세요.

- 템플릿의 슬롯(`${accessCreate}` 등)은 선언이 없으면 비어 있어 생성물이 바뀌지 않습니다.
- 런타임: `io.elasticore.springboot3.security` 의 `CurrentUser`(기본 `SessionCurrentUser`),
  `AccessPolicy`, `PasswordHasher`(기본 PBKDF2), 401·403 예외. 빈을 등록하면 그것을 씁니다.
- `@login` + `@password` 엔티티의 CoreService·유스케이스에는 `authenticate` 가 생깁니다. 엔티티 줄에 `@signin` 까지
  있으면 `me()` 와 그 컨트롤러의 `signin`·`signout`·`me` 엔드포인트도 이 규칙이 쓰고, 로그인 컬럼은 unique 가 됩니다
  (unique 는 `@signin` 만으로 - 아래 조건에 걸려 엔드포인트를 만들지 않을 때도).
  조건은 행의 비밀번호를 본인과 관리자만 바꿀 수 있을 것(`update=owner` 와 키를 가리키는 `@owner`, 또는
  `update=admin`·`none`), 요청이 정하는 키면 삭제도 그럴 것(`delete=owner` 와 키의 `@owner`, 또는 `admin`·`none`),
  `@expose`, 키 하나, 그리고 애플리케이션에 `@signin` 하나 - 단일 모듈 프로젝트는 모든 도메인이
  하나의 애플리케이션, 멀티 모듈은 컨트롤러가 생기는 모듈마다입니다. 못 만드는 이유는 엔티티마다 DSL 경고로 나옵니다.
  컨트롤러는 개발자 소유라 손댄 컨트롤러에는 들어가지 않고, 생성 로그가 빠진 엔드포인트도, DSL 이 더는 만들지 않는데
  남아 있는 로그인(`POST /signin`·`GET /me`, 세션에 넣는 역할)도 DSL 경고로 알려 줍니다.

#### 트랜잭션 Outbox (`ddd-outbox`)

Spring Data 가 발행하는 도메인 이벤트는 **같은 JVM 안**에만 전달됩니다. 거기서 브로커로 보내면
롤백될 트랜잭션의 메시지가 나가거나, 커밋 후 전송 실패로 메시지를 잃습니다. Outbox 는 이벤트를
**같은 트랜잭션**에서 테이블에 적고, 커밋 후 따로 보냅니다.

```yaml
# env.yml
elasticore:
  config:
    outbox: true
```

도메인마다 **infrastructure 패키지**(`namespace.persistence`, 없으면 `repository`)에 생성 — Outbox 는 어댑터이므로
도메인·애플리케이션은 이를 모릅니다(모두 재생성 관리 파일). `@EntityScan` 을 직접 지정하는 앱은 이 패키지를 포함하세요:

| 파일 | 역할 |
|---|---|
| `<Domain>OutboxMessage` | 엔티티 (`<prefix>outbox` 테이블): 애그리거트 종류·ID, 이벤트 타입, JSON, 발생 시각, 발행 시각, 시도 횟수, 마지막 오류 |
| `<Domain>OutboxRepository` | 미발행 100건을 순서대로 잠가서 읽는 조회 |
| `<Domain>OutboxRecorder` | 모든 이벤트를 `BEFORE_COMMIT` 에 JSON 으로 기록 — 변경이 커밋되면 메시지도 있고, 롤백되면 없다 |
| `<Domain>OutboxRelay` | `relay()`: 미발행 메시지를 `OutboxPublisher` 로 보내고 성공 시 발행 처리. 실패하면 그 애그리거트의 뒤 메시지는 멈추고(순서 보장) 다음 번에 재시도. `elasticore.outbox.max-attempts`(기본 10)번 실패한 메시지는 따로 빼 두어 뒤를 막지 않는다. `@Scheduled(fixedDelayString = "${elasticore.outbox.relay-delay-ms:5000}")` — 앱이 `@EnableScheduling` 하면 주기 실행 |

애플리케이션이 쓰는 것은 전송 하나입니다:

```java
@Bean
OutboxPublisher kafka(KafkaTemplate<String, String> kafka) {
    return m -> kafka.send("erp-events", m.getAggregateId(), m.getPayload()).get();
}
```

- 전달은 **최소 한 번**(at least once). 소비자는 `OutboxEnvelope.getId()` 로 중복을 거릅니다.
- `OutboxPublisher` 빈이 없으면 Relay 는 아무것도 하지 않고 메시지는 테이블에 쌓입니다.
- `pending()` 으로 대기 건수를 볼 수 있습니다(모니터링).

#### 프로세스 매니저 (사가) — `processManager:`

한 애그리거트의 이벤트가 **다른 애그리거트의 다음 단계**를 부르는 흐름(주문이 들어오면 영수증 발행)입니다.
`elasticore-template-ddd` 의 `billing` 이 그대로의 예입니다.

```yaml
# blueprint/billing/model/domainservice.yml
processManager:
  Receipting:
    desc: 주문이 들어오면 영수증을 한 장 만든다
    on: [shop:OrderPlaced]       # (컨텍스트:)루트 이름 + 이벤트 이름 (Event 접미사 없이)
    uses: [Receipt]              # 주입받을 애그리거트의 도메인 포트
```

생성물은 두 파일입니다 (application 패키지 또는 `namespace.processManager`). 업무 명령의 `XxxBase` + `Xxx` 와 같은
나눔이라, DSL 을 고치면 연결은 다시 생성되고 개발자가 쓴 단계는 그대로 남습니다. 실제 생성물에서 import 와 클래스
주석만 뺐습니다:

```java
// ReceiptingProcessManagerBase - 매번 재생성 (규칙 ddd-process-manager-base)
public abstract class ReceiptingProcessManagerBase {

	@Autowired
	protected ReceiptRepositoryPort receiptRepositoryPort;

	/** After OrderPlacedEvent has committed, in a transaction of its own. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void on(OrderPlacedEvent event) {
	    handle(event);
	}

	/** What shop:OrderPlaced means here - the next step, written in ReceiptingProcessManager. */
	protected abstract void handle(OrderPlacedEvent event);

}

// ReceiptingProcessManager - 한 번 생성, 이후 개발자 소유 (규칙 ddd-process-manager)
@Component

public class ReceiptingProcessManager extends ReceiptingProcessManagerBase {

	@Override
	protected void handle(OrderPlacedEvent event) {
	    // The next step goes here.
	}

}
```

- `on` 에 이벤트를 더하면 Base 에 추상 `handle(새 이벤트)` 가 생겨, 개발자 클래스에 쓰기 전까지 컴파일되지 않습니다
  (빠뜨릴 수 없음). `uses` 에 더한 애그리거트는 Base 의 필드가 되어 개발자 클래스를 고칠 필요가 없습니다 — 포트를
  생성자가 아니라 필드로 주입하는 이유입니다. 개발자 파일을 고친 적이 없다면 그것도 다시 생성됩니다.
- 이벤트를 낸 트랜잭션이 **커밋된 뒤, 자기 트랜잭션에서** 실행됩니다. 여기서 실패해도 이미 일어난
  출고는 되돌려지지 않습니다 — 재시도·보상은 `handle` 이 정합니다(사가의 보상 단계).
- 이벤트 내용(payload)에 필요한 값을 실어 두면 원래 애그리거트를 다시 읽지 않아도 됩니다.
- 중복 전달에 대비해 멱등하게 작성하세요(예: `findByOrderId` 로 이미 발행했는지 확인).
- 선언한 이벤트가 어느 루트의 `@event` 에도 없으면 DSL 경고.
- 다른 바운디드 컨텍스트의 이벤트는 `on: [shop:OrderPlaced]` 로 받습니다. 생성기가 컨텍스트 지도에서 그 컨텍스트의
  이벤트 클래스를 찾아 import 하고(`on` 의 두 이벤트가 단순 이름이 같으면 나중에 적은 쪽을 전체 이름으로 씀), `config.context` 의
  `uses`·`publishes` 로 검사합니다. 문법과 한계(같은 애플리케이션 안에서만 전달)는 `elasticore-dsl-guide.md` 의
  "프로세스 매니저".

#### 읽기 모델 — 투영 쿼리

화면·보고서용 조회는 애그리거트가 아니라 **DTO 로 투영**합니다. repository DSL 에서 반환 타입을
DTO 로 적고, JPQL/SQL 의 컬럼 별칭을 DTO 속성 이름에 맞춥니다.

```yaml
repository:
  SalesOrder:
    methods:
      - id: summarizeByPartner
        params: { status: OrderStatus }
        return: List<PartnerSalesDTO>
        query: "SELECT o.partner.partnerId AS partnerId, o.partner.name AS partnerName, COUNT(o) AS orderCount, SUM(o.totalAmount) AS totalAmount FROM SalesOrder o WHERE o.status = :status GROUP BY o.partner.partnerId, o.partner.name"
```

- v3/Kotlin: 리포지터리는 `List<Tuple>` 을 반환하고, 쿼리 포트는 `List<PartnerSalesDTO>` 를 반환합니다.
  어댑터가 `TupleMapper` 로 별칭 → 속성(대소문자·밑줄 무시, 타입 변환 포함)을 채웁니다.
  `List`, `Page`, `Optional`, 단건 모두 지원. 2.x 호환 모드는 기존대로 `List<Object[]>`.
- 별칭이 없는 컬럼은 오류로 알려 줍니다.
- DB 뷰가 이미 있다면 `@immutable(view_name)` 엔티티 + 쿼리 포트로 읽기 모델을 둘 수도 있습니다.

#### 값 객체 (`@embeddable`)

`@embeddable` 은 불변 값 객체(`ddd-value-object`: 세터 없음, `withXxx()` 로 새 값)로 생성되고,
v3/Kotlin 에서는 **DTO 에 그대로 실려** API 로 주고받습니다(요청 JSON 의 중첩 객체). 바뀔 때는
통째로 교체됩니다. 식별자 타입 래핑(`OrderId` 같은 타입 있는 PK)은 지원하지 않습니다 — JPA 가
`@Id` 에 변환기를 허용하지 않아 `@EmbeddedId` 로 모든 계층이 바뀌어야 하기 때문입니다.

#### 다른 애그리거트는 ID 로 참조 (`ddd-reference`)

DDD 번들에서 **다른 루트를 가리키는 to-one 참조**는 ID 필드로 바뀝니다. DSL 은 그대로 씁니다.

```yaml
SalesOrder:
  fields:
    partner: Partner @ManyToOne        # 쓰는 그대로
```

생성 결과:

```java
@Column(name = "partner_partner_id")
@Setter(AccessLevel.PROTECTED)          // 잠김 - 만들 때 정하고, 이후엔 메서드로
private Long partnerId;                 // 참조는 이 필드

/** Another aggregate, to read. The reference is partnerId; ... */
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "partner_partner_id", insertable = false, updatable = false)
@Setter(AccessLevel.NONE)
private Partner partner;                // 같은 컬럼의 읽기 전용 조인 - setter 없음
```

- 애그리거트는 다른 루트의 **식별자만** 가지고, 그 객체로 쓸 수 있는 길이 없습니다. 참조를 바꾸는 것은
  `partnerId` 를 바꾸는 것뿐이고, 다른 루트를 고치는 것은 그 루트의 일입니다.
- 관계는 **읽기 전용**으로 남아 조회가 그대로 됩니다: JPQL `o.partner.name`, 파생 쿼리
  `findByPartnerPartnerId`, DTO 의 `partnerName @ref(partner.name)` 표시.
- 컬럼은 관계가 원래 쓰던 이름(`<필드>_<대상 키 컬럼>`, `@db` 가 있으면 그것)이라 **테이블은 바뀌지 않습니다**.
- 필드 이름: 대상 키 이름이 참조 이름으로 시작하면 그 이름(`partner` -> `partnerId`, `item` -> `itemCode`),
  아니면 `참조 + 키`(`category` -> `categoryCatId`). 같은 이름의 필드가 이미 있으면 DSL 경고 후 객체 참조 유지.
- 저장 요청이 보내는 것은 이 ID 필드입니다. 필수 참조(`member: Member!`)이면 DTO 에서 ID 필드가 필수이고,
  응답용 중첩 DTO 는 선택입니다 (3.0.261022-rc1 부터. 이전에는 중첩 DTO 도 `@NotNull` 이라 ID 만 보낸 요청이
  `@Valid` 검증에서 거절되었습니다). `member: Member!`, `book: Book!`(Book 의 키 `isbn`) 의 `LoanDTO`:

  ```java
  @Schema(description = "borrower" , accessMode = Schema.AccessMode.READ_ONLY )
  private MemberDTO member;            // 응답용 - 요청에서는 선택
  ...
  @Schema(description = "memberId" , requiredMode=Schema.RequiredMode.REQUIRED )
  @NotNull
  private Long memberId;               // 저장 요청에 필수

  @Schema(description = "bookIsbn" , requiredMode=Schema.RequiredMode.REQUIRED )
  @NotNull
  @Size(max=13)
  private String bookIsbn;
  ```
- 매퍼는 `@ref(partner.partnerId)` 를 `partnerId` 로 씁니다 (관계 객체는 쓰지 않음).
- 적용 대상: 같은 도메인의, 키가 하나인 다른 루트를 가리키는 to-one (`cascade`, `@embedded`, 역방향
  `@OneToOne(mappedBy)`, 이미 `@joinBy` 를 쓴 필드는 제외). 자기 루트를 가리키는 멤버의 역참조는 그대로.
- 객체 참조로 두려면 `@objectref`.
- 코드에서: `order.creation().setPartnerId(partner.getPartnerId())` (Kotlin: `order.creation().partnerId = partner.partnerId`).

#### 도메인에서 JPA 걷어내기 (`mapping: xml`) 와 아키텍처 테스트 (`archTest`)

`env.yml` 의 `mapping: xml` 이면 생성기는 도메인 클래스를 평소대로 만든 뒤 JPA 어노테이션을 떼어 내고 같은
내용을 `META-INF/orm.xml` 에 씁니다 (`OrmMapping`). XML 요소 순서는 JPA 3.1 스키마를 따릅니다
(`id`, `basic`, `version`, `many-to-one`, `one-to-many`, `one-to-one`, `many-to-many`, `embedded`, `transient`).
엔티티 리스너(`XxxInvariants`)의 콜백도 XML 로 선언되어, 도메인 패키지에는 `jakarta.persistence` 가 남지 않습니다.
`archTest: true` 는 이것과 헥사고날 경계를 ArchUnit 규칙으로 생성합니다. 자세한 설정은
`elasticore-env-guide.md` 6.1.2, 6.1.3.

한 메서드에 콜백 두 개를 걸지 않습니다: XML 매핑은 메서드 하나에 콜백 하나만 붙이므로, 잠금 표시는
`settleOnLoad()`(`@PostLoad`) 와 `settleOnPersist()`(`@PostPersist`) 두 메서드입니다.

#### 바운디드 컨텍스트 경계

다른 도메인(blueprint)의 엔티티를 직접 참조하면 DDD 번들이 경고합니다. 두 컨텍스트가 같은 테이블
모양을 공유하게 되므로, 상대의 식별자를 컬럼으로 두고 포트(`port:` DSL)로 조회해 이쪽 모델로
변환하는 것을 권장합니다(Anti-Corruption Layer). 생성은 막지 않습니다.

#### 도메인 서비스 (`ddd-domain-service`)

어느 애그리거트에도 속하지 않는 연산입니다. **엔티티가 아니라 도메인에 선언**합니다 —
다른 DDD 애노테이션은 전부 엔티티에 쓰는데, 도메인 서비스는 "누구의 것도 아니다"가
정의이기 때문입니다. 루트 하나를 골라 매다는 것이 값싼 선택이었지만, 그러면 이
패턴이기를 그만두고 "누가 애그리거트에 넣는 걸 잊은 메서드"가 됩니다.

```yaml
# model/domainservice.yml
domainService:
  Pricing:
    desc: What an order costs once the products it names are priced
    uses: [Order, Product]
```

`uses` 는 장식이 아닙니다. **생성기가 주입하는 대상**이고, 이 서비스가 존재할 이유를
말하는 문장입니다. 하나만 적으면 DSL 경고가 납니다 — 애그리거트 하나에 대한 연산은
보통 그 애그리거트의 메서드입니다.

생성물은 두 파일입니다. `PricingDomainServiceBase` 는 **매번 재생성**되며 명명한 애그리거트의 도메인 포트
(`OrderRepositoryPort`, `ProductRepositoryPort`)를 주입받는 필드를 갖고(규칙 `ddd-domain-service-base`),
`PricingDomainService` 는 그것을 상속하는 **generate-once** 클래스입니다(규칙 `ddd-domain-service`). `uses` 에
애그리거트를 더하면 Base 에 포트가 생기고 개발자 클래스는 고치지 않습니다. 연산 자체는 생성하지 않습니다 —
`@factory` 와 같은 이유로, 모델은 데이터를 선언하므로 생성할 수 있는 것은 행위가 아니라 자리와 연결입니다.

놓일 자리는 `namespace.domainService` 로 정합니다. 선언하지 않으면 엔티티와 같은
패키지입니다. **application 네임스페이스에 두지 마세요** — 도메인 코드라서 유스케이스
옆에 있으면 의존 방향이 뒤집힙니다.

#### TypeScript 에 닿는 것 (`ts-model`)

브라우저가 받는 것은 엔티티가 아니라 DTO 입니다. 그래서 서버의 모든 규칙이 여기
오는 것이 아니라, **브라우저가 정직하게 가질 수 있는 것만** 옵니다.

| 규칙 | TS | 이유 |
|---|---|---|
| `ddd-specification` | O | 이미 가진 데이터에 대한 질문입니다 |
| `ddd-invariant` | O | 서버가 거절할 것을 미리 물어보는 것 (강제 아님) |
| 애그리거트 경계 | **이미 닿아 있음** | 멤버는 엔드포인트가 없으므로 `ts-api` 가 클라이언트를 만들지 않습니다 |
| `ddd-event` / `ddd-factory` / `ddd-domain-service` | X | 저장 시점·리포지터리에 매인 것들이라 브라우저에 대응물이 없습니다 |

##### 왜 명세를 생성할 가치가 있는가

이 언어에서는 손으로 쓴 사본이 단순 중복이 아니라 **대개 틀립니다.**

```ts
// 손으로 쓰면
order.totalAmount > 100

// 생성되면
Number(order.totalAmount) > 100
```

금액은 TS 에서 `number` 로 선언됩니다 - Jackson 의 기본은 소수를 **JSON 숫자로** 보내고,
`JSON.parse` 는 선언과 무관하게 number 를 줍니다. 그런데 서버가 정밀도 때문에 소수를
**문자열로** 보내도록 설정되어 있으면(`@JsonFormat(shape = STRING)` 등)
첫 줄은 문자열과 숫자를 비교하고, JavaScript 는 금액과 무관하게 false 를 답합니다.
생성된 `Number(...)` 는 두 경우 모두 맞습니다.

같은 이유로 TS 모델은 **서버가 고른 규칙으로** 모양을 정합니다. `ts-model` 번들이 아니라
같은 빌드의 Java/Kotlin 선택이 식별자 참조(`customerId`)·값 객체 인라인(`deliveryAddress`)을
쓰면 TS DTO 도 그렇고, 값 객체마다 인터페이스(`Address.ts`)가 생깁니다. `XxxApi.ts` 는
컨트롤러에 없는 엔드포인트(`@readonly`, `@restExclude`, 명령만 받는 루트의 `update`)를 만들지
않고, `@writeonly` 필드는 응답에 없으니 optional 입니다. 검색 DTO 의 조건은 모두 optional 이고
`pageNumber`·`pageSize`·`sortColumn`·`sortAscending` 을 가집니다.

컬렉션도 마찬가지로 `.size()` 가 아니라 `.length` 이고, 프로퍼티는 옵셔널이라
모든 읽기 앞에 `!= null` 가드가 붙습니다.

##### 불변식은 강제가 아닙니다

생성된 파일이 그렇게 말합니다. 서버가 행을 쓸 때 거절하는 것이 약속을 참으로
만드는 것이고, 브라우저는 신뢰 대상이 아닙니다. 여기서 얻는 것은 다른 것입니다 —
요청을 보내기 전에 **도메인 자신의 문장으로** 무엇이 잘못됐는지 폼에 말해주는 것.

```ts
violations(order)   // 어긴 약속들. 폼은 한 번에 다 보여주고 싶으므로 목록
isValid(order)      // 하나도 어기지 않았는가
```

#### 포트의 두 방향

헥사고날에는 방향이 둘 있고, 생성물도 둘로 나뉩니다.

| 방향 | 무엇 | 네임스페이스 | 어댑터 |
|---|---|---|---|
| **인바운드** | `OrderUseCase` — 애플리케이션에 들어가는 문 | `inboundPort` (대체: `service`) | 컨트롤러 (생성됨) |
| **아웃바운드 (영속)** | `OrderRepositoryPort` — 도메인이 소유 | `domainRepository` (대체: `entity`) | `OrderRepositoryAdapter` (생성됨) |
| **아웃바운드 (sql/http)** | DSL `port:` 선언 | `port` | **런타임 프록시** (생성물 없음) |

```yaml
namespace:
  inboundPort: com.acme.shop.application.port.in
  port:        com.acme.shop.application.port.out
```

##### sql/http 포트는 애플리케이션의 것입니다

`dbms` / `http` 포트의 계약은 **application DTO** 로 쓰이고, 페이징하면 Spring 의
`Page` 도 씁니다. 그래서 이건 **애플리케이션의 아웃바운드 포트**입니다.

도메인 서비스의 `uses:` 에 적으면 **경고와 함께 무시됩니다** — 도메인 코드가 위
계층에 의존하게 되기 때문입니다. 도메인은 애그리거트(=리포지터리 포트)를 받고,
sql/http 포트는 그것을 호출하는 서비스가 씁니다.

##### 어댑터는 빈이어야 합니다

생성물은 인터페이스뿐이고 어댑터는 런타임 프록시입니다. 그런데 **아무도 등록하지
않은 프록시는 주입할 수 없는 포트**입니다:

```
No qualifying bean of type 'ShopReportService'
```

Spring Boot 애플리케이션에서는 런타임 라이브러리의 자동 구성이 스캔으로 처리합니다 (3.0.261023 부터 - 그 전에는
`@EnableElastiCore` 가 있어야 했고, 지금도 붙여 두면 그대로 동작합니다). 찾는 곳은 `@SpringBootApplication` 클래스의
패키지와 그 아래이므로, 그 클래스를 띄우는 `@SpringBootTest` 도 포트 빈을 받습니다. 같은 포트에 직접 선언한
`@Bean` 이 있으면 그것을 씁니다. `DddSampleApp` 은 생성된 코드(`io.elasticore.sample.ddd`)와 다른 패키지(`ddd`)에 있는
테스트 구성이라 스캔이 포트에 닿지 않고, `@Bean` 하나로 직접 등록합니다 — 이음매가 정확히 어디인지 읽기에 가장 좋은
자리이기도 합니다. 자세한 것은 포트 가이드 5장.

#### 리포지터리 포트 (`ddd-repository-port`)

도메인이 소유하는 인터페이스와, Spring Data 를 아는 구현으로 나눕니다.

```java
// domain.repository — Spring 타입이 하나도 없습니다
public interface OrderRepositoryPort {
    Optional<Order> findById(String orderId);
    Order save(Order order);
    void delete(Order order);
    boolean existsById(String orderId);
}

// infrastructure.persistence
@Repository
public class OrderRepositoryAdapter implements OrderRepositoryPort { ... }
```

##### 왜 지금인가

`hexagonal-review` 2-B 는 이걸 "우선순위 낮음"으로 판정했고, 이유는 **"JPA 를 걷어낼
수 있다"는 이득이 실무에서 거의 실현되지 않는다** 였습니다. 그 이유는 지금도 맞습니다.

바뀐 것은 이득이 하나 더 생겼다는 것이고, 그건 이 생성기가 스스로 만든 것입니다 —
도메인 서비스가 도착하면서:

```java
package ...domain.service;
import ...domain.repository.OrderRepository;   // extends JpaRepository
```

**도메인 서비스는 정의상 도메인 코드인데 영속 메커니즘을 주입받고 있었습니다.**
가장 안쪽 계층의 의존이 바깥을 향하는 것 — 이 번들이 이름을 딴 아키텍처가 하지 말라는
바로 그 한 가지입니다. JPA 를 걷어내든 말든 고칠 값이 있습니다.

##### 포트가 좁은 이유

Spring Data 리포지터리를 그대로 흉내 내고 포트라 부르기는 쉽습니다. 그리고 무의미
합니다 — 검색·페이징·명세는 **질의 표면**이고, 그걸 도메인이 소유한 인터페이스 뒤에
두면 `Pageable` 과 `Specification` 이 결국 도메인에 들어옵니다. 의존을 없앤 게 아니라
이름만 바꾼 것입니다.

애그리거트를 넘거나 페이징하는 질의는 **read model 의 일**입니다(17장). 그래서 검색은
도메인 포트가 아니라 **응용 계층의 조회 포트**로 갑니다.

##### 응용 서비스는 포트만 받습니다

이전에는 응용 서비스가 검색을 위해 Spring Data 리포지터리를 직접 썼습니다("응용 계층은
자기 인프라를 알아도 된다"). 그 결과 **모든 응용 서비스가
`infrastructure.persistence` 를 import** 했고, 헥사고날이 금지하는 바로 그 방향이었습니다.
샘플의 계층 테스트가 `.infrastructure.` 를 검사하지 않아 드러나지 않았습니다.

지금은 루트마다 파일이 하나 더 생기고, 응용 서비스는 포트 둘과 `EntityManager`
(매퍼가 식별자를 참조로 바꿀 때만 사용)만 받습니다.

```java
// application.port.out — 응용 계층의 검색 포트. Specification/Pageable 은 여기서만
public interface OrderQueryPort {
    List<Order> findAll(Specification<Order> spec);
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);
    long count(Specification<Order> spec);
    // findAll(), findAll(Sort), findAll(spec, Sort)
}

// application — infrastructure 를 import 하지 않습니다
public class OrderCoreService {
    protected final OrderRepositoryPort repository;   // 찾기·저장·삭제 (도메인 포트)
    protected final OrderQueryPort query;             // 검색·페이징 (조회 포트)
    protected final EntityManager entityManager;      // 참조 해석 전용
}

// infrastructure.persistence — 두 포트를 한 어댑터가 구현
public class OrderRepositoryAdapter implements OrderRepositoryPort, OrderQueryPort { ... }
```

> **기존 3.0 스냅샷으로 만든 헥사고날/DDD 프로젝트:** `OrderService` 는 generate-once 라
> 재생성되지 않습니다. 생성자를 `(OrderRepositoryPort repository, OrderQueryPort query,
> EntityManager entityManager)` 로 바꾸고 `super(repository, query, entityManager)` 를
> 호출하도록 고쳐 주세요. **생성기가 이를 감지해 알려 줍니다** — 소유 파일의 생성자
> 호출이 지금 생성될 것과 다르면 `constructor out of date ... the generator now writes
> super(...)` 경고를 출력합니다. `supportAI: true` 도메인도 같은 포트를 받습니다.

##### 네임스페이스

| 키 | 무엇 | 대체 |
|---|---|---|
| `domainRepository` | 포트 (도메인) | `entity` |
| `queryPort` | 조회 포트 (응용 계층) | `service` |
| `persistence` | 어댑터 + Spring Data (인프라) | `repository` |

`repository` 를 `.domain.` 아래 두면 도메인이 `JpaRepository` 를 알게 됩니다. 샘플은
`repository` 를 인프라로 옮기고 `domainRepository` 에 포트를 둡니다.

> **복합키 루트는 생성하지 않습니다.** 식별자가 별도 클래스라 어느 클래스인지가
> 아티팩트에 대한 결정인데, 이 룰셋이 조용히 정할 일이 아닙니다.

#### 쓰기 경로가 지키는 것 (v3 서비스 공통)

생성된 코어 서비스(`jpa-springboot-v3`, `ddd-springboot`, `hexagonal-springboot`, Kotlin 판
포함)는 쓰기 전에 다음을 확인합니다. 2.x 호환 번들(`jpa-springboot`)은 바뀌지 않습니다.

| 경우 | 동작 | API 응답 (`spring-api-errors`, 모든 v3 번들에 포함) |
|---|---|---|
| 생성(`save`)에 이미 있는 식별자 | 거절 (`DuplicateKeyException`). 덮어쓰지 않음 | 409 |
| 수정에 보낸 `version` 이 행의 것과 다름 | 거절 (`ObjectOptimisticLockingFailureException`) | 409 |
| 수정이 보낸 값이 제약 위반 (`@Size` 등) | 거절 (`ConstraintViolationException`). 보낸 필드만 검사 | 400 |
| 없는 행 수정 | `EntityNotFoundException` | 404 |
| 요청이 가리킨 행이 없음 - 다른 애그리거트를 id 로 참조 (`loan.memberId`, 주문 줄의 `itemId`) | `InvariantViolationException`, 코드 `reference.<Entity>.<relation>` (DDD 번들, 저장 전 `requireReferences`) | 404 - `Member 99 does not exist` |
| 지금 상태로는 할 수 없는 명령·삭제 (`states:`) | `InvariantViolationException`, 코드 `state.…` | 409 |
| 다른 불변식 위반 | `InvariantViolationException` (`IllegalStateException` 하위) | 422 |
| 손으로 쓴 규칙이 거절 (`beforeSave`, 루트 메서드) | `io.elasticore.springboot3.validation.BadRequestException` (잘못된 요청), `NotFoundException` (가리킨 행이 없음), `ConflictException` (저장된 것과 충돌 - 빌려줄 권수가 없다, 이미 반납했다), `security.ForbiddenException`, `security.NotSignedInException`, `http.PortCallException` (부른 다른 시스템이 실패) | 400, 404, 409, 403, 401, 502 - 던진 메시지가 `detail` 로 나간다. 메시지 코드를 주면 요청 언어로 번역된다 (아래 "손으로 쓴 거절을 번역하기") |
| 조건 삭제 `delete(srchDTO)` | 한 건씩 읽어서 삭제 — 멤버·리스너가 함께 동작 | - |
| 조건 수정 `updateBySpecification` | 한 건씩 읽어서 매퍼로 반영 — 버전·감사·불변식 적용 | - |
| 일괄 수정 `batchUpdate` (`PUT /batch`) | 한 트랜잭션 안에서 행마다 `update(dto)` 를 부름 (3.0.261022-rc1 부터) - 확장 서비스에서 `update()` 를 override 한 규칙이 일괄 수정에도 적용되고, 행마다 같은 검사·`readBack`·409 변환 | 단건 수정과 같음 |

**`@restExclude(...)`** 는 서비스·컨트롤러·유스케이스·TypeScript 클라이언트에서 메서드를 뺍니다. 이름은 서비스 메서드
(`save`, `update`, `batchUpdate`, `delete`(목록 삭제), `deleteById`, `updateBySpecification`)와 컨트롤러 엔드포인트
(`create`, `read`, `search`, `update`, `batchUpdate`, `deleteBatch`(`POST /delete`), `deleteById`, `schemaInfo`)입니다.
v3 출력에서는 빠진 메서드를 부르는 것도 함께 빠져 컴파일이 깨지지 않습니다: `update` 는 `batchUpdate` 를, `deleteById` 는
`delete`·`deleteBatch` 를, `delete` 는 `deleteBatch` 를, `save` 는 `create` 를 데려갑니다. 헥사고날의 유스케이스
인터페이스도 서비스가 뺀 메서드를 약속하지 않습니다(전에는 `@restExclude(batchUpdate)` 가 `XxxService is not abstract
and does not override abstract method batchUpdate` 로 컴파일되지 않았음). Kotlin 서비스는 메서드를 빼지 않고 그대로 두며,
컨트롤러·유스케이스만 따릅니다. 2.x 호환 번들은 적힌 대로만 뺍니다. 프로젝트 규칙이 `ctx(e).excludeMethod("update")` 로
직접 뺄 때도 v3 출력은 같이 데려갑니다(`batchUpdate` 가 없는 `update` 를 부르지 않음). TypeScript 클라이언트는 서버를
따라, 서버가 2.x 호환 출력이면 적힌 것만 뺍니다(`@restExclude(update)` 여도 2.x 서버에 남는 `PUT /batch` 의 `batchUpdate` 는
클라이언트에도 남음). 서버의 모드는 `elcore3ts` 자신의 설정(아키텍처·언어·`-Pv3`·v3 규칙)과, 서버를 마지막으로 만든
`elcore3` 가 `build/elasticore/server.mode` 에 남긴 값으로 압니다 - `-Pv3` 를 `elcore3` 에만 준 레이어드 프로젝트도
`elcore3` 를 먼저 돌렸으면 클라이언트가 v3 서버를 따릅니다.

- **버전 비교**는 DTO 의 `version` 속성을 이름으로 읽습니다(`VersionCheck`). DTO 에 버전이
  없거나 요청이 보내지 않으면 검사하지 않습니다.
- **부분 수정 검증**(`BeanValidation.validatePresent`)은 값이 있는 속성의 제약만 봅니다.
  skip-null 병합은 빠진 필드를 건드리지 않으므로 `@NotNull` 을 요구하지 않습니다.
  `@dynamicupdate(false)` 엔티티는 전체 검증입니다.
- **코어 서비스는 더 이상 `@Service` 빈이 아닙니다.** 확장 서비스만 빈이라 타입당 한
  인스턴스입니다.

DDD 번들의 매퍼는 여기에 더해 **`@jpa:Version` 과 Spring Data 감사 필드
(`@jpa:...CreatedBy/CreatedDate/LastModifiedBy/LastModifiedDate`)를 DTO 에서 엔티티로
쓰지 않습니다.** 응답에는 그대로 나가지만(클라이언트가 버전을 되보내야 하므로), 요청으로
감사 기록을 위조할 수 없습니다.

#### API 오류 응답 (`spring-api-errors`)

`jpa-springboot-v3` 와 `jpa-springboot-kt` 에 포함되어 모든 v3 번들(DDD·헥사고날 포함)이
받습니다. 2.x 호환 번들 `jpa-springboot` 에는 없습니다. 노출된 엔티티가 있는 도메인마다 컨트롤러
패키지에 `ShopApiErrors` (`@RestControllerAdvice`, RFC 9457 `ProblemDetail`) 를 한 번
생성합니다. 위 표의 상태 코드를 답하고, 그 패키지의 컨트롤러에만 적용됩니다.
generate-once 라 응답 형식은 개발자 것입니다. 생성자에서 Spring 이 구성한 Validator 를
`BeanValidation.use(...)` 로 넘겨, 서비스 측 검증도 주입이 필요한 커스텀 제약과
애플리케이션 메시지를 씁니다. 불변식이 실려 오는 두 래퍼(`InvalidDataAccessApiUsageException`,
`TransactionSystemException`)만 잡으므로 unique 충돌 같은 다른 데이터 접근 오류는 사용자의
핸들러로 갑니다.

**컨트롤러에 닿기 전의 거절 (`RequestShapeErrors`).** 생성된 `ShopApiErrors` 는 런타임의
`io.elasticore.springboot3.validation.RequestShapeErrors` 를 `@Import` 합니다. Spring MVC 가 컨트롤러 메서드를 부르기
전에 거절한 요청을 **애플리케이션 전체에서** 같은 모양(`ProblemDetail` + `code`)으로 답합니다 - 다른 패키지에 손으로 쓴
컨트롤러도 포함합니다. 전에는 Spring Boot 의 기본 본문 `{timestamp, status, error, path}` 였고 `code` 가 없었습니다.
상태 코드는 전과 같습니다.

| 요청 | 상태 | `code` | 예 (Pet Store 에서 확인) |
|---|---|---|---|
| 본문이 없거나 JSON 이 아님 | 400 | `unreadable` | 본문 `{"lines":` |
| 타입이 받지 않는 값 - 숫자 자리에 글자, 없는 enum 코드, 음수 `quantity`, int 범위 밖 | 400 | `invalid`, `fieldErrors[].code` 는 `typeMismatch` | `lines[0].quantity: a quantity cannot be negative: -1`, `creditCard.creditCardType: Unknown CreditCardType code: DINERS`, `GET /item/abc` -> `itemId: must be a number` |
| 필수 쿼리 파라미터 없음 | 400 | `invalid`, `fieldErrors[].code` 는 `NotNull` | 손으로 쓴 `@RequestParam` |
| 파라미터 제약 (`@RequestParam @Min(1) int page`) | 400 | `invalid`, 제약 이름 (`Min`) | 손으로 쓴 컨트롤러 |
| 손으로 쓴 컨트롤러의 `@Valid` 본문 | 400 | `invalid` | (생성된 컨트롤러의 것은 `ShopApiErrors` 가 답함) |
| 없는 경로 | 404 | `not-found` | `GET /api/petstore/nothing/here` |
| 그 경로에 없는 메서드 | 405 (`Allow` 헤더 그대로) | `method-not-allowed` | `@editable` 이 아닌 루트에 `PUT` |
| 줄 수 없는 응답 형식 (`Accept: image/png`) | 406 | `not-acceptable` | |
| 읽을 수 없는 본문 형식 (`Content-Type: text/plain`) | 415 | `unsupported-media-type` | |

- 브라우저가 페이지를 달라고 하면(`Accept` 에 `text/html`) 404·405 는 Spring Boot 의 오류 페이지(Whitelabel 이나
  `error/404.html`)가 그대로 나옵니다.
- 생성된 컨트롤러의 `findById`·`delete` 가 없는 행에 답하던 **빈 본문의 404** 도 이제 같은 모양입니다
  (`NotFoundException`, `"code": "not-found"`, `"detail": "Item not found"`). `NotFoundException` 은 `@ResponseStatus(404)` 라서
  `spring-api-errors` 를 꺼도 404 입니다 (본문은 Spring Boot 의 것).
- **다르게 답하려면** 그 예외를 `ShopApiErrors` 에서 처리하세요 - 그 파일이 먼저 불립니다. 회사 공통
  `@RestControllerAdvice` 도 `RequestShapeErrors` 가 등록한 처리기(`RequestShapeAdvice`)보다 먼저입니다(그 처리기는 맨 뒤). 확실히 하려면 공통
  advice 에 `@Order` 를 붙이세요.
- `spring.mvc.problemdetails.enabled=true` 는 켜지 마세요. Spring Boot 의 처리기가 맨 앞(`@Order(0)`)에 서서 필드 검증
  응답(`fieldErrors`)까지 가져갑니다.
- 자동 구성이 아닙니다. `ShopApiErrors` 가 가져올 때만 생기므로, 같은 런타임 라이브러리를 쓰는 2.x 애플리케이션은 그대로입니다.
  `spring-api-errors` 를 끄면 함께 빠집니다. `RequestShapeErrors` 는 처리기(`RequestShapeAdvice`)를 등록만 하는 클래스이고,
  처리기는 `@ComponentScan("io.elasticore.springboot3")` 에 잡히지 않습니다 (예전 포트 가이드가 2.x 앱에 권하던 스캔).
  여러 도메인의 `XxxApiErrors` 가 모두 가져와도 처리기는 하나입니다.
- **이미 있는 `XxxApiErrors` 를 고친 적이 있으면** 다시 생성되지 않으니 클래스에 한 줄을 붙이세요
  (손대지 않았으면 다시 생성되어 붙습니다):
  ```java
  @org.springframework.context.annotation.Import(io.elasticore.springboot3.validation.RequestShapeErrors.class)
  ```
  Kotlin 은 `@org.springframework.context.annotation.Import(io.elasticore.springboot3.validation.RequestShapeErrors::class)`.
- Spring Framework 6.1 (Spring Boot 3.2) 이상이 필요합니다 (`NoResourceFoundException`, `HandlerMethodValidationException`).
  샘플은 Spring Boot 3.3.2 와 4.0.8 에서 확인합니다.
- **다른 시스템 호출 실패 (502)**: 포트를 부른 코드가 실패라고 판단하면 `io.elasticore.springboot3.http.PortCallException` 을
  던집니다. `ShopApiErrors` 가 502, `"code": "upstream"` 으로 답합니다. 언제 무엇을 실패로 볼지는
  `elasticore-dsl-port-guide.md` 2.8 에 있습니다.

#### 오류 코드와 다국어 (Accept-Language)

모든 오류 응답에 프로그램이 분기할 수 있는 `code` 가 있고, 문구는 **한 응답에 한 언어**로 나갑니다:

1. `Accept-Language` 에 적힌 언어를 순서대로 보아, **애플리케이션이 번역한 언어**(classpath 의 `messages_<언어>`, 도메인 번들
   옆 `<도메인>-messages_<언어>`, `ValidationMessages_<언어>` 파일이 있는 언어), 기본 언어, 영어 중 처음 나온 것.
2. 그런 것이 없으면(헤더가 없거나 번역이 없는 언어) **기본 언어**: Spring Boot 의 `spring.web.locale` (Boot 3·4 같은 이름).
   설정하지 않았으면 번들의 기본 문구(영어, 또는 DSL 에 적은 문장)입니다.
3. Bean Validation 의 기본 메시지(`Size`, `NotNull`, `Min` ...)도 같은 언어로, 숫자를 넣어 다시 말합니다
   (`size must be between 0 and 10` / `크기가 0에서 10 사이여야 합니다`). DSL 에 적은 문장은 적은 그대로입니다.

**서버 머신의 언어는 쓰지 않습니다.** 한국어 OS 의 서버도 헤더 없는 요청, `en`, `sv` 에 영어로 답합니다 - Pet Store 를
`-Duser.language=ko -Duser.country=KR` 로 띄워 확인했고, `ShopFlowTest` 가 한국어 JVM 에서 같은 것을 확인합니다.

```yaml
# application.yml - 헤더 없는 요청을 한국어로 (번역 파일 _ko 가 있어야 의미가 있음)
spring:
  web:
    locale: ko
```

- 영어는 번들의 기본 문구라서, `spring.web.locale: ko` 여도 `Accept-Language: en` 이면 영어입니다.
- Hibernate Validator 에 프랑스어가 있어도 애플리케이션이 번역하지 않았으면 `fr` 요청은 기본 언어로 나갑니다 - DSL 문장과
  기본 메시지가 섞이지 않게.
- 세션·쿠키 등 다른 `LocaleResolver` (`spring.web.locale-resolver: fixed` 포함)를 쓰면 그 resolver 가 정한 언어를 따릅니다.
  헤더도 없고 resolver 가 컨테이너의 언어를 그대로 돌려준 요청(아무도 정하지 않은 요청)만 번들의 기본 문구입니다.

번역되는 것:

| 응답 | `title` | `detail` |
|---|---|---|
| 409 (생명주기), 404 (참조한 행 없음), 422 (불변식) | O | O (규칙의 코드로) |
| 409·400 의 데이터베이스 거절 (`stale`, `required`, `unique`, `referenced`, `conflict`) | O | O (`error.<code>`) |
| 400 필드 검증 (`@Valid`, 서비스 측 검증 모두) | O | 필드 메시지 O (`<제약>.<필드>`, 예 `Size.lines`). 번역이 없는 기본 메시지는 요청 언어로 |
| 컨트롤러 전의 거절 (`RequestShapeErrors`) | O | O (`error.unreadable`, `error.method-not-allowed` ..., 필드는 `typeMismatch.<필드>`) |
| 손으로 쓴 거절에 메시지 코드를 준 것 | O | O (그 코드로) |
| 404·409(이미 있음)·401·403·502·기타 400 중 코드 없이 던진 것 | O | X — 예외 메시지(영어, 식별자 포함) 그대로 |

Pet Store 에서 배송된 주문을 취소하면 (`ShopFlowTest` 로 확인):

```http
POST /api/petstore/purchaseOrder/{id}/cancel
Accept-Language: ko

HTTP/1.1 409
{ "title": "지금 상태에서는 할 수 없는 요청",
  "detail": "주문 상태가 SHIPPED이면 취소할 수 없습니다 (가능한 상태: [PLACED])",
  "code": "state.PurchaseOrder.status.cancel", "status": 409 }
```

3.0.261022-rc1 부터 생명주기 거절은 409, 참조한 행이 없으면 404 입니다 (전에는 생명주기 거절이 422, 없는 행을 id 로
참조한 저장은 외래 키 위반의 409 "Conflicts with stored data", `@snapshot` 이 원본 행을 못 찾을 때만 422). 이미 있는 프로젝트에서는
`<도메인>ApiErrors` 를 **손댄 적이 없으면** 다시 생성되어(`REGENERATED(owned, never edited)`) 새 응답으로 바뀌고 -
422 로 분기하던 클라이언트가 있으면 함께 고칩니다 - **손댄 적이 있으면** 그대로라 생명주기 거절도, 없는 참조도 422 로
답합니다. 그 파일의 `refused(...)` 를 새로 생성된 것처럼 `state.` 는 409, `reference.` 는 404 로 고치세요.

`Accept-Language` 가 없거나 번역이 없는 언어면
`"detail": "PurchaseOrder cannot cancel when status is SHIPPED; it must be [PLACED]"` 입니다.

**코드 체계**

| 코드 | 언제 | 값 (`{0}`, `{1}`) |
|---|---|---|
| `not-found`, `already-exists`, `stale`, `required`, `unique`, `referenced`, `conflict`, `invalid`, `refused`, `state`, `not-signed-in`, `forbidden`, `bad-request`, `no-such-property` | 모든 도메인 공통 (제목 `error.<code>.title`, 문장 `error.<code>`) | - |
| `state.<Entity>.<field>.<command>` | 생명주기: 시작 상태가 아닌데 명령이 옴 (`states:`) | 지금 상태, 가능한 상태 |
| `invariant.<Entity>.<field>.min` / `.max` / `.minsize` / `.maxsize` | 필드 범위 불변식 | - |
| `invariant.<Entity>.<조건>` | `@invariant(조건, '문장')` — 조건식으로 만든 이름 (`items.size > 0` -> `items-size-gt-0`) | - |
| `reference.<Entity>.<relation>` | 요청이 id 로 가리킨 행이 없음 - 다른 애그리거트 참조(저장 전 확인), `@snapshot` 의 원본 (404) | 식별자 |
| `upstream` | 다른 시스템 호출 실패 (`PortCallException`, 502) | - |
| `unreadable`, `method-not-allowed`, `unsupported-media-type`, `not-acceptable` | 컨트롤러 전의 거절 (`RequestShapeErrors`; 제목 `error.<code>.title`, 문장 `error.<code>`) | 405 `{0}` 메서드, 415 `{0}` 본문 형식, 406 `{0}` 낼 수 있는 형식 |
| `error.no-such-path`, `error.missing-value` | 없는 경로(404 `not-found`)의 문장, 없는 파라미터의 필드 메시지 | `{0}` 경로 / - |
| `typeMismatch.<필드>` | 타입이 받지 않는 값의 필드 메시지. `typeMismatch.lines[0].quantity` 그 줄만, `typeMismatch.lines.quantity` 모든 줄. 둘 다 없으면 타입이 거절한 메시지(`Quantity`, enum 의 `fromCode` - 영어), 그것도 없으면 `error.type.number` / `.boolean` / `.text` / `.datetime` / `.enum` / `.other` | `.enum` 의 `{0}` 가능한 값 |
| (손으로 쓴 코드) | 손으로 쓴 거절에 준 메시지 코드 (아래) | 준 값 |

**번역하는 법**

1. 빌드하면 도메인마다 `src/main/resources/i18n/<도메인>-messages.properties` 가 생깁니다.
   그 도메인이 답할 수 있는 **모든 코드와 기본 문구**가 들어 있습니다 (생성물: 고치지 마세요).
2. 같은 폴더에 `<도메인>-messages_ko.properties` 를 만들어 필요한 키만 번역합니다
   (UTF-8 로 저장해도 됩니다). 빠진 키는 기본 문구로 나갑니다.

```properties
# src/main/resources/i18n/petstore-messages_ko.properties
error.state.title=지금 상태에서는 할 수 없는 요청
state.PurchaseOrder.status.cancel=주문 상태가 {0}이면 취소할 수 없습니다 (가능한 상태: {1})
invariant.PurchaseOrder.lines.minsize=장바구니가 비어 있습니다
Size.lines=장바구니가 비어 있습니다
NotNull.lines=장바구니가 비어 있습니다

# 컨트롤러 전의 거절 - RequestShapeErrors 도 모든 도메인 번들과 그 옆 번역을 읽습니다
error.unreadable=요청 본문이 없거나 JSON 이 아닙니다.
error.method-not-allowed={0} 메서드는 여기서 쓸 수 없습니다.
typeMismatch.lines.quantity=수량은 0 이상의 정수입니다
```

- 필드의 값은 Spring 의 순서입니다: `{0}` 필드, 그다음 제약 속성의 이름순 (`Size` 는 `{1}` max, `{2}` min).
  `Size.login=로그인은 {1}자까지` 는 `@Valid` 와 서비스 측 검증 모두에 같은 값으로 나갑니다.
- **필드 없는 코드(`Size=`, `NotNull=`)는 쓰지 마세요.** 그 언어의 모든 `@Size` DSL 문장을 덮어 버리고, 범위는 위치
  (`{1}`, `{2}`)로만 받습니다. 필드마다(`Size.login`) 쓰거나, 쓰지 않으면 기본 메시지가 요청 언어로 숫자와 함께 나갑니다.
- **`ValidationMessages*.properties` 로 언어를 맞추지 마세요.** 기본 파일(`ValidationMessages.properties`)은 다른 모든
  언어의 Hibernate Validator 번역을 덮고, `_<언어>` 파일만 두면 그 파일이 없는 언어의 요청에 서버 머신의 언어로 답하게
  됩니다(JDK 의 bundle fallback). 위의 규칙이 이미 한 응답을 한 언어로 맞춥니다.

찾는 순서: classpath 의 `messages_<언어>.properties` -> 도메인 번들과 그 옆 번역 ->
애플리케이션의 `MessageSource` (예: `spring.messages.basename` 으로 바꾼 번들) -> 기본 문구. 설정은 필요 없습니다.
앞의 둘은 **요청 언어의 파일을 먼저** 봅니다: `messages_ko`, 모든 도메인의 `_ko` 파일을 다 본 뒤에야 기본 파일(영어)을 봅니다.
그래서 도메인이 여럿이어도(`billing`, `shop`) `shop-messages_ko` 에만 둔 `error.unreadable` 번역이, 이름순으로 앞선
`billing-messages.properties` 의 영어에 가려지지 않습니다.
다만 `spring.messages.basename` 을 바꿔 둔 번역은 도메인 번들(영어 기본 문구)보다 뒤에 찾으므로, 도메인 코드의
번역은 `messages_<언어>.properties` 나 도메인 번들 옆 파일에 두세요.

- **작은따옴표**: 값(`{0}`)이 들어가는 문장(`state.*`, `reference.*`)은 Java `MessageFormat` 규칙을 따라 `'` 를
  `''` 로 적습니다 (`can''t`). 값이 없는 문장은 그대로 적습니다.
- 같은 엔티티의 두 `@invariant` 가 같은 이름이 되면 두 번째에 `-2` 가 붙습니다. 조건식의 `+ - * /` 와 한글 등은
  이름에 구분되어 들어갑니다 (`a + b > 0` -> `a-plus-b-gt-0`).

**손으로 쓴 거절을 번역하기**

`beforeSave`·루트 메서드·서비스 오버라이드·포트를 부르는 코드에서 던지는 런타임의 예외는 **메시지 코드**를 받습니다.
생성된 거절과 같은 순서로 번역을 찾고, 없으면 두 번째 인자(기본 문구, 값이 있으면 `MessageFormat` 패턴)가 나가며,
응답의 `code` 가 그 코드가 됩니다:

```java
throw new ConflictException("loan.no-copy-left", "No copy of {0} is left to lend", isbn);
```

```properties
# src/main/resources/i18n/library-messages_ko.properties (도메인 번들 옆, 개발자 파일) 또는 messages_ko.properties
loan.no-copy-left=빌려줄 수 있는 {0} 이(가) 없습니다
```

```http
HTTP/1.1 409                       (Accept-Language: ko)
{ "title": "...", "detail": "빌려줄 수 있는 978-0-00 이(가) 없습니다", "code": "loan.no-copy-left", "status": 409 }

HTTP/1.1 409                       (헤더 없음)
{ "title": "Conflicts with stored data", "detail": "No copy of 978-0-00 is left to lend", "code": "loan.no-copy-left", "status": 409 }
```

| 예외 (`io.elasticore.springboot3.`) | 상태 | 코드 없이 던지면 `code` |
|---|---|---|
| `validation.BadRequestException(code, message, args...)` | 400 | `bad-request` |
| `security.NotSignedInException(code, message, args...)` | 401 | `not-signed-in` |
| `security.ForbiddenException(code, message, args...)` | 403 | `forbidden` |
| `validation.NotFoundException(code, message, args...)` | 404 | `not-found` |
| `validation.ConflictException(code, message, args...)` | 409 | `conflict` |
| `http.PortCallException(code, message, args...)` | 502 | `upstream` |

- 기본 문구는 코드에 적습니다. 생성되는 `<도메인>-messages.properties` 는 손으로 쓴 코드를 모르고 빌드마다 다시 쓰이므로
  거기에 적지 마세요. 번역만 그 옆 `_ko` 파일이나 `messages_ko.properties` 에 둡니다.
- 값이 있는 문장은 `'` 를 `''` 로 적습니다 (기본 문구도). 예외의 `getMessage()` 는 값을 넣은 기본 문구입니다(로그에 그대로).
- 코드 없이 던지면(`new ConflictException("...")`) 예전처럼 그 메시지 그대로 나가고 `code` 는 표의 마지막 칸입니다.
- 불변식처럼 상태를 코드로 고르는 거절은 `InvariantViolationException(code, message, args...)` 입니다 (`state.` 409,
  `reference.` 404, 그 밖 422).
- 확인: Pet Store `ShopFlowTest.aRefusalHandCodeThrowsIsSaidByItsCode` (테스트용 컨트롤러가 던진 것).
- 고친 적이 있는 `XxxApiErrors` 는 다시 생성되지 않아 처리기가 `ex.getMessage()` 만 씁니다. 새로 생성된 파일처럼
  `coded(problem(..., messages.text(ex.getCode(), ex.getMessage(), ex.getArgs())), ex.getCode())` 로 고치세요.

**400 (필드 검증)** 은 `errors` (`"필드: 메시지"` 목록, 예전과 같음) 에 더해 `fieldErrors` 를 보냅니다:
`[{ "field": "lines", "code": "Size", "message": "장바구니가 비어 있습니다" }]`. 필드 메시지는 Spring 의
메시지 코드(`Size.lines`, `NotNull.customerDTO.login`, `NotNull` ...)로 같은 번들에서 찾습니다.
생성된 TypeScript 클라이언트의 `ApiError` 는 `code` 와 `fieldErrors` (필드 -> 메시지)를 읽습니다.

- 런타임: `io.elasticore.springboot3.validation.ErrorMessages` (`text`, 서비스 측 검증의 `violation`),
  `RequestShapeErrors`, `InvariantViolationException(code, message, args...)`.
- 이미 있는 `XxxApiErrors` 는 개발자 파일(generate-once)입니다. 한 번도 고치지 않았다면 다시 생성되고,
  고쳤다면 그대로 둡니다. 새 형식을 쓰려면 파일을 지우고 다시 빌드하세요. 고친 파일도 런타임만 올리면 `@Valid` 의
  기본 메시지는 요청 언어로 나갑니다(`messages.text(f)`). 서비스 측 검증은 `messages.text(code + "." + field, v.getMessage())`
  를 `messages.violation(code + "." + field, v, v.getMessage())` 로 바꿔야 같아집니다.
- `MessageSource` 빈이 여럿이고 primary 가 없으면 애플리케이션의 것은 쓰지 않습니다 (기동은 됨). classpath 의
  `messages_<언어>.properties` 와 도메인 번들은 그대로 찾습니다.
- 번역이 있는지는 classpath 의 파일(`messages_<언어>`, 도메인 번들, `ValidationMessages`)로 봅니다. `spring.messages.basename`
  으로 바꾼 번들에만 있는 언어는 번역으로 보지 않습니다 - 그 언어도 쓰려면 위 파일 중 하나를 두세요.
- 생성기와 **런타임 라이브러리(`elasticore-springboot3`/`4`)를 같은 버전으로** 올리세요. 생성된 엔티티가 런타임의
  `InvariantViolationException(code, message, args...)` 와 `ErrorMessages` 를, 생성된 `XxxApiErrors` 가 `RequestShapeErrors`
  를 씁니다 (예전 런타임에는 없어서 컴파일되지 않음).

#### Kotlin 에서 생성물이 부팅되기 위한 것

Kotlin 샘플을 Spring Boot 로 실제 기동하며(`ShopApiContractTest`) 드러난 세 가지를 고쳤습니다.
`kotlin-spring` 플러그인이 없어도 동작합니다.

- 확장 서비스(`OrderService`)는 `@Transactional` 을 상속하므로 `open class` 입니다.
- 리포지터리 어댑터는 `@Repository` 예외 변환 프록시 대상이라 `open class` 입니다.
- 엔티티 컬렉션은 `MutableList<X>` 입니다. `List<X>` 는 열린 엔티티 타입에 대해
  `List<? extends X>` 로 컴파일되어 Hibernate 가 raw 컬렉션으로 거절합니다.

Spring 을 쓰는 Kotlin 앱은 `org.jetbrains.kotlin:kotlin-reflect` 가 필요합니다.

#### 애그리거트 멤버 — 쓰기는 루트로만

- **컬렉션 멤버**의 게터는 읽기 전용 뷰를 돌려줍니다 — Java 는
  `Collections.unmodifiableList`, Kotlin 은 프로퍼티 getter 가 `MemberList.view(field)`.
  `order.getItems().add(x)` 는 `UnsupportedOperationException` — 전에는 소유자가 설정되지
  않은 채 저장 시 조용히 사라졌습니다. 추가·삭제는 `addOrderItem` / `removeOrderItem` /
  `replaceItems` 로.
- **멤버 삭제도 루트의 쓰기입니다.** 불변식을 선언한 루트의 멤버에는
  `OrderItemRemovalCheck` 리스너가 붙어, 멤버 행이 삭제될 때(flush) 루트의 불변식을
  남은 상태로 검사합니다. 마지막 주문 상품을 지우는 것이 이제 거절됩니다. 교체
  (`replaceItems`)는 flush 시점에 이미 채워져 있으므로 허용됩니다. 이를 위해
  `removeX()` 는 멤버의 루트 참조를 비우지 않습니다(행은 같은 flush 에 삭제).
- **1:1 멤버**(`@OneToOne` + 같은 애그리거트)는 루트 DTO 로 오가며, 수정 시 기존 멤버를
  **제자리에서 갱신**합니다(새 행으로 바꾸지 않음). 멤버는 자기 엔드포인트가 없으므로
  루트 DTO 에서 `--` 로 빼면 도달할 길이 없습니다.
- **도메인 이벤트**의 `occurredOn` 은 `raiseX()` 를 부른 시각입니다. 이벤트 객체는
  식별자가 생긴 뒤(발행 시)에 만들어지지만 시각은 raise 때 고정됩니다.

### 잘못 조합하면 실패합니다

파일 셸은 정의상 언어 전용입니다. 번들은 이름의 목록일 뿐이라 잘못 조합해도
**아무도 눈치채지 못합니다** — 템플릿은 텍스트고, `.kt` 파일 속 Java는 컴파일러가
돌 때에야 드러납니다.

그래서 셸은 자기가 쓰는 언어를 말합니다:

```java
c.requireSyntax(Syntax.KOTLIN, "kt-dto-file");
```

어긋나면 어느 규칙이 어느 언어를 쓰려 했는지와 함께 그 자리에서 멈춥니다.

### 언어별 규칙 선택

`publisher.rules`는 **서버의 규칙**을 정합니다. Java와 Kotlin은 둘 다 서버라
같은 키를 씁니다. TypeScript는 산출물이 다른 프로그램이라 **자기 키**를 씁니다.

| 키 | 적용 대상 |
|---|---|
| `publisher.rules` | Java, Kotlin |
| `publisher.rules.kt` | Kotlin (있으면 위보다 우선) |
| `publisher.rules.ts` | TypeScript |

`publisher.rules`에 Kotlin 번들을 적어두면 TypeScript 생성이 그걸 물려받아
`.ts` 파일에 Kotlin 규칙이 들어갑니다 — 그래서 TypeScript는 물려받지 않습니다.

### 번들 조합

번들은 이름을 나열해 조합합니다. 언어 번들과 아키텍처 규칙은 **서로 다른 축**이라
같이 적습니다.

```yaml
    publisher:
      rules: [jpa-springboot-kt, ddd-aggregate]   # Kotlin + 애그리거트 경계
```

언어별로 미리 묶인 번들이 있으면 그걸 쓰는 편이 낫습니다 — `ddd-springboot` 와
`ddd-springboot-kt` 는 같은 읽기의 두 언어 판이고, 다른 점은 언어가 정하는 것뿐입니다
(값 객체를 Lombok 으로 만드느냐, 언어로 만드느냐). 위의 "언어별로 다르게 도달하는
규칙" 표가 각 규칙이 어느 언어에 닿는지를 말합니다.

### 규칙 하나만 끄기

```yaml
    publisher:
      suppress: [jpa-comment]   # @Comment 애노테이션을 더 이상 붙이지 않음
```

`suppress`의 값은 `@Rule(value = ...)`에 적힌 id입니다.

---

## 3. 규칙 작성

> **프로젝트에서 우리 팀 규칙을 더하려는 경우**(규칙 모듈, `elasticore { rules = [...] }`, 새 파일 종류,
> 테스트까지)는 예제 중심의 `elasticore-ddd-customize-guide.md` 를 먼저 보세요.
> 이 장은 ElastiCORE 자체의 규칙을 고치는 사람을 위한 API 참조입니다.

### 3.1 최소 형태

```java
package kr.co.acme;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

public class AcmeRules implements CodeRules {

    /** @masked 필드에 사내 표준 애노테이션을 붙인다. */
    @Rule(value = "acme-masked", target = "dto", order = 50)
    void masked(EntitySpec e, FieldSpec f) {
        if (!f.hasDsl("masked")) return;
        ctx(e).field(f).ensure(AnnoCode.of("kr.co.acme.security.Masked"));
    }
}
```

`CodeRules`는 표식(marker) 인터페이스입니다. 구현할 메서드가 없고,
`@Rule`이 붙은 메서드가 규칙이 됩니다.

### 3.2 `@Rule` 속성

| 속성 | 기본값 | 의미 |
|---|---|---|
| `value` | (필수) | 규칙 id. 추적과 `suppress`에 쓰입니다 |
| `target` | `"entity"` | 어떤 아티팩트에 기여하는가 |
| `order` | `0` | 낮을수록 먼저. 같으면 등록 순서 |
| `phase` | `HEAD` | `HEAD`는 필드 규칙보다 먼저, `TAIL`은 나중 |

`target`은 env.yml의 네임스페이스 키와 같은 단어를 씁니다:
`entity`, `dto`, `srchdto`, `enum`, `repository`, `service`, `control`, `mapper`, `port`, `Q` 등.

### 3.3 메서드 시그니처가 호출 시점을 정합니다

| 시그니처 | 언제 |
|---|---|
| `(EntitySpec e)` | 엔티티마다 한 번 |
| `(EntitySpec e, FieldSpec f)` | 필드마다 한 번 |
| `(DomainSpec d)` | 도메인마다 한 번 (Q, mapper, 지원 타입) |
| `(EnumSpec e)` | 열거형마다 한 번 |
| `(ProtoServiceSpec p)` | proto 포트마다 한 번 |

메서드는 `void`이고 접근 제어자는 없어도 됩니다.

### 3.4 실행 순서

```
HEAD 엔티티 규칙  →  필드 규칙(필드별)  →  TAIL 엔티티 규칙
```

파일 셸 규칙(`importList`, `classDeclaration` 등을 채우는 것)은 보통
`phase = TAIL, order = 90`입니다 — 모든 필드 규칙이 import를 등록한 **뒤에**
import 목록을 렌더링해야 하기 때문입니다.

---

## 4. 모델 읽기 — Spec

### `FieldSpec` — 자주 쓰는 것

```java
f.name()                 // 필드 이름
f.capitalName()          // 접근자용 대문자 시작 이름
f.typeName()             // 선언 타입
f.coreItemType()         // List<X>의 X, 아니면 자기 타입
f.required()             // ! 로 표시됐는가
f.isList()               // List<...> 인가
f.isBaseType()           // 원시/래퍼/문자열인가
f.isBaseItemType()       // 아이템 타입이 그런가
f.isEntityRef()          // 엔티티 참조인가
f.label()                // 주석에 쓸 설명

f.hasDsl("id")                     // @id 가 있는가 (대소문자 무시)
f.hasDslAny("min", "minval")       // 별칭 중 하나라도
f.dslValue("length")               // @length(200) 의 200
f.dslValueAny("label", "desc")     // 먼저 맞는 것
```

### `EntitySpec`

```java
e.getName()              // 엔티티 이름
e.pkg("dto")             // 네임스페이스 (env.yml의 키)
e.fields()               // 자기 필드
e.allFields()            // 상속 포함
e.dtoFields()            // DTO 쪽 필드
e.pkFields()             // @id/@pk 필드들
e.parentName()           // @extend 대상
e.hasMeta("expose")      // 메타 애노테이션
e.metaValue("dynamicupdate")
e.isKnownEnum(type)      // 모델이 아는 열거형인가
```

> `Field.getType()`은 private입니다. 타입은 `FieldSpec`을 통해 읽으세요.

---

## 5. 코드 쓰기 — `SourceCode` / `FieldCode` / `AnnoCode`

`ctx(spec)`이 그 아티팩트의 `SourceCode`를 돌려줍니다.

### 5.1 필드 단위 — `FieldCode`

```java
ctx(e).field(f)
      .ensure(anno)          // 없으면 추가 (권장)
      .add(anno)             // 무조건 추가
      .merge(anno)           // 같은 애노테이션에 속성 병합
      .replace(anno)         // 교체
      .removeAnnotation(fqcn)
      .type("String")        // 선언 타입 변경
      .rename("nameFrom")    // 이름 변경
      .initial("0")          // 초기값
      .before("/* ... */")   // 선언 위에
      .after("...")          // 선언 아래에 (별도 멤버)
      .comment("...");
```

필드를 아예 빼려면 `ctx(e).removeField(f)`,
아티팩트 자체를 만들지 않으려면 `ctx(e).dontMake()`.

### 5.2 애노테이션 — `AnnoCode`

**문자열을 이어 붙여 애노테이션을 만들지 마세요.** `AnnoCode`가 그 일을 합니다.

```java
AnnoCode.of("jakarta.persistence.Column")
        .val("name", "user_id")        // name = "user_id"  (따옴표 붙음)
        .valRaw("length", "%d", 200)   // length = 200      (그대로)

AnnoCode.of(VALIDATION, "NotNull")     // 포맷 + 단순 이름
AnnoCode.of("...JsonInclude").valueRaw("...Include.NON_NULL")   // value = ...
AnnoCode.of("...").qualified()         // 완전한 이름으로 렌더링
AnnoCode.of("...").block("  ")         // 속성을 줄마다
```

중첩 목록(`indexes = {...}`)은 `AnnoCode.AnnoList.of(...)`를 씁니다.
괄호가 Java의 `{}`인지 Kotlin의 `[]`인지는 렌더러가 압니다.

### 5.3 템플릿 슬롯 — `SourceCode`

```java
c.var("packageName", e.pkg("dto"))          // ${packageName}
c.var("className", "%sDTO", e.getName())    // 포맷 사용
c.varLine("methodList", "...")              // 슬롯에 한 줄 추가
c.varLines("importList", list)              // 여러 줄
c.line("forward", "...")                    // 이름 붙은 블록에 (mapper 등)
c.addImport("%s.%s", pkg, name)             // import 등록
c.renderImports()                           // 실제 사용된 것만 렌더
c.renderFieldBlock(1)                       // 필드 블록 (빈 줄 수)
c.ensureClassAnnotation(anno)
c.renderClassAnnotations()
c.template("dto")                           // 다른 스켈레톤 사용
```

> `var(key, null)`은 **무시**됩니다(저장 안 함). 빈 문자열을 원하면 `""`를 넘기세요.
> 해결되지 않은 `${key}`가 있는 줄은 통째로 빈 줄이 됩니다 — 오류가 아닙니다.

---

## 6. 언어 이음새 — `Syntax`

**이것이 이 아키텍처에서 가장 중요한 규칙입니다.**

> 규칙 안에 언어 분기(`if (kotlin) ... else ...`)를 넣지 마세요.
> 규칙은 *무엇을* 낼지만 말하고, *어떻게* 쓸지는 `Syntax`가 답합니다.

이유: 공통 규칙에 분기를 하나 넣을 때마다 **Kotlin을 고치다 Java를 깨뜨릴 자리**가
하나씩 늘어납니다. 이음새를 거치면 그 자리가 구조적으로 생기지 않습니다.

```java
Syntax s = ctx(e).syntax();

s.importLine(fqcn)                  // import x;      /  import x
s.declaration(mod, type, name, init, required)
s.typeName("Integer")               // Integer        /  Int
s.nullableType("String")            // String         /  String?
s.classLiteral("X")                 // X.class        /  X::class
s.javaClassLiteral("X")             // X.class        /  X::class.java
s.statement("return x")             // return x;      /  return x
s.methodSignature(mod, ret, name, params)
s.parameter("String", "x")          // String x       /  x: String
s.getter("dto", "name")             // dto.getName()  /  dto.name
s.setter("to", "name", v)           // to.setName(v)  /  to.name = v
s.setterRef("to", "name")           // to::setName    /  { v -> to.name = v }
s.localVar(type, name, init)        // T x = init;    /  val x = init
s.newInstance("X", "")              // new X()        /  X()
s.lambda("e", body)                 // e->body        /  { e -> body }
s.sam(type, params, body)           // (p) -> body    /  Type { p -> body }
s.notNull("x")                      // x              /  x!!
s.escapeName("in")                  // in             /  `in`
s.cast("short", "1")                // (short) 1      /  1.toShort()
s.overriding()                      // public         /  override
s.injectedProperty(type, name)      // private final T x;  /  val x: T,
s.needsImport(fqcn)                 // 이미 스코프에 있는 타입은 제외
```

### 이음새가 JVM 모양인 부분

TypeScript를 붙여보고 알게 된 것: **27개 중 8개는 JVM 개념**입니다.
`classLiteral`, `javaClassLiteral`, `sam`, `samBlockOpen`, `samBlockClose`,
`lambdaReturn`, `injectedProperty`, `overriding`.

TypeScript는 이들을 **답하지 않고 예외를 던집니다.** 그럴듯한 값을 돌려주면
JVM을 가정한 규칙이 조용히 통과해서 말이 안 되는 코드를 내기 때문입니다.
실제로 `dto-jsonview`가 여기 걸렸고, 그래서 그 규칙이 `supportsAnnotations()`를
먼저 묻도록 고쳤습니다.

`importLine`은 **진짜로 깨진 하나**입니다. 완전한 이름이 곧 import인 언어를
전제로 만들어졌는데, TypeScript의 import는 **모듈 경로 + 이름**이고 완전한
이름은 경로에 대해 아무 말도 하지 않습니다.

### 이음새로 풀 수 없는 것

문법이 아니라 **역할**이 다르면 이음새로 풀리지 않습니다. 그럴 때는
**규칙을 분리하고 번들이 고르게** 하세요 — 분기가 아니라.

예:

- Lombok은 Java 전용 도구입니다. `@Builder.Default`는 Kotlin에서 "다르게 쓰는" 게
  아니라 **아예 없는 개념**입니다 → Java 전용 규칙으로 분리
- Java는 `Specification sp` 파라미터를 재대입하며 조건을 누적하지만,
  **Kotlin은 파라미터를 재대입할 수 없습니다** → 셸을 언어별로 분리

이것이 `MapperBaseRules`(공유 결정)와 `MapperAssembleRules` /
`KotlinMapperAssembleRules`(언어별 셸)로 나뉜 이유입니다.

---

## 7. 스켈레톤(`.tmpl`)

```
resources/elasticore-template/
    entity.tmpl          # 2.x 호환
    v3/entity.tmpl       # 정리 모드
    kt/entity.tmpl       # Kotlin
    ts/dto.tmpl          # TypeScript
```

`target`, 템플릿 파일 이름, env.yml 네임스페이스 키는 **같은 단어**입니다.
따로 매핑표가 없으므로 어긋날 일이 없습니다.

v3에 자기 스켈레톤이 없는 타깃은 호환 스켈레톤으로 떨어집니다.
**단, 언어가 다르면 떨어지지 않고 예외가 납니다** — 폴백은 Java로 쓰여 있어서
`.kt` 파일에 Java가 조용히 들어가는 것을 막습니다. `.proto`처럼 어느 언어도 아닌
아티팩트만 예외입니다.

---

## 7.5 생성 파일의 소유권 (elcore3)

생성기가 파일을 어떻게 다루는지는 첫 줄로 정해집니다.

| 파일 상태 | 동작 |
|---|---|
| `//ecd:` 해시 + 관리 파일, 내용이 해시와 같음 | DSL 에 맞춰 다시 씀 |
| `//ecd:` 해시 + 관리 파일, 사용자가 수정함 | 건너뜀 (`-Pforce` 로 덮어씀) |
| `Initially generated ... no longer managed` 표식, **한 번도 수정 안 함** (해시 일치) | DSL 에 맞춰 다시 씀 — 예: 엔티티에 `@readonly` 를 추가하면 컨트롤러의 쓰기 엔드포인트가 사라짐 |
| 같은 표식, 사용자가 수정함 | 사용자 것 — 건너뜀. 단 아래 두 가지는 계속 맞춰 줌 |
| `//ecd:` 줄이 없음 | 사용자 것 — 건너뜀 (2.x 와 동일) |

사용자 소유가 된 파일에 대해 생성기가 여전히 해 주는 일:

- **`@behavior` 엔티티의 테이블 어노테이션**: `@Entity`/`@Table`/`@DynamicUpdate`/`@Audited` 등은
  DSL 에 맞춰 교체하고, 사용자가 붙인 다른 어노테이션과 본문은 그대로 둡니다.
  여러 줄로 나눠 쓴 어노테이션은 건드리지 않고 경고합니다.
- **확장 서비스의 생성자**: 코어 서비스 생성자가 바뀌었을 때(예: 헥사고날 전환) 파일에 생성자 외
  코드가 없으면 다시 쓰고, 있으면 새 `super(...)` 를 경고로 알려 줍니다.
- 코어 서비스 생성이 실패한 실행에서는 확장 서비스·컨트롤러를 만들지 않습니다
  (실패한 모양으로 한 번만 생성된 파일이 남지 않도록).
- 보존 블록(`// !---------- ... ------------!`) 안의 코드는 관리 파일이 다시 쓰일 때도 유지됩니다.

### Kotlin 예약어가 든 패키지

헥사고날 관례 패키지 `adapter.in.web` 의 `in` 은 Kotlin 예약어입니다. Kotlin 산출물에서는
패키지·import·정규화 이름의 예약어 조각을 자동으로 `` `in` `` 처럼 감쌉니다(문자열 리터럴 제외).
직접 작성하는 Kotlin 파일에서도 `package com.acme.adapter.`in`.web` 로 써야 합니다.

## 8. 지켜야 할 것

1. **문자열 이어붙이기 금지.** `"@Column(name = \"" + n + "\")"` 대신 `AnnoCode`,
   그 외에는 `String.format` 또는 `Formatted`를 쓰세요.
   `Formatted.text(fmt, args)`는 포맷 인자 개수를 **호출 지점에서** 검증합니다.
2. **주석은 영어로.** `elasticore-base`의 Java 소스에는 **ASCII만** 씁니다 —
   한글 Windows(MS949)에서 컴파일이 깨집니다. `→` 대신 `->`.
3. **파일 입출력은 UTF-8.** `FileWriter`/`FileReader` 금지.
4. **공통 규칙에 언어 분기 금지.** 6절 참조.
5. **새 규칙은 경고를 내되 예외를 던지지 않습니다.** 모델 로딩 중 검증은
   `DSL_WARN`으로 기록합니다.

---

## 9. 검증

규칙을 고쳤으면 **반드시** 아래를 통과시키세요.

```bash
# 1. 단위 테스트
./gradlew :elasticore-project:elasticore-base:test

# 2. 2.x 출력과 같은가 (호환 모드)
./gradlew elcoreRulesCheck -Pstrict

# 3. 생성 -> 컴파일 -> 실행 (Kotlin + TypeScript)
./gradlew :elasticore-project:elasticore-template-kt:elcore3Verify

# 4. 생성된 TypeScript 를 node 로 실행
./gradlew :elasticore-project:elasticore-template-kt:elcore3tsTest
```

> **타입 체크만으로는 부족한 이유가 이 언어에 하나 있습니다.** 소수가 문자열로
> 오므로, 손으로 쓴 `order.totalAmount > 100` 은 컴파일러가 받아들이고 JavaScript 가
> 금액과 무관하게 false 를 답합니다 — 이 언어가 부르는 유일한 결함이 정확히 타입
> 체크가 볼 수 없는 것입니다. 그래서 생성된 명세·불변식은 node 로 **실행**됩니다.

> 검증 태스크는 **생성 트리를 먼저 비웁니다.** generate-once 마커가 붙은 파일은
> 규칙을 고쳐도 재생성되지 않아서, 낡은 파일이 검사되고 통과로 보고될 수 있습니다.
> 실제로 이 프로젝트에서 한 번 그렇게 됐습니다.

**2번이 핵심입니다.** 호환 모드의 출력이 2.x와 한 글자라도 달라지면 실패합니다.
기존 사용자의 생성 코드가 바뀌지 않는다는 보증이 이것입니다.

의도한 차이는 `RulesVerifyRunner.ACCEPTED_DIFFS`에 **이유와 함께** 등록합니다.

### Gradle 플러그인 (권장)

태스크를 손으로 쓰는 대신 플러그인을 적용합니다.

`settings.gradle` (프로젝트당 한 번):

```gradle
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

`build.gradle`:

```gradle
plugins {
    id 'io.elasticore' version '3.0.261022-rc1'
}

elasticore {
    architecture = 'ddd'       // layered | ddd | hexagonal
    language     = 'kotlin'    // java | kotlin | typescript
}
```

`elcore` 와 `elcore3` 태스크가 생기고, `compileJava` 는 생성을 기다립니다.

**`elasticore { }` 블록을 쓰지 않으면 아무것도 달라지지 않습니다.** 규칙
프로퍼티가 하나도 전달되지 않고, 생성기는 원래 가던 길을 그대로 갑니다.
기본값을 `layered` 로 "정해두는" 것과 아예 전달하지 않는 것은 다릅니다.

**아키텍처와 언어는 따로 적을 수 없습니다.** 두 단어가 `RulesConfig` 에서
하나의 번들과 하나의 언어 스위치로 합성되므로, 번들과 스위치가 어긋나는
경우가 생기지 않습니다. 플러그인은 이 표를 갖고 있지 않고 두 단어를 그대로
넘깁니다.

#### 멀티 모듈

루트에 적용하면 `src/main/resources/blueprint` 를 가진 하위 모듈을 자동으로
찾습니다. 손으로 쓰던 태스크와 달리 특정 모듈 이름이 박히지 않습니다.

```gradle
elasticore {
    modules = ['core-domain', 'app-admin']   // 자동 탐색을 덮어쓰고 싶을 때만
}
```

#### 기존 스위치

`-Pdomain=shop`, `-Pmode=valid`, `-Pforce`, `-Pv3`, `-Pkt`, `-Pts` 모두 그대로
전달됩니다. 플러그인을 도입한다고 명령어가 바뀌지 않습니다.

#### TypeScript 클라이언트 (`elcore3ts`, `typescriptClient`)

`src/main/ts` 의 TypeScript 클라이언트는 `elcore3ts` 태스크만 씁니다. `build`·`compileJava` 는 그 태스크를 부르지 않으므로,
모델을 바꾼 뒤 그대로 두면 클라이언트에 새 필드·엔티티가 없습니다. 빌드가 함께 쓰게 하려면:

```gradle
elasticore {
    architecture     = 'hexagonal'
    typescriptClient = true      // 기본 false. elcore3 다음, 컴파일 전에 elcore3ts 를 실행
}
```

이 설정이 없는데 `src/main/ts` 에 생성된 `.ts`(첫 줄 `//ecd:`)가 있고, 그 클라이언트가 지금 모델로 만들어진 것이 아니면
`elcore3` 가 끝에 한 번 경고합니다:
`[elcore3] .../src/main/ts holds a generated TypeScript client made from an older model - run the elcore3ts task after a
model change, or set typescriptClient = true in the elasticore { } block so the build runs it.`
`elcore3ts` 는 만든 모델(블루프린트 도메인 폴더의 `.yml` 전부, 그리고 `architecture`·`language`·`rules` 설정과 ElastiCORE
버전)의 지문을 `build/elasticore/ts-client.model` 에 남기고, `elcore3` 는 그 지문이 지금과 같으면 말하지 않습니다 - 빌드
스크립트의 다른 태스크(`webBuild` 등)가 `elcore3ts` 를 부르는 프로젝트도 바꾼 것이 없으면 조용합니다. `build` 폴더를
지우면 `elcore3ts` 를 다시 돌릴 때까지 `elcore3` 가 매번 알려 줍니다.

`-PlistRules` 는 생성하지 않고, 도메인마다 지금 선택된 규칙을 파일 종류(target)별로 실행 순서대로
보여 줍니다(`suppress` 에 쓰는 id 포함). 파일을 만들거나 지우지 않습니다.

#### 프로젝트의 규칙 더하기

```gradle
elasticore {
    rules = ['com.acme.rules.AcmeDddRules']   // 고른 번들 뒤에 더해짐
}
dependencies {
    elasticore project(':acme-rules')          // 생성기 클래스패스 (애플리케이션에는 안 들어감)
}
```

`elasticore` 설정에 무엇을 더해도 `elasticore-base` 는 빠지지 않고, 여기 선언한 모듈은 생성을 기다리지
않고 먼저 컴파일됩니다. 예제와 테스트 방법은 `elasticore-ddd-customize-guide.md`.

### Apache Ossie 시맨틱 모델 내보내기 (`ossie-export`)

[Apache Ossie](https://github.com/apache/ossie) (incubating) 는 분석·BI·AI 플랫폼 간에 시맨틱 메타데이터 -
dataset(테이블), field(컬럼), relationship(조인), metric(지표) - 를 교환하는 ASF 의 벤더 중립 표준입니다.
ElastiCORE blueprint 는 dataset 쪽이 요구하는 것을 이미 다 말하고 있으므로, 같은 내용을 두 번 선언하지 않고
내보냅니다. **core spec `0.2.0.dev0`** (`core-spec/spec.md`, `ossie-schema.json`) 기준입니다.

켜는 방법 (둘 중 하나):

```gradle
elasticore {
    rules = ['ossie-export']          // 번들은 그대로, 내보내기만 추가
}
```

```yaml
    publisher:
      rules: [hexagonal-springboot, ossie-export]   # env.yml: 번들과 함께 적기
```

생성할 때마다 blueprint 루트에 `semantic_model.yml` 이 다시 만들어집니다 (프로젝트 전체가 **한 모델** -
도메인을 넘는 관계가 끊기지 않게). Pet Store 의 일부:

```yaml
version: 0.2.0.dev0
name: petstore
datasets:
  - name: PurchaseOrder
    source: ps_purchase_order
    primary_key: [order_id]
    description: "주문"
    fields:
      - name: status
        expression:
          dialects:
            - dialect: OSSIE_SQL_2026
              expression: "status"
        datatype: String
        ai_context: "Codes: PLC = Placed, SHP = Shipped, CAN = Cancelled"
      - name: total
        expression: { dialects: [ { dialect: OSSIE_SQL_2026, expression: "total" } ] }
        datatype: Decimal
      - name: city                       # 임베디드 값(deliveryAddress)의 컬럼
        description: "deliveryAddress.city"
    custom_extensions:
      - vendor_name: ELASTICORE
        data: "{\"owner\": \"customerId\", \"access\": {\"read\": \"owner\", ...},
                \"states\": {\"status\": {\"initial\": \"PLACED\", \"ship\": \"PLACED -> SHIPPED\", ...}},
                \"omitted_columns\": [\"credit_card_number\"]}"
relationships:
  - name: PurchaseOrder_customer
    from: PurchaseOrder
    to: Customer
    from_columns: [customer_id]
    to_columns: [customer_id]
```

**DSL → Ossie 대응**

| DSL | Ossie | 비고 |
|---|---|---|
| 엔티티 (`@abstract`·`@embeddable` 제외) | `datasets[]` | `source` = 테이블 (`@table` 또는 `table.prefix` + 이름) |
| `-- 라벨` | `description` | |
| `@id` 필드 (복합 키 포함) | `primary_key` | |
| `@unique`, `@Index(unique="true")` | `unique_keys` | |
| 값 필드 | `fields[]` + `expression`(컬럼, `OSSIE_SQL_2026`) + `datatype` | 타입 대응은 DSL 가이드 "값 타입 한눈에" 표 |
| `@formula(sql)` | `expression` = 그 SQL | 계산 컬럼. 식이면 `OSSIE_SQL_2026`, 서브쿼리(SELECT)가 들어 있으면 `ANSI_SQL` 항목 하나 - 아래 "dialect" |
| `@extend(Parent)` 의 필드 | 자식 dataset 의 `fields` | 상속된 컬럼도 그 테이블에 있으므로 |
| `@Embedded` 값 객체 | 그 컬럼들 (`deliveryAddress.city`) | `@embedded(prefix)` 면 `prefix_city` |
| enum 필드 | `datatype: String` + `ai_context: "Codes: PLC = Placed, ..."` | 테이블에는 코드만 있으므로 뜻을 함께 |
| 다른 엔티티 참조 | `relationships[]` + 조인 컬럼 필드 | 실제 조인 컬럼 (`referenceColumn: short` 면 `customer_id`) |
| `@password`, `@writeonly` | **내보내지 않음** | 애플리케이션도 읽어 주지 않는 값. `omitted_columns` 에 기록 |
| `states:`, `@access`, `@owner`, `@event` | `custom_extensions` (`ELASTICORE`) | Ossie 에 해당 어휘가 없음 |
| `@compute`, `@snapshot`, `money`/`quantity` | 필드의 `custom_extensions` | 파생 규칙과 값 타입 |
| `metrics:` 의 지표 | `metrics[]` (`OSSIE_SQL_2026`, `datatype`, `description`) | 선언한 것만. 문법은 DSL 가이드 "업무 지표" |
| `@terms(…)` (엔티티·필드·지표) | `ai_context.synonyms` | 업무 용어 |

**dialect**: 스펙이 등록한 dialect 이름은 열두 개지만 의미를 정의한 것은 `OSSIE_SQL_2026` 하나입니다
(`core-spec/expression_language.md` 의 이식 가능한 식 언어 - 컬럼 참조, 산술·비교·논리 연산, `CASE WHEN`, 집계,
`COALESCE`·`NULLIF`·`CAST` 같은 함수). `ANSI_SQL` 을 비롯한 나머지는 벤더 언어 이름이라 소비자가 **무시해도 되는** 항목입니다.
그래서 컬럼 이름인 필드 식과 `metrics:` 에서 만든 지표 식은 모두 `OSSIE_SQL_2026` 으로 내보냅니다. `@formula` 는 식에 따라
갈립니다: `unit_price * quantity` 처럼 그 언어 안의 식이면 `OSSIE_SQL_2026`, `(select count(*) from ...)` 처럼 서브쿼리가
들어 있으면 그 언어로 쓸 수 없으므로(스펙 "Not Supported in Expressions": SELECT/FROM/JOIN, 서브쿼리, CTE) `ANSI_SQL`
항목 하나만 내보냅니다 - 읽는 쪽이 그 SQL 을 이해하면 쓰고, 아니면 건너뜁니다. `@formula` 필드는 `custom_extensions` 에
`"formula": "database"` 가 붙어 데이터베이스가 계산하는 컬럼임을 알립니다.

**내보내지 않는 것**: 선언하지 않은 지표 - 엔티티 필드에서 매출 공식을 지어내는 것은 추측을 내보내는 일입니다.
`is_time` 은 쓰지 않습니다 - 스펙이 `datatype`(Date/Time/DateTime/DateTimeTz)
에서 기본값을 정하고, 그 답이 이 모델의 답입니다.

**검증**: `elasticore-sample-petstore` 와 `elasticore-template-ddd` 의 `SemanticModelTest` 가 내보낸 모든
테이블·컬럼·기본 키·조인 컬럼이 실제 DB 에 있는지 빌드마다 확인합니다. 두 파일을 Ossie 의 `ossie-schema.json` 으로
검증한 결과는 오류 0 이었습니다 (2026-10-03, 직접 실행 - 빌드 테스트에는 없음). 이전 출력(`semantic_model:` 배열, `expression` 없음, `money` → String, enum·임베디드 컬럼
누락, 2.x 식 조인 컬럼, 비밀번호 컬럼 포함)은 스펙에 맞지 않았습니다 - 3.0.260925 에서 고쳤습니다.

내보내기 실패는 빌드가 아니라 내보내기만 저하시킵니다. schema.yml 과 같은 성격의 사이드카로, 항상
재생성되며 손으로 편집하지 않습니다. 스펙이 아직 `dev` 버전이라 바뀔 수 있습니다.

### 플러그인 없이 실행하기 (수동 구성)

**`publisher.rules`는 규칙엔진이 읽습니다. 규칙엔진은 `@ElastiCore` 애노테이션
프로세서와 별개의 진입점이고, 일반 빌드는 이 설정을 보지 않습니다.** 번들을
적어두기만 하면 파일은 설정된 것처럼 보이고, 빌드는 그것을 무시하며, 아무 경고도
나오지 않습니다 — 가능한 세 상태 중 가장 나쁜 것입니다.

**플러그인이 이 태스크를 자동으로 등록하므로, 플러그인을 적용했다면 이 절은
필요 없습니다.** 플러그인을 쓸 수 없는 환경(사내 저장소 접근 불가 등)에서만
수동으로 구성하십시오:

```gradle
task elcore3(type: JavaExec) {
    group = 'elasticore'
    classpath = configurations.annotationProcessor
    mainClass = 'io.elasticore.base.model.pub.rules.verify.RulesGenRunner'
    systemProperty 'elasticore.base.path', "$projectDir"
    systemProperty 'elasticore.rules.v3', 'true'
}
```

```bash
./gradlew elcore3 && ./gradlew build
```

> **애플리케이션 클래스에 `@ElastiCore`를 붙이지 마세요.** 두 경로는 같은 파일을
> 서로 다른 규칙으로 생성하므로, 번들을 쓰는 프로젝트는 태스크만 사용합니다.
> `elasticore-template-ddd` 모듈이 정확히 이 구성이며 — `@ElastiCore`가
> 한 군데도 없습니다.

도메인 초기화 마법사는 레이어드가 아닌 아키텍처를 선택하면 이 태스크를 그대로
출력합니다.

### 생성해보기 (이 저장소의 샘플 모듈에서)

```bash
./gradlew elcore3                 # 호환 모드 (2.x와 동일한 출력)
./gradlew elcore3 -Pv3            # 정리 모드
./gradlew elcore3 -Pkt            # Kotlin (src/main/kotlin 에 .kt)
./gradlew elcore3 -Pts            # TypeScript (src/main/ts 에 .ts) - 플러그인에서는 elcore3ts, 빌드와 함께는 typescriptClient = true
./gradlew elcore3 -Pdomain=shop   # 도메인 한정
./gradlew elcore3 -Pforce         # 수정된 파일도 덮어쓰기
```

> **언어와 규칙 선택은 서로 다른 스위치입니다.**
> env.yml의 `publisher.rules`는 *어떤 규칙을 쓸지*만 정하고,
> *어느 트리에 어떤 확장자로 쓸지*와 *어느 스켈레톤을 쓸지*는 `-Pkt`가 정합니다.
> `publisher.rules: [jpa-springboot-kt]`만 적어서는 `.kt` 파일이 나오지 않습니다.

> `elasticore-template`의 생성 소스는 **골든 파일**입니다.
> `-Pv3`/`-Pforce`로 덮어썼다면 되돌리세요.

---

## 10. 체크리스트

### 새 규칙을 추가할 때

- [ ] `@Rule(value = ...)` id가 유일한가
- [ ] `target`이 맞는가
- [ ] `order`/`phase`가 의도한 시점인가 (셸 규칙은 `TAIL`)
- [ ] 문자열을 이어붙이지 않았는가
- [ ] 언어 분기가 없는가 — 필요하면 `Syntax`에 추가
- [ ] `RuleCatalog.standard()`에 등록하고 번들에 넣었는가
- [ ] 위 3가지 검증을 통과하는가

### 새 아티팩트를 추가할 때

- [ ] `.tmpl` 스켈레톤 (필요한 템플릿 세트마다)
- [ ] 규칙 클래스 — 결정은 `base/`, 파일 셸은 `lang/<언어>/`
- [ ] `RuleCatalog`에 등록 + 번들
- [ ] `RulesGenRunner`에 emit 추가 (`ArtifactTarget`)

### 새 아티팩트 종류를 추가할 때 (확장점)

러너는 새 파일 종류의 이름을 배우지 않습니다. 룰셋이 스스로 선언합니다.

| 대상 | 인터페이스 | 답하는 것 |
|---|---|---|
| 엔티티마다 하나 | `ArtifactContributor` | `artifactsFor(EntitySpec)` |
| 도메인마다 하나 | `DomainArtifactContributor` | `artifactsFor(DomainSpec)` |

```java
public List<ContributedArtifact> artifactsFor(EntitySpec e) {
    if (!e.aggregateRoot()) return Collections.emptyList();   // 조건은 코드입니다

    return Collections.singletonList(
            ContributedArtifact.of("event", "event", e.getName() + "PlacedEvent")
                    .orNamespace("entity"));                  // 대체 네임스페이스
}
```

지켜야 할 것:

- **`orNamespace(...)` 를 주세요.** 새 아티팩트는 새 자리와 함께 오는데, 기존
  `env.yml` 은 그 키가 생기기 전에 쓰였습니다. 대체가 없으면 프로젝트가
  네임스페이스를 먼저 고쳐야 하거나 `null` 패키지가 나옵니다.
- **한 target 으로 여러 개를 선언할 수 있습니다.** 그럴 때 규칙은
  `SourceCode.artifactName()` 으로 지금 무엇을 쓰는지 압니다. 이걸 읽지 않으면
  전부 같은 내용이 나오고 마지막 것만 남습니다.
- **조건은 코드입니다.** 적용되지 않으면 빈 목록을 답하세요. 조건 언어는 없습니다 —
  spec 이 이미 말할 수 있는 것을 두 번 표현하게 될 뿐입니다.
- **언어를 가정하지 마세요.** 파일이 놓일 트리와 확장자는 실행이 정합니다. 규칙은
  `requireSyntaxAnyOf(...)` 로 자기가 쓸 수 있는 언어를 말하고, 나머지는 러너의
  일입니다.
- **스켈레톤은 언어마다 필요합니다.** `v3/<target>.tmpl` 과 `kt/<target>.tmpl` 이
  없으면 그 언어에서는 실패합니다 — 조용히 Java 를 쓰는 대신에.

### 새 언어를 추가할 때

- [ ] `Syntax`에 항목 추가
- [ ] `SourceCode.TemplateSet`에 항목 추가
- [ ] `resources/elasticore-template/<언어>/` 스켈레톤
- [ ] `lang/<언어>/` 파일 셸 규칙 (결정 규칙은 `base/`에서 그대로 공유)
- [ ] 번들 등록
- [ ] **생성물을 실제로 컴파일하는 검증 모듈** —
      `elasticore-template-kt`가 그 예입니다. 문자열 비교만으로는
      "그 언어처럼 보이지만 컴파일 안 되는" 출력을 잡지 못합니다

---

## 11. 패키지 구조

```
pub/rules/
    Rule.java, CodeRules.java, RuleEngine.java, RuleCatalog.java, RulesConfig.java
    EntitySpec, FieldSpec, DomainSpec, EnumSpec, ProtoServiceSpec
    SourceCode, FieldCode, AnnoCode, Syntax, Formatted
    base/          # 언어 중립 규칙 + 공유 헬퍼
    lang/java/     # Java 파일 셸
    lang/kotlin/   # Kotlin 파일 셸
    verify/        # elcoreRulesCheck, elcore3 실행기
```

**새 언어를 추가할 때 다시 써야 하는 것은 파일 셸뿐입니다.**
어떤 필드에 무엇이 붙는지 결정하는 규칙은 `base/`에서 그대로 공유됩니다.
