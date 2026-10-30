### Documentation (마이그레이션 가이드 - `withJoinFetches()` 가 생기는 서비스)

#### 2.x 는 모든 CoreService 에 `withJoinFetches()` 를 만든다 - v3 로 옮기면 연관이 없는 엔티티의 호출이 컴파일되지 않는다
- 왜: `@fetchjoin` 항목이 "2.x 와 같이 그 연관이 있는 엔티티에만 생성" 이라고 했다. 2.x 는 연관이 없어도 만든다 - 아무 조건도
  더하지 않는 빈 Specification(`return null`). 그래서 그런 엔티티의 확장 서비스가 `.and(withJoinFetches())` 를 부르는 2.x
  프로젝트는 v3 로 옮기면 컴파일되지 않는데, "2.x 코드에서 옮길 때 손볼 것" 표에 없었다. 개발 가이드(12장)는 이미 바르게 적고
  있었다
- 확인: 2.x 기준 트리 `elasticore-template` 의 CoreService 24 개 모두에 `withJoinFetches()` 가 있고, 연관이 없는
  `CouponCoreService` 는 빈 조건이다. 도메인 9개짜리 2.x 프로젝트(CoreService 53 개, 모두 있음)를 `-Pv3` 로 옮기자 v3
  CoreService 7 개에만 생겼고, 연관이 없는 엔티티의 확장 서비스 2 곳이 컴파일되지 않았다. v3 의 판정은
  `JpaServiceFileRules.joinFetchVars`(가져올 연관이 없으면 메서드를 쓰지 않음)
- `elasticore-migration-guide.md`: `@fetchjoin` 항목의 문장을 바로잡고, "손볼 것" 표에 `withJoinFetches()` 줄을 더했다 -
  2.x 에서도 아무것도 하지 않던 호출이므로 지운다
- 생성물은 그대로다
- 관련 파일: `elasticore-migration-guide.md`
