### Changes (기여 - 머지 전 게이트)

#### 바뀐 줄의 포맷·Error Prone·커버리지·mutation 을 CI 가 막는다
- 왜: 여러 사람과 여러 AI 가 같은 코드를 고친다. CI 는 테스트·2.x 호환은 막았지만, 새 코드가 테스트로 실행되는지, 그
  테스트가 실제로 무엇을 확인하는지, 포맷과 흔한 버그 패턴은 사람의 눈에 맡겼다.
- 무엇이 (`gradle/quality.gradle`, `./gradlew prCheck` 가 넷 다):
  - 모두 **변경이 더하거나 고친 줄**만 본다 - `origin/main` 과의 merge base 부터 작업 트리까지(커밋 전 변경·새 파일 포함).
    그 전 코드는 게이트 밖(전체를 재포맷하면 523 파일 3만 7천 줄이 바뀌고 열린 PR 마다 충돌했다).
  - `formatCheck`·`formatChanged`: 라이브러리·플러그인·도구의 바뀐 Java 줄만 palantir-java-format 으로(git clang-format 방식,
    `elasticore-quality-tools` 의 `FormatChangedLines`).
  - `diffErrorprone`: Error Prone 2.50 이 라이브러리 main 소스를 따로(JDK 21, 배포 클래스는 그대로) 컴파일 - ERROR 는 어디서든
    `build` 실패, WARNING 은 바뀐 줄에서 실패. 억제는 `@SuppressWarnings("Check") // 이유`.
  - `diffCoverage`: JaCoCo - 바뀐 라이브러리 줄의 80% 를 테스트가 실행. 런타임 라이브러리는 샘플 테스트가 실행한 줄도 셈.
  - `diffMutation`: PIT 1.22 - 바뀐 줄의 mutation 만(`elasticore-quality-tools` 의 PIT 기능 `CHANGEDLINES`) 돌려, 테스트가
    실행하는 줄의 mutation 70% 를 테스트가 잡아야 함.
  - 실패하면 PR 의 줄에 annotation, job summary 에 표, HTML 보고서는 artifact.
  - 아무것도 확인하지 않고 통과하는 길을 막음: CI 나 `-PdiffBase` 에서 base 를 못 찾으면 실패(`main` 은 `origin/main` 으로도
    찾음), 포매터가 깨지면(`DONE` 없음·예외) 실패, Error Prone 의 출력 캡처는 일부러 경고를 내는 canary 클래스로 확인,
    git 설정(`diff.noprefix`, `core.quotePath`)과 무관한 diff.
  - 소스 루트별로 셈: springboot3 의 `src/main/java` 는 springboot3·4 어느 테스트가 실행해도, `src/jackson2/java`(3)와
    `src/jackson3/java`(4)는 같은 이름의 파일이 있어 각 모듈 것만. Korean 서버 테스트(`koreanServerTest`)의 실행도 셈.
  - 큰 변경(300줄 초과)은 줄마다 mutation 1개(`perline`) - PR #52 크기(4993줄)로 CI 에서 9.5분. PIT 는 빌드가 병렬
    (`org.gradle.parallel`)이어도 한 번에 하나(build service) - 셋이 함께 돌면 32GB 작업 PC 의 메모리가 바닥났다.
- CI: 새 job `Format and static analysis of the change`·`Mutation testing of the change`, `Build, test and compatibility
  checks` 에 커버리지 단계. CONTRIBUTING.md 에 "The gates on the lines you write", PR 템플릿에 `prCheck`.
- 게이트가 찾은 기존 코드의 결함(ERROR 2건):
  - `DbmsServiceImpl`(런타임)이 `DbmsService` 애노테이션을 구현하면서 `equals`·`hashCode` 가 없었다 - 이제
    `Annotation` 의 계약대로(JVM 이 만든 같은 값의 애노테이션과 같고 해시도 같음), `toString` 도.
  - `ECoreModel.findModelByName(String)` 의 `modelName = modelName;`(자기 대입, 아무 일도 안 함)을 지움 - 동작 그대로.
    `ns:Model` 이름은 지금도 접두어째 찾는다(별도 확인 대상).
- 확인: 각 게이트를 실패시켜 봄 - 이 PR 의 포맷 안 된 줄·`PatternMatchingInstanceof` 를 게이트가 잡았고 고친 뒤 통과;
  PR #52 를 기준으로(수동 실행 `base`) 포맷 32 파일·Error Prone 51건으로 실패, mutation 74.8%(981개, 9.5분),
  로컬 `diffCoverage` 79.8%(샘플 테스트를 세기 전). 없는 base 는 실패. `DbmsServiceImplTest`, `elasticore-quality-tools`
  테스트 14(포매터의 종료 코드·오류, 인터셉터의 줄 필터·`perline`).
- 샘플 `AggregateFactoryTest` 는 JaCoCo 가 더하는 synthetic 메서드를 세지 않음.
- 관련 파일: `gradle/quality.gradle`(새), `build.gradle`, `settings.gradle`, `elasticore-project/elasticore-quality-tools/`(새),
  `.github/workflows/ci.yml`, `.github/PULL_REQUEST_TEMPLATE.md`, `CONTRIBUTING.md`,
  런타임 `dbms/meta/DbmsServiceImpl.java`, `ECoreModel.java`.
