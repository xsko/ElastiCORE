### Documentation (v3 규칙 엔진 - 한 번만 생성하는 파일)

#### 가이드가 한 번만 생성하는 파일을 "다시 쓰이지 않는다"고 하던 곳을 병합에 맞춤
- 왜: Java·Kotlin 병합(merge-java) 뒤로 한 번만 생성하는 파일(컨트롤러, 확장 서비스, 나뉜 루트의 개발자 클래스, 팩토리)은
  고치기 전에는 DSL 을 따라가고, 고친 뒤에는 깔끔하게 합쳐지는 변경만 받는다. 그런데 가이드 여러 곳이 아직 "처음 한 번만
  생성되고 그 뒤로는 건드리지 않는다", "규칙은 처음 만들어질 때만 적용된다", "Java 파일은 고치면 건너뛴다"고 해, 나중에
  더한 규칙이나 `supportAI` 를 손으로 옮겨야 하는 것처럼 읽혔다.
- 무엇: `elasticore-rule-dev-guide.md` 2장 끝("이미 만들어져 내가 고친 파일에도 붙습니다")·4장 `target` 끝·문제 해결 표,
  `elasticore-ddd-customize-guide.md` "이미 있는 내 파일"·스켈레톤 첫 줄 표·Kotlin 표·문제 해결 표,
  `elasticore-env-guide.md` `supportAI` 를 나중에 켤 때와 MyBatis 직접 쓰는 쿼리, `elasticore-migration-guide.md`
  "손으로 고쳐둔 파일" 질문, `elasticore-rule-guide.md`·`elasticore-annotation-guide.md` 의 `@factory` 와 도메인 서비스 예.
  손으로 붙여야 하는 경우는 그대로 적었다: 겹쳐서 `KEPT`, `merge = false`, 병합 전 버전이 만든 파일을 고친
  `SKIP(user-owned)`, 표시 줄 없이 복사한 파일(`SKIP(no ElastiCORE header - yours)` - ERP 예제의 세 파일). 빠진 클래스
  어노테이션 알림은 여전히 엔티티 클래스에만 나온다. 이전 스냅샷에서 올라오는 프로젝트를 위한 안내(`XxxApiErrors`,
  `OrderService` 생성자)는 그 파일들이 기준을 확인할 수 없어 여전히 맞으므로 두었다.
- 생성물은 그대로다. 팩토리와 도메인 서비스가 생성하는 주석의 "written once" 도 두었다 - 개발자 파일의 생성 줄을 바꾸면
  그 줄을 고치거나 지운 개발자의 파일이 매 빌드 `KEPT` 로 남아 그 뒤의 DSL 변경을 받지 못한다.
- 확인: `GeneratedMergeTest` -
  `aRuleAddedLaterReachesAnEditedSkeleton`(나중에 더한 프로젝트 규칙의 클래스 어노테이션이 고친 컨트롤러에 `MERGED`,
  규칙을 빼면 다시 빠짐), `aRuleAddedLaterReachesTheEditedClassOfASplitRoot`(DDD 루트의 고친 개발자 클래스에
  `@AggregateRoot` 와 import 가 병합, 메서드는 남음, "add them by hand" 알림 없음),
  `supportAiTurnedOnLaterReachesAnEditedService`(`supportAI` 를 나중에 켜면 고친 `CustomerService` 에 `@Tool` 병합),
  `aFactoryWithItsBodyWrittenFollowsTheSignatureOrSaysSo`(손대지 않은 팩토리는 새 필수 필드를 시그니처에 받고, 본문을 쓴
  팩토리는 `KEPT` 와 알림).
- 관련 파일: 위 가이드 6개, `DddFactoryRules`(Javadoc), `GeneratedMergeTest`, `com.acme.probe.AddedLaterRules`
