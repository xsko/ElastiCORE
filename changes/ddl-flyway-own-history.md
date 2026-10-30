### Bug Fixes (프로젝트 DDL - 기본 위치에 있는 Flyway 이력)

#### `db/migration` 바로 아래에 직접 쓴 이력이 있으면 첫 마이그레이션을 쓰지 않는다 - `-PddlBaseline` 도 그 이력에 먹는다
- 왜: 마이그레이션은 `db/migration/{db}/` 에 쓰고, 이미 있는 이력은 그 폴더에서만 찾았다. Spring Boot 의 기본 위치
  `classpath:db/migration` 에 이력을 둔 프로젝트(`db/migration/V1__baseline.sql` ...)는 이력이 없는 것으로 보여, 처음 켜면
  `db/migration/{db}/V{시각}__init.sql`(모든 테이블의 CREATE)을 썼다. Flyway 는 위치 아래의 폴더까지 읽으므로, 다음 기동에서
  그 init 이 기존 이력 뒤에 실행돼 이미 있는 테이블을 만들다 실패한다 - 앱이 뜨지 않는다. 가이드가 이런 프로젝트에 권하는
  `-PddlBaseline` 도 같은 판정이라 효과가 없었다(init 을 또 씀). 마이그레이션 디렉터리를 읽는 다른 도구(Atlas 의 `atlas.sum` 등)도
  그 파일을 이력에 넣는다
- 무엇: 상태 파일이 없고 `db/migration`(분리 도메인은 `db/{domain}/migration`) 바로 아래에 `V*.sql` 이 있으면
  - DB 가 하나: baseline 없이는 마이그레이션도 상태도 쓰지 않고 실패(이유와 `-PddlBaseline` 을 말함). `-PddlBaseline` 이면 상태만
    지금 모델로 쓰고, 다음 변경은 `db/migration/{db}/` 에 - 기본 위치가 그 폴더도 읽으므로 `locations` 를 바꾸지 않아도 된다
  - DB 가 둘 이상: 실패 - 기본 위치는 아래 폴더를 모두 읽어 한 DB 에 다른 DB 의 마이그레이션을 실행한다. 이력을 DB 별 폴더로
    옮기고 `locations` 를 `{vendor}` 로 하라고 말한다
  - 이력이 `db/migration/{db}/` 에 있는 경우는 전과 같다
- 확인: `DdlMigrationTest.aHistoryOfTheProjectsOwnInTheDefaultLocationIsNotWrittenOver`(거절 → baseline 으로 상태만 →
  모델 변경이 `migration/h2/` 의 마이그레이션 하나), `aHistoryInTheDefaultLocationIsNotForSeveralDatabases` - 둘 다 고치기 전
  코드에서 실패했다(init 을 쓰고 성공). Flyway 가 위치 아래 폴더를 읽는 것은 Flyway 10.20.1 의 classpath·파일 스캐너로 확인했다.
  도메인 9개짜리 프로젝트(`db/migration/V1__baseline.sql`~`V19`, `classpath:db/migration`)에서 고치기 전에는 `-PddlBaseline`
  을 줘도 `db/migration/postgresql/V…__init.sql`(테이블 54개)이 생겼고, 고친 뒤에는 `elcore`(→ `elcoreDdl`)가 마이그레이션
  없이 멈추고, `-PddlBaseline` 이 `schema-state.json` 만 쓰며, 다시 돌리면 아무것도 쓰지 않는다
- 리뷰 뒤(넘겨받은 이름 - `ddl-baseline-names` - 과 함께):
  - `migration/` 에 바로 있는 이력의 baseline 도 외래키·인덱스 이름을 `takenOver` 로 적는다(`startFromTheModel` - 두 baseline
    길이 함께 씀). 둘이 따로 들어가면 이 길만 이름을 적지 않아, 관계를 뺄 때 없는 이름의 `DROP CONSTRAINT` 로 Flyway 가 실패했다
  - 데이터베이스가 둘 이상이면 상태가 이미 있어도 거절 - baseline 뒤에 `databases` 에 둘째 DB 를 더하면 그 `init` 이 첫 DB 의
    Flyway 가 읽는 자리에 생기고, 그 뒤 변경마다 같은 버전이 두 폴더에 쓰여 Flyway 가 시작을 거부했다
  - 자기 이력은 Flyway 가 읽는 모든 마이그레이션(`V`·`U`·`R`·`B` 로 시작하고 `__` 가 있는 `.sql`)이 `migration/` 바로 아래에
    있는 것(`hasOwnHistory`) - 전에는 `V*.sql` 만 보았다. 하위 폴더(Flyway 의 `common/` 과 `{vendor}`)는 세지 않는다 - 프로젝트의
    `locations` 가 이름을 댈 때만 읽히므로 기본 위치를 쓴다는 표시가 아니고, 세면 그 배치의 여러 DB 빌드를 잘못 거절한다
  - 확인: `aBaselineOverTheDefaultLocationTakesTheNamesOverAndASecondDatabaseIsRefusedLater`,
    `aHistoryOfTheProjectsOwnIsAnyMigrationFlywayReadsOutsideTheDatabasesFolders` - 둘 다 리뷰 전 코드에서 실패
- 관련 파일: `DdlRun.java`(`write`, `startFromTheModel`, `hasOwnHistory`), `DdlMigrationTest.java`, `elasticore-env-guide.md`(9-1 절 - `locations` 와 이력을 깨는 빌드 표)
