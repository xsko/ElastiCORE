### Bug Fixes (생성 파일 병합 - 표시가 있는 파일의 사용자 블록)

#### 표시가 있는 파일의 사용자 블록은 병합하지 않고 2.x 의 자리로 돌려놓는다 - 2.x 에서 3.0 코드로 옮길 때 충돌하지 않는다
- 왜: 사용자 블록(`// !----------` ~ `// ------------!`)은 해시에서 빠진다 - 블록이 어디 있든 파일은 생성된 그대로로
  읽힌다. 병합하는 첫 실행은 그런 파일의 기준을 해시가 확인한 본문(블록 없음)으로, 디스크를 블록이 든 본문으로 잡았다.
  새 생성본에도 블록이 없으니 블록이 개발자 편집이 되어, 그 옆 줄을 DSL 이 바꾸면 같은 자리 충돌이 됐다. 2.x 에서 3.0
  코드로 옮기면 블록 옆(닫는 괄호, import, 빈 줄)이 거의 늘 바뀐다 - 도메인 9개짜리 2.x 프로젝트의 `-Pv3` 이행에서 사용자
  블록이 있는 파일 4개(모두 표시가 생성된 그대로)가 전부 `CONFLICT` 였고 빌드가 멈췄다. 병합하지 않는 실행은 블록을 새
  텍스트에 다시 넣으므로(`withCustomBlocks`) 이 일이 없었다
- 무엇: 표시가 있는 파일을 처음 병합할 때 사용자 블록은 해시처럼 병합에서도 뺀다 - 디스크 쪽도 블록을 뺀 본문으로 병합하고,
  쓸 때 블록을 2.x 가 다시 쓸 때의 자리(import 블록은 `package` 줄 아래, 나머지는 닫는 줄 앞)에 넣는다. 그래서 고치지 않은
  파일은 기준과 디스크가 같아 새 생성본에 블록을 넣은 것이 되고, 고친 파일은 고친 줄만 병합된다. Java·Kotlin 소스와 그 밖의
  생성 파일 모두. 캐시에는 전처럼 블록 없는 생성본을 두므로 다음 실행부터 블록은 다른 편집과 같은 개발자의 줄이다.
  개발자가 블록을 다른 자리(예: 클래스 중간)에 두었다면 2.x 가 다시 쓸 때처럼 그 자리로 옮겨진다
- 리뷰 뒤: `HashUtils` 는 import 블록을 마지막 하나만 기억하고, import 블록 위의 블록을 그 안에 넣는다 - import 블록이 둘이면
  첫 번째가 말없이 사라지고(`WROTE`), 코드 블록이 import 블록 위에 있으면 import 자리로 옮겨져 컴파일되지 않았다. 그래서 블록을
  온전히 돌려놓을 수 있는 파일(`blocksPutBackWhole` - import 블록이 많아야 하나이고 맨 앞, 닫히지 않은 블록이 없음 - 닫는 줄이
  틀린 블록은 `HashUtils` 에게 파일 끝까지라 닫는 중괄호가 블록과 함께 옮겨졌다)만 위처럼 병합하고, 나머지는 전처럼
  블록을 고친 내용으로 그 자리에 둔 채 병합한다. 블록을 뺀 병합이 충돌하면 블록을 둔 채 다시 병합해 그 결과를 쓴다 - 끝 줄을
  덮는 충돌 표시의 한쪽에 블록이 들어가 "yours" 로 해결하면 지워지던 것. `KEPT(yours ...)` 와 `UNCHANGED` 는 파일을 블록이
  있던 그대로 둔다
- 확인: `GeneratedMergeTest.theCustomBlocksOfATwoXFileComeIntoTheThreeZeroCodeWithoutAConflict`(2.x 가 쓴 DTO 의
  `package` 아래 import 블록과 첫 필드 뒤 블록 - 고치기 전 `CONFLICT` 로 실패, 고친 뒤 두 블록이 2.x 의 자리에 남고,
  이어진 모델 변경도 충돌 없이 병합). `customBlocksOfAStampedFileAreKept`(매퍼 XML)는 그대로 통과. 위 프로젝트의 `-Pv3`
  이행: `4 conflict(s)` → `0 conflict(s)`, 4개 파일이 블록 수 그대로 `WROTE`, 다시 돌리면 `0 written / 579 unchanged`.
  `blocksThatWouldNotComeBackWholeAreMergedAsEdits`, `twoBlocksOfImportsOfATwoXFileAreBothKeptOnTheMoveTo30`(import 블록 둘과
  코드 블록 - 셋 다 남고 말없는 `WROTE` 가 아님; 리뷰 전 코드에서는 `WROTE` 로 실패)
- 관련 파일: `RulesGenRunner.java`(`writeMergedSource`, `writeMerged`, `withBlocks`, `blocksPutBackWhole`), `GeneratedMergeTest.java`,
  `elasticore-env-guide.md`(9-2 절 - 표시가 있던 프로젝트의 표)
