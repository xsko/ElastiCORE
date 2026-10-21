## 3.0.261021

같은 요구사항(도서·회원·대출 API)을 에이전트가 ElastiCORE 로 만들게 하고 어디서 Java 를 손으로 쓰는지 보았다. 네 군데였다:
서버가 정하는 값(반납일)을 클라이언트가 보내도 막을 수 없었고, "아직 반납하지 않은" 검색을 DSL 로 쓸 수 없었고,
ISBN 13자리 같은 정규식 검사는 `@pattern` 이 컴파일되지 않는 코드를 냈고, `member: Member!` 는 키(`memberId`)가 아니라
중첩 객체를 요구했다. 아래 네 항목이 그 자리를 DSL 로 메운다. 네 항목 모두 그 문법을 쓰지 않는 프로젝트의 생성 결과는
바뀌지 않고, 2.x 생성기와 2.x 와 같은 출력을 내는 `jpa-springboot` 번들은 그대로다(`elcoreRulesCheck -Pstrict` 150 MATCH /
0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra).

### New Features (DSL - 요구사항을 손코딩 없이)

#### 필드 `@readonly` 는 서버가 정하는 값 - v3 매퍼가 DTO 에서 엔티티로 쓰지 않는다
- 필드 `@readonly`(별칭 `@noinput`)는 지금까지 DTO 에 `@JsonProperty(access=READ_ONLY)` 만 붙였다. JSON 요청의 값은 Jackson
  이 버렸지만, v3 매퍼는 서버 코드가 DTO 에 넣은 값(`dto.setX(..)` 후 `service.save(dto)`)을 그대로 엔티티에 썼고, `!` 가
  붙은 `@readonly` 필드는 DTO 의 `@NotNull` 이 남아 그 값을 보낼 수 없는 생성 요청을 모두 400 으로 거절했다. DDD 의 나뉜
  루트에서는 매퍼가 요청 값을 `to.creation()::setX` 로 새 루트에 넣었다
- 이제 v3 번들(`jpa-springboot-v3`·DDD·헥사고날·Kotlin)에서 `@readonly`/`@noinput` 필드는
  - 매퍼의 DTO -> 엔티티 방향(`toEntity`, `mapping(dto, entity, ...)`, EntityManager 판 포함)이 쓰지 않는다. 엔티티 -> DTO
    방향은 그대로라 응답에 나간다. 엔티티 필드에 붙이면 그 엔티티의 모든 DTO 에, `dto.yml` 의 DTO 필드에 붙이면 그 DTO 의
    매핑에만 적용된다. `@id` 에 붙은 `@readonly` 는 식별자 쓰기를 그대로 둔다
  - DTO 의 `@NotNull`·`@NotBlank` 를 뺀다. 엔티티 컬럼(`nullable = false`)은 그대로라 서버가 채운다
  - DTO 의 `@Schema` 에 `accessMode = Schema.AccessMode.READ_ONLY` 를 더한다. springdoc 은 `@JsonProperty` 만으로도
    `readOnly: true` 를 냈다 - `@Schema` 만 읽는 도구를 위해 적는다
  - 나뉜 루트에서는 여전히 잠긴 필드다: `protected` setter 와 `creation()` setter 가 남아 서버 코드가 새 루트에 넣는다
- v3 동작 변경: 필드에 `@readonly` 를 붙이고 서버 코드에서 DTO 에 값을 넣어 저장하던 코드는 그 값이 더는 저장되지 않는다.
  엔티티에 넣는다(확장 서비스의 `beforeSave`, 루트의 메서드). HTTP 동작은 같다 - JSON 의 값은 이전에도 버려졌다. 이유:
  `@readonly` 는 "요청으로 받지 않는다" 는 선언인데 매퍼가 그 선언을 무시해, 서비스가 `creation().setX(null)` 같은 우회를
  쓰고 있었다(새 루트에서는 아무 일도 하지 않는다). `@readonly` 가 없는 프로젝트의 v3 출력은 바뀌지 않는다
- 가이드: annotation guide 에 `@readonly / @noinput` 절(생성물 표, `@behavior` 와의 비교, `creation().setX(null)` 은 새 루트에서
  아무 일도 하지 않는다는 것), §2.8 감사 필드, jsonview 표, `@behavior` 는 나뉜 루트에서만 효과가 있다는 것. dsl guide 필드 표와
  `@behavior` 행, rule guide 의 잠긴 루트 표와 '새 루트의 null'
- 확인: 규칙 단위 테스트(Java·Kotlin 매퍼, DTO, 2.x 호환 출력, 나뉜 루트, `@editable` 루트), hexagonal Java·Kotlin 도서관 모델을
  `RulesGenRunner` 로 생성해 매퍼·DTO·`LoanBase` 를 읽는 테스트. `elasticore-template-ddd` 의 catalog `Book` 에 `shelfCode:
  string(10)! @readonly` 와 그 값을 채우는 `beforeSave` 를 임시로 넣고 Spring Boot 3.3.2 에서 실행해 확인했다: 클라이언트가
  값을 보낸 POST·PUT 은 200 이고 `beforeSave` 의 값이 남는다. `service.save(dto)` 도 DTO 에 넣은 값 대신 `beforeSave` 의 값을
  저장한다. `/v3/api-docs` 는 `readOnly: true` 이다. 변경 전 출력에서는 POST 가 400(`shelfCode: must not be null`)이었고,
  DTO 에 넣은 값이 저장되었다
- 남은 것: DTO 의 `@ref(...)` 필드에 붙인 `@readonly` 는 아직 관계 쓰기(`reverseRefPass`)를 막지 않는다. 나뉜 루트의 필드
  `@behavior` 는 `@JsonProperty` READ_ONLY 만 붙고 `@Schema accessMode` 는 붙지 않는다
- 관련 파일: `WriteGuardRules.java`, `DtoBaseRules.java`, `MapperBaseRules.java`, `ServerSetFieldTest.java`, `SealedRootTest.java`,
  `ServerSetFieldGenerationTest.java`

#### `@search(isnull)` - 값이 비어 있는지로 검색한다
- "아직 반납하지 않은 대출", "아직 입고된 적 없는 상품"처럼 값이 비어 있는지로 찾으려면 Service 에
  `Q.Loan.returnedDate(Op.IS_NULL, null)` 를 손으로 쓰거나 Repository 에 JPQL 을 따로 두어야 했다. `@search(isnull)` 은
  2.x 와 규칙 엔진 모두에서 경고 없이 같은 값 비교(`c.equal`)로 만들어졌고, 가이드는 `ISNULL`/`ISNOTNULL` 을 미구현으로
  적고 있었다
- 이제 `returnedDate: date @search(isnull)` 은 검색 DTO 에 `Boolean returnedDateEmpty` 하나를 만든다. 보내지 않거나
  `null` 이면 조건 없음, `true` 면 `c.isNull(r.get("returnedDate"))`, `false` 면 `c.isNotNull(...)`. `@Schema` 설명은
  `... Field is empty (true) or set (false). field:returnedDate` 이고, 보내는 값이 날짜가 아니므로 `@DateTimeFormat`·
  `@JsonFormat`·`example` 은 붙지 않는다. `@ref(order.shippedDate)` 와 쓰면 `r.get("order").get("shippedDate")` 를
  검사하고, 쉼표 뒤 두 번째 값은 다른 조건처럼 엔티티 필드 이름이다(`@search(isnull,open)` -> `openEmpty`, `r.get("open")`)
- Kotlin(`val xEmpty = searchDTO.xEmpty`, `if(xEmpty!!)`, `Specification<E> { r,q,c -> c.isNull(...) }`)과
  TypeScript(`xEmpty?: boolean`)도 같은 규칙에서 나온다
- 필드 하나에 조건 하나: `isnull` 을 적은 필드는 `between` 의 From/To 를 만들지 않는다. `@expose` 의 타입별 자동
  할당(날짜 -> `between`)은 바뀌지 않는다
- v3 번들(`jpa-springboot-v3`·DDD·헥사고날·Kotlin·`ts-model`) 전용이다. 2.x 생성기와 2.x 호환 번들(`jpa-springboot`)은
  이전처럼 같은 값 비교를 만들고 `@Schema` 문구도 그대로다 - `StringUtils.getOperatorDescription(op)` 는 바뀌지 않고 새
  문구는 `getOperatorDescription(op, true)` 에만 있다
- v3 번들에서 이미 `@search(isnull)` 을 적어 둔 필드는 검색 속성이 `<필드>`(필드 타입, 같은 값 비교)에서
  `<필드>Empty`(Boolean)로 바뀐다. 미구현으로 문서화된 키워드가 조용히 같은 값 비교가 되던 것을 바로잡는 것이며,
  저장소의 블루프린트 중 쓰는 곳은 없다
- 확인: 단위 테스트로 Java·Kotlin·TypeScript 출력과 2.x 호환 출력이 그대로인 것을 확인했다. `elasticore-template-ddd`
  의 `Product.lastRestocked` 에 잠시 `@search(isnull)` 을 붙여 생성·컴파일하고, H2 위에서
  `POST /api/v1/shop/product/search` 가 `true` 는 입고일이 없는 상품만, `false` 는 있는 상품만, 생략과 `null` 은 둘 다
  돌려주는 것을 확인했다. `elasticore-template-kt` 의 `Project.endDate` 로 Kotlin 을 생성해 `compileKotlin` 이 통과하고
  TypeScript 에 `endDateEmpty?: boolean;` 이 나오는 것을 확인했다(샘플 변경은 되돌렸다)
- 남은 것: List 필드나 `!` 필드에 `isnull` 을 적어도 경고하지 않는다. v3 의 별칭 구분자는 쉼표인데 가이드 §3.1 은 `$` 로
  적고 있다(별개 버그)
- 관련 파일: `SearchDtoBaseRules.java`, `MapperSpecRules.java`, `StringUtils.java`, `SearchDtoTest.java`,
  `MapperSpecTest.java`, `TypeScriptModelTest.java`, `OperatorDescriptionTest.java`,
  `elasticore-annotation-guide.md`(3.2, 3.4.1, 3.8, 8.5), `elasticore-dsl-guide.md`, `elasticore-dev-guide.md`

### Bug Fixes (생성기)

#### `@pattern` 이 컴파일되는 `@Pattern` 을 쓰고, `@min` 처럼 값 다음에 메시지를 받는다
- 규칙 엔진의 v3·Kotlin 번들에서 `@pattern(regexp="...", message="...")` 이 컴파일되지 않았다. 네 줄짜리
  `@Pattern( regexp="...", msg="..." )` 를 썼는데 Jakarta `@Pattern` 에는 `msg` 속성이 없다. 이제 한 줄
  `@Pattern(regexp = "...", message = "...")` 을 쓴다
- 위치 형식 `@pattern('^[0-9]{13}$', 'ISBN must be 13 digits')`, `@pattern('^[A-Z]+$')` 도 읽는다. 이전에는 아무것도
  생성하지 않았다. 메시지는 따옴표와 괄호 밖의 첫 쉼표 뒤라서 `{10,13}` 은 정규식에 남는다. 메시지를 생략하면 TypeScript
  클라이언트와 같은 `"<라벨> is not in the expected form"` 을 쓴다. TS 의 `validate...()` 도 같은 값을 읽는다.
  이전에 아무 검사도 없던 필드(위치 형식, 메시지 없는 이름 형식)에 요청 검사(400)가 새로 생긴다
- Kotlin 은 문자열 템플릿이 되는 `$`(영문자·`_`·`{` 앞)를 `\$` 로 쓴다. `@Pattern` 은 문자열 필드에만 붙는다. 날짜의
  `@pattern(yyyyMMdd)` 는 이전처럼 날짜 형식이다. clean 출력은 문자열 필드의 `@pattern` 을 Swagger `example` 로 쓰지 않는다
  (위치 형식에서는 `^[0-9]{13}$', 'ISBN must be 13 digits` 같은 깨진 값이 나왔다)
- 정규식은 Java 문자열 리터럴의 내용으로 그대로 들어가므로 백슬래시는 `\\d` 처럼 두 번 쓴다. `\d` 처럼 하나만 쓰면
  규칙 엔진이 DSL 경고를 낸다(2.x 추출기는 경고에 실패하므로 규칙 엔진에서만)
- 2.x 생성기(`elcore`)와 2.x 호환 번들(`jpa-springboot`)의 출력은 그대로다(`msg=`, 이름 형식에 메시지가 있을 때만) -
  2.x 의 `@pattern` 은 여전히 컴파일되지 않는 코드를 내며, 2.x 호환을 위해 손대지 않았다
- 확인: 도서관 모델(`isbn: string(13)! @id @pattern('^[0-9]{13}$', 'ISBN must be 13 digits')`)을 layered·Kotlin·TS 로
  생성했다. Java DTO 는 javac, Kotlin DTO 는 kotlinc 1.9.24 로 컴파일했고, Hibernate Validator 8 이 `12345` 를
  "ISBN must be 13 digits" 로 거부하는 것을 확인했다. TS 의 `validateBook()` 이 node 에서 같은 메시지를 내는 것도 확인했다
- 남은 것: `=` 가 든 정규식은 이름 형식으로만 쓸 수 있다. 따옴표 하나로 감싼 이름 형식의 값은 공백에서 잘린다(기존 파서).
  DDD 루트의 저장 시점 검사(`@min` 처럼)는 하지 않는다
- 관련 파일: `FieldSpec.java`, `DtoBaseRules.java`, `FieldDoc.java`, `TypeScriptDtoFileRules.java`, `AbstractModelLoader.java`,
  `PatternAnnotationTest.java`, `DtoGapCoverageTest.java`, `elasticore-annotation-guide.md`

#### 필수 관계(`post: Post!`)는 저장 요청에서 FK id 필드로 요구한다
- `@expose`/`@service` 엔티티가 자동 생성하는 DTO 에서 `!` 가 붙은 to-one 관계는 중첩 DTO(`PostDTO post`)에 `@NotNull`·
  `requiredMode=REQUIRED` 가 붙고 FK id 필드(`postPostId`)에는 붙지 않았다. 매퍼는 저장/수정할 때 `postPostId` 만 읽으므로
  (`em.getReference`), 키만 보낸 요청은 `@Valid` 검증에서 거절되고 중첩 객체만 보낸 요청은 검증을 통과해도 관계를 연결하지
  못했다. 요청은 둘 다 보내야 했다
- 이제 v3·Kotlin·TypeScript 생성(`jpa-springboot-v3`·DDD·헥사고날·Kotlin·TypeScript 번들)에서는 FK id 필드가 `@NotNull`·
  `requiredMode=REQUIRED` 이고, 응답용 중첩 DTO(`accessMode = READ_ONLY`)는 선택이다. TypeScript 는 `post?: PostDTO;`·
  `postPostId: number;` 로 선언하고 `validateXxx` 가 `post` 를 요구하지 않는다. `ddd-reference` 를 쓰는 번들은 ID 필드(`memberId`)가
  이미 필수였고, 중첩 DTO 만 선택이 된다. 키 이름 규칙(`필드 + 키`, 키가 `id` 면 `topicId`)은 그대로다
- **동작 변경 (v3·Kotlin·TypeScript 사용자)**: 중첩 `post` 만 보내던 클라이언트는 이제 `@Valid` 검증에서 거절된다 - FK id 필드를
  보내야 한다(그 요청은 이전에도 관계를 연결하지 못했다). `postPostId` 를 함께 보내던 클라이언트는 그대로 동작한다. OpenAPI 의
  required 가 `post` 에서 `postPostId` 로 옮겨 간다
- 바뀌지 않는 것: 2.x 생성기(`elcore`)와 2.x 와 같은 출력을 내는 `jpa-springboot` 번들(중첩 DTO 에 `@NotNull` 그대로), 검색 DTO
  (`XxxSrchDTO` 의 `postPostId` 는 검색 조건이라 필수가 아니다), Entity(`@JoinColumn(nullable = false)`), 매퍼, `dto.yml` 에 직접
  선언한 DTO. `!` 가 붙은 엔티티 참조가 없는 프로젝트의 생성 결과는 바뀌지 않는다
- 확인: `Post`·`Comment`(`post: Post!`) 게시판을 v3 layered·Kotlin·TypeScript·호환 모드·2.x 로, `Member`·`Book`·`Loan`
  (`member: Member!`, `book: Book!`, Book 의 키 `isbn`)을 헥사고날로 생성해, v3 `CommentDTO` 의 `postPostId` 에 `@NotNull`·`REQUIRED`
  가 붙고 `post` 에는 없는 것, 키가 `id` 인 `topic: Topic!` 의 `topicId` 가 필수인 것, 선택 관계의 `postPostId` 와 `CommentSrchDTO` 의
  `postPostId` 가 그대로인 것, 헥사고날 `LoanDTO` 의 `memberId`·`bookIsbn` 이 필수이고 `member`·`book` 이 선택인 것, 호환 모드 출력이
  2.x 와 같은 것을 테스트로 확인했다. DTO 와 검색 DTO 가 함께 쓰는 키 필드는 복사본에만 `@notnull` 을 붙여, 어느 DTO 를 먼저
  만들어도 검색 DTO 가 바뀌지 않는 것도 확인했다
- 남은 것: TypeScript 의 `XxxFields` 는 FK id 필드를 여전히 `readOnly: true` 로 표시하고 `validateXxx` 가 검사하지 않는다
- 관련 파일: `SpecFactory.java`, `RulesGenRunner.java`, `RulesVerifyRunner.java`, `RequiredReferenceDtoTest.java`,
  `elasticore-annotation-guide.md`, `elasticore-dsl-guide.md`, `elasticore-rule-guide.md`

### Improvements (런타임 - 손으로 쓴 규칙의 409)

#### `ConflictException` - 손으로 쓴 규칙이 409 를 자기 메시지로 낸다
- "빌려줄 권수가 없다", "이미 반납한 대출이다" 같은 규칙을 `beforeSave` 나 루트 메서드에 쓸 때 409 로 답할 예외가 없었다.
  `DataIntegrityViolationException` 을 던지면 409 는 나가지만 `detail` 이 데이터베이스 제약 문구("A unique value is already
  taken, or the row is still referenced by another.")로 바뀌어 이유를 알 수 없었다
- `io.elasticore.springboot3.validation.ConflictException` (`BadRequestException` 과 같은 자리)을 던지면 생성된
  `XxxApiErrors` 가 409, `code: conflict`, 던진 메시지를 `detail` 로 답한다. Java 와 Kotlin 의 `apiErrors` 템플릿에 처리기를
  더했다. 2.x 와 `jpa-springboot` 번들에는 API 오류 처리기가 없으므로 바뀌지 않는다
- `XxxApiErrors.java` 는 한 번만 생성되는 파일이라 이미 생성한 프로젝트는 그 처리기 다섯 줄을 손으로 더하거나 파일을 지우고
  다시 생성한다. 저장소의 샘플에는 더해 두었다
- 확인: 도서 대출 프로젝트의 `LoanService.beforeSave` 와 반납 메서드에서 던져 `{"status":409,"code":"conflict","detail":"No
  copies of book ... left: 1 of 1 are on loan"}` 이 나오는 것을 확인했다
- 관련 파일: `ConflictException.java`, `elasticore-template/v3/apiErrors.tmpl`, `elasticore-template/kt/apiErrors.tmpl`,
  `elasticore-rule-guide.md`(쓰기 경로가 지키는 것)

### Bug Fixes (Ossie 내보내기 - dialect)

#### 식을 `OSSIE_SQL_2026` 으로 내보낸다. 서브쿼리 `@formula` 만 `ANSI_SQL`
- `OssieSemanticModelExporter` 가 모든 필드 식과 지표 식을 `ANSI_SQL` 로 내보냈다. Ossie core spec 은 `OSSIE_SQL_2026` 만
  의미를 정의하고(`spec.md` "Dialects": 나머지 이름은 벤더 언어, 소비자가 무시해도 됨) 그것을 기본 dialect 로 삼으므로,
  `ANSI_SQL` 로 내보낸 모델은 읽는 쪽이 식을 전부 건너뛰어도 스펙에 맞는 것이었다
- 컬럼 이름인 필드 식과 `metrics:` 의 지표 식(집계, `CASE WHEN`, `CAST`, `NULLIF`, 비교 - 모두 `expression_language.md`
  의 지원 구문)은 `OSSIE_SQL_2026` 으로 내보낸다. `@formula` 는 `OssieSql` 이 가른다: 문자열 리터럴 밖에 SELECT 같은 질의
  단어가 없으면 `OSSIE_SQL_2026`, 서브쿼리·CTE 처럼 그 언어가 지원하지 않는 식이면 `ANSI_SQL` 항목 하나로 둔다. `@formula`
  필드의 `custom_extensions` 에 `"formula": "database"` 를 더했다
- 확인: `OssieSqlTest`(식 16개·질의 5개), `OssieFormulaDialectTest`(@formula 둘을 가진 프로젝트를 생성해 내보낸 파일의
  dialect 확인), `OssieExportTest`(ddd 샘플의 모든 필드가 `OSSIE_SQL_2026`); `elasticore-template-ddd` 와
  `elasticore-sample-petstore` 의 `semantic_model.yml` 을 다시 생성했다
- 관련 파일: `OssieSql.java`, `OssieSemanticModelExporter.java`, `OssieSqlTest.java`, `OssieFormulaDialectTest.java`,
  `OssieExportTest.java`, `elasticore-rule-guide.md`(dialect 단락), `dsl-guide.html`(펫스토어 안내 페이지의 예)

### New Features (로그인 API 생성)

#### `@login` + `@password` 엔티티에 signin·signout·me 가 생성된다
- 펫스토어를 Claude Code 와 agent kit 으로 세 번(Java hexagonal, Java ddd, Kotlin hexagonal) 새로 만들게 했더니 셋 다,
  그리고 저장소의 펫스토어 샘플도 같은 로그인 컨트롤러(로그인·로그아웃·지금 누구인지)를 손으로 썼다. 세 에이전트 모두
  `SessionCurrentUser` 의 사용법을 찾으려고 jar 를 `javap` 로 열었다
- `@login` 과 `@password` 가 있고 `@expose` 된 엔티티의 컨트롤러에 `POST /signin` (`{"<login 필드>", "<password 필드>"}`,
  틀리면 401 `Wrong <login> or password`), `POST /signout` (204), `GET /me` (방문자 401) 가 붙는다. 세션에는 행의 키와
  역할(`@role` 필드, 없으면 `role` 필드 - enum 이면 상수 이름)이 들어간다. CoreService·유스케이스에 `me()`,
  TypeScript 클라이언트에 `signIn`·`signOut`·`me()`(방문자면 null)
- 로그인을 따로 하는 애플리케이션(Spring Security)은 `env.yml` 의 `config: signin: false` 로 끈다. v3 번들의 출력이
  기본으로 바뀌는 변경이다(3.0 출시 전): `@login` 을 쓰는 프로젝트의 컨트롤러에 엔드포인트 세 개가 더해진다.
  `jpa-springboot`(2.x 호환)에는 `jpa-service-access` 가 없어 바뀌지 않는다
- 펫스토어 샘플의 `AccountController` 는 가입하고 바로 로그인하는 것만 남겼다. 화면(`web/src/shop.ts`)은 생성된
  `CustomerApi.signIn/signOut/me` 를 쓴다
- 확인: `SignInAndReferencesTest`(Java·Kotlin·TypeScript·`signin: false`), 펫스토어 `ShopFlowTest` 15개(생성된
  `/customer/signin`, `/customer/me`), `webBuild` 타입 검사, `elasticore-template-kt` 의 `AgentController` 컴파일·테스트
- 관련 파일: `ServiceAccessRules.java`(`signInControl`, `me`, `signsIn`, `roleField`), `HexUseCaseRules.java`,
  `TypeScriptApiRules.java`, `SignInAndReferencesTest.java`, `elasticore-annotation-guide.md`, `elasticore-env-guide.md`

### Improvements (오류 응답 - 상태 충돌 409, 없는 참조 404)

#### 생명주기가 거절한 명령은 409, 요청이 가리킨 행이 없으면 404
- 세 에이전트 모두 생성된 `XxxApiErrors` 를 고쳐 "배송된 주문 취소"를 422 에서 409 로 바꿨다. RFC 9110 의 409 가
  "대상의 현재 상태와 충돌"이므로 그쪽이 맞다. 다른 불변식 위반은 그대로 422 다
- `apiErrors` 템플릿(Java, Kotlin)의 `refused(...)`: 코드가 `state.` 로 시작하면 409 (제목 코드 `state`, "Not allowed in
  this state"), `reference.` 로 시작하면 404, 나머지는 422. 공통 코드에 `error.state.title` 을 더했다
- `XxxApiErrors` 는 한 번만 생성되므로 **이미 있는 프로젝트는 422 그대로**다. 파일을 지우고 다시 생성하거나 `refused` 를
  고친다. 펫스토어 샘플은 다시 생성하고 테스트를 409·404 로 바꿨다(`petstore-messages_ko.properties` 에 새 제목)
- 관련 파일: `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ErrorCatalog.java`, `ShopFlowTest.java`, `ShopModelTest.java`,
  `elasticore-rule-guide.md`, `elasticore-dsl-guide.md`, `elasticore-annotation-guide.md`, `elasticore-ddd-customize-guide.md`

### Improvements (DDD 번들 - 참조한 행이 있는지 저장 전에 확인)

#### `requireReferences(lookup)` - 없는 id 를 가리키는 참조는 404 로 거절
- 다른 애그리거트를 id 로 참조하는 필드(`loan.memberId`, 주문 줄의 `itemId`)에 없는 id 를 보내면 외래 키 위반이 409
  "Conflicts with stored data" 로 나갔다. 그래서 도서 대출 레시피와 펫스토어를 만든 세 에이전트가 모두 저장 전에
  `entityManager.find` 로 확인하는 코드를 손으로 썼다
- 그런 참조가 있는 엔티티(직접, 또는 소유한 멤버를 통해)에 `requireReferences(Lookup)` 이 생기고, CoreService 가 생성·
  수정·일괄 수정·조건 수정에서 `beforeSave` 전에 부른다. 없으면 `InvariantViolationException("reference.<Entity>.<relation>",
  "<Target> 99 does not exist")` - API 는 404. `@snapshot` 이 원본 행을 못 찾을 때도 같은 코드·문구(전에는
  "OrderLine.item 99 does not exist", 422)
- 생성되는 Kotlin 서비스 테스트(`testCode`)는 모의 `entityManager.find` 가 요청한 클래스의 모의 객체를 돌려주게 한다(lenient)
- 관련 파일: `DddReferenceRules.java`(`requireReferences`, `-service`, `-test`), `DddDeriveRules.java`, `DddRepositoryPortRules.java`,
  `EntitySpec.java`(`knownReferencing`), `SpecFactory.java`(`registerReferencing`), `SignInAndReferencesTest.java`

### Bug Fixes (DTO - 최소 개수)

#### `@minsize(1)` 컬렉션을 아예 보내지 않으면 400 (전에는 엔티티 불변식의 422)
- `@Size` 는 null 을 통과시키므로 `lines` 를 뺀 주문이 DTO 검증을 지나 저장 시점 불변식에서 422 가 났다. 빈 배열은 400
  이었다(채점 에이전트가 찾음)
- 정리된 출력(v3)에서 `@minsize(n)` 의 n 이 1 이상이면 DTO 필드에 같은 메시지의 `@NotNull` 을 더한다. 2.x 호환 출력은 그대로
- 관련 파일: `DtoBaseRules.java`, `SignInAndReferencesTest.java`, `ShopFlowTest.java`, `elasticore-annotation-guide.md`

## 3.0.261020-alpha2

### Bug Fixes (생성기)

#### 도메인에 `Comment` 엔티티가 있어도 컴파일된다
- 같은 도메인에 `Comment` 라는 엔티티나 enum 이 있으면 생성된 코드가 컴파일되지 않았다. `-- 설명` 이 붙은 필드마다
  Hibernate 의 `@Comment` 가 붙는데
  - 규칙 엔진(`elcore3`)은 `import org.hibernate.annotations.Comment;` 를 써서 같은 패키지의 `Comment` 엔티티를
    가렸다. `PostBase` 의 `List<Comment>` 가 애노테이션을 가리켜 컴파일 오류가 났다
  - 2.x 생성기(`elcore`)는 `org.hibernate.annotations.*` 를 import 하는데, 같은 패키지의 `Comment` 엔티티가 이름을
    가져가 `@Comment("...")` 가 엔티티를 가리켰다
- 이제 도메인에 그런 엔티티나 enum 이 있으면 `@Comment` 를 import 하지 않고 `@org.hibernate.annotations.Comment("...")`
  처럼 패키지까지 붙여 쓴다. 규칙 엔진의 v3 번들(`jpa-springboot-v3`·DDD·헥사고날)과 Kotlin 번들은 규칙이 만들어
  쓰는 다른 애노테이션도 도메인의 타입과 이름이 겹치면 같게 한다. 2.x 와 같은 출력을 내는 `jpa-springboot` 번들은 2.x
  생성기처럼 `@Comment` 만 바꾼다. 규칙이 정해진 한 줄로 쓰는 애노테이션(DTO 의 `@Size`·`@Schema`,
  `@Setter(AccessLevel.PROTECTED)` 등), 같은 파일에서 그런 한 줄로도 쓰이는 애노테이션, 다른 애노테이션 안이나
  `@jpa:` 로 쓰는 애노테이션(`@Table` 의 `@Index` 등)은 단순 이름과 import 를 그대로 둔다. 그런 이름이 없는 도메인의
  생성 결과는 바뀌지 않는다(`elcoreRulesCheck`·`elcoreTreeCheck` 결과 그대로)
- 아직 남은 경우: 다른 도메인의 `Comment` 엔티티를 참조하는 엔티티(그 파일이 그 엔티티를 import 한다), DTO 에서
  `@Size` 와 이름이 같은 타입(`Size` enum 등)을 필드 타입으로 쓰는 경우, MongoDB 출력은 이전처럼 컴파일되지 않는다
- 확인: 2.x 생성기, 규칙 엔진 layered·hexagonal·Kotlin 으로 `Post`·`Comment` 게시판을 생성해 단순 이름 `@Comment` 와
  그 import 가 없는 것을, `Comment` 가 없는 같은 모델은 단순 이름 `@Comment` 와 그 import 를 그대로 쓰는 것을, `Size`
  enum 과 `Schema` 엔티티가 있는 도메인의 DTO 가 `@Size`·`@Schema` 의 import 를, `Setter`·`JoinColumn` 엔티티가 있는
  도메인의 파일이 `@Setter`·`@JoinColumn` 의 import 를 그대로 두는 것을 테스트로 확인했다. hexagonal 게시판은
  Spring Boot 4.0.8 에서 컴파일하고 실행했다
- 관련 파일: `SourceCode.java`, `AnnoCode.java`, `EntitySpec.java`, `EntitySrcFilePublisher.java`,
  `ModelNamedLikeAnAnnotationTest.java`

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
