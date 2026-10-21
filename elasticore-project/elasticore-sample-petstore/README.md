# YAPS Pet Store - ElastiCORE 3.0 샘플

[agoncal-application-petstore-ee7](https://github.com/agoncal/agoncal-application-petstore-ee7)(Java EE 7)을
**ElastiCORE 3.0 + Spring Boot 4** 헥사고날로 다시 만든 쇼핑몰입니다. 생성기 변경마다 생성·컴파일·실행되는
저장소의 샘플 모듈이기도 합니다.

```bash
./gradlew :elasticore-project:elasticore-sample-petstore:test       # MockMvc 흐름 테스트
./gradlew :elasticore-project:elasticore-sample-petstore:webBuild   # 화면 (Node.js 필요)
./gradlew :elasticore-project:elasticore-sample-petstore:run        # http://localhost:8080
```

데모 계정: `user` / `user`, `admin` / `admin` (`petstore.demo-accounts=false` 로 끔). API 문서: `/swagger-ui.html`.

**업무를 DSL 로 어떻게 표현했는지**는 실행한 뒤 `/dsl-guide.html` (쇼핑몰 헤더의 "DSL 가이드") 에 업무 단위로 있습니다 -
업무마다 규칙, DSL, 확인한 응답, 손으로 쓴 것, DSL 로 못 하는 것. 페이지는 `python guide/build_guide.py` 가 실제 DSL 과
생성된 소스에서 만듭니다(빌드 뒤 실행). 같은 DSL 에서 Apache Ossie 시맨틱 모델 `blueprint/semantic_model.yml` 도 생성됩니다
(`elasticore { rules = ['ossie-export'] }`, `SemanticModelTest` 가 실제 DB 와 대조). 업무 지표(매출·주문 수·객단가 …)는
`model/metrics.yml` 에 있고 같은 파일의 `metrics` 로 나갑니다 (`MetricsTest` 가 실제 데이터로 확인).

## DSL 이 말하는 것 (`src/main/resources/blueprint/petstore/model`)

| 업무 규칙 | DSL |
|---|---|
| 카탈로그는 누구나 보고 관리자만 고친다 | `@access(read=public)` |
| 주문은 로그인한 고객의 것, 조회·취소는 본인만, 배송은 관리자 | `@owner(customerId) @access(read=owner, create=user, cancel=owner)` |
| 가입자는 스스로 관리자가 될 수 없다 | `role: UserRole @access(admin)` |
| 비밀번호는 해시로만, 로그인 | `password: string(255)! @password`, `login: ... @login` |
| 주문은 접수 -> 배송 / 취소, 배송 후엔 취소 불가 | `states: status: { initial: PLACED, ship: PLACED -> SHIPPED, cancel: PLACED -> CANCELLED }` |
| 줄의 단가는 주문 당시 가격 | `unitCost: money(10.2) @snapshot(item.unitCost)` |
| 소계·합계 | `@compute(unitCost * quantity)`, `@compute(sum(lines.subTotal))` |
| 카드번호는 응답에 없다, 주소 필수값은 400 | 값 객체의 `@writeonly`, `!` |
| 집 주소·카드는 필수 | `homeAddress: Address! @Embedded`, `creditCard: CreditCard! @Embedded` |

## 손으로 쓴 것 (Java 7개 파일, 빈 줄 빼고 231줄)

| 파일 | 내용 |
|---|---|
| `domain/model/PurchaseOrder.java` | VAT·할인 정책(설정 값), 기본 배송지 = 집 주소 |
| `application/PurchaseOrderService.java` | 정책 값 주입, 처음 저장될 때 `place()` |
| `adapter/in/web/shop/AccountController.java` | 가입하고 바로 로그인 (로그인·로그아웃·me 는 `@login` + `@password` 로 생성) |
| `DemoAccounts.java`, `PetstoreApplication.java` | 데모 계정, 빈 2개 |

나머지 - 엔티티·DTO·매퍼·리포지터리·서비스·컨트롤러·권한 검사·상태 전이·가격 계산·TS 클라이언트 - 는 생성됩니다.
화면(`web/src`)은 생성된 TS 클라이언트의 라벨(`OrderStatusLabel`), 검증(`validateAddress`), 서버 오류(`ApiError.fieldErrors`),
버튼 판단(`canCancel`)을 씁니다.

## 라이선스

카탈로그 텍스트·이미지(`data.sql`, `static/images`)는 원본의
[CC BY-SA 3.0](http://creativecommons.org/licenses/by-sa/3.0/) (Antonio Goncalves) 을 따릅니다.
