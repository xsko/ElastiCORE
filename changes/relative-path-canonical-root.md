### Bug Fixes (v3 규칙 엔진 - 남은 클래스를 쓰는 파일의 경로)

#### "still uses it" 줄이 개발자 파일을 프로젝트 안의 경로로 말한다 - 링크나 다른 이름으로 받은 프로젝트에서도
- 왜: DSL 이 더 만들지 않는 생성 클래스를 개발자 코드가 쓰면 `... is no longer generated, but <파일> still uses it - kept`
  라고 알린다. 그 파일은 소스를 정규 경로(`getCanonicalFile`)로 읽어 찾는데, 줄에 쓸 때는 정규화하지 않은 프로젝트 경로를
  앞에서 떼어 냈다. 프로젝트 경로가 정규 경로와 다르면 - 링크를 거쳤거나, macOS 의 `/var`(실제 `/private/var`), Windows 의
  8.3 짧은 이름(`C:\Users\DEVELO~1\...`) - 절대 경로가 그대로 찍혔다. 그래서 `GeneratedMergeTest.aClassNoLongerProducedStaysWhileACodeOfTheDevelopersUsesIt`
  는 임시 폴더가 짧은 이름인 Windows 에서 실패했다(리눅스 CI 에서는 같은 경로라 통과, macOS 의 `/var` 는 같은 이유로 보이나
  돌려 보지 않았다)
- 무엇: `relative` 가 그대로 떼어지지 않으면 파일과 프로젝트 경로를 둘 다 정규화해 다시 뗀다. 화면에 찍는 줄만 바뀐다 -
  캐시 키는 이 경로를 쓰지 않는다
- 확인: 새 `GeneratedMergeTest.theCodeThatKeepsAClassIsNamedInTheProjectAlsoThroughALink`(심볼릭 링크로 받은 프로젝트 -
  링크를 만들 수 없는 곳에서는 건너뜀)가 리눅스(`eclipse-temurin:21-jdk`)에서 고치기 전 그 줄의 단정으로 실패하고 고친 뒤
  통과한다. Windows(임시 폴더가 8.3 짧은 이름)에서는 위의 기존 테스트가 고치기 전 실패, 고친 뒤 통과(링크 테스트는 권한이
  없어 건너뜀)
- 관련 파일: `RulesGenRunner.java`(`relative`), `GeneratedMergeTest.java`
