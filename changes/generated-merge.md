### New Features (v3 규칙 엔진 - 생성 파일 병합)

#### `elasticore { merge = true }` - 개발자가 고친 생성 파일을 DSL 변경과 병합 (시제품: MyBatis 매퍼 XML)
- 왜: 생성 파일을 고치면 그 파일은 `SKIP(user-modified)` 로 DSL 을 더 따라가지 않았다. 고친 내용과 DSL 의 변경 중
  하나를 잃어야 했고, 첫 줄의 `//ecd:` 표시는 주석 문법이 없는 형식이나 첫 줄이 정해진 형식(`<!DOCTYPE>`)에 맞지 않았다.
- 무엇:
  - 켜면 매퍼 XML 은 표시 없이 쓰이고, 쓴 그대로가 `.elasticore/cache` 에 남는다(프로젝트와 함께 커밋). 무압축 zip,
    항목 순서와 시각 고정 - 같은 내용이면 같은 바이트라 git 이 바뀐 만큼만 저장한다. 옆의 `.gitattributes` 가 binary 로 둔다.
  - 다음 생성은 파일마다 세 가지를 견준다: 지난 생성본(캐시), 디스크의 파일, 새 생성본. 고친 것이 없으면 새로 쓰고, DSL 이
    바뀌지 않았으면 고친 파일을 그대로 두고, 둘 다 바뀌었으면 줄 단위 3-way 병합(git 과 같은 규칙). 주석·새 문장·고친 줄 -
    무엇을 고쳤는지는 보지 않고 같은 줄을 양쪽이 바꿨는지만 본다. 같은 줄이면 `<<<<<<< yours` / `>>>>>>> elasticore` 표시를
    남기고 빌드가 실패하며, 표시가 남은 동안은 계속 실패한다. 해결한 파일은 그 뒤로 개발자 것으로 병합된다.
  - 지운 파일은 다시 생성. DSL 이 더 만들지 않는 파일은 캐시와 같으면 지우고, 고쳤으면 남기고 경고(캐시에서 빠진다).
    캐시에 없는 자리에 다른 파일이 있으면 덮어쓰지 않는다. 켜기 전에 표시와 함께 생성된 파일은 고친 적이 없으면 넘겨받고,
    고쳤으면 그대로 두고 알린다. 캐시를 읽을 수 없으면 아무것도 쓰지 않고 실패한다.
  - `-Pforce`(`elasticore.rules.force`)는 병합하는 파일에 적용하지 않는다 - 샘플처럼 빌드가 늘 켜 두는 설정이 고친 내용을
    지우면 안 된다. 생성된 그대로 다시 받으려면 파일을 지운다.
- 확인: `GeneratedMergeTest`(위 경우 전부와 얕은 클론), `ThreeWayMergeTest`. 펫스토어 MyBatis 모델로 git 저장소를 만들어
  실제로: 수정 후 두 브랜치(각자 모델 변경)를 병합 - 충돌한 생성 파일은 한쪽을 골라 다시 생성하면 양쪽 모델 변경과 개발자
  수정이 모두 남음; `--depth 1` 클론에서 수정과 모델 변경이 병합됨; 모델 변경 30번 커밋에 캐시 34개 버전이 pack 에서 약
  20KB(매퍼 XML 자체와 비슷).
- 관련 파일: `util/ThreeWayMerge.java`, `verify/GeneratedCache.java`, `verify/RulesGenRunner.java`(writeMerged·
  removeStaleMerged), `elasticore-gradle-plugin`(`merge`)
