### New Features (v3 규칙 엔진 - Java·Kotlin 병합)

#### Java·Kotlin 소스도 병합 - 첫 줄 `//ecd:` 없이, 2.x 에서 올라오는 프로젝트의 고친 파일까지
- 왜: 생성 파일 병합(generated-merge)이 Java·Kotlin 을 빼고 있었다. 고친 Java 파일은 `SKIP(user-modified)` 로 DSL 을 더
  따라가지 않았고, 한 번만 생성하는 파일(확장 서비스·컨트롤러)은 DSL 이 바뀌어도 경고만 했다. 줄 단위 병합만으로는 DSL 이
  넣는 import 와 개발자가 넣은 import, DSL 의 새 필드와 개발자의 새 메서드가 한 자리에서 충돌했다(측정: 날짜 필드 하나에
  DTO 둘이 충돌).
- 무엇:
  - 3.0 코드의 Java·Kotlin 소스도 표시 없이 쓰이고 `.elasticore/cache` 를 기준으로 병합된다. 소스 병합: import 는 묶음으로
    (양쪽이 넣은 것은 모두, 개발자가 지운 것은 지운 채), 클래스 안에서 양쪽이 같은 자리에 더하기만 한 줄은 둘 다(생성된 줄
    먼저), 한쪽이 바꾼 줄 옆에 다른 쪽이 더하기만 한 줄은 그 옆에. 같은 줄을 각자 바꾼 것만 충돌.
  - 한 번만 생성하는 파일: 고치기 전에는 DSL 을 따라가고, 고친 뒤에는 깔끔하게 합쳐지는 DSL 의 변경만 받는다(`MERGED`).
    겹치면 충돌 표시 없이 그대로 두고(`KEPT(yours ...)`) 전부터 있던 경고(엔드포인트, 로그인, 생성자, 클래스 어노테이션,
    베이스 상속)로 옮길 것을 말한다. 예: `@readonly` 는 손대지 않은 쓰기 엔드포인트를 개발자 컨트롤러에서도 지우고,
    `@signin` 을 지우면 손대지 않은 로그인 엔드포인트가 빠진다 - 전에는 경고만 했다.
  - 표시가 있는 파일은 첫 병합 빌드에서 파일마다 넘겨받는다 - 기준을 표시의 해시로 확인할 수 있을 때만: 고치지 않았으면
    그 내용, 고쳤으면 이번 빌드가 만드는 것 또는 - 2.x 프로젝트가 `output = 'v3'` 로 옮기는 첫 빌드에서 - 2.x 번들을
    메모리에서만 한 번 더 돌려 만든 것(`[elcore3] upgrade : ...`). 확인할 수 없으면 표시를 두고 전처럼 건너뛴다.
  - 빌드 요약에 `=== merge: N merged with your edits / N kept as yours / N conflict(s) / N still stamped ... ===` 와 표시가
    남은 파일 목록.
  - 오래된 파일 정리는 개발자 클래스가 아직 상속하는 `...Base` 를 남긴다(표시 방식과 같게). 그리고 DSL 이 더 만들지
    않는 생성 클래스라도 남는 소스가 아직 쓰면(같은 패키지, import, 전체 이름) 지우지 않고 어느 파일이 쓰는지 알린다
    (`... is no longer generated, but ... still uses it - kept`) - 쓰는 곳이 없어진 다음 빌드에서 지워진다. 지워지는
    엔티티의 파일끼리만 서로 쓰는 것은 전처럼 함께 지워진다(엔티티 클래스와 그 `...Base` 도 한 번에). 남는 매퍼 인터페이스는 같은 이름의 XML 도 남긴다. 전에는
    2.x template 을 `output = 'v3'` 로 옮기는 첫 빌드가 2.x 호환 번들이 쓴(3.0 표시) `CouponCoreService` 를 지워, 개발자
    컨트롤러가 컴파일되지 않았다. 병합하는 빌드에서만 - 2.x 코드(`output = 'v2'`)는 전처럼 지운다.
  - 소스의 import 묶음은 맨 위의 import 와 그 사이 빈 줄뿐이다: 그 사이의 주석(주석 처리한 import 포함)부터는 줄로
    병합되어 남고, 코드 안(텍스트 블록)이나 주석 안의 `import` 는 import 가 아니다.
  - 2.x 프로젝트의 첫 3.0 빌드는 `-Pdomain` 으로 하면 멈춘다(다른 도메인의 고친 파일이 기준을 찾을 기회를 잃으므로).
    그 빌드가 2.x 코드를 메모리에서 만든 뒤에는 모델을 다시 읽어, 업그레이드 빌드와 다음 빌드가 같은 코드를 쓴다.
  - 표시도 없고 캐시에도 없는 소스는 그대로 두고 요약에 센다 - 캐시 파일이 없으면 git 에서 가져오라고 한다.
  - 마이그레이션 가이드: 2.x 는 `@service`·`@expose` 없이도 키가 있는 엔티티에 리포지토리를, `@template`·`@searchable`
    DTO 가 있으면 서비스와 컨트롤러까지 썼고 v3 는 `@expose`·`@service` 엔티티만 쓴다는 줄, `@ElastiCore` 로
    생성하던 프로젝트는 옮길 때 그것을 지운다는 줄(남기면 컴파일마다 2.x 코드가 다시 쓰여 병합에 편집으로 들어온다).
  - 3.0 미발표 출력의 기본 변경: Java·Kotlin 소스의 첫 줄 표시가 빠진다. 샘플의 한 번만 생성한 파일 넷(pet store
    `CustomerService`·`PurchaseOrderService`·`OrderLine`, MyBatis pet store `PurchaseOrderService`)이 표시 줄만 빠졌다.
    2.x 코드(`output = 'v2'`)는 그대로(`elcoreRulesCheck` 157/0/2, `elcoreTreeCheck` 0/0/0).
- 확인:
  - `ThreeWayMergeTest`(import 묶음, 같은 자리 추가, 바뀐 줄 옆 추가, Kotlin, import 사이 주석, 텍스트 블록),
    `GeneratedMergeTest`(Java 편집과 날짜 필드, 한 번만 생성하는 파일의 세 경우, 표시 넘겨받기와 확인 못 하는 기준,
    2.x → 3.0 업그레이드와 그 다음 빌드, `-Pdomain` 업그레이드 거절, 개발자 코드가 쓰는 오래된 클래스와 매퍼 XML,
    2.x 코드의 정리), `SignInAndReferencesTest`.
  - 실제 프로젝트 측정: template(2.x) 과 elasticore-petstore 에 모든 엔티티 필드 추가 - 바뀐 생성 파일 119 개 충돌 0;
    template 의 2.x → 3.0 - 바뀐 187 개 충돌 0(줄 단위만이면 1).
  - 샘플 시나리오(작업 트리에서 실제 생성·컴파일·테스트): MyBatis pet store 24/24, JPA pet store 23/23, template-kt
    21/21, template-ddd 23/23, template-mybatis 24/24 - 편집(생성 DTO 의 import·메서드·주석, 서비스, 매퍼 XML, DDL)
    뒤 필드 추가가 병합되고 컴파일·테스트 통과, 이름 바꾸기 충돌이 빌드를 멈추고 풀면 계속, 엔티티를 넣었다 빼면
    고치지 않은 파일은 지워지고 고친 것은 남고 알림. (샘플의 `elcore3clean` 은 생성 전에 소스를 지우므로 `-x
    elcore3clean` 으로.)
  - 2.x template 을 가이드대로 옮기기(`output = 'v3'`, `@ElastiCore` 제거, 서비스를 쓰던 엔티티에 `@expose`) 15/15:
    표시가 있는 2.x 파일 263 개를 기준으로 읽고, 고친 DTO 와 CoreService 는 표시 없이 편집이 남은 채 병합, 고치지 않은
    파일은 3.0 코드, 두 번째 빌드는 그대로, 컴파일 통과.
- 관련 파일: `util/ThreeWayMerge.java`(mergeSource), `verify/RulesGenRunner.java`(writeMergedSource·confirmedBase·
  twoXBases·rewriteOwned·removeStale·removeStaleMerged·removeUnused·stillUsed·printSummary), `util/ConsoleLog.java`(snapshot·restore),
  `elasticore-env-guide.md` 9-2, `elasticore-rule-guide.md` 7.5, `elasticore-migration-guide.md`
