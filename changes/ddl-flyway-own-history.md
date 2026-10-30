### Bug Fixes (프로젝트 DDL - 기본 위치에 있는 Flyway 이력)

#### `db/migration` 바로 아래에 직접 쓴 이력이 있으면 첫 마이그레이션을 쓰지 않는다 - `-PddlBaseline` 도 그 이력에 먹는다
- 왜: 마이그레이션은 `db/migration/{db}/` 에 쓰고, 이미 있는 이력은 그 폴더에서만 찾았다. Spring Boot 의 기본 위치
  `classpath:db/migration` 에 이력을 둔 프로젝트(`db/migration/V1__baseline.sql` ...)는 이력이 없는 것으로 보여, 처음 켜면
  `db/migration/{db}/V{시각}__init.sql`(모든 테이블의 CREATE)을 썼다. Flyway 는 위치 아래의 폴더까지 읽으므로, 다음 기동에서
  그 init 이 기존 이력 뒤에 실행돼 이미 있는 테이블을 만들다 실패한다 - 앱이 뜨지 않는다. 가이드가 이런 프로젝트에 권하는
  `-PddlBaseline` 도 같은 판정이라 효과가 없었다(init 을 또 씀). 마이그레이션 디렉터리를 읽는 다른 도구(Atlas 의 `atlas.sum` 등)도
  그 파일을 이력에 넣는다
- 무엇: `db/migration`(분리 도메인은 `db/{domain}/migration`) 바로 아래에 Flyway 가 읽는 마이그레이션(`V`·`U`·`R`·`B` 로
  시작하고 `__` 가 있는 `.sql`)이 있으면 프로젝트 자기 이력이다. 하위 폴더(Flyway 의 `common/` 과 `{vendor}`)는 세지 않는다 -
  프로젝트의 `locations` 가 이름을 댈 때만 읽히므로 기본 위치를 쓴다는 표시가 아니다
  - DB 가 하나이고 상태 파일이 없음: baseline 없이는 마이그레이션도 상태도 쓰지 않고 실패(이유와 `-PddlBaseline` 을 말함).
    `-PddlBaseline` 이면 상태만 지금 모델로 쓰고 외래키·인덱스 이름을 `takenOver` 로 적는다(`db/migration/{db}/` 의 이력과 같은
    `startFromTheModel` - `ddl-baseline-names`). 다음 변경은 `db/migration/{db}/` 에 - 기본 위치가 그 폴더도 읽으므로
    `locations` 를 바꾸지 않아도 된다
  - DB 가 둘 이상: 상태 파일이 있든 없든 실패 - 기본 위치는 아래 폴더를 모두 읽어 한 DB 에 다른 DB 의 마이그레이션을 실행한다
    (baseline 뒤에 둘째 DB 를 더하면 그 `init` 이 첫 DB 의 Flyway 가 읽는 자리에 생겼다). 이력을 DB 별 폴더로 옮기고
    `locations` 를 `{vendor}` 로 하라고 말하고, 찾은 파일 이름을 대어 Flyway 가 더는 읽지 않는 남은 파일이면 옮기라고 한다
  - 이력이 `db/migration/{db}/` 에 있는 경우는 전과 같다
- 확인: `DdlMigrationTest.aHistoryOfTheProjectsOwnInTheDefaultLocationIsNotWrittenOver`(거절 → baseline 으로 상태만 →
  모델 변경이 `migration/h2/` 의 마이그레이션 하나), `aHistoryInTheDefaultLocationIsNotForSeveralDatabases` - 둘 다 고치기 전
  코드에서 실패했다(init 을 쓰고 성공). `aBaselineOverTheDefaultLocationTakesTheNamesOverAndASecondDatabaseIsRefusedLater`(이름을
  넘겨받아 관계를 빼면 검토, baseline 뒤 둘째 DB 는 거절과 파일 이름), `aHistoryOfTheProjectsOwnIsAnyMigrationFlywayReadsOutsideTheDatabasesFolders`.
  Flyway 가 위치 아래 폴더를 읽는 것은 Flyway 10.20.1 의 classpath·파일 스캐너로 확인했다. 도메인 9개짜리 프로젝트
  (`db/migration/V1__baseline.sql`~`V19`, `classpath:db/migration`)에서 고치기 전에는 `-PddlBaseline` 을 줘도
  `db/migration/postgresql/V…__init.sql`(테이블 54개)이 생겼고, 고친 뒤에는 `elcore`(→ `elcoreDdl`)가 마이그레이션 없이 멈추고,
  `-PddlBaseline` 이 `schema-state.json` 만 쓰며, 다시 돌리면 아무것도 쓰지 않는다
- 관련 파일: `DdlRun.java`(`write`, `startFromTheModel`, `ownHistoryFile`), `DdlMigrationTest.java`, `elasticore-env-guide.md`(9-1 절 - `locations` 와 이력을 깨는 빌드 표)
