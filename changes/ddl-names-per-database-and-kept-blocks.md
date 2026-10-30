### Bug Fixes (프로젝트 DDL - 넘겨받은 이름은 넘겨받은 DB 에만)

#### baseline 뒤에 더한 DB 의 마이그레이션은 넘겨받은 이름 때문에 기다리지 않는다
- 왜: `-PddlBaseline` 이 적는 `takenOver`(`ddl-baseline-names`)는 모든 DB 에 하나였다. baseline 뒤에 `databases` 에 더한 DB 는
  `init` 이 모델의 이름으로 만드므로 이름이 맞는데도, 그 DB 의 마이그레이션에서 그 이름의 외래키·인덱스를 지우는 문장이 검토
  주석이 되었고, 나중에 더한 SQL Server 는 그 이름 위의 컬럼 변경까지 기다렸다 - 주석을 풀지 않으면 그 DB 의 스키마가 모델과
  어긋난 채 남는다
- 무엇: baseline 이 쓴 상태는 이름을 넘겨받은 DB 들을 `takenOverOn` 으로 함께 적고, 그 이름 때문에 기다리는 문장은 그 DB 의
  마이그레이션에서만 검토로 쓴다(나머지 DB 는 실행 문장, 빌드의 `REVIEW` 줄은 그 DB 의 폴더). 모든 DB 의 파일에 붙는 그
  문장의 설명은 어느 DB 에서 넘겨받은 이름인지 말한다(`- on h2, its name was taken over ...`). 모델에 그 이름이 남는 동안
  `takenOverOn` 도 남는다. `takenOverOn` 이 없는 상태는 모든 DB 에서 넘겨받은 것으로 읽고, `takenOver` 가 없는 상태 파일은
  전과 같다
- 함께: `db/migration` 바로 아래의 이력 때문에 여러 DB 빌드를 거절할 때(`ddl-flyway-own-history`) 찾은 파일 이름을 대고, 이력을
  이미 DB 별 폴더로 옮겨 Flyway 가 그 자리에서 더는 읽지 않는 남은 파일이면 옮기라고 말한다
- 확인: `DdlMigrationTest.aDatabaseAddedAfterTheBaselineHasTheModelsNames`(H2 이력 위 baseline 뒤 PostgreSQL 을 더하면 상태에
  `"takenOverOn" : [ "h2" ]`, 관계를 빼면 H2 는 검토이고 PostgreSQL 은 `ALTER TABLE visit DROP CONSTRAINT fk_visit_customer;`),
  `SchemaDiffTest.aNameTakenOverWaitsOnlyOnTheDatabasesItWasTakenOverFrom`(같은 이름으로 다시 만드는 인덱스는 H2 에서만 검토,
  SQL Server 의 컬럼 넓힘은 실행, JSON 을 오가도 같고 `keepTakenOver` 가 이어 받음), 거절 메시지의 파일 이름은
  `aBaselineOverTheDefaultLocationTakesTheNamesOverAndASecondDatabaseIsRefusedLater`
- 관련 파일: `Schema.java`(`takenOverOn`), `SchemaDiff.java`(`byName`, `takenOverOn`), `DdlRun.java`(`startFromTheModel`,
  `ownHistoryFile`), `DdlMigrationTest.java`, `SchemaDiffTest.java`, `elasticore-env-guide.md`(9-1 절)

### Bug Fixes (생성 파일 병합 - DSL 이 바꾸지 않은 파일의 사용자 블록)

#### 표시가 있는 파일을 DSL 이 바꾸지 않으면 첫 병합에서 사용자 블록을 옮기지 않는다
- 왜: 사용자 블록을 2.x 의 자리로 돌려놓는 첫 병합(`merge-stamped-custom-blocks`)은 고치지 않은 파일이면 DSL 이 그 파일을
  바꾸지 않았어도 새로 쓰며 블록을 옮겼다 - 클래스 중간에 둔 블록이 닫는 괄호 앞으로 갔다. 같은 경우의 고친 파일은 블록을 그
  자리에 두었고, 가이드는 어느 쪽도 그렇게 말하지 않았다
- 무엇: DSL 이 바꾸지 않은 파일은 고쳤든 아니든 표시만 떼고 블록을 그 자리에 둔다. 블록을 2.x 의 자리로 옮기는 것은 DSL 이
  바꾸어 새로 쓰는 파일뿐이다. Java·Kotlin 소스와 그 밖의 생성 파일 모두
- 확인: `GeneratedMergeTest.customBlocksOfAStampedFileTheDslDoesNotChangeStayWhereTheyStand`(클래스 중간에 블록을 둔 Java DTO 와
  `</resultMap>` 뒤에 블록을 둔 매퍼 XML - 첫 병합 뒤 파일이 표시 줄만 빠진 그대로)
- 관련 파일: `RulesGenRunner.java`(`writeMergedSource`, `writeMerged`), `GeneratedMergeTest.java`, `elasticore-env-guide.md`(9-2 절 - 표시가 있던 프로젝트의 표)
