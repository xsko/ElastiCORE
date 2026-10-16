## 3.0.261016

### Documentation (README 의 시작하기)

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

### New Features (생성·검사 태스크)

#### `elcoreGen`·`elcoreValidate`·`elcoreGenTs`
- Gradle 플러그인에 세 태스크를 더했다. `./gradlew elcoreGen` 은 이 빌드가 고른 생성기를 돌린다 - `elasticore { }` 에
  `architecture`·`language`·`rules` 중 하나라도 있으면 3.0 규칙 엔진(`elcore3`), 없으면 2.x 생성기(`elcore`).
  `compileJava` 가 무엇을 기다릴지 정하는 기준과 같다. 규칙 엔진만 아는 스위치(`-Pv3`·`-Pkt`·`-Pts`·`-PlistRules`)를
  주면 선언이 없어도 규칙 엔진이다 - 2.x 생성기는 이 스위치를 몰라 2.x Java 를 생성해 버린다. 이전에는 엔진마다 명령이
  달라(`elcore`·`elcore3`) 어느 엔진인지 알아야 했고, 명령 이름에 엔진 버전이 들어 있었다
- `./gradlew elcoreValidate` 는 생성과 똑같이 만들어 보되 아무것도 쓰지 않는다. 생성이 바꿀 파일(새 파일, DSL 이 바뀐
  파일, 지워질 파일, 손대지 않아 다시 쓸 한 번 생성 파일)이 있거나, 생성기가 관리하는 파일을 손으로 고쳐 DSL 과 다르거나,
  생성이 개발자 파일에 손볼 곳이 있다고 말하거나(생성자, 테이블 어노테이션, Base 상속), DSL 경고가 있거나(DSL 의 일부가
  적용되지 않음), 모델 파일을 읽지 못했거나, 생성 자체가 실패할 상황이면 실패한다(exit 1). 2.x 생성기의 `-Pmode=valid` 를
  두 엔진 모두에 태스크로 만든 것이다. 검사하는 엔진 태스크에 빌드가 넣은 설정(`systemProperty`·`jvmArgs`·`javaLauncher`·
  `workingDir`)을 그대로 쓰고, 같은 실행에 생성이 있어도 먼저 돈다 (`./gradlew elcoreValidate build`). 판정 표는
  `elasticore-rule-guide.md` 의 "검사". 네 샘플(`elasticore-template`, `-template-ddd`, `-template-kt`,
  `elasticore-sample-petstore`)은 생성 직후 검사를 통과한다
- 3.0 규칙 엔진의 DSL 경고 중 생성기가 내린 결정을 알리기만 하는 셋 - 접근 범위가 좁은 참조를 DTO 에서 뺐다, 두 번 적힌
  이벤트에 처리기 하나, 같은 상태를 말하는 `@default` 와 `states.initial`(다른 상태면 `@default` 가 적용되지 않으므로 그대로
  경고) - 은 이제 노트다: `[elcore3] DSL notes - what the generator decided:` 아래에 따로 나오고 검사에서 세지 않는다. 경고로
  세면 그런 모델을 일부러 둔 프로젝트(갓 생성한 `elasticore-template-kt` 도)는 검사를 통과할 수 없었다. 문구는 그대로다
- 명령 파라미터가 값이 아니라는 경고(`a command parameter is a value`)가 값의 목록(`tags: List<string>`)에도 났다. 이제
  엔티티(와 엔티티의 목록)에만 난다
- `./gradlew elcoreGenTs` 는 TypeScript 클라이언트 생성(`elcore3ts`). 검사는 `./gradlew elcoreGenTs -Pmode=valid`
- `elcoreValidate` 에는 `-Pmode`·`-PlistRules` 가 먹지 않는다 - 검사가 쓰는 실행이나 아무것도 비교하지 않는 목록으로
  바뀌지 않게
- `elcore`·`elcore3`·`elcore3ts` 는 그대로다. 아무것도 선언하지 않은 프로젝트의 생성 결과도 그대로. 빌드에 같은 이름의
  태스크가 이미 있으면 Gradle 이 설정 단계에서 멈춘다 - 그 태스크의 이름을 바꾼다
- 가이드의 명령을 `elcoreGen`·`elcoreValidate` 로 바꿨다. README 는 이 태스크가 든 버전이 Maven Central 에 나온 뒤에
  바꾼다 - README 는 받을 수 있는 버전의 명령을 적는다
- 관련 파일: `ElastiCorePlugin.java`, `ElastiCorePluginTest.java`, `InitializationHelper.java`, `ServiceAccessRules.java`,
  `DddStateRules.java`, `DddDomainServiceRules.java`, `DddCommandRules.java`, `ProcessManagerRulesTest.java`,
  `StateDefaultTest.java`(신규),
  `elasticore-template-ddd/build.gradle`, `elasticore-template-kt/build.gradle`, `elasticore-rule-guide.md`,
  `elasticore-dev-guide.md`, `elasticore-env-guide.md`, `elasticore-dsl-guide.md`, `elasticore-annotation-guide.md`,
  `elasticore-ddd-customize-guide.md`, `elasticore-rule-dev-guide.md`

### Bug Fixes (검증 모드가 쓰거나, 검사하지 않고 통과하던 곳)

#### 3.0 규칙 엔진: `-Pmode=valid` 가 검사하지 않고 파일을 썼다
- 플러그인은 `-Pmode` 를 규칙 엔진에도 넘겼지만 규칙 엔진은 읽지 않아서, `./gradlew elcore3 -Pmode=valid` 가 쓰지 않고
  비교하는 대신 생성을 그대로 했다 (3.0.261015-alpha1 에서 실행해 확인: 지운 파일이 `WROTE` 로 다시 생겼다). 이제 규칙
  엔진도 `-Pmode=valid` 를 검사로 읽는다 - 파일·디렉터리를 만들거나 지우지 않고, 가이드 복사와 `schema.yml`·
  `context-map.md`·`semantic_model.yml` 갱신도 하지 않는다. 파일마다 `WOULD WRITE`·`WOULD WRITE(new)`·
  `WOULD REGENERATE(owned, never edited)`·`WOULD REMOVE(...)`·`USER-MODIFIED(...)` 로 보여 주고, 실패하면 이유마다
  `[VALID] Validation FAILED: ...` 를 낸다. `-PlistRules` 를 함께 주면 목록 대신 검사를 한다
- `-Pmode=clean` 도 생성을 그대로 했다. 2.x 생성기의 모드라 규칙 엔진은 이제 아무것도 쓰거나 지우지 않고 실패한다
  (DSL 이 더는 만들지 않는 생성 파일은 생성이 직접 지운다)
- 관련 파일: `RulesGenRunner.java`, `ValidationModeTest.java`(신규)

#### 2.x 생성기: `-Pmode=valid` 가 디렉터리와 서비스 테스트를 썼다
- 없는 파일을 찾으면서 그 패키지 디렉터리를 만들었고, `testCode: "true"` 면 없는 서비스 테스트를 써 버렸다 - 그 뒤의
  검사는 그 테스트가 없었다는 것을 알 수 없었다. 이제 둘 다 쓰지 않고, 없는 서비스 테스트는 다른 없는 파일처럼
  `New Files Pending Generation` 으로 실패한다. 소스 루트 인자 없이 실행하면 생성 쪽으로 가서 쓰던 것도 고쳤다
- 검사하지 않고 통과하던 경우가 이제 실패한다: DSL 파일을 찾지 못함(`no model DSL found - nothing was checked`), 실행 중
  오류(`the run itself failed`), 읽지 못한 YAML 파일(`LOAD ERROR` + `model file(s) could not be read` - 두 엔진의 검사 모두).
  생성의 동작은 그대로다
- 관련 파일: `ModelExtractor.java`, `FileBasedSourceFileAccessFactory.java`, `MultiFileBasedSourceFileAccessFactory.java`,
  `ServiceTestPublisher.java`, `YamlFileMainModelLoader.java`, `ExtractorValidationTest.java`(신규)

#### 3.0 규칙 엔진: 손으로 고친 생성 파일을 DSL 이 더는 만들지 않으면 아무 말 없이 남겼다
- 더는 만들지 않는 생성 파일을 정리할 때, 손대지 않은 파일은 지우고 고친 파일은 "지우거나 쓴 것을 옮기라" 고 말하게 되어
  있었지만, 고친 파일은 상태에 첫 줄(스탬프)이 실리지 않아 말 없이 건너뛰었다. 이제 그 파일의 첫 줄을 직접 읽어 말하고,
  검사에서는 실패로 센다. 보존 블록만 더한 파일은 전에도 말했다
- 관련 파일: `RulesGenRunner.java`, `ValidationModeTest.java`

#### 3.0 규칙 엔진: `-Pdomain` 이 이름의 일부로도 골랐다
- `-Pdomain=orders` 가 `order` 도메인도 골랐다(문자열 포함 비교). 이제 2.x 생성기처럼 쉼표로 나눈 이름을 통째로 비교한다.
  빈 `-Pdomain=` 은 아무 도메인도 생성하지 않았는데, 이제 2.x 생성기처럼 지정하지 않은 것과 같다(모든 도메인 생성,
  더는 만들지 않는 파일 정리)
- 관련 파일: `RulesGenRunner.java`, `ValidationModeTest.java`

### Documentation (검증 모드)

#### `elasticore-dev-guide.md` 의 검증 표
- 생성기가 관리하는 파일을 손으로 고친 경우를 "스킵 - 통과" 로 적었지만, 2.x 의 `-Pmode=valid` 는 그 파일을
  `[WARN] User-Modified Files:` 로 보여 주고 실패한다. 표를 실제 출력으로 고치고, 보존 블록이 있는 파일(실패), DSL
  경고(실패), 공백·줄 순서만 다른 파일(통과), 한 번만 생성되는 파일(검사하지 않음)을 더했다 - 행마다 2.x 생성기로 실행해
  확인. 3.0 규칙 엔진과 기준이 다른 곳(들여쓰기, 보존 블록, DSL 경고)도 적었다
- 관련 파일: `elasticore-dev-guide.md`

#### `elasticore-annotation-guide.md` 의 `@fk` 검사
- 대상 엔티티·필드가 없으면 DSL 경고가 나고 검증이 실패한다고 적었지만, 어느 생성기도 그 경고를 내지 않는다
  (2.x 생성기와 3.0 규칙 엔진 모두 실행해 확인 - 검사하는 코드가 호출되지 않는다). 사실대로 고쳤다. 검사를 켜면
  지금 생성되는 2.x 모델의 생성이 실패하게 되므로(2.x 는 DSL 경고에서 멈춘다) 이번에는 문서만 고쳤다
- 관련 파일: `elasticore-annotation-guide.md`

#### `elasticore-env-guide.md` 의 `-Pdomain` 검증
- `-Pdomain=hr` 이면 `hr` 만 DSL 을 검증한다고 적었지만, `hr` 것만 비교하는 것은 생성 파일이고 DSL 은 모든 도메인을 읽는다 -
  읽을 때 나오는 다른 도메인의 경고도 나오고, 2.x 생성기의 검증은 그 경고로도 실패한다 (실행해 확인)
- 관련 파일: `elasticore-env-guide.md`

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
