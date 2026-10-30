# ElastiCORE 2.x → 3.0 마이그레이션 가이드

## 요약: 고칠 것이 없습니다

라이브러리 버전만 올리면 됩니다. `env.yml` 도 `build.gradle` 도 모델 파일도
그대로 두십시오.

```gradle
- implementation      'io.elasticore:elasticore-base:2.2.260703'
+ implementation      'io.elasticore:elasticore-base:3.0.261030-rc6'
```

**생성되는 소스 코드는 바뀌지 않습니다** - 2.x 의 결함을 고친 몇 곳만 다르고, 그 목록은
`elasticore-history.md` 에 있습니다. 2.x 처럼 손으로 쓴 `elcore` 태스크나 `@ElastiCore` 로
생성하는 프로젝트는 2.x 생성기를 그대로 부르므로, 3.0 의 새 기능(DDD, 헥사고날, Kotlin,
TypeScript)은 선언해야 켜집니다.

### Gradle 플러그인(`id 'io.elasticore'`)으로 옮긴다면: `output = 'v2'`

3.0.261028-rc3 부터 플러그인은 **아무것도 선언하지 않으면 3.0 코드**(레이어드 Java 의 v3 번들)를
생성합니다. 2.x 코드를 그대로 유지하려면 한 줄을 적습니다:

```gradle
elasticore {
    output = 'v2'      // 2.x 생성기가 쓰던 코드 그대로. 빌드가 2.x 생성기(elcore)를 부릅니다
}
```

적지 않고 빌드하면, 생성기가 2.x 가 만든 소스(첫 줄 `//ecd:..._V2.x`)나 2.x 호환으로 돌았던 마지막 실행의 기록
(`build/elasticore/server.mode`)을 찾아 **아무 소스도 쓰지 않고 멈춥니다** - 버전만 올렸는데 소스 수백 개가 다시 쓰이고 검색·거절 응답·접근 검사의 동작이 바뀌는
일을 막기 위해서입니다. 메시지대로 `output = 'v2'`(유지) 나 `output = 'v3'`(3.0 코드로 이동) 중 하나를
적으면 됩니다. 한 번만 실행할 때는 `-Pv2` / `-Pv3` 도 됩니다. 도메인의 `env.yml` 이
`publisher.rules: [jpa-springboot]` 를 적고 있으면(3.0.261028-rc3 전의 초기화 마법사가 레이어드 Java 에
적던 값) 그 프로젝트도 2.x 코드를 유지합니다.

3.0 코드로 옮기면 달라지는 동작은 아래 "3.0 기능을 쓰고 싶다면" 과 `elasticore-history.md` 에 있고, 손볼 코드는
그 안의 "2.x 코드에서 옮길 때 손볼 것" 에 있습니다.

`output = 'v3'` 로 옮기는 첫 빌드는 2.x 가 생성해 **손으로 고친** 파일도 3.0 코드에 병합합니다: 그 파일들의 2.x 원본을
메모리에서 다시 만들어(`[elcore3] upgrade : ...`) 첫 줄 `//ecd:` 의 해시와 같은 것을 기준으로 고친 내용을 남깁니다.
고치지 않은 파일은 3.0 코드로 새로 쓰이고, 기준을 확인할 수 없는 파일은 전처럼 건너뛰고 알립니다. 이 첫 빌드는 모든
도메인을 한 번에 합니다(`-Pdomain` 이면 아무것도 쓰지 않고 멈춥니다) - 이 빌드에서 생긴
`.elasticore/cache` 를 생성 파일과 함께 커밋하세요(`elasticore-env-guide.md` 9-2). 한 번만 생성하는 파일(확장 서비스·
컨트롤러)은 개발자의 것이라, 3.0 의 변경이 고친 줄과 겹치면 충돌 표시 없이 그대로 두고 손으로 옮길 것을 경고합니다.
3.0 이 더 만들지 않는 생성 파일은 지워지지만, 남는 소스가 아직 쓰는 것은 남기고 어느 파일이 쓰는지 알립니다
(`... is no longer generated, but ... still uses it - kept`) - 아래 표의 "서비스·리포지토리" 를 보십시오.
애플리케이션 클래스에 `@ElastiCore` 를 붙여 생성하던 프로젝트는 옮길 때 그것을 지웁니다(`annotationProcessor` 의
elasticore-base 도) - 남겨 두면 컴파일할 때마다 2.x 생성기가 2.x 코드를 다시 써서 3.0 코드와 섞이고, 병합은 그것을
개발자의 편집으로 받습니다.
레이어드 Java 의 v3 번들(`jpa-springboot-v3`)은 2.x 번들에 없는 규칙을 더 씁니다 - `@access`·`@owner` 접근 검사
(`jpa-service-access`), 오류 코드가 있는 거절 응답(`spring-api-errors`), 서버가 정하는 필드의 쓰기 보호
(`jpa-write-guard`). 검색에서는 `like` 가 대소문자를 무시하고 `gt`·`gte` 같은 키워드가 기호와 같아집니다. `@fetchjoin` 연관은 2.x 처럼 페이지
검색에서 루트와 함께 가져옵니다(3.0.261029-rc4 부터 - 그 전의 v3 서비스는 가져오지 않았습니다).

### 근거

3.0 개발 기간 전체에 대해 샘플 프로젝트의 생성 소스를 비교했습니다:

```
2.2.260703 → 3.0.260829   생성 도메인 소스 변화: 0개
```

수정된 파일이 몇 개 보이지만 전부 `_V2.2` 스탬프로, 3.0 이전에 샘플 모델에
엔티티를 추가하면서 생긴 것입니다. 또한 현재 코드로 재생성해도 커밋된
트리와 차이가 없습니다.

---

## 그래도 달라지는 것 (전부 버그 수정)

### 1. `schema.yml` 의 위치와 인코딩

| | 2.x | 3.0 |
|---|---|---|
| 위치 | 저장소 루트 (모듈 것은 갱신 안 됨) | 자기 blueprint 옆 |
| 인코딩 | 플랫폼 기본값 (한글 Windows = MS949) | UTF-8 |

**할 일**: 저장소 루트에 있던 옛 `schema.yml` 은 더 이상 갱신되지 않으므로
삭제하십시오. 한글 라벨이 깨져 있었다면 이제 정상입니다.

### 2. 생성 코드가 모델을 가진 모듈에 놓입니다

일부 구성에서 생성 코드가 저장소 루트로 새어 나가던 문제가 고쳐졌습니다.

**할 일**: 저장소 루트에 정체를 알 수 없는 생성 파일 묶음이 있다면 그것이
이 문제입니다. 삭제하십시오. 정상 위치에 다시 생성됩니다.

### 3. `DTOUtils.getMetaInfo(Class)` → `getMetaInfo(Class<T>)`

IDE 가 생성된 컨트롤러의 호출부에 raw type 경고를 표시하던 문제입니다.

**할 일**: 없습니다. 시그니처 서술자
(`(Ljava/lang/Class;)Ljava/util/Map;`)가 동일하여 재컴파일 없이 기존
바이너리가 그대로 동작합니다.

### 4. 새 DSL 검증은 2.x 모델에 반응하지 않습니다

3.0 이 추가한 `DSL_WARN` 은 `domainService`, `uses:` 같은 DDD 전용 문법에만
적용됩니다. 2.x 모델에는 해당 문법이 없으므로 `-Pmode=valid` CI 가 갑자기
실패하지 않습니다.

---

## 3.0 기능을 쓰고 싶다면 (선택)

여기서부터는 **원할 때만** 하십시오. 손으로 쓴 2.x 태스크나 `output = 'v2'` 로는 계속 2.x 동작입니다.

### 무엇이 생기나

| 번들 | 내용 |
|---|---|
| `jpa-springboot` | 2.x 와 같은 코드 (`output = 'v2'` 와 같음) |
| `jpa-springboot-v3` | 레이어드 Java 의 3.0 코드 - 플러그인의 기본값 (3.0.261028-rc3 부터) |
| `ddd-springboot` | 집합체 경계, 불변식, 도메인 이벤트, 값 객체 |
| `hexagonal-springboot` | 위 + 인바운드 포트, 헥사고날 패키지 배치 |
| `*-kt` | 위 셋의 Kotlin 판 |
| `ts-model` | 브라우저용 DTO, enum, 타입 있는 REST 클라이언트 |

### 켜는 법

도메인의 `env.yml`:

```yaml
elasticore:
  config:
    publisher:
      rules: [ddd-springboot]
```

**주의: 이 설정은 규칙엔진이 읽습니다.** 규칙엔진은 `@ElastiCore` 애노테이션
프로세서와 별개의 진입점이고, 일반 빌드는 이 설정을 보지 않습니다. 실행
방법은 `elasticore-rule-guide.md` 의 "내 프로젝트에서 규칙엔진 실행하기" 를
보십시오.

**검색의 `like` 가 대소문자를 무시합니다.** v3 번들(2.x 코드를 유지하는 레이어드 Java 제외 - 플러그인의 기본값,
헥사고날·DDD, `-Pv3`, 모든 Kotlin 빌드)에서 문자열 필드의 `@search(like)` 는 `%__%` 처럼 대소문자를 무시하는 포함 검색입니다. 2.x 처럼
구분하려면 `@search(%_%)` 로 바꿉니다. 비교는 `lower(컬럼)` 으로 하므로, 포함 검색을 `pg_trgm` 같은 인덱스로
받치고 있었다면 그 인덱스를 `lower(컬럼)` 에 다시 만드십시오.

**`@fetchjoin`·`table.autoJoin` 은 v3 번들에서도 2.x 처럼 동작합니다(3.0.261029-rc4 부터).** CoreService 에
`withJoinFetches()` 가 생성되고, 페이지 검색(`findBySearch`)이 그 연관을 루트와 함께 가져옵니다(LEFT JOIN FETCH, count
쿼리 제외). 확장 서비스에서 `withJoinFetches()` 를 부르는 코드도 컴파일됩니다. v3 에서는 그 연관이 있는 엔티티에만
생성되고(2.x 는 모든 CoreService 에 - 아래 표), 페이지 검색 밖(`findAll`, `listBySearch`)에는 적용되지 않습니다.
3.0.261028-rc3 까지의 v3 서비스는 가져오지 않아 연관을 행마다 따로 읽었고(N+1), 생성할 때 경고만 냈습니다.

### 2.x 코드에서 옮길 때 손볼 것

2.x 가 생성한 코드(또는 `output = 'v2'`)를 쓰던 프로젝트가 v3 번들로 옮기면, 아래 차이가 컴파일이나 테스트에서
드러납니다. 도메인 9개짜리 프로젝트를 옮기며 실제로 걸린 것들입니다.

| 무엇이 | 2.x 코드 | v3 코드 | 할 일 |
|---|---|---|---|
| 서비스·리포지토리가 생기는 엔티티 | 키가 있으면 `XxxRepository`, `@template`·`@searchable` DTO 가 있으면 `XxxCoreService`·`XxxService`·컨트롤러까지 (`@service`·`@expose` 없이도) | `@expose`(컨트롤러까지) 나 `@service`(컨트롤러 없이) 를 적은 엔티티만 | 그 엔티티의 줄에 `@expose` 를 적습니다(2.x 처럼 컨트롤러까지 - 고치지 않은 컨트롤러는 3.0 것으로 바뀝니다). 적지 않으면 그 엔티티의 2.x 서비스·리포지토리는 쓰는 곳이 없어질 때 지워집니다 |
| 키를 받는 엔티티 생성자 | `new Order(id)` | 없음 (기본 생성자만) | 연관에 넣을 참조는 `repository.getReferenceById(id)`, 테스트 데이터는 기본 생성자와 setter 로 만듭니다 |
| 보호 블록(`// !----` ~ `// ----!`) 안 코드가 쓰는 import | `jakarta.persistence.*`·`java.util.*` 같은 `*` import 덕에 컴파일됨 | 생성 코드가 쓰는 것만 import | 블록이 쓰는 import 를 `package` 아래의 보호 블록에 적습니다 (`elasticore-dev-guide.md` 의 "사용자 코드 보존 블록") |
| `@fetchjoin`·`table.autoJoin` | `withJoinFetches()` 로 페이지 검색에서 루트와 함께 가져옴 | 같음 - 3.0.261029-rc4 부터 (3.0.261028-rc3 까지의 v3 는 가져오지 않았음, 위) | 없음. rc3 까지의 v3 로 옮기며 `JoinSpec.fetchJoin()` 으로 대신 쓴 쿼리는 그대로 동작합니다 |
| CoreService 의 `withJoinFetches()` | 모든 CoreService 에 - 가져올 연관이 없으면 아무 조건도 더하지 않는 빈 Specification | 가져올 연관(`@fetchjoin`·`table.autoJoin`)이 있는 엔티티에만 | 연관이 없는 엔티티의 확장 서비스가 부르면 컴파일되지 않습니다. 2.x 에서도 아무것도 하지 않던 호출이므로 `.and(withJoinFetches())` 를 지웁니다 |
| `@writeonly`·`@nooutput` 값 | 엔티티 → DTO 로 복사 (JSON 응답에서만 빠짐) | 복사하지 않음 | 서버 코드에서 그 값이 필요하면 엔티티에서 읽습니다 |
| CoreService `save(dto)` | 키가 이미 있으면 그 행을 덮어씀 | 먼저 Bean Validation (`ConstraintViolationException`), 키가 이미 있으면 `DuplicateKeyException` | 있는 행은 `update(dto)` 로 바꿉니다 |
| CoreService `update(dto)` | 검증 없음 | 먼저 Bean Validation (기본은 보낸 값만) - 없는 키보다 검증 오류가 먼저 납니다 | |
| CoreService `delete(searchDTO)` | 조건으로 한 번에 지움 (bulk DELETE) | 찾은 행을 하나씩 지움 - 엔티티 리스너와 cascade 가 동작합니다 | |
| CoreService `updateBySpecification` | 한 번의 bulk UPDATE | 행마다 읽어 매퍼로 바꾸고 저장 - 버전·감사 필드·`beforeSave` 가 적용됩니다 | |

2.x 가 생성한 서비스 테스트(`*ServiceTest`)는 2.x CoreService 가 부르는 리포지토리 메서드를 mock 으로 고정해 두었습니다.
v3 CoreService 는 다른 메서드를 부르므로(`deleteById` 는 `findById` 후 `delete`, `delete(searchDTO)` 는 `findAll` 후
`delete`) 그 테스트는 맞지 않습니다 - 지우고 다시 생성하거나(`env.yml` 의 `testCode: "true"`) 새 호출에 맞춰 고칩니다.

### 되돌리는 법

`publisher.rules` 줄을 지우면 됩니다. 플러그인을 쓰는 레이어드 Java 프로젝트가 2.x 코드로 돌아가려면
`elasticore { output = 'v2' }` 를 적습니다. 그 외에 되돌릴 것은 없습니다 - 다만 병합이 켜진 기본값에서 3.0 코드의 생성
파일에는 첫 줄 `//ecd:` 가 없어서, 2.x 생성기는 그 파일들을(컨트롤러·확장 서비스까지) 2.x 코드로 덮어씁니다. 그 안에서
고친 내용은 git 에서 옮겨 오십시오.

---

## Spring Boot 4 / Spring AI 2.0으로 올리기 (3.0만)

ElastiCORE 3.0은 Spring Boot 4.0과 Spring AI 2.0을 지원합니다(2.2는 Spring Boot 3까지).
런타임 의존성을 `elasticore-springboot3` -> `elasticore-springboot4`로 바꾸고 Boot 플러그인을 4.x로 올리면,
ElastiCORE가 Boot 버전을 읽어 enum과 outbox를 Jackson 3에 맞게 생성합니다.
자세한 절차와 직접 고칠 것은 `elasticore-env-guide.md` 3-1장.

---

## 자주 나오는 질문

**Q. 한 번에 전부 바꿔야 하나요?**
아니요. `publisher.rules` 는 도메인별 설정입니다. 도메인 하나만 DDD 로
바꾸고 나머지는 그대로 둘 수 있습니다.

**Q. 멀티 모듈에서도 되나요?**
됩니다. `elasticore.modules` 시스템 프로퍼티는 두 진입점 모두 지원하며,
`module://` 크로스 모듈 네임스페이스 리다이렉트도 그대로입니다.

**Q. 생성 코드를 손으로 고쳐둔 파일이 있는데요?**
고친 내용은 남습니다. `output = 'v2'` 는 2.x 와 같습니다: 내용 해시로 수정을 감지하여 건드리지 않고,
`// Initially generated by ElastiCORE; no longer managed` 표시가 있는 파일은 처음 생성 이후 다시 쓰이지 않습니다.
3.0 코드(`output = 'v3'`)는 고친 파일도 DSL 을 따라갑니다: 고친 내용을 남긴 채 DSL 의 변경을 병합하고, 표시가 있는
파일(컨트롤러, 확장 서비스 ...)은 손대기 전에는 다시 생성되고, 고친 뒤에는 고친 줄과 겹치지 않는 변경만 받습니다
(겹치면 그대로 두고 경고). 2.x 에서 고친 파일은 첫 빌드가 넘겨받습니다 - 첫 줄 `//ecd:` 의 해시로 원래 생성본을
확인할 수 있는 파일만, 나머지는 전처럼 건너뜁니다(위 "`output = 'v2'`" 절, `elasticore-env-guide.md` 9-2).

**Q. 롤백하려면?**
2.x 코드를 유지해 온 프로젝트(`output = 'v2'`, 손으로 쓴 2.x 태스크)는 버전을 되돌리면 됩니다. 3.0 이 남긴 상태가
없습니다 — 단, 위 1·2번에서 삭제한 옛 파일들은 2.x 로 돌아가면 다시 잘못된 위치에 생성됩니다.
3.0 코드로 옮긴 프로젝트(`output = 'v3'`, 플러그인의 기본값)는 버전만 되돌리지 마십시오. 병합이 켜진 기본값에서 3.0 이 쓴
생성 파일에는 첫 줄 `//ecd:` 가 없어서, 2.x 생성기는 그 파일들을 고친 것으로 보지 않고 2.x 코드로 덮어씁니다 - 그 안에서
고친 내용은 사라집니다. 컨트롤러·확장 서비스처럼 `// Initially generated by ElastiCORE` 표시가 있는 파일도 같습니다(2.x 는
그 표시를 첫 줄 `//ecd:` 아래에서만 찾습니다). 3.0 만 만든 파일은 지워지지 않고 남고, `.elasticore/cache` 는 2.x 가 읽지
않습니다. 옮기기 전 커밋으로 소스를 되돌린 뒤 버전을 내리십시오. (`elasticore { merge = false }` 로 생성해 온 프로젝트는
파일에 `//ecd:` 가 있어, 2.x 도 고친 파일을 건너뜁니다.)
