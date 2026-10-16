<p align="right"><a href="comparison.md">English</a> · <b>한국어</b></p>

# 다른 도구와 비교

ElastiCORE 는 업무 규칙을 담은 모델에서 Spring Boot 백엔드와 TypeScript 클라이언트를 **계속** 생성하는 DSL 이자
규칙 엔진입니다. 비교 대상으로 자주 거론되는 것은 다음과 같습니다.

- 코드 생성기: JHipster, Amplication, Bootify, Sculptor, OpenAPI Generator
- DDD 모델링 도구: Context Mapper
- 구조 검사기: Spring Modulith
- 애플리케이션 플랫폼: Jmix, Wasp
- 그리고 AI 에게 코드를 바로 쓰게 하는 방법

이 문서는 각각이 무엇을 위한 것인지, ElastiCORE 가 어디서 다른지, 어디서는 다른 도구가 더 나은지를 적습니다.

다른 제품에 대한 내용은 모두 **2026-10-06** 에 각 제품의 공식 문서나 저장소로 확인했습니다. 출처는 맨 아래에 있습니다.
제품은 바뀌니, 낡은 내용이 있으면 이슈로 알려 주세요.

## 요약

아래 도구 중 어느 하나도 함께 갖추지 못한 것을 ElastiCORE 는 함께 갖추고 있습니다.

1. **업무 규칙을 선언하면 그 강제가 생성됩니다** (3.0 규칙 번들. 생명주기·불변식·파생 값은 DDD·헥사고날 번들).
   다음 규칙이 각각 한 줄이고, 다시 생성되는 코드에서 강제됩니다.
   - 생명주기(`ship: PLACED -> SHIPPED`)
   - 동작별 권한과 행 소유(`@access`, `@owner`)
   - 불변식
   - 주문 시점에 고정되는 값(`@snapshot`)과 계산되는 값(`@compute`)
   - 지울 수 있는 상태

   규칙을 어긴 요청은 정해진 HTTP 응답(401, 403, 404, 422)과 번역할 수 있는 오류 코드를 받습니다. 서버가 정하는 값은
   요청에서 받지 않을 뿐입니다. 화면용 확인(`canShip(dto)`)도 함께 생깁니다.
2. **생성이 멈추지 않습니다.** 첫날 이후에도 모델이 유일한 원본입니다. 다시 생성되는 파일과 개발자의 파일을 나눠 둡니다.
   - 나누는 방법: 상위 클래스와 개발자 클래스로 나누고, 한 번만 생성하는 파일을 두고, 고친 파일은 내용 해시로 알아봅니다.
   - 모델이 더는 만들지 않는 생성 파일은 지웁니다 (직접 고친 파일은 지우지 않고 알립니다).
3. **전략적 DDD 를 그림이 아니라 강제로 합니다.** 바운디드 컨텍스트마다 쓰는 것(`uses`)과 내보내는 것(`publishes`)을
   선언합니다. 아무도 열지 않은 경계를 넘는 타입이나 이벤트가 있으면, 컴파일 전 생성 단계에서 실패합니다.
   컨텍스트 지도도 같은 선언에서 그립니다.
4. **모델 하나를 여러 방식으로 읽습니다.** 같은 DSL 을 설정 하나로 다음 중에서 골라 생성합니다.
   - 구조: 레이어드, DDD, 헥사고날
   - 언어: Java, Kotlin
   - Spring Boot 3 또는 4
   - 화면용 TypeScript
5. **생성기가 팀이 덧붙일 수 있는 규칙으로 되어 있습니다.** 평범한 Java 로 쓰고, 빌드를 실패시키는 검사도 넣을 수 있습니다
   ("명령이 있는 루트는 이벤트를 선언해야 한다").
6. **데이터의 의미까지 내보냅니다.** [Apache Ossie](https://github.com/apache/ossie) 의미 모델이 다음을 담습니다.
   - 조인
   - 코드값의 뜻
   - "매출" 의 정의(SQL)

   BI 도구와 AI 에이전트가 업무 정의를 추측하지 않고 읽습니다.
7. **같은 DSL, 같은 코드.** 생성은 결정적이고, 측정했습니다.
   - 2.x 와 3.0 의 출력을 풀 리퀘스트마다 비교합니다.
   - DSL 한 줄이 코드를 얼마나 움직이는지 [측정](benchmarks/change-amplification.ko.md)했습니다.

## 한눈에

| | 모델의 업무 규칙 | 재생성 때 내 코드 | 도메인 경계 | 생성물 | 상태 (2026-10) |
|---|---|---|---|---|---|
| **ElastiCORE** | 생명주기, 동작별 권한, 행 소유, 불변식, 파생 값, 삭제 조건, 업무 지표 — 강제됨 | 지킴: 상위/개발자 클래스 분리, 한 번만 생성, 해시 감지, 안 쓰는 파일 정리 | 생성 시점에 컨텍스트 검사 (타입·이벤트), 애그리거트 멤버는 엔드포인트 없음 | Spring Boot 백엔드 (Java/Kotlin), TypeScript 클라이언트, Ossie 의미 모델 | 활발, Apache-2.0 |
| JHipster (JDL) | 필드 검증만 | 덮어씀 (물어본 뒤), 우회책으로 "side-by-side" | 생성된 ArchUnit 테스트가 기술 계층을 검사, JDL 에 바운디드 컨텍스트 없음 | 풀스택: Spring Boot + Angular/React/Vue, Liquibase, 테스트, 배포 | 활발 (9.x), Apache-2.0 |
| Context Mapper (CML) | 생명주기 상태·전이 — 문서로만, 강제 없음 | 해당 없음 (코드 없음) | 컨텍스트와 관계 패턴을 가장 풍부하게 모델링, 강제는 없음 | 다이어그램, 서비스 계약(MDSL), JHipster JDL | 마지막 릴리스 2024-08, Apache-2.0 |
| Sculptor | Bean Validation 제약 | 지킴: generation gap (상위 + gap 클래스) | 모듈(순환 금지), 애그리거트 | Java, JPA, Spring | 마지막 릴리스 2015 |
| Spring Modulith | 다루지 않음 | 해당 없음 (코드 없음) | 테스트에서 모듈 의존 검증, 이벤트 발행 기록 | 문서 (C4/UML) | 활발 (2.1), Apache-2.0 |
| Amplication | 엔티티·동작별 역할 권한, 로직은 손으로 | 지킴: 상위 클래스는 덮어쓰고 하위 클래스는 유지 | 여러 서비스, 바운디드 컨텍스트 모델 없음 | NestJS/Prisma 또는 .NET 서비스, 관리 UI | 사이트가 다른 제품으로 연결됨, 저장소는 보관되지 않음 |
| Jmix | Bean Validation, 행 단위 보안 역할 | 해당 없음 (소스를 직접 편집) | 문서에서 찾지 못함 | 엔티티와 CRUD 화면, 나머지는 런타임 플랫폼 | 활발 (3.0), Apache-2.0 + 유료 Studio 기능 |
| Wasp | 선언형 없음 (오퍼레이션 안에서 직접 검사) | 해당 없음 (컴파일러가 연결 코드 생성) | 단일 앱 | React/Node 연결: 인증, RPC, CRUD, 잡 | 0.x, 자체 DSL 은 TypeScript 명세로 대체, MIT |
| Bootify | 필드 검증 | 내보내기 브랜치를 덮어쓰고 직접 병합 | Spring Modulith 모듈과 생성된 테스트 | Spring Boot 코드, 마이그레이션, 프론트엔드 | 상용 (무료 플랜 있음) |
| AI 가 Spring 을 직접 작성 | 프롬프트대로, 매번 새로 | 해당 없음 | 리뷰로 | 무엇이든 | - |

## JHipster 와 JDL

JHipster 는 Spring Boot 용으로 성숙하고 널리 쓰이는 애플리케이션 생성기입니다.

- JDL 이 적는 것: 애플리케이션, 배포, 엔티티, 검증 규칙이 붙은 필드(`required`, `min`, `max`, `pattern` ...), 관계,
  enum, 그리고 DTO·서비스 클래스·페이징·필터 같은 옵션
- ElastiCORE 에 없는 생성물: 완전한 Angular·React·Vue 프론트엔드, Liquibase 변경 이력
- 기술 계층 검사: ArchUnit 테스트(`TechnicalStructureTest`)를 생성합니다

ElastiCORE 가 다른 점:

- **DSL 에 데이터만이 아니라 업무 규칙이 담깁니다.** JDL 의 키워드는 애플리케이션, 엔티티, 관계, 옵션입니다.
  생명주기, 동작별 권한, 행 소유, 불변식, 파생 값을 적는 구문은 없습니다. ElastiCORE 에서는 각각이 한 줄이고,
  강제 코드와 HTTP 응답까지 생성합니다.
- **생성이 멈추지 않습니다.** JHipster 는 엔티티 생성기를 다시 돌리면 직접 고친 코드를 덮어쓴다고 경고합니다. 그래서
  공식 문서는 고친 것을 지키는 방법으로 "side-by-side" 배치를 권합니다(생성된 클래스를 상속하고, 컨트롤러를 따로 둠).
  ElastiCORE 는 생성물마다 모델의 몫과 개발자의 몫을 나눠 두므로, 다시 생성하는 것이 평소에 바꾸는 방법입니다.
- **구조와 언어는 같은 모델을 읽는 방식입니다.** 레이어드·DDD·헥사고날, Java·Kotlin, Boot 3·4 중에서 고릅니다.
- **경계는 도메인 경계입니다.** JHipster 의 ArchUnit 테스트는 기술 계층(웹, 서비스, 영속)을 검사합니다.
  ElastiCORE 는 바운디드 컨텍스트와 애그리거트를 검사합니다.

UI 와 배포까지 포함한 풀스택 애플리케이션을 빨리 생성한 뒤 손으로 개발해 나가려면 JHipster 를 고르세요.

## Context Mapper

Context Mapper 는 전략적 DDD 모델링의 대표 DSL 입니다.

- 모델링하는 것: 컨텍스트 지도, 바운디드 컨텍스트, 서브도메인, 그 사이의 관계 패턴(Customer/Supplier,
  Open Host Service, Anticorruption Layer ...)
- 그 밖에: Sculptor DSL 을 바탕으로 한 전술 부분, 명령과 이벤트의 흐름, 생명주기 상태와 오퍼레이션의 전이 선언
- 만드는 것: 다이어그램, 서비스 계약, JHipster JDL. 실행되는 코드는 만들지 않으므로, 모델링한 것이 어디서도 강제되지 않습니다.

ElastiCORE 의 컨텍스트 선언(`config.context`)은 일부러 더 작게 잡았습니다. 무엇을 쓰고 무엇을 내보내는지만 적습니다.
대신 **강제됩니다.**

- 선언 없이 경계를 넘는 타입이나 이벤트는 생성 단계에서 실패합니다.
- 컨텍스트 지도는 코드가 실제로 하는 일에서 그려집니다.
- 생명주기는 생성된 애그리거트에서 강제됩니다.

컨텍스트 지도를 탐색하고 토론하는 데는 Context Mapper 를 쓰세요. ElastiCORE 는 관계 패턴을 모델링하지 않습니다.

## Sculptor

Sculptor 는 Xtext 기반의 DDD DSL 로, Java·JPA·Spring 코드를 생성했습니다. 생성된 상위 클래스와 손으로 쓰는 하위
클래스로 나누는 generation gap 패턴을 썼고, ElastiCORE 도 애그리거트 루트에 같은 방식(`XxxBase` + `Xxx`)을 씁니다.
Maven Central 의 마지막 릴리스는 2015년입니다. ElastiCORE 는 이 생각을 이어 받아 다음을 더했습니다.

- 모델에 담는 업무 규칙
- 현재의 Spring Boot
- Kotlin
- 확장할 수 있는 규칙 엔진

## Spring Modulith

Spring Modulith 는 하나의 Spring Boot 애플리케이션을 애플리케이션 모듈로 나누는 라이브러리입니다.

- 모듈이 허용된 대로만 서로 의존하는지 검증합니다(`ApplicationModules.verify()`).
- 이벤트 발행을 기록해, 실패한 리스너를 다시 실행할 수 있게 합니다.
- 구조를 문서로 만듭니다.
- 이미 있는 코드 위에서 동작하고, 코드를 생성하지는 않습니다.

ElastiCORE 는 같은 종류의 경계를 한 단계 앞, 모델에서 말하고, 넘는 것을 생성 시점에 거절합니다. 같은 관심사를 다른
시점에 다루므로 함께 쓸 수도 있겠지만, 지금 함께 쓰려면 패키지를 직접 맞춰야 합니다.

- ElastiCORE 는 Modulith 설정을 생성하지 않습니다.
- 도메인별 패키지 배치가 Modulith 의 관례에 맞춰져 있지 않습니다. Modulith 는 named interface 로 선언하지 않은 모듈의
  하위 패키지를 내부로 봅니다.
- ElastiCORE 의 컨텍스트 간 이벤트는 한 애플리케이션 안의 Spring 이벤트이고, 자체 발행 기록은 없습니다. 서비스 밖으로
  나가는 이벤트는 트랜잭션 Outbox 가 맡습니다.

이미 있는 코드베이스의 모듈 경계를 검증하고 문서화하려면 Spring Modulith 를 고르세요.

## Amplication, Bootify 와 다른 생성기

- **Amplication**
  - 하는 일: 웹 UI 에서 정의한 엔티티로 NestJS/Prisma(또는 .NET) 서비스를 생성합니다. 엔티티·동작별 역할 권한을 두고,
    직접 쓴 코드는 덮어쓰는 상위 클래스의 하위 클래스에 남깁니다. 업무 로직은 손으로 씁니다.
  - 현재 상태: 2026년 10월 현재 웹사이트가 다른 제품으로 연결됩니다. 저장소는 보관(archive)되지 않았고 2026년 4월에
    릴리스가 있었습니다.
- **Bootify** 는 화면 빌더에서 Spring Boot 프로젝트를 생성하고, Spring Modulith 모듈도 만듭니다. Git 내보내기는 한 브랜치를
  덮어쓰고, 병합은 직접 합니다.
- **OpenAPI Generator** 는 도메인이 아니라 API 계약에서 출발하고, 인터페이스나 위임 패턴으로 손으로 쓴 구현을 분리합니다.

Amplication, Bootify 와 달리 ElastiCORE 의 모델은 저장소 안의 텍스트 파일이라 코드처럼 리뷰합니다(OpenAPI 명세도
그렇습니다). 세 가지 모두와 달리 업무 규칙 — 생명주기, 소유, 불변식, 파생 값 — 을 담고, 이는 Amplication 의 역할 권한보다
넓습니다.

## Jmix 와 Wasp

둘은 생성기라기보다 애플리케이션 플랫폼입니다.

- **Jmix**
  - 코드가 원본입니다. Studio 가 Java 엔티티를 직접 편집합니다.
  - UI 와 보안은 플랫폼이 런타임에 제공하며, 행 단위 보안 역할도 있습니다. ElastiCORE 는 이 점을 일부(`@owner`)만 갖추고 있습니다.
- **Wasp**
  - React/Node 애플리케이션의 연결(페이지, 라우트, 오퍼레이션, 인증)을 선언합니다. 0.24 부터는 자체 DSL 대신 TypeScript 명세로 씁니다.
  - 권한 검사를 포함한 로직은 개발자가 쓰는 함수에 맡깁니다.

## AI 에게 Spring 코드를 바로 쓰게 하는 것

AI 는 DSL 이 표현하지 못하는 것까지 무엇이든 쓸 수 있습니다. 그 자유가 곧 비용입니다.

- **리뷰.** 생성된 모든 줄이 리뷰할 줄입니다. ElastiCORE 에서 리뷰하는 변경은 DSL 입니다. DSL 한 줄이 생성 코드를
  얼마나 움직이는지는 [변경 증폭 측정](benchmarks/change-amplification.ko.md)에 있습니다.
- **일관성.** 손으로 쓰다 보면 한 엔드포인트에는 권한 검사를 넣고 다른 곳에서는 빠뜨리는 결함이 흔합니다. 생성된
  권한 검사는 모든 진입점에서 같은 코드입니다.
- **결정성.** 같은 것을 두 번 물으면 모델은 다른 코드를 쓸 수 있습니다.
  - ChatGPT 를 829개 코딩 문제로 실험한 연구(Ouyang 외, ACM TOSEM)는 세 벤치마크에서 "요청마다 같은 테스트 출력이 하나도
    없는(zero equal test output across different requests) 과제의 비율이 75.76%, 51.00%, 47.56%" 라고 보고합니다.
  - 같은 연구는 "temperature 를 0 으로 해도 코드 생성의 결정성이 보장되지 않는다" 고 적습니다.
  - 같은 DSL 은 같은 코드를 냅니다. 그래서 재생성이 안전하고, diff 에는 모델이 바꾼 것만 보입니다(다시 쓴 파일의
    `//ecd:` 머리 줄 제외).
- **피드백.** 잘못된 DSL 은 무엇이 틀렸는지 말하는 한 문장과 함께 거절되거나 경고됩니다. 에이전트가 스스로 닫을 수 있는
  짧은 루프입니다.

둘은 배타적이지 않습니다. 의도한 작업 방식은 이렇습니다.

- AI 가 DSL 을 고치고, 빌드의 답을 읽고, 생성된 코드는 건드리지 않습니다.
- DSL 이 끝나는 곳(행위 클래스, 서비스 확장, 포트)은 손으로 쓴 코드가 맡습니다.

## ElastiCORE 가 맞지 않는 경우

- 프론트엔드가 일의 대부분인 UI 중심 애플리케이션 (JHipster, Jmix)
- 관계 패턴까지 포함한 컨텍스트 지도를 탐색·토론하는 일 (Context Mapper)
- 영속이 관계형이 아니거나 JPA 가 아닌 시스템 (MongoDB 지원은 있지만 범위가 좁음)
- 선언형 모델에 맞지 않는 로직: 복잡한 계산, 여러 애그리거트에 걸친 워크플로. ElastiCORE 는 그 코드를 둘 자리를
  명시적으로 남기지만, 쓰는 것은 개발자입니다.

## 출처 (2026-10-06 확인)

- JHipster: [JDL](https://www.jhipster.tech/jdl/intro), [옵션](https://www.jhipster.tech/jdl/options),
  [필드와 검증](https://www.jhipster.tech/jdl/entities-fields),
  [엔티티 생성](https://www.jhipster.tech/creating-an-entity/),
  [생성과 직접 작성 코드의 결합](https://www.jhipster.tech/tips/035_tip_combine_generation_and_custom_code.html),
  [생성기 저장소](https://github.com/jhipster/generator-jhipster) (JDL lexer, `TechnicalStructureTest` 템플릿)
- Context Mapper: [언어 레퍼런스](https://contextmapper.org/docs/language-reference/),
  [애플리케이션·프로세스 계층](https://contextmapper.org/docs/application-and-process-layer/),
  [애그리거트와 생명주기](https://contextmapper.org/docs/aggregate/),
  [생성기](https://contextmapper.org/docs/generators/),
  [JHipster 생성](https://contextmapper.org/docs/jhipster-microservice-generation/),
  [릴리스](https://github.com/ContextMapper/context-mapper-dsl/releases)
- Sculptor: [저장소](https://github.com/sculptor/sculptor),
  [고급 튜토리얼](https://sculptor.github.io/documentation/advanced-tutorial),
  [Maven Central](https://repo1.maven.org/maven2/org/sculptorgenerator/sculptor-maven-plugin/maven-metadata.xml)
- Spring Modulith: [검증](https://docs.spring.io/spring-modulith/reference/verification.html),
  [이벤트](https://docs.spring.io/spring-modulith/reference/events.html),
  [문서화](https://docs.spring.io/spring-modulith/reference/documentation.html),
  [Maven Central](https://repo1.maven.org/maven2/org/springframework/modulith/spring-modulith-core/maven-metadata.xml)
- Amplication: [저장소](https://github.com/amplication/amplication), [문서 저장소](https://github.com/amplication/docs)
- Jmix: [엔티티 디자이너](https://docs.jmix.io/jmix/studio/entity-designer.html),
  [행 단위 역할](https://docs.jmix.io/jmix/security/row-level-roles.html),
  [DB 마이그레이션](https://docs.jmix.io/jmix/data-model/db-migration.html),
  [3.0 릴리스](https://docs.jmix.io/jmix/whats-new/release-3.0.html)
- Wasp: [문서](https://wasp.sh/docs), [Wasp 명세](https://wasp.sh/docs/general/wasp-ts-config),
  [액션](https://wasp.sh/docs/data-model/operations/actions)
- Bootify: [엔티티](https://bootify.io/docs/entities-tab.html), [모듈](https://bootify.io/docs/modules-tab.html),
  [Git 연동](https://bootify.io/docs/git-integration.html)
- OpenAPI Generator: [커스터마이징](https://openapi-generator.tech/docs/customization/)
- Ouyang, Zhang, Harman, Wang, *An Empirical Study of the Non-determinism of ChatGPT in Code Generation*, ACM TOSEM —
  [arXiv:2308.02828](https://arxiv.org/abs/2308.02828)
