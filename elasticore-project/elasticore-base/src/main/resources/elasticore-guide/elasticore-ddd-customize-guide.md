# ElastiCORE DDD 룰셋 커스터마이즈 가이드 (심화)

> **규칙을 처음 만든다면 `elasticore-rule-dev-guide.md`부터 보세요.** 그 문서만으로 어노테이션 붙이기·빼기, 우리만의 YAML 표시, 빌드 검사, 테스트까지 할 수 있습니다.
> 이 문서는 그다음 단계(새 파일 만들기, 끄기, 팀 설정, 내부 동작)를 다룹니다.

DDD(헥사고날) 번들이 만들어 주는 코드에 **우리 팀의 규칙을 더하는** 방법입니다.
생성된 파일을 하나씩 손으로 고치는 대신 생성 규칙을 추가해서, 모든 도메인·모든 엔티티에 같은 방식으로 적용되게 합니다.

**읽기 전에**: "ElastiCORE 헥사고날 ERP 가이드"의 예제(Partner·Item·SalesOrder·Stock·Invoice)를 따라 해 본 상태를 가정합니다.
이 문서의 예제는 모두 그 프로젝트에 더하는 방식이고, 이 문서에 나오는 코드만으로 프로젝트를 만들어 빌드·테스트까지 확인했습니다.
Java 기준으로 쓰고, Kotlin에서 다른 점은 10장에 모았습니다.

| 이 문서 | 다른 문서 |
|---|---|
| 우리 규칙을 **만들어 더하는** 방법 (예제 중심) | 규칙 API 전체 목록: `elasticore-rule-guide.md` 3~6장 |
| | 번들 고르기·기본 규칙 설명: `elasticore-rule-guide.md` 2장 |

---

## 0. 다 하고 나면

ERP 프로젝트에 규칙 모듈(`acme-rules`)이 하나 생기고, `./gradlew build` 할 때마다 다음이 자동으로 됩니다.

| 예제 | DSL·설정에 쓰는 것 | 결과 |
|---|---|---|
| 1. 끄기 (4장) | `env.yml`에 `suppress: [...]` | 원하지 않는 생성물이 사라짐 (방법만 보고, ERP에는 적용하지 않음) |
| 2. 역할 표시 (5장) | 없음 (모델에서 읽음) | 루트에 `@AggregateRoot`, 멤버에 `@Entity`, 값 객체에 `@ValueObject`, 식별자 필드에 `@Identity` (jMolecules) |
| 3. 새 DSL 단어 + 새 파일 (6장) | 루트 meta에 `@topic(erp.sales)` | `ErpEventTopics` 클래스: 이벤트마다 메시지 토픽 이름 |
| 4. 모델 검사 (7장) | `env.yml`에 `acme.requireTopic: true` | 규칙 위반 시 경고, 또는 빌드 실패 |

---

## 1. 규칙이 코드를 만드는 방식

```
DSL(yml) ──▶ Spec ──▶ 규칙들 ──▶ 스켈레톤(.tmpl) ──▶ 파일
           (모델)    (판단)      (파일 뼈대)
```

| 용어 | 뜻 | 예 |
|---|---|---|
| **도메인** | `blueprint/` 아래 폴더 하나 (그 안의 `env.yml` + `model/*.yml`). DDD의 "도메인"과 같은 단어지만 여기서는 이 폴더 단위 | `blueprint/erp` → 도메인 `erp` |
| **Spec** | 규칙이 읽는 모델. 엔티티는 `EntitySpec`, 필드는 `FieldSpec`, 도메인 전체는 `DomainSpec` | `e.aggregateRoot()`, `e.events()` |
| **규칙(rule)** | `@Rule`이 붙은 메서드 하나. 모델을 읽고 "무엇을 쓸지"만 정함 | "루트면 `@AggregateRoot`를 붙인다" |
| **룰셋** | 규칙 메서드를 모은 클래스 (`CodeRules` 구현) | `AcmeDddRules` |
| **번들** | 룰셋 여러 개의 묶음. 빌드의 `architecture`·`language`가 정함 | `hexagonal-springboot` |
| **target** | 규칙이 기여하는 **파일 종류**의 이름 | `entity`, `behavior`, `dto` … (아래 표) |
| **스켈레톤** | 파일의 뼈대. `${packageName}` 같은 빈칸을 규칙이 채움 | `elasticore-template/v3/eventTopics.tmpl` |

규칙은 파일을 직접 쓰지 않습니다. "이 클래스에 이 어노테이션", "이 빈칸에 이 값"을 말하면, import 정리와 파일 쓰기는 생성기가 합니다.
그래서 같은 규칙이 Java와 Kotlin 모두에 적용되고, 여러 규칙이 한 파일에 함께 기여할 수 있습니다.

### 파일 종류(target) — 헥사고날 ERP에서

생성 로그(`./gradlew elcore3`)의 각 줄 첫 단어가 target입니다: `entity  src/main/java/.../Partner.java  WROTE`.

| target | 예 (ERP) | 누구 것 | 클래스 어노테이션을 그리는 규칙 |
|---|---|---|---|
| `entity` | `Partner.java`, 나뉜 루트는 `SalesOrderBase.java` | 생성기 | `v3-file-vars` (tail 90) |
| `behavior` | `SalesOrder.java` (나뉜 루트의 개발자 클래스) | **개발자** | `ddd-behavior-entity` (head 10) |
| `dto`, `srchdto` | `SalesOrderDTO.java`, `PartnerSrchDTO.java` | 생성기 | `v3-dto-file`, `v3-srchdto-file` (tail 90) |
| `event` | `SalesOrderShippedEvent.java` | 생성기 | `ddd-event` (head 10) |
| `repositoryPort`, `queryPort`, `usecase` | `PartnerRepositoryPort.java`, `PartnerQueryPort.java`, `PartnerUseCase.java` | 생성기 | `ddd-repository-port`, `ddd-query-port`, `hex-usecase` (head 10) |
| `domainServiceBase`, `processManagerBase` | `ShippingDomainServiceBase.java`, `BillingProcessManagerBase.java` | 생성기 (포트, 리스너, 추상 `handle`) | `ddd-domain-service-base`, `ddd-process-manager-base` (10 — 도메인 단위) |
| `domainService`, `processManager` | `ShippingDomainService.java`, `BillingProcessManager.java` | **개발자** | `ddd-domain-service`, `ddd-process-manager` (once 10 — 도메인 단위) |
| `service` / `service_extend` | `PartnerCoreService.java` / `PartnerService.java` | 생성기 / **개발자** | 없음 |
| `control` | `PartnerController.java` | **개발자** | 없음 |
| `invariants`, `removalCheck`, `factory`, `specifications`, `repositoryAdapter` | `ItemInvariants.java`, `PartnerRepositoryAdapter.java` … | 생성기 (`factory`는 **개발자**) | `ddd-invariant`, `ddd-invariant-removal`, `ddd-factory`, `ddd-specification`, `ddd-repository-adapter` (head 10) |
| `mapper`, `repository`, `repositoryHelper`, `outbox…` | `ErpMapper.java`, `PartnerRepository.java` … | 생성기 | 없음 |

"없음"인 파일에는 클래스 어노테이션을 붙일 수 없습니다(붙여도 아무 일도 일어나지 않음).
"once"인 파일은 엔티티가 아니라 **도메인 단위**로 만들어집니다. 그 파일의 규칙은 `(DomainSpec d)`를 받고, 여러 개 중 어느 파일인지는 `ctx(d).artifactName()`으로 압니다 (`(EntitySpec e)`로 쓰면 돌지 않고 경고가 나옵니다). 전체 target 목록은 `./gradlew elcore3 -PlistRules`(3장)에 나옵니다.
Kotlin에서는 `entity`·`dto`·`srchdto`를 `kt-entity-file`·`kt-dto-file`·`kt-srchdto-file` (tail 90)이 그립니다.

### 루트와 멤버

`e.aggregateRoot()`는 "멤버도 값 객체도 아니다"라는 뜻입니다.

| 엔티티 | 판단 (위에서부터 먼저 맞는 것) | ERP 예 |
|---|---|---|
| 루트 | 자기에게 `@root`(또는 값 없는 `@aggregate`)를 붙였음 | ERP의 다섯 루트 모두 (`Partner`, `Item`, `SalesOrder`, `Stock`, `Invoice`) |
| 멤버 | 자기에게 `@aggregate(X)`를 붙였음 | |
| 멤버 | 다른 엔티티가 `@cascade`가 붙은 관계로 가지고 있음 | |
| 멤버 | `@root`를 붙인 엔티티 **하나만** `List<X> @OneToMany`로 가지고 있음 (둘 이상이면 판단하지 않음 → `@aggregate(X)`로 정하기) | `SalesOrderLine` |
| 값 객체 | `@embeddable` | (없음) |
| 루트 | 그 밖의 모든 엔티티 (`@root`를 안 붙였어도) | |

그래서 `@root`가 아닌 엔티티가 `List<X> @OneToMany`를 가져도 `X`는 멤버가 되지 않습니다. 의도와 다르면 가진 쪽에 `@root`를 붙이거나 `X`에 `@aggregate(...)`를 붙이세요.

---

## 2. 무엇을 어떻게 바꾸나

| 하고 싶은 것 | 방법 | 예제 |
|---|---|---|
| 기본 생성물 일부를 빼기 | `env.yml`의 `suppress` | 4장 |
| 생성되는 클래스·필드에 무언가 더하기 | 기존 target에 규칙 추가 | 5장 |
| DSL에 우리만의 단어를 만들기 | 규칙이 `e.metaValue("단어")`, `f.dslValue("단어")`로 읽음 | 6장 |
| 새로운 파일 만들기 | `ArtifactContributor`(엔티티마다) / `DomainArtifactContributor`(도메인마다) + 스켈레톤 | 6장 |
| 팀 설정값을 규칙에 주기 | `env.yml`의 `config:` 아래에 적고 `e.config("키")`로 읽음 | 7장 |
| 모델이 팀 규칙을 지키는지 검사 | `ModelCheck.warn` / `ModelCheck.fail` | 7장 |
| 기본 스켈레톤(.tmpl)을 통째로 바꾸기 | **하지 마세요.** 버전이 오를 때마다 따라가야 합니다. 끄고(4장) 새 파일을 만드세요(6장) | - |

생성된 파일을 손으로 고치면 어떻게 되나:

| 파일 | 손으로 고치면 |
|---|---|
| 생성기 것 (`entity`, `dto` …) | 다음 생성 때 **건너뛰고 알려 줍니다** (`-Pforce`로 덮어씀). 고친 내용은 DSL과 어긋난 채 남으므로, 바꾸고 싶은 것은 규칙으로 |
| 개발자 것 (`behavior`, `control`, `service_extend` …) | 그대로 둡니다. 한 번도 고치지 않은 동안에는 DSL에 맞춰 다시 써 줍니다 |

---

## 3. 준비: 규칙 모듈 만들기

규칙은 **별도 모듈**에 둡니다. 생성기는 애플리케이션을 컴파일하기 **전에** 돌기 때문에, 애플리케이션 소스 안에 둔 규칙은 생성기가 볼 수 없습니다.

```
erp/
├── settings.gradle
├── build.gradle                 ← 규칙 모듈을 생성기에 연결
├── acme-rules/                  ← 새 모듈: 우리 규칙
│   ├── build.gradle
│   └── src/
│       ├── main/java/com/acme/rules/AcmeDddRules.java
│       ├── main/resources/elasticore-template/v3/eventTopics.tmpl   ← Java 스켈레톤
│       ├── main/resources/elasticore-template/kt/eventTopics.tmpl   ← Kotlin 스켈레톤
│       ├── test/java/com/acme/rules/AcmeDddRulesTest.java
│       ├── test/java/com/acme/rules/NoDynamicUpdateRules.java       ← 4장 예제
│       ├── test/java/com/acme/rules/PerRootExampleRules.java        ← 6.7 예제
│       └── test/resources/elasticore-template/v3/rootEvents.tmpl    ← 6.7 예제
└── src/ …                       ← 기존 ERP 애플리케이션
```

**`settings.gradle`** 에 한 줄 추가:

```gradle
include 'acme-rules'
```

**파일: `acme-rules/build.gradle`**

```gradle
plugins {
    id 'java-library'
}

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

repositories {
    mavenCentral()
}

dependencies {
    // The generator runs these rules; it is not part of the application.
    compileOnly 'io.elasticore:elasticore-base:3.0.261030-rc5'

    testImplementation 'io.elasticore:elasticore-base:3.0.261030-rc5'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.2'
}

tasks.named('test') { useJUnitPlatform() }
```

`elasticore-base` 버전은 루트 `build.gradle`의 `id 'io.elasticore' version '...'`과 **같게** 적습니다. 규칙이 컴파일된 API와 생성기가 다르면 실행 중에 메서드를 못 찾습니다.

룰셋 클래스는 5~7장에서 채웁니다. 지금은 빈 틀만 만들어 두면 이후 단계마다 빌드해 볼 수 있습니다.

**파일: `acme-rules/src/main/java/com/acme/rules/AcmeDddRules.java`** (빈 틀 — 완성본은 부록 A)

```java
package com.acme.rules;

import io.elasticore.base.model.pub.rules.CodeRules;

/** ACME's additions to the DDD rules. Filled in chapters 5-7. */
public class AcmeDddRules implements CodeRules {
}
```

**루트 `build.gradle`** 의 두 곳을 바꿉니다:

```gradle
elasticore {
    architecture = 'hexagonal'
    language     = 'java'
    rules        = ['com.acme.rules.AcmeDddRules']     // 우리 규칙 (기본 번들에 더해짐)
}

dependencies {
    elasticore project(':acme-rules')                     // 생성기가 우리 규칙을 찾는 곳
    implementation 'org.jmolecules:jmolecules-ddd:1.10.0'  // 5장이 붙이는 어노테이션
    // ... 기존 의존성 그대로
}
```

| 줄 | 뜻 |
|---|---|
| `rules = [...]` | 룰셋 클래스의 전체 이름. 아키텍처·언어가 고른 번들 **뒤에 더해집니다** (번들을 다시 적을 필요 없음). `architecture`와 함께 쓰세요 — 없으면 2.x 호환 모드로 생성되어 DDD 규칙과 `v3/` 스켈레톤이 쓰이지 않습니다 |
| `elasticore project(':acme-rules')` | `elasticore`는 **생성기의 클래스패스**입니다. 애플리케이션 실행에는 들어가지 않습니다. 이 모듈은 생성보다 먼저 컴파일됩니다 |

> **`env.yml`의 `elasticore.config.publisher.rules`와의 차이**: `env.yml`에 `rules: [...]`를 쓰면 번들을 **대체**합니다.
> 그때는 `rules: [hexagonal-springboot, com.acme.rules.AcmeDddRules]`처럼 번들도 적어야 하고, Kotlin이면 `hexagonal-springboot-kt`를 적어야 합니다.
> 모든 도메인에 적용할 규칙은 빌드의 `rules`가 간단하고 틀릴 곳이 없습니다. 특정 도메인에만 적용할 때만 `env.yml`을 쓰세요.

### 지금 어떤 규칙이 도는지 보기: `-PlistRules`

```bash
./gradlew elcore3 -PlistRules
```

5~7장을 마친 뒤에는 이렇게 나옵니다 (지금은 `acme-` 줄이 없습니다):

```
[rules] erp: hexagonal-springboot + com.acme.rules.AcmeDddRules
  entity
        3 head  ddd-reference-context                DddReferenceRules
        5 head  v3-class-annotations                 JpaEntityAnnotationRules
       …
        0 head  acme-model-check                     AcmeDddRules
        0 head  acme-role                            AcmeDddRules
        5 field jpa-field-desc                       JpaEntityBaseRules
       …
        0 field acme-identity                        AcmeDddRules
       …
       90 tail  v3-file-vars                         JpaEntityFileRules
```

| 칸 | 뜻 |
|---|---|
| `behavior`, `entity` … | target (파일 종류) |
| `5`, `10` … | `order`. 같은 단계 안에서 작은 것부터 |
| `head` / `field` / `tail` | 단계. 파일마다 `head` 규칙 → 필드마다 `field` 규칙 → `tail` 규칙 순으로 돕니다 |
| `once` | 도메인·enum·port 단위 규칙. 단계 없이 `order` 순으로 한 번 |
| `acme-role` | 규칙 id. `suppress`에 쓰는 이름 |
| `AcmeDddRules` | 규칙이 든 클래스 |

이 명령은 파일을 만들거나 지우지 않고, 도메인마다 지금 선택된 규칙을 **실제로 도는 순서대로** 보여 줍니다.
우리 규칙(`acme-…`)은 각 단계에서 ElastiCORE 규칙이 모두 끝난 **뒤에** 돕니다. 그래서 `order`를 적지 않아도 되고, 우리 규칙끼리의 순서가 필요할 때만 씁니다.

---

## 4. 예제 1 — 기본 생성물 끄기

회사에 공통 오류 처리기(`@RestControllerAdvice`)가 이미 있어서, 도메인마다 생기는 `ErpApiErrors`가 필요 없다고 합시다.

> **이 장은 방법만 보세요. ERP 예제에는 적용하지 마세요.** ERP에는 공통 오류 처리기가 없어서, 끄면 404·409·422 응답이 모두 500이 됩니다.

1. `./gradlew elcore3 -PlistRules`에서 그 파일(target `apiErrors`)을 만드는 규칙을 찾습니다:
   ```
     apiErrors
          10 once  spring-api-errors                    ApiErrorRules
   ```
2. `env.yml`의 `config:` 아래에 적습니다:
   ```yaml
   elasticore:
     config:
       publisher:
         suppress: [spring-api-errors]
   ```
3. 이미 만들어진 `ErpApiErrors.java`는 개발자 것이라 지워지지 않습니다. 필요 없으면 직접 지우세요.

모든 도메인에서 끄려면 도메인마다 적지 않고 빌드에 한 번 적습니다 (각 `env.yml`의 `suppress`와 합쳐짐):

```gradle
elasticore {
    suppress = ['spring-api-errors']
}
```

- 한 파일 종류의 규칙을 **모두** 끄면 그 파일은 만들어지지 않습니다 (생성 로그에 `SKIP(dontMake)`).
- 일부만 끄면 그 부분만 빠집니다. 예: `jpa-comment`를 끄면 엔티티 필드의 `@Comment`만 사라집니다.
- **동작이 바뀝니다.** `ErpApiErrors`는 "없는 대상 404, 중복·지금 상태와 충돌 409, 업무 규칙 위반 422"를 정하는 파일입니다. 끄면 그 응답은 회사 처리기가 정해야 합니다(없으면 500).
- 컨트롤러에 닿기 전의 거절(JSON 이 아닌 본문, 타입이 틀린 값, 없는 경로·메서드 - `code` 가 있는 같은 모양)도 함께 빠집니다. 그 응답을 맡는 런타임의 `RequestShapeErrors` 는 `ErpApiErrors` 가 `@Import` 해서 생기기 때문입니다. 회사 처리기에서도 쓰려면 그 처리기에 `@Import(io.elasticore.springboot3.validation.RequestShapeErrors.class)` 한 줄을 붙이세요(회사 처리기에 `@Order` 를 주면 그쪽이 먼저입니다). 생성된 컨트롤러의 `findById`·`delete` 가 없는 행에 던지는 `NotFoundException` 은 `@ResponseStatus(404)` 라서 끄더라도 404 입니다.

> **끄면 안 되는 것**: 파일의 뼈대를 채우는 규칙 — `${packageName}`, `${className}` 같은 빈칸을 채우는 규칙(1장 표의 마지막 칸에 있는 규칙, 그리고 `controller-vars`, `v3-service-vars` 같은 `*-vars`, `*-file`) — 을 일부만 끄면 빈칸이 비어 파일이 깨집니다.
> `suppress`는 "무언가를 더 붙이는" 규칙을 끄거나, 파일 하나의 규칙을 모두 꺼서 통째로 뺄 때 쓰세요.

### 규칙이 하는 일 중 하나만 빼기

`suppress`는 규칙 단위입니다. 한 규칙이 여러 가지를 하는데 **하나만** 빼려면, 우리 규칙이 그 뒤에 지웁니다.
예: 엔티티마다 붙는 `@DynamicUpdate`만 빼기 (`v3-class-annotations`는 여러 어노테이션을 붙이므로 통째로 끌 수 없음):

**파일: `acme-rules/src/test/java/com/acme/rules/NoDynamicUpdateRules.java`** (예제라 테스트에만 둠. 실제로 쓰려면 `src/main/java`로 옮기고 빌드의 `rules` 목록에 추가)

```java
package com.acme.rules;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/** Takes an annotation the built-in rules put on every entity back off. */
public class NoDynamicUpdateRules implements CodeRules {

    /** Our rules run after ElastiCORE's, so what they take off stays off - on a split root's classes too. */
    @Rule("acme-no-dynamic-update")
    void noDynamicUpdate(EntitySpec e) {
        ctx(e).removeClassAnnotation("org.hibernate.annotations.DynamicUpdate");
    }
}
```

`c.removeClassAnnotation(...)`, `c.removeField(f)`, `c.field(f).removeAnnotation(...)`이 같은 방식입니다. 우리 규칙은 기본 규칙 뒤에 돌므로 뺀 것이 다시 붙지 않고, 나뉜 루트의 개발자 클래스(`SalesOrder.java`)에서도 빠집니다.
(`@DynamicUpdate`는 DSL로도 뺄 수 있습니다: 엔티티 meta에 `@dynamicupdate(false)`.)

---

## 5. 예제 2 — 모든 클래스에 DDD 역할 표시하기 (jMolecules)

[jMolecules](https://github.com/xmolecules/jmolecules)는 "이 클래스는 애그리거트 루트다" 같은 DDD 역할을 어노테이션으로 표시하는 라이브러리입니다.
ArchUnit·IDE 같은 도구가 이 표시를 읽고 경계 위반을 찾아 줍니다.
ElastiCORE 모델은 역할을 이미 알고 있으므로(`@root`, 멤버, `@embeddable`, `@id`) 사람이 손으로 붙일 필요가 없습니다.

아래는 `AcmeDddRules.java`의 1번 부분입니다 (전체 파일은 6.6).

3장의 빈 틀에 아래를 채웁니다(import는 부록 A 참고. IDE가 채워 줍니다).

```java
public class AcmeDddRules implements CodeRules {      // DomainArtifactContributor 는 6.3에서 추가

    private static final String JMOLECULES = "org.jmolecules.ddd.annotation.%s";

    /** Each entity class says its DDD role. A root split in two gets it on both of its classes. */
    @Rule("acme-role")
    void role(EntitySpec e) {
        if (e.hasMeta("abstract")) return;   // a base class for others, not a role of its own
        SourceCode c = ctx(e);
        if (e.hasMeta("embeddable")) {
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "ValueObject"));
        } else if (e.aggregateRoot()) {
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "AggregateRoot"));
        } else {
            // Same simple name as jakarta.persistence.Entity: written in full.
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "Entity").qualified());
        }
    }

    /** A field rule: called once per field of each entity. */
    @Rule("acme-identity")
    void identity(EntitySpec e, FieldSpec f) {
        if (!f.hasDslAny("id", "pk")) return;
        ctx(e).field(f).ensure(AnnoCode.of(JMOLECULES, "Identity"));
    }
```

| 코드 | 뜻 |
|---|---|
| `@Rule("acme-role")` | 엔티티 파일을 만들 때 실행(`target`을 적지 않으면 `"entity"`). 괄호 안은 id — 우리 규칙은 `acme-`로 시작하게 지어 기본 규칙과 겹치지 않게 합니다 |
| 메서드의 **파라미터** | 언제 불리는지를 정합니다. `(EntitySpec e)`는 파일마다 한 번, `(EntitySpec e, FieldSpec f)`는 **필드마다** 한 번 (`acme-identity`) |
| `ctx(e)` | 지금 만들고 있는 파일(`SourceCode`) |
| `ensureClassAnnotation(...)` | 클래스에 어노테이션. 이미 있으면 두 번 붙이지 않음. **import는 자동** |
| `ctx(e).field(f).ensure(...)` | 그 필드에 어노테이션 |
| `AnnoCode.of(JMOLECULES, "Entity").qualified()` | `jakarta.persistence.Entity`와 이름이 같아 전체 이름으로 씀 → `@org.jmolecules.ddd.annotation.Entity` |
| `e.aggregateRoot()` | 루트인가 (멤버·값 객체면 false) |
| `e.hasMeta("abstract")` | `@abstract` 엔티티는 다른 엔티티가 상속하는 틀이라 역할이 없음 |

### 두 파일로 나뉜 루트

DDD 번들에서 **`@editable`이 아닌 루트**는 두 파일로 나뉩니다. ERP에서는 `SalesOrder`, `Stock`, `Invoice`가 나뉘고, `@editable`인 `Partner`, `Item`은 한 파일입니다.

| 파일 | target | 누구 것 |
|---|---|---|
| `SalesOrderBase.java` (필드) | `entity` | 생성기 (매번 다시 씀) |
| `SalesOrder.java` (규칙 메서드) | `behavior` | **개발자** |

`target = "entity"`인 우리 규칙은 **두 파일 모두에** 적용됩니다. 규칙을 쓸 때 나뉘었는지 신경 쓰지 않아도 됩니다.
(나뉘었는지 알아야 할 때는 `e.isSplitForBehavior(e.getName())`. 조건은 "`@editable`이 아니고 식별자가 하나인 루트, 또는 `@behavior`를 붙인 엔티티"이고, `@abstract`·`@embeddable`·`@rollup`이거나 `@discriminator` 필드가 있으면 나뉘지 않습니다.)

### 순서(order)는 신경 쓰지 않아도 됩니다

우리 규칙은 각 단계(파일 단위 `head` → 필드 단위 `field` → `tail`)에서 ElastiCORE 규칙이 모두 끝난 **뒤에** 돕니다.
그래서 우리 규칙이 붙이거나 뺀 것은 그대로 남습니다. `order`는 우리 규칙끼리 순서가 필요할 때만 적습니다.

> 1장 표에서 "없음"인 파일에는 붙지 않습니다. 규칙 테스트(9장)에서 렌더링해 보면 바로 확인됩니다.

### 이미 있는 내 파일

`SalesOrder.java`처럼 **개발자 것**인 파일이라도 생성기가 만든 소유 파일(첫 줄 근처에 `// Initially generated by ElastiCORE; no longer managed` 줄이 있는 파일)이면 나중에 추가한 규칙이 닿습니다.
손대지 않았으면 다시 생성되고, 고쳤으면 규칙이 바꾸는 부분이 고친 줄과 겹치지 않을 때 병합됩니다(`MERGED(your edits kept)`, `elasticore-env-guide.md` 9-2). 컨트롤러·서비스도 같습니다.

겹치면 파일을 그대로 두고(`KEPT(yours ...)`) 겹친 곳이 몇 군데인지 알립니다(`the DSL changes this file of yours where you changed it too`) - 그 부분을 손으로 옮깁니다.
병합을 끈 프로젝트(`merge = false`)에서 고친 파일과 병합 전 버전이 만든 파일을 고친 경우(`SKIP(user-owned)`)는 규칙이 닿지 않습니다.

이렇게 남은 파일이 **나뉜 엔티티의 개발자 클래스**면, 빠진 클래스 어노테이션을 따로 알려 줍니다. 예를 들어 새 루트 `Shipment`의 `Shipment.java`가 그렇다면:

```
[elcore3] src/main/java/com/acme/erp/domain/model/Shipment.java: the rules also put these on the class,
which this file does not have - it is yours, so add them by hand if they apply:
    @AggregateRoot
```

이 알림은 엔티티 클래스에만 나옵니다. 컨트롤러·서비스처럼 다른 개발자 파일이 남았을 때 무엇이 빠졌는지는 팀에 공유하세요.

헥사고날 가이드에서 통째로 복사한 `SalesOrder.java`, `Stock.java`, `Invoice.java`는 그 표시 줄이 없어서 알림도 없습니다. 세 파일에 직접 붙이세요:

```java
import org.jmolecules.ddd.annotation.AggregateRoot;

@AggregateRoot
@Entity
@Table(name = "erp_stock", …)
public class Stock extends StockBase {
```

위 `Shipment`처럼 생성기가 만든 파일은 알림을 보고, 이 세 파일은 알림 없이 직접 붙입니다.

---

## 6. 예제 3 — 우리만의 DSL 단어로 새 파일 만들기 (`@topic`)

도메인 이벤트를 Kafka 같은 메시지 브로커로 보낼 때, 이벤트마다 **토픽 이름**이 필요합니다.
이것을 코드 곳곳에 문자열로 흩어 두지 않고, DSL에 한 번 적고 상수 클래스로 생성하겠습니다.

### 6.1 DSL에 쓰기

`src/main/resources/blueprint/erp/model/entity.yml`의 `SalesOrder` meta 끝에 `@topic(erp.sales)`를 더합니다:

```yaml
  SalesOrder:
    meta: entity @root @expose(20) @event(Confirmed(totalAmount), Shipped(orderNo, shipWarehouse, totalAmount), Cancelled) @topic(erp.sales) @Index(name="uk_erp_sales_order_no", columnList="order_no", unique="true")
```

ElastiCORE는 모르는 단어를 오류로 보지 않고 **보관만** 합니다. 생성 코드에는 아무것도 나오지 않고, 규칙이 `e.metaValue("topic")`으로 `erp.sales`를 읽습니다.
필드에 붙인 단어(`bizNo: string(12) @masked`)는 `f.hasDsl("masked")`, `f.dslValue("masked")`로 읽습니다.

> 철자를 틀려도(`@topci`) 오류가 나지 않고 조용히 무시됩니다. 중요한 단어라면 7장처럼 검사 규칙을 두세요.
> ElastiCORE가 이미 쓰는 단어(`@event`, `@search`, `@label` … `elasticore-annotation-guide.md`)와 겹치지 않게 지으세요. 걱정되면 팀 접두사를 붙입니다(예: `@acmeTopic`).

### 6.2 생성될 파일

```java
public final class ErpEventTopics {

    public static final String SALES_ORDER_CONFIRMED = "erp.sales.confirmed";
    public static final String SALES_ORDER_SHIPPED = "erp.sales.shipped";
    public static final String SALES_ORDER_CANCELLED = "erp.sales.cancelled";
    …
    public static String topicOf(String eventType) { … }   // 이벤트 클래스의 전체 이름 → 토픽
}
```

### 6.3 파일을 선언하기 — `DomainArtifactContributor`

> 6.3~6.4의 조각은 설명용입니다. 여기서 쓰는 도우미 메서드(`topicOf`, `className`, `upperSnake`, `kebab`)와 `implements … DomainArtifactContributor`는 부록 A에 있으니,
> 빌드해 볼 때는 6.6처럼 부록 A로 파일을 바꾸세요.

생성기는 새 파일 종류를 **미리 알지 못합니다**. 룰셋이 "이 도메인에는 이런 파일이 하나 있다"고 알려 줘야 합니다.

| 파일이 | 구현할 인터페이스 | 메서드 |
|---|---|---|
| 도메인마다 하나 | `DomainArtifactContributor` | `artifactsFor(DomainSpec d)` |
| 엔티티마다 하나 | `ArtifactContributor` | `artifactsFor(EntitySpec e)` (6.7) |

```java
    @Override
    public List<ContributedArtifact> artifactsFor(DomainSpec d) {
        for (EntitySpec e : d.entities()) {
            if (topicOf(e) != null && !e.events().isEmpty()) {
                return Collections.singletonList(
                        ContributedArtifact.of("eventTopics", "event", className(d)).orNamespace("entity"));
            }
        }
        return Collections.emptyList();
    }
```

`ContributedArtifact.of("eventTopics", "event", "ErpEventTopics")`의 값:

| 값 | 뜻 |
|---|---|
| `"eventTopics"` | **새 target 이름**. 이 파일을 채울 규칙의 `target`, 그리고 스켈레톤 파일 이름(`eventTopics.tmpl`) |
| `"event"` | 파일이 놓일 **패키지를 찾는 키** (`env.yml`의 `namespace`). `namespace: base: com.acme.erp`면 `com.acme.erp.domain.event`. target `event`(이벤트 클래스)와는 다른 것으로, 같은 패키지에 두려고 같은 키를 썼습니다 |
| `"ErpEventTopics"` | 클래스(파일) 이름 |
| `.orNamespace("entity")` | `event` 키가 없는 프로젝트에서 대신 쓸 키 |

패키지 키는 `env.yml`의 `namespace` 아래 이름이면 무엇이든 됩니다. `namespace: base: com.acme.erp`가 만들어 주는 키:

| 키 | 패키지 |
|---|---|
| `entity`, `enumeration` | `…domain.model` |
| `event` | `…domain.event` |
| `domainService`, `domainRepository` | `…domain.service`, `…domain.repository` |
| `service`, `dto` | `…application`, `…application.dto` |
| `inboundPort` / `queryPort`, `port` | `…application.port.in` / `…application.port.out` |
| `repository`, `persistence` | `…infrastructure.persistence` |
| `control` | `…adapter.in.web` |

우리 파일만의 자리가 필요하면 `namespace`에 한 줄 더 쓰고(`messaging: com.acme.erp.infrastructure.messaging`) 그 키를 씁니다. `base`와 함께 써도 됩니다.
만들 것이 없으면 빈 목록을 돌려줍니다. 이 도메인에 `@topic`이 없으면 파일도 없습니다.

### 6.4 빈칸 채우기 — 규칙

```java
    @Rule(value = "acme-event-topics", target = "eventTopics")
    void eventTopics(DomainSpec d) {
        SourceCode c = ctx(d);
        c.requireSyntaxAnyOf("acme-event-topics", Syntax.JAVA, Syntax.KOTLIN);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;

        for (EntitySpec e : d.entities()) {
            String topic = topicOf(e);
            if (topic == null) continue;
            for (String event : e.events()) {
                String constant = upperSnake(e.getName() + event);
                String eventClass = e.eventPackage() + "." + e.eventClassName(event);
                if (kotlin) {
                    c.varLine("constantList", "    const val %s = \"%s.%s\"", constant, topic, kebab(event));
                    c.varLine("entryList", "        \"%s\" to %s,", eventClass, constant);
                } else {
                    c.varLine("constantList", "    public static final String %s = \"%s.%s\";", constant, topic, kebab(event));
                    c.varLine("entryList", "        TOPICS.put(\"%s\", %s);", eventClass, constant);
                }
            }
        }
        // ${packageName} is already the package artifactsFor chose.
        c.var("className", className(d));
    }
```

| 코드 | 뜻 |
|---|---|
| `(DomainSpec d)` | 도메인마다 한 번 불림 |
| `e.events()` | 이 루트의 `@event` 이름들: `[Confirmed, Shipped, Cancelled]` |
| `e.eventClassName(event)`, `e.eventPackage()` | 생성된 이벤트 클래스의 이름·패키지: `SalesOrderShippedEvent`, `com.acme.erp.domain.event` |
| `c.var("이름", 값)` | 스켈레톤의 `${이름}`을 채움 |
| `c.varLine("이름", ...)` | `${이름}` 자리에 줄을 하나씩 추가 |
| `requireSyntaxAnyOf(...)` | 이 규칙이 쓸 수 있는 언어. 다른 언어의 생성에 쓰이면 이상한 파일을 조용히 만드는 대신 그 파일을 실패(`ERROR`)로 남김 |
| `${packageName}` | 규칙이 채우지 않습니다. 생성기가 `artifactsFor`에서 고른 패키지를 미리 넣어 두므로, 파일 위치와 `package` 줄이 어긋날 수 없습니다 |

### 6.5 스켈레톤

스켈레톤은 규칙 모듈의 `src/main/resources/elasticore-template/` 아래, **언어별 폴더**에 **target 이름**으로 둡니다.

| 언어 | 경로 |
|---|---|
| Java | `elasticore-template/v3/eventTopics.tmpl` |
| Kotlin | `elasticore-template/kt/eventTopics.tmpl` (10장) |

**파일: `acme-rules/src/main/resources/elasticore-template/v3/eventTopics.tmpl`**

```java
package ${packageName};

import java.util.HashMap;
import java.util.Map;

/**
 * ${className} - where each domain event of the domain is published.
 *
 * Generated from @topic by the ACME rules. Change the DSL, not this file.
 */
public final class ${className} {

${constantList}

    private static final Map<String, String> TOPICS = new HashMap<>();

    static {
${entryList}
    }

    private ${className}() {
    }

    /** The topic of an event, by its class name as the outbox records it; null when it has none. */
    public static String topicOf(String eventType) {
        return TOPICS.get(eventType);
    }
}
```

스켈레톤에 `${importList}`나 `${classAnnotationList}`를 **한 줄로** 두면, 우리 규칙에서 `c.addImport(...)`·`c.ensureClassAnnotation(...)`한 것이 그 자리에 자동으로 들어갑니다
(그 파일의 규칙이 모두 우리 룰셋 — `io.elasticore` 패키지가 아닌 것 — 일 때. 룰셋 패키지를 `io.elasticore`로 짓지 마세요).
위 예제는 import가 고정이라 직접 적었습니다.

누가 이 파일을 관리하는지는 스켈레톤의 첫 줄로 정합니다.

| 스켈레톤 첫 줄 | 생성된 파일은 |
|---|---|
| (표시 없음) — 위 예제 | **생성기 것**. DSL이 바뀌면 다시 씀. 손으로 고치면 고친 내용을 남기고 DSL의 변경을 병합 (같은 줄을 양쪽이 바꾸면 충돌로 빌드가 멈춤, `elasticore-env-guide.md` 9-2) |
| `// Initially generated by ElastiCORE; no longer managed - do not remove this line.` | **개발자 것**. 고치기 전에는 DSL을 따라가고, 고친 뒤에는 겹치지 않는 변경만 받음 - 겹치면 그대로 두고 알림 (컨트롤러처럼 채워 넣을 틀에 사용) |

### 6.6 전체 규칙 파일

5~7장의 조각을 합친 `AcmeDddRules.java` 완성본은 **부록 A**에 있습니다. 3장의 빈 틀을 그 내용으로 바꾸세요.

### 6.7 변형: 엔티티마다 파일 하나

도메인에 하나가 아니라 **루트마다** 하나를 만들려면(예: `SalesOrderEvents`) 세 곳이 다릅니다.

| | 도메인마다 (6.3~6.4) | 엔티티마다 |
|---|---|---|
| 인터페이스 | `DomainArtifactContributor` | `ArtifactContributor` |
| 선언 | `artifactsFor(DomainSpec d)` | `artifactsFor(EntitySpec e)` — 엔티티마다 불림 |
| 규칙 파라미터 | `(DomainSpec d)` | `(EntitySpec e)` |

**파일: `acme-rules/src/test/java/com/acme/rules/PerRootExampleRules.java`** (이 가이드에서는 테스트에서만 씀)

```java
package com.acme.rules;

import io.elasticore.base.model.pub.rules.ArtifactContributor;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;

import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/** One file per aggregate root that declares events: SalesOrderEvents, listing them. */
public class PerRootExampleRules implements CodeRules, ArtifactContributor {

    @Override
    public List<ContributedArtifact> artifactsFor(EntitySpec e) {
        if (!e.aggregateRoot() || e.events().isEmpty()) return Collections.emptyList();
        return Collections.singletonList(
                ContributedArtifact.of("rootEvents", "event", e.getName() + "Events").orNamespace("entity"));
    }

    @Rule(value = "acme-root-events", target = "rootEvents")
    void rootEvents(EntitySpec e) {
        SourceCode c = ctx(e);
        for (String event : e.events()) {
            c.varLine("eventList", "    %s.class,", e.eventClassName(event));
        }
        c.var("className", e.getName() + "Events");
    }
}
```

스켈레톤은 같은 방식으로 `elasticore-template/v3/rootEvents.tmpl`에 둡니다(9.1).
이 예제는 규칙 테스트에서만 씁니다. 실제로 쓰려면 클래스를 `src/main/java`로, 스켈레톤을 `src/main/resources`로 옮기고 빌드의 `rules` 목록에 `'com.acme.rules.PerRootExampleRules'`를 더합니다(룰셋은 여러 개여도 됩니다).
Kotlin 프로젝트에서도 쓰려면 `kt/rootEvents.tmpl`을 만들고 규칙에 `requireSyntaxAnyOf(...)`를 넣으세요(6.4, 12장).

### 6.8 생성된 클래스 쓰기 — Outbox로 토픽에 보내기

이벤트를 브로커로 보내려면 두 가지를 켭니다.

1. `env.yml`의 `config:` 아래:
   ```yaml
       apiPath: "/api"
       outbox: true                # 이벤트를 Outbox 테이블을 거쳐 내보냄
   ```
2. 메인 클래스에 `@EnableScheduling` — 보내는 일(Relay)이 주기 실행(기본 5초)이라 이것이 없으면 메시지가 테이블에 쌓이기만 합니다.
   ```java
   @SpringBootApplication
   @EnableScheduling                 // the outbox relay sends on a schedule
   @EnableJpaAuditing          // 작성자·작성시각 자동 기록
   public class ErpApplication {
   ```

흐름: 이벤트가 저장과 **같은 트랜잭션**에서 Outbox 테이블에 기록되고, 생성된 `ErpOutboxRelay`가 커밋 뒤에 `OutboxPublisher` 빈으로 넘깁니다.
`OutboxPublisher`를 구현해 토픽을 고릅니다.

**파일: `src/main/java/com/acme/erp/infrastructure/messaging/TopicOutboxPublisher.java`**

```java
package com.acme.erp.infrastructure.messaging;

import com.acme.erp.domain.event.ErpEventTopics;
import io.elasticore.springboot3.outbox.OutboxEnvelope;
import io.elasticore.springboot3.outbox.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Sends each outbox message to the topic its event was given in the DSL (@topic). */
@Component
public class TopicOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(TopicOutboxPublisher.class);

    @Override
    public void publish(OutboxEnvelope message) {
        String topic = ErpEventTopics.topicOf(message.getEventType());
        if (topic == null) throw new IllegalStateException("No topic for " + message.getEventType());
        // A real broker goes here, e.g. kafkaTemplate.send(topic, message.getAggregateId(), message.getPayload()).
        log.info("-> {} {}", topic, message.getPayload());
    }
}
```

| `publish`가 | 결과 |
|---|---|
| 정상 반환 | 발행 완료로 표시 |
| 예외 | 발행되지 않은 채 남고 다음 주기에 다시 시도. 그동안 **뒤의 모든 메시지(다른 애그리거트 것 포함)가 기다립니다** — 발행 순서 보장 |
| 10번 실패 (`application.yml`의 `elasticore.outbox.max-attempts`) | 따로 빼 두고 오류 로그. 그 뒤 메시지부터 다시 나감 |

토픽이 없는 이벤트는 예외로 알리므로 조용히 버려지지 않습니다.

---

## 7. 예제 4 — 모델 검사하기 (팀 설정과 함께)

규칙은 코드를 쓰지 않고 **모델만 검사**할 수도 있습니다. 전체 파일의 3번 부분입니다.

```java
    @Rule("acme-model-check")
    void check(EntitySpec e) {
        if (!e.aggregateRoot()) return;

        if (!e.commands().isEmpty() && e.events().isEmpty()) {
            ModelCheck.warn(String.format(
                    "[acme] %s takes commands but declares no @event: nothing outside the aggregate hears what happened.",
                    e.getName()));
        }

        // A team setting in env.yml: elasticore.config.acme.requireTopic
        if (Boolean.parseBoolean(e.config("acme.requireTopic")) && !e.events().isEmpty() && topicOf(e) == null) {
            ModelCheck.fail(String.format(
                    "[acme] %s declares events but no @topic - add @topic(...) to its meta.", e.getName()));
        }
    }
```

| 코드 | 뜻 |
|---|---|
| `ModelCheck.warn(msg)` | 경고만. 같은 메시지는 한 번만 출력 |
| `ModelCheck.fail(msg)` | 생성은 끝까지 하고(모든 문제를 한 번에 보이도록) 마지막에 **빌드를 실패**시킴 |
| `e.config("acme.requireTopic")` | 그 도메인 `env.yml`의 `elasticore.config.acme.requireTopic` 값 (없으면 `null`). 도메인 규칙에서는 `d.config(...)` |
| `e.commands()` | `commands:`에 선언한 명령들. 각각 `name()`, `params()` |

팀 설정을 `env.yml`의 `config:` 아래에 적습니다:

```yaml
    outbox: true                # 이벤트를 Outbox 테이블을 거쳐 내보냄
    acme:
      requireTopic: true        # 우리 규칙의 설정: 이벤트가 있는 루트는 @topic 필수
```

확인해 보기: `SalesOrder`의 `@topic(erp.sales)`를 잠깐 지우고 `./gradlew elcore3`를 실행하면:

```
[acme] SalesOrder declares events but no @topic - add @topic(...) to its meta.
...
[elcore3] the model check failed (1):
  [acme] SalesOrder declares events but no @topic - add @topic(...) to its meta.
```

빌드가 실패합니다(`elcore3` 종료 코드 1). 다시 붙이면 통과합니다.
ERP 모델은 명령이 있는 루트(`SalesOrder`)가 이벤트도 선언하므로 `warn` 쪽은 나오지 않습니다. `SalesOrder`의 `@event(...)`를 지워 보면 나옵니다.

> **규칙에서 예외를 던지지 마세요.** 그 규칙이 쓰던 **파일 하나만** 실패(`ERROR`)로 남고 나머지는 생성된 뒤 종료 코드 1로 끝납니다.
> 어느 파일이 왜 빠졌는지 찾기 어렵습니다. 모델 문제는 `ModelCheck.fail`로 알리세요.

---

## 8. 조립: 전체 빌드

```bash
./gradlew build        # 규칙 모듈 컴파일 → 생성(elcore3) → 애플리케이션 컴파일 → 테스트
```

생성 로그에 새 파일이 보입니다:

```
    eventTopics  src/main/java/com/acme/erp/domain/event/ErpEventTopics.java  WROTE
```

---

## 9. 규칙 테스트하기

### 9.1 규칙만 테스트 (빠름, 프로젝트 없이)

모델을 테스트 안에서 만들고, 번들 + 우리 룰셋으로 파일 하나를 만들어 봅니다.

**파일: `acme-rules/src/test/java/com/acme/rules/AcmeDddRulesTest.java`**

```java
package com.acme.rules;

import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.RuleCatalog;
import io.elasticore.base.model.pub.rules.RuleEngine;
import io.elasticore.base.model.pub.rules.SourceCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules run on a model built in the test - no project, no generation run. */
class AcmeDddRulesTest {

    @AfterEach
    void forget() {
        ModelCheck.reset();
    }

    private EntitySpec salesOrder() {
        return new EntitySpec("SalesOrder")
                .domain("erp", "Erp")
                .meta("root", "")
                .meta("event", "Confirmed(totalAmount), Shipped")
                .meta("topic", "erp.sales")
                .pkg("entity", "com.acme.erp.domain.model")
                .pkg("event", "com.acme.erp.domain.event")
                .field("orderId", "string(36) @id")
                .field("orderNo", "string(20)!");
    }

    private RuleEngine engine(SourceCode.TemplateSet set) {
        return RuleCatalog.standard().engineFor("hexagonal-springboot")
                .register(new AcmeDddRules())
                .templateSet(set);
    }

    @Test
    void aRootAndItsIdentityAreMarked() {
        String out = engine(SourceCode.TemplateSet.V3).run(salesOrder(), "entity").render();
        assertTrue(out.contains("@AggregateRoot"), out);
        assertTrue(out.contains("import org.jmolecules.ddd.annotation.AggregateRoot;"), out);
        assertTrue(out.contains("@Identity"), out);
    }

    @Test
    void aSplitRootIsMarkedOnTheDevelopersClass() {
        EntitySpec order = salesOrder().entitySplitForBehavior("SalesOrder");
        String base = engine(SourceCode.TemplateSet.V3).run(order, "entity").render();
        String mine = engine(SourceCode.TemplateSet.V3).run(order, "behavior").render();
        assertTrue(base.contains("@AggregateRoot"), base);
        assertTrue(mine.contains("@AggregateRoot"), mine);
    }

    @Test
    void aMemberIsAnEntityNotARoot() {
        EntitySpec line = new EntitySpec("SalesOrderLine").domain("erp", "Erp")
                .containedIn("SalesOrder").pkg("entity", "com.acme.erp.domain.model")
                .field("lineId", "long @id");
        String out = engine(SourceCode.TemplateSet.V3).run(line, "entity").render();
        assertTrue(out.contains("@org.jmolecules.ddd.annotation.Entity"), out);
        assertFalse(out.contains("@AggregateRoot"), out);
    }

    @Test
    void eachEventGetsATopic() {
        DomainSpec erp = new DomainSpec("erp", "Erp").entity(salesOrder());
        // The generator hands the rule the package artifactsFor chose; a test says it.
        SourceCode code = engine(SourceCode.TemplateSet.V3).packageName("com.acme.erp.domain.event")
                .run(erp, "eventTopics");
        String out = code.render();
        assertTrue(out.contains("package com.acme.erp.domain.event;"), out);
        assertTrue(out.contains("SALES_ORDER_SHIPPED = \"erp.sales.shipped\";"), out);
        assertTrue(out.contains("TOPICS.put(\"com.acme.erp.domain.event.SalesOrderShippedEvent\", SALES_ORDER_SHIPPED);"), out);
        // An unfilled ${slot} renders as an empty line, not an error: ask for them.
        assertTrue(code.unfilledSlots().isEmpty(), String.valueOf(code.unfilledSlots()));
    }

    @Test
    void kotlinToo() {
        DomainSpec erp = new DomainSpec("erp", "Erp").entity(salesOrder());
        SourceCode code = engine(SourceCode.TemplateSet.KOTLIN).packageName("com.acme.erp.domain.event")
                .run(erp, "eventTopics");
        String out = code.render();
        assertTrue(out.contains("object ErpEventTopics"), out);
        assertTrue(code.unfilledSlots().isEmpty(), String.valueOf(code.unfilledSlots()));
        assertTrue(out.contains("const val SALES_ORDER_SHIPPED = \"erp.sales.shipped\""), out);
    }

    @Test
    void oneFilePerRoot() {
        RuleEngine perRoot = RuleCatalog.standard().engineFor("hexagonal-springboot")
                .register(new PerRootExampleRules()).templateSet(SourceCode.TemplateSet.V3);
        assertEquals(1, new PerRootExampleRules().artifactsFor(salesOrder()).size());
        String out = perRoot.run(salesOrder(), "rootEvents").render();
        assertTrue(out.contains("public final class SalesOrderEvents"), out);
        assertTrue(out.contains("SalesOrderShippedEvent.class,"), out);
    }

    @Test
    void aBuiltInAnnotationCanBeTakenOff() {
        RuleEngine without = engine(SourceCode.TemplateSet.V3).register(new NoDynamicUpdateRules());
        String out = without.run(salesOrder(), "entity").render();
        assertFalse(out.contains("@DynamicUpdate"), out);
        assertTrue(engine(SourceCode.TemplateSet.V3).run(salesOrder(), "entity").render().contains("@DynamicUpdate"));
        // A root split in two: its developer's class too.
        String mine = without.run(salesOrder().entitySplitForBehavior("SalesOrder"), "behavior").render();
        assertFalse(mine.contains("@DynamicUpdate"), mine);
    }

    @Test
    void eventsWithoutATopicFailWhenTheTeamRequiresOne() {
        EntitySpec noTopic = new EntitySpec("Stock").domain("erp", "Erp").meta("root", "")
                .meta("event", "Received").config("acme.requireTopic", "true")
                .field("stockId", "string(36) @id");
        engine(SourceCode.TemplateSet.V3).run(noTopic, "entity");
        assertEquals(1, ModelCheck.failures().size());
    }
}
```

| 코드 | 뜻 |
|---|---|
| `.meta("root", "")` | DSL의 `meta: entity @root`. `.meta("topic", "erp.sales")`는 `@topic(erp.sales)` |
| `.field("orderId", "string(36) @id")` | DSL의 필드 한 줄을 그대로 |
| `.containedIn("SalesOrder")` | 멤버 엔티티 (그 루트에 속함) |
| `.entitySplitForBehavior("SalesOrder")` | 두 파일로 나뉘는 루트로 만듦 → 개발자 클래스(`behavior`)도 테스트할 수 있음 |
| `.config("acme.requireTopic", "true")` | `env.yml` 설정 대신 |
| `engineFor("hexagonal-springboot").register(new AcmeDddRules())` | 프로젝트와 같은 조합: 번들 + 우리 룰셋 |
| `run(spec, "entity").render()` | 그 target의 파일 내용 (문자열) |
| `code.unfilledSlots()` | `render()` 후, 값이 없어 빈 줄이 된 `${빈칸}` 이름들. 빈칸은 오류 없이 빈 줄이 되므로 이것으로 확인 (이름 오타가 여기서 드러남) |
| `ModelCheck.reset()` | 테스트끼리 검사 결과가 섞이지 않게 |
| `.packageName("…")` | 생성기가 넣어 주는 `${packageName}`을 테스트에서 대신 넣음 |

6.7의 변형 규칙은 `elasticore-template/v3/rootEvents.tmpl`을 **테스트 리소스**(`acme-rules/src/test/resources/`)에 둡니다:

**파일: `acme-rules/src/test/resources/elasticore-template/v3/rootEvents.tmpl`**

```java
package ${packageName};

/** The events ${className} lists - one file per aggregate root. */
public final class ${className} {

    public static final Class<?>[] ALL = {
${eventList}
    };

    private ${className}() {
    }
}
```

```bash
./gradlew :acme-rules:test
```

### 9.2 애플리케이션에서 확인 (결과가 실제로 동작하는가)

**파일: `src/test/java/com/acme/erp/AcmeRulesAppliedTest.java`**

```java
package com.acme.erp;

import com.acme.erp.domain.event.ErpEventTopics;
import com.acme.erp.domain.event.SalesOrderShippedEvent;
import com.acme.erp.domain.model.Invoice;
import com.acme.erp.domain.model.Item;
import com.acme.erp.domain.model.Partner;
import com.acme.erp.domain.model.SalesOrder;
import com.acme.erp.domain.model.SalesOrderLine;
import com.acme.erp.domain.model.Stock;
import com.acme.erp.infrastructure.persistence.ErpOutboxRelay;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What the ACME rules promise, on the application they generated. */
// After its first run at startup the scheduled relay waits an hour, so it cannot race the relay() below.
@SpringBootTest(properties = "elasticore.outbox.relay-delay-ms=3600000")
@AutoConfigureMockMvc
class AcmeRulesAppliedTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ErpOutboxRelay relay;

    @Test
    void everyClassSaysItsRole() {
        for (Class<?> root : new Class<?>[]{Partner.class, Item.class, SalesOrder.class, Stock.class, Invoice.class}) {
            assertTrue(root.isAnnotationPresent(AggregateRoot.class), root.getSimpleName());
        }
        assertTrue(SalesOrderLine.class.isAnnotationPresent(org.jmolecules.ddd.annotation.Entity.class));
    }

    @Test
    void theIdentityIsMarked() throws Exception {
        assertTrue(com.acme.erp.domain.model.SalesOrderBase.class.getDeclaredField("orderId")
                .isAnnotationPresent(org.jmolecules.ddd.annotation.Identity.class));
    }

    @Test
    void eachEventHasItsTopic() {
        assertEquals("erp.sales.shipped", ErpEventTopics.topicOf(SalesOrderShippedEvent.class.getName()));
        assertEquals("erp.sales.shipped", ErpEventTopics.SALES_ORDER_SHIPPED);
    }

    @Test
    void theOutboxSendsToTheTopic() throws Exception {
        String partnerId = json.readTree(post("/api/erp/partner", "{\"name\":\"토픽상사\",\"bizNo\":\"555-55-55555\"}"))
                .path("partnerId").asText();
        post("/api/erp/item", "{\"itemCode\":\"TPC\",\"name\":\"토픽\",\"unitPrice\":10}");
        post("/api/erp/stock/receive", "{\"itemCode\":\"TPC\",\"warehouse\":\"TOPIC\",\"qty\":5}");
        String orderId = json.readTree(post("/api/erp/salesOrder", "{\"orderNo\":\"SO-TOPIC\",\"partnerId\":" + partnerId
                + ",\"orderDate\":\"2026-09-25\",\"lines\":[{\"itemCode\":\"TPC\",\"qty\":1,\"unitPrice\":10}]}")).path("orderId").asText();
        post("/api/erp/salesOrder/" + orderId + "/confirm", "");
        post("/api/erp/salesOrder/" + orderId + "/ship", "{\"warehouse\":\"TOPIC\"}");

        relay.relay();
        // The publisher refuses an event without a topic, which leaves it pending.
        assertEquals(0, relay.pending());
    }

    private String post(String url, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
```

`theOutboxSendsToTheTopic`은 수주를 출고한 뒤 Relay를 직접 한 번 돌리고(`relay.relay()`) 남은 메시지가 없는지 봅니다.
토픽이 없는 이벤트가 있으면 `TopicOutboxPublisher`가 예외를 던져 메시지가 남으므로 실패합니다.
주기 실행되는 Relay와 테스트의 직접 호출이 겹치지 않도록, 테스트에서는 주기를 한 시간으로 늘렸습니다(`@SpringBootTest(properties = ...)`).

---

## 10. Kotlin 프로젝트에서

같은 룰셋이 그대로 동작합니다. 루트 `build.gradle`의 `language = 'kotlin'`만 다릅니다.

| 무엇 | Kotlin에서 |
|---|---|
| 클래스·필드 어노테이션 | 그대로. 생성기가 Kotlin 문법으로 씀 |
| 새 파일 | `elasticore-template/kt/<target>.tmpl`이 **따로 있어야** 합니다. 없으면 Java 스켈레톤으로 떨어지지 않고 그 파일이 실패(`ERROR`)로 남습니다 (생성은 종료 코드 1) |
| 줄 단위로 채우는 빈칸 | 문법이 다르므로 6.4처럼 `c.syntax() == Syntax.KOTLIN`으로 나눕니다 (새 파일의 규칙에서만) |
| 이미 있는 내 파일 | Java와 같습니다(5장): 병합되고, 남은 파일의 알림도 Kotlin 파일에 나옵니다. 직접 붙일 때는 `@AggregateRoot`를 `class` 위에 |

**파일: `acme-rules/src/main/resources/elasticore-template/kt/eventTopics.tmpl`**

```kotlin
package ${packageName}

/**
 * ${className} - where each domain event of the domain is published.
 *
 * Generated from @topic by the ACME rules. Change the DSL, not this file.
 */
object ${className} {

${constantList}

    private val TOPICS: Map<String, String> = mapOf(
${entryList}
    )

    /** The topic of an event, by its class name as the outbox records it; null when it has none. */
    @JvmStatic
    fun topicOf(eventType: String): String? = TOPICS[eventType]
}
```

---

## 11. 자주 막히는 곳

| 증상 | 원인 | 해결 |
|---|---|---|
| `Rule set class 'com.acme...' is not on the generator's classpath` | 클래스 이름 오타, 또는 `elasticore project(':acme-rules')` 누락 | 이름과 의존성 확인. 고친 뒤 `-PlistRules` 첫 줄에 우리 룰셋이 보이면 됨 |
| `NoSuchMethodError` 등이 생성 중에 남 | `acme-rules`의 `elasticore-base` 버전이 플러그인 버전과 다름 | 3장처럼 같은 버전으로 |
| 규칙이 도는데 결과가 없다 | 그 파일에 어노테이션 자리가 없음(1장 표 "없음"), 또는 `target`이 다름 | 규칙 테스트로 렌더링해서 출력해 보기 (9.1) |
| 내 파일(`SalesOrder.java`)에 반영이 안 된다 | 빌드 로그에 `KEPT`·`SKIP`으로 남은 개발자 파일, 또는 표시 줄 없이 복사한 파일 | 생성기 알림대로 직접 붙이기 (5장) |
| 생성된 파일에 빈 줄이 생기고 값이 없다 | 채우지 않은 `${빈칸}` — 오류가 아니라 빈 줄이 됨 | 9.1처럼 `code.unfilledSlots()`로 어느 빈칸인지 확인 (대개 이름 오타) |
| `env.yml`에 `rules:`를 쓰니 헥사고날 파일이 사라졌다 | `env.yml`의 `rules`는 번들을 **대체** | 빌드의 `rules = [...]`를 쓰거나 번들 이름도 함께 적기 |
| TypeScript 클라이언트(`elcore3ts`)에 우리 규칙이 안 들어간다 | 의도된 동작. 더한 규칙은 서버(Java·Kotlin)에만 적용 | TypeScript용은 따로 만듭니다: TypeScript를 쓰는 규칙과 `elasticore-template/ts/` 스켈레톤을 만들고 `env.yml`의 `publisher.rules.ts`에 `ts-model`과 함께 적기 |
| Outbox 메시지가 나가지 않는다 | `@EnableScheduling` 없음, 또는 `OutboxPublisher` 빈 없음 | 6.8 |
| 규칙을 고쳤는데 결과가 그대로 | 생성기 것이 아닌 파일이거나 손으로 고친 파일 | 생성 로그의 `SKIP(...)` 이유 확인 |

---

## 12. 체크리스트

새 규칙을 더할 때:

- [ ] 규칙 id가 팀 접두사로 시작한다 (`acme-…`)
- [ ] `target`이 맞다 (엔티티는 `entity` — 나뉜 루트의 두 파일 모두에 적용됨)
- [ ] 문자열을 이어 붙여 어노테이션을 만들지 않았다 (`AnnoCode` 사용)
- [ ] 새 파일이면: 조건이 안 맞을 때 빈 목록, `orNamespace`, 스켈레톤이 `v3/`와 `kt/`에 모두 있다
- [ ] 규칙 테스트(9.1)가 있고, `unfilledSlots()`가 비어 있는지 확인한다
- [ ] 예외를 던지지 않는다 (`ModelCheck` 사용)
- [ ] 이미 있는 개발자 파일에 필요한 변경을 팀에 알렸다

## 13. 자주 쓰는 API

| 읽기 (`EntitySpec e`) | |
|---|---|
| `e.getName()`, `e.domainClassName()` | `SalesOrder`, `Erp` |
| `e.aggregateRoot()`, `e.containedIn()` | 루트인가 / 멤버라면 소속 루트 이름 |
| `e.isSplitForBehavior(e.getName())` | 두 파일로 나뉘는 엔티티인가 |
| `e.hasMeta("x")`, `e.metaValue("x")` | meta 단어가 있나 / 괄호 안 값 |
| `e.events()`, `e.eventClassName(ev)`, `e.eventPackage()` | 도메인 이벤트 |
| `e.commands()` → `name()`, `params()` | 업무 명령 |
| `e.fields()`, `e.pkField()`, `e.pkg("dto")` | 필드 / 식별자 / 네임스페이스 패키지 |
| `e.config("a.b")` (`d.config`도 같음) | `env.yml`의 `elasticore.config.a.b` |

| 읽기 (`DomainSpec d`) | |
|---|---|
| `d.getName()`, `d.className()` | `erp`, `Erp` |
| `d.entities()` | 도메인의 모든 엔티티 (`EntitySpec`) |
| `d.namespace("dto")`, `d.config("a.b")` | `namespace` 키(6.3의 표, 직접 쓴 키 포함)의 패키지 / `env.yml` 설정 |

| 읽기 (`FieldSpec f`) | |
|---|---|
| `f.name()`, `f.typeName()`, `f.required()` | 이름 / 타입 / `!` 여부 |
| `f.hasDsl("x")`, `f.hasDslAny("x", "y")`, `f.dslValue("x")` | 필드 어노테이션 `@x` / 값 |
| `f.isList()`, `f.isEntityRef()` | 컬렉션인가 / 다른 엔티티 참조인가 |

| 쓰기 (`SourceCode c = ctx(e)`) | |
|---|---|
| `c.ensureClassAnnotation(AnnoCode.of("a.b.C"))` | 클래스 어노테이션 (import 자동) |
| `c.field(f).ensure(AnnoCode.of(...))` | 필드 어노테이션 |
| `AnnoCode.of("a.b.C").val("name", "x").valRaw("length", "%d", 10)` | 속성 (`val`은 따옴표를 붙임, `valRaw`는 그대로) |
| `c.var("k", v)`, `c.varLine("k", fmt, args)` | 스켈레톤 빈칸 |
| `c.dontMake()` | 이 파일은 만들지 않음 |
| `ModelCheck.warn(msg)`, `ModelCheck.fail(msg)` | 모델 검사 결과 |

전체 목록은 `elasticore-rule-guide.md`의 4·5장에 있습니다.

---

## 부록 A. `AcmeDddRules.java` 완성본

**파일: `acme-rules/src/main/java/com/acme/rules/AcmeDddRules.java`**

```java
package com.acme.rules;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.DomainArtifactContributor;
import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * ACME's additions to the DDD rules.
 *
 * 1. Marks each class and identity with its DDD role (jMolecules).
 * 2. Reads @topic on a root and writes one EventTopics class per domain.
 * 3. Checks the model: commands without events, events without a topic.
 */
public class AcmeDddRules implements CodeRules, DomainArtifactContributor {

    private static final String JMOLECULES = "org.jmolecules.ddd.annotation.%s";

    // ---- 1. DDD roles --------------------------------------------------------

    /** Each entity class says its DDD role. A root split in two gets it on both of its classes. */
    @Rule("acme-role")
    void role(EntitySpec e) {
        if (e.hasMeta("abstract")) return;   // a base class for others, not a role of its own
        SourceCode c = ctx(e);
        if (e.hasMeta("embeddable")) {
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "ValueObject"));
        } else if (e.aggregateRoot()) {
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "AggregateRoot"));
        } else {
            // Same simple name as jakarta.persistence.Entity: written in full.
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "Entity").qualified());
        }
    }

    /** A field rule: called once per field of each entity. */
    @Rule("acme-identity")
    void identity(EntitySpec e, FieldSpec f) {
        if (!f.hasDslAny("id", "pk")) return;
        ctx(e).field(f).ensure(AnnoCode.of(JMOLECULES, "Identity"));
    }

    // ---- 2. @topic -> <Domain>EventTopics ------------------------------------

    @Override
    public List<ContributedArtifact> artifactsFor(DomainSpec d) {
        for (EntitySpec e : d.entities()) {
            if (topicOf(e) != null && !e.events().isEmpty()) {
                return Collections.singletonList(
                        ContributedArtifact.of("eventTopics", "event", className(d)).orNamespace("entity"));
            }
        }
        return Collections.emptyList();
    }

    @Rule(value = "acme-event-topics", target = "eventTopics")
    void eventTopics(DomainSpec d) {
        SourceCode c = ctx(d);
        c.requireSyntaxAnyOf("acme-event-topics", Syntax.JAVA, Syntax.KOTLIN);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;

        for (EntitySpec e : d.entities()) {
            String topic = topicOf(e);
            if (topic == null) continue;
            for (String event : e.events()) {
                String constant = upperSnake(e.getName() + event);
                String eventClass = e.eventPackage() + "." + e.eventClassName(event);
                if (kotlin) {
                    c.varLine("constantList", "    const val %s = \"%s.%s\"", constant, topic, kebab(event));
                    c.varLine("entryList", "        \"%s\" to %s,", eventClass, constant);
                } else {
                    c.varLine("constantList", "    public static final String %s = \"%s.%s\";", constant, topic, kebab(event));
                    c.varLine("entryList", "        TOPICS.put(\"%s\", %s);", eventClass, constant);
                }
            }
        }
        // ${packageName} is already the package artifactsFor chose.
        c.var("className", className(d));
    }

    // ---- 3. Checks on the model -------------------------------------------------

    @Rule("acme-model-check")
    void check(EntitySpec e) {
        if (!e.aggregateRoot()) return;

        if (!e.commands().isEmpty() && e.events().isEmpty()) {
            ModelCheck.warn(String.format(
                    "[acme] %s takes commands but declares no @event: nothing outside the aggregate hears what happened.",
                    e.getName()));
        }

        // A team setting in env.yml: elasticore.config.acme.requireTopic
        if (Boolean.parseBoolean(e.config("acme.requireTopic")) && !e.events().isEmpty() && topicOf(e) == null) {
            ModelCheck.fail(String.format(
                    "[acme] %s declares events but no @topic - add @topic(...) to its meta.", e.getName()));
        }
    }

    // ---- helpers ---------------------------------------------------------------

    /** @topic(erp.sales), with or without quotes. */
    private static String topicOf(EntitySpec e) {
        String v = e.metaValue("topic");
        if (v == null) return null;
        v = v.replace("\"", "").replace("'", "").trim();
        return v.isEmpty() ? null : v;
    }

    private static String className(DomainSpec d) {
        return d.className() + "EventTopics";
    }

    /** SalesOrderShipped -> SALES_ORDER_SHIPPED */
    private static String upperSnake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
    }

    /** PartiallyShipped -> partially-shipped */
    private static String kebab(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
```
