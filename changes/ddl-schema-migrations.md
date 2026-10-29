### New Features (v3 규칙 엔진 - DDL)

#### `elasticore { ddl {} }` - 프로젝트의 테이블을 DB 별 DDL 로, 모델의 변화를 Flyway 마이그레이션으로
- 왜: 운영 DB 는 `ddl-auto` 로 만들지 않는다. 엔티티를 바꿀 때마다 무엇을 ALTER 해야 하는지 사람이 찾아 DB 마다 다른 문법으로
  써 왔고, MyBatis 도메인은 H2 용 스키마 하나뿐이었다.
- 무엇:
  - 설정은 프로젝트 한 번 - `build.gradle` 의 `elasticore { ddl { databases = [...]; migration = 'flyway' } }`(플러그인이
    `elasticore.ddl.*` 로 넘김). 도메인은 `env.yml` 의 `ddl: false`, `ddl.databases`, `ddl.migration`, `ddl.separate: true`
    로 필요한 것만 덮어쓴다(별도 DB 는 `db/{domain}/` 아래 따로).
  - `db/schema-{h2|postgresql|oracle|sqlserver}.sql`: 모든 도메인의 테이블·외래키·인덱스·시퀀스·스키마, 그 DB 의 Hibernate
    방언이 고르는 타입으로. 매 빌드 다시 생성.
  - `migration = 'flyway'`: `db/migration/{db}/V{시각}__init.sql`(그 DB 이력의 시작)과, 모델이 바뀌면
    `V{시각}__{무엇}.sql`(바뀐 만큼), 그리고 `db/migration/schema-state.json`(마이그레이션이 만든 스키마). 더하거나 넓히는
    것(테이블·선택 컬럼·길이·외래키·인덱스·스키마)은 실행되는 문장으로, 데이터를 잃거나 기존 행에서 실패할 수 있는 것(삭제·
    타입 변경·줄임·NOT NULL·키 변경)은 파일 끝의 **검토** 주석과 빌드 로그의 `REVIEW` 로. 필수 컬럼 추가는 빈 채로 추가하고
    NOT NULL 을 검토로. 마이그레이션은 한 번 쓰면 다시 쓰지 않는다. SQL Server 의 ALTER COLUMN 은 그 컬럼의 인덱스·외래키를
    지웠다 다시 만든다. 이력을 깨는 실행은 그 이력의 마이그레이션과 상태를 쓰지 않고 실패한다: 상태 파일 없이 마이그레이션만
    남은 경우, 상태 파일을 읽을 수 없는 경우(병합 충돌), 목록에서 빠졌던 DB 가 다시 들어온 경우(그 DB 이력을 손으로 맞춘 뒤
    상태에 추가), 이번 버전의 파일이 이미 있는 경우. 직접 쓴 Flyway 이력이 있는 프로젝트는 한 번 `-PddlBaseline` 으로 상태만
    지금 모델로 시작한다. 상태 파일은 모든 마이그레이션을 쓴 뒤 임시 파일에서 옮겨 갱신한다. 목록에서 빠진 DB 는 경고 - 그
    이력은 거기서 멈춘다.
  - JPA 도메인의 테이블은 생성된 엔티티 소스(JavaParser, Kotlin 은 줄 단위)와 `mapping: xml` 의 `orm.xml` 에서 읽는다 -
    Hibernate 가 보는 그대로: Spring Boot 의 이름 규칙, Hibernate 6 의 타입, `@Size`·`@NotNull` 의 DDL 반영, 임베디드·
    `@AttributeOverrides`, `@IdClass`, 다대다·조인 테이블(양방향이면 소유 쪽 컬럼은 반대편 속성 이름, Set 만 기본 키),
    일대일 소유 쪽 조인 컬럼의 유일 인덱스, `@OneToMany @JoinColumn` 의 외래키, `@ElementCollection`(`{엔티티}_{속성}`,
    값 컬럼은 Hibernate 처럼 nullable, 모두 필수인 Set 만 전 컬럼 기본 키, `@OrderColumn`; Map 은 경고 후 생략), 다른
    스키마 엔티티의 조인 테이블은 기본 스키마에, 상속(SINGLE_TABLE·JOINED·TABLE_PER_CLASS), 변환기의 컬럼 타입,
    `@JdbcTypeCode`(JSON·XML·LONG32VARCHAR), Envers 의 감사 테이블(JOINED 하위 클래스는 키·자기 컬럼·`rev` 만, SINGLE_TABLE
    은 루트 감사 테이블에 하위 컬럼과 `dtype`)·`revinfo`·`revinfo_seq`(application.yml 의 `audit_table_suffix` 등 반영),
    도메인 패키지 밖의 엔티티(outbox). `@NotBlank`·`@NotEmpty` 도 NOT NULL - Hibernate 6.6/7 의 Bean Validation DDL 이
    그렇게 한다. 2.x 코드를 지키는 프로젝트는 새 `elcoreDdl` 작업이 `elcore` 뒤에 쓴다.
  - MyBatis 도메인은 모델에서(MyBatisTables). 프로젝트 DDL 이 켜진 도메인은 `{domain}-schema.sql` 대신 프로젝트 파일에 든다.
    PR #44 의 도메인별 `-schema-{db}.sql` 은 이것으로 바뀌었다(미발표). 컬럼 이름이 Oracle·PostgreSQL·SQL Server 의 예약어면 경고.
  - DDL 을 켜지 않은 MyBatis 도메인의 `{domain}-schema.sql`(H2)도 같은 모델로 쓰여 바뀐다(3.0 미발표 출력, PR #43): 크기를
    말하지 않은 소수는 `DECIMAL(19,2)` 대신 Hibernate 와 같은 `DECIMAL(38,2)`, 유일 컬럼은 테이블 안 `UNIQUE (...)` 대신
    `CREATE UNIQUE INDEX uk_...`. 한 프로젝트의 JPA·MyBatis 도메인이 같은 타입을 쓰게 하려는 것이다.
  - 공통 모델(`rules/schema`): `Schema`·`Table`·`Column`·`ColumnType`, `Database`(DB 별 타입과 문장), `SchemaWriter`,
    `SchemaDiff`, `DdlConfig`.
- 확인:
  - JPA 샘플 넷이 생성된 `schema-h2.sql` 로 만든 테이블에서 Hibernate `ddl-auto=validate` 로 기동(`SchemaValidateTest`):
    2.x 템플릿(Hibernate 6.6 - 다대다·복합 키·JSON·`acct` 스키마·`_hist` Envers), template-ddd(`mapping: xml`·Envers·
    `columnDefinition TEXT`·outbox), template-kt(Kotlin), 펫스토어(Boot 4·Hibernate 7). 펫스토어는 CI 의 PostgreSQL·Oracle·
    SQL Server 에서도(`SchemaValidateOn*Test`).
  - `DdlMigrationTest`: 첫 실행·변경 없음·모델 변경의 세 번, DB 별 문장, 검토 주석, 마이그레이션을 차례로 H2 에 실행한 결과가
    마지막 모델의 테이블. 도메인의 `ddl: false`·`separate`. 이력을 깨는 실행의 거부. `JpaSchemaReaderTest`: 이름·타입·
    관계·상속별 Envers·컬렉션 테이블·Kotlin 규칙. `SchemaDiffTest`: 넓힘과 줄임(크기 없는 소수, Oracle 의 CLOB 경계), SQL
    Server 의 인덱스와 NOT NULL.
  - `MigrationTest`(template-mybatis): 넓힘(유일 인덱스가 걸린 컬럼 포함)·선택으로·새 컬럼·필터 유일 인덱스·새 테이블과
    외래키·인덱스 삭제를 H2 와 CI 의 세 DB 에서 실행.
  - MyBatis 펫스토어는 이제 Flyway 로 시작한다(`db/migration/{vendor}` + 반복 마이그레이션 `R__catalog.sql`) - H2 와 세 DB.
- 관련 파일: `rules/schema/*`, `verify/JpaSchemaReader.java`, `verify/DdlRun.java`, `verify/RulesGenRunner.java`,
  `mybatis/MyBatisTables.java`·`MyBatisSchemaRules.java`·`MyBatisSql.java`, `elasticore-gradle-plugin`(`DdlSpec`, `elcoreDdl`),
  `elasticore-env-guide.md`(2.3·9-1 절), 샘플 넷과 MyBatis 둘의 `build.gradle`·테스트, `.github/workflows/ci.yml`
