### New Features (v3 규칙 엔진 - 생성 파일 병합)

#### 생성 파일 병합 - 고친 내용을 지키면서 DSL 을 따라간다 (3.0 코드 기본, `elasticore { merge = false }` 로 끔)
- 왜: 생성 파일을 고치면 그 파일은 `SKIP(user-modified)` 로 DSL 을 더 따라가지 않았다 - 고친 내용과 DSL 의 변경 중 하나를
  잃어야 했다. 첫 줄의 `//ecd:` 표시는 형식마다 주석 문법이 필요했고(XML `<!-- -->`, SQL `--`), properties 에서는 주석이
  아니라 `//ecd` 키였으며, 첫 줄이 정해진 형식(`<!DOCTYPE>`)과 맞지 않았다. `orm.xml` 과 `i18n/*-messages.properties` 는
  표시 없이 매번 통째로 덮어써 고친 내용이 사라졌다.
- 무엇:
  - Java·Kotlin 소스를 뺀 생성 파일 - MyBatis 매퍼 XML, `orm.xml`, `db/schema-{db}.sql`, `{domain}-schema.sql`,
    `*-http-services.properties`, `i18n/*-messages.properties`, TypeScript 클라이언트 - 은 표시 없이 쓰이고, 쓴 그대로가
    `.elasticore/cache` 에 남는다(프로젝트와 함께 커밋). 무압축 zip, 항목 순서와 시각 고정 - 같은 내용이면 같은 바이트라
    git 이 바뀐 만큼만 저장한다. 옆의 `.gitattributes` 가 binary 로 둔다.
  - 다음 생성은 파일마다 지난 생성본(캐시)·디스크의 파일·새 생성본을 견준다: 고친 것이 없으면 새로 쓰고, DSL 이 그대로면
    고친 파일을 그대로 두고, 둘 다 바뀌었으면 줄 단위 3-way 병합(git 과 같은 규칙). 무엇을 고쳤는지는 보지 않고 같은 줄을
    양쪽이 바꿨는지만 본다. 같은 줄이면 `<<<<<<< yours` / `>>>>>>> elasticore` 를 남기고 빌드가 실패하며, 표시가 남은 동안
    계속 실패한다. 정리한 파일이 그 뒤의 기준.
  - 지운 파일은 다시 생성. DSL 이 더 만들지 않는 파일은 캐시와 같으면 지우고, 고쳤으면 남기고 경고(캐시에서 빠진다).
    캐시에 없는 자리의 다른 파일은 덮어쓰지 않는다. 이전에 표시와 함께 생성된 파일은 해시가 맞으면 넘겨받고(사용자 블록은
    고친 내용으로 남음), 고쳤으면 그대로 두고 알린다. `orm.xml`·메시지처럼 늘 덮어쓰던 파일은 처음 병합하는 실행(`.elasticore/`
    가 없음)에서만 전처럼 쓰고, 그 뒤로 캐시에 없으면 다른 파일처럼 건드리지 않는다. 캐시를 읽을 수 없으면 - 잘렸거나
    프로젝트 밖 경로를 담았으면 - 아무것도 쓰지 않고 실패한다. 예외로 끝난 실행도 쓴 파일만큼 캐시를 남긴다.
  - `elcoreDdl`(DDL 만)의 충돌도 빌드를 실패시킨다.
  - 병합 파일은 플랫폼 줄바꿈으로 쓴다(Windows 에서 CRLF) - `orm.xml` 과 메시지는 전에 LF 였다.
  - 캐시와 생성 파일은 같이 다닌다(둘 다 커밋하거나 둘 다 git 에서 뺀다) - 캐시만 커밋하면 pull 뒤 로컬의 옛 생성 파일이
    고친 파일로 보인다. 저장소의 샘플은 생성 파일 대부분을 빼고 매 빌드 다시 만들므로 `.elasticore/` 도 뺀다.
  - git 병합 충돌: 캐시는 한쪽, 충돌한 생성 파일은 캐시와 같은 쪽을 고르고 빌드(가이드 9-2).
  - `-Pforce` 는 병합하는 파일에 적용하지 않는다 - 샘플처럼 빌드가 늘 켜 두는 설정이 고친 내용을 지우면 안 된다.
  - 클라이언트 실행(`elcore3ts`)과 서버 실행이 한 캐시를 쓴다: 각자 자기 파일만 오래된 것으로 본다. 오래된 클라이언트 경고는
    캐시의 `.ts` 도 생성된 클라이언트로 본다.
  - 2.x 코드(`output = 'v2'`, `jpa-springboot`)는 끈 채로 - 2.x 가 쓰던 그대로(`elcoreRulesCheck`·`elcoreTreeCheck` 불변).
    3.0 미발표 출력의 기본 변경: 위 파일들의 첫 줄 표시가 빠지고, MyBatis 매퍼 머리 주석이 "고치지 말라" 대신 병합을 말한다.
  - Java·Kotlin 은 그대로 첫 줄 표시 - 한 번만 생성하는 파일, 사용자 블록, 2.x 감지가 그 줄을 읽는다(다음 단계).
- 확인: `GeneratedMergeTest`(위 경우 전부, 형식별 - 매퍼·DDL·메시지·`orm.xml`·TypeScript, 클라이언트와 서버의 공유 캐시와
  오래된 클라이언트 경고, 기본값과 2.x, 얕은 클론, `force`, 잃은 캐시, 한 도메인 실행, 잘린·밖을 가리키는 캐시, DDL 만의 충돌),
  `ThreeWayMergeTest`, 플러그인 `mergeIsPassed`. 펫스토어 MyBatis 모델로 git 저장소를
  만들어 실제로: 수정 후 두 브랜치(각자 모델 변경)를 병합 - 충돌한 생성 파일은 한쪽을 골라 다시 생성하면 양쪽 모델 변경과
  개발자 수정이 모두 남음; `--depth 1` 클론에서 수정과 모델 변경이 병합됨; 모델 변경 30번 커밋에 캐시 34개 버전이 pack 에서
  약 20KB(매퍼 XML 자체와 비슷).
- 관련 파일: `util/ThreeWayMerge.java`, `verify/GeneratedCache.java`, `verify/RulesGenRunner.java`(writeMerged·
  removeStaleMerged·staleClient), `v3/mybatis_xml.tmpl`, `elasticore-gradle-plugin`(`merge`), `elasticore-env-guide.md`(9-2,
  2.3), `elasticore-rule-guide.md`(7.5, TypeScript), 샘플 다섯의 `.gitignore`
