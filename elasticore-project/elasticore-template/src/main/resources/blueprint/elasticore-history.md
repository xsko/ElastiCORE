## 3.0.261016

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
