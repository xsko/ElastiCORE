### Bug Fixes (프로젝트 DDL - `-PddlBaseline` 으로 넘겨받은 이름)

#### 넘겨받은 이력의 외래키·인덱스를 그 이름으로 지우는 문장은 검토로 - 그 DB 는 다른 이름일 수 있다
- 왜: `-PddlBaseline` 은 직접 쓴 Flyway 이력이 만든 DB 위에서 상태를 지금 모델로 시작한다. 상태의 외래키·인덱스 이름은 모델이
  붙일 이름(`fk_{table}_{field}`, 30 자 넘으면 해시)인데, 그 DB 를 만든 것은 대개 Hibernate 이고 Hibernate 는 외래키를 해시로
  이름 붙이며(`FK` 와 해시 25 자) `@Index(unique)` 를 유일 제약으로 둔다. 마이그레이션은 외래키·인덱스 삭제를 실행
  문장으로 쓰므로, 넘겨받은 뒤 관계나 `@Index` 를 빼면 `DROP CONSTRAINT fk_...`(없는 이름)·`DROP INDEX ..._u`(제약이 쓰는
  인덱스)가 실패한다 - Flyway 가 실패하면 앱이 뜨지 않는다. 도메인 9개짜리 프로젝트에서 Hibernate 스키마와 프로젝트 DDL 을
  대조하면 테이블·컬럼·타입·외래키 대상은 모두 같고, 외래키 이름 69/69 와 유일 인덱스의 형태 32/32 가 달랐다
- 무엇: `-PddlBaseline` 이 쓴 상태는 그때의 외래키·인덱스 이름을 `takenOver` 로, 그 이름을 넘겨받은 DB 들을 `takenOverOn` 으로
  적는다. 그 DB 의 마이그레이션에서 그 이름을 지우는 문장과 지운 뒤 같은 이름으로 다시 만드는 문장은 검토 주석으로 쓰고 이유를
  단다(그 DB 의 이름으로 바꿔 실행)
  - SQL Server 에서 컬럼을 바꾸며 그 이름의 인덱스·외래키를 지웠다 다시 만드는 것은 SQL Server 마이그레이션에서만 검토이고,
    같은 이력의 다른 DB(PostgreSQL 등)에서는 컬럼 변경을 그대로 실행한다(`Change.reviewOn`·`safeOn`)
  - baseline 뒤에 `databases` 에 더한 DB 는 `init` 으로 모델의 이름을 쓰므로 넘겨받은 것이 없다 - 그 DB 의 마이그레이션은
    전처럼 실행 문장이다
  - 빌드의 `REVIEW` 줄은 모든 DB 가 기다리면 `*/V...`, 일부만이면 그 DB 의 폴더를 가리킨다
  - 넘겨받은 뒤 마이그레이션이 만든 외래키·인덱스는 그 이름이 맞으므로 전처럼 실행한다. 모델에 그 이름이 남아 있는 동안
    `takenOver` 도 남는다. 이름은 바꾸지 않는다 - 3.0 의 `init` 으로 만든 DB 는 지금 이름을 쓴다
  - `takenOver` 가 없는 상태 파일은 전과 같다(쓰는 내용도 바이트 단위로 같다). `takenOverOn` 이 없는 상태는 모든 DB 에서
    넘겨받은 것으로 읽는다. 이 변경 전에 `-PddlBaseline` 으로 만든 상태에는 `takenOver` 가 없다 - 가이드(9-1)에 다시
    baseline 하는 법
- 확인: `DdlMigrationTest.aKeyNamedByAHistoryTakenOverIsNotDroppedAsWritten`(자기 이력 위에 baseline → 관계를 빼면
  `-- ALTER TABLE visit DROP CONSTRAINT fk_visit_customer;` 와 이유, 마이그레이션이 만든 `fk_visit_owner` 는 빼면 실행 문장),
  `whatOnlySqlServerDropsAndMakesAgainWaitsThereAlone`, `aDatabaseAddedAfterTheBaselineHasTheModelsNames`(H2 이력 위 baseline
  뒤 PostgreSQL 을 더하면, 관계를 뺄 때 H2 는 검토이고 PostgreSQL 은 실행 문장), `SchemaDiffTest.anIndexByANameTakenOverIsNotDroppedAndMadeAgainAsWritten`,
  `whatANameTakenOverChangesIsSaidAndWaitsWhereItMatters`, `aNameTakenOverWaitsOnlyOnTheDatabasesItWasTakenOverFrom`.
  위 프로젝트(`db/migration` 의 자기 이력, 기본 위치 이력을 다루는 변경과 함께 돌림)에서 baseline 이 이름 124 개(외래키 69,
  인덱스 55)를 넘겨받고, 생성된 엔티티에서 관계 하나를 빼자 마이그레이션이 `-- ALTER TABLE <테이블> DROP CONSTRAINT fk_<테이블>_<관계>;`
  와 이유를 검토로 썼다 - 그 DB 에서 이 외래키의 이름은 Hibernate 의 해시 이름이다
- 관련 파일: `Schema.java`(`takenOver`, `takenOverOn`), `SchemaDiff.java`, `DdlRun.java`(baseline, 상태 쓰기), `DdlMigrationTest.java`,
  `SchemaDiffTest.java`, `elasticore-env-guide.md`(9-1 절)
