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
  - 오래된 파일 정리는 개발자 클래스가 아직 상속하는 `...Base` 를 남긴다(표시 방식과 같게).
  - 3.0 미발표 출력의 기본 변경: Java·Kotlin 소스의 첫 줄 표시가 빠진다. 샘플의 한 번만 생성한 파일 넷(pet store
    `CustomerService`·`PurchaseOrderService`·`OrderLine`, MyBatis pet store `PurchaseOrderService`)이 표시 줄만 빠졌다.
    2.x 코드(`output = 'v2'`)는 그대로(`elcoreRulesCheck` 157/0/2, `elcoreTreeCheck` 0/0/0).
- 확인:
  - `ThreeWayMergeTest`(import 묶음, 같은 자리 추가, 바뀐 줄 옆 추가, Kotlin), `GeneratedMergeTest`(Java 편집과 날짜 필드,
    한 번만 생성하는 파일의 세 경우, 표시 넘겨받기와 확인 못 하는 기준, 2.x → 3.0 업그레이드), `SignInAndReferencesTest`.
  - 실제 프로젝트 측정: template(2.x) 과 elasticore-petstore 에 모든 엔티티 필드 추가 - 바뀐 생성 파일 119 개 충돌 0;
    template 의 2.x → 3.0 - 바뀐 187 개 충돌 0(줄 단위만이면 1).
  - 샘플 시나리오(작업 트리에서 실제 생성·컴파일·테스트): MyBatis pet store, JPA pet store, template-kt, template-ddd, ...
    - 편집(생성 DTO 의 import·메서드·주석, 서비스, 매퍼 XML, DDL) 뒤 필드 추가가 병합되고 컴파일·테스트 통과, 이름 바꾸기
    충돌이 빌드를 멈추고 풀면 계속, 엔티티를 넣었다 빼면 고치지 않은 파일은 지워지고 고친 것은 남고 알림.
- 관련 파일: `util/ThreeWayMerge.java`(mergeSource), `verify/RulesGenRunner.java`(writeMergedSource·confirmedBase·
  twoXBases·rewriteOwned·removeStaleMerged·printSummary), `util/ConsoleLog.java`(snapshot·restore),
  `elasticore-env-guide.md` 9-2, `elasticore-rule-guide.md` 7.5, `elasticore-migration-guide.md`
