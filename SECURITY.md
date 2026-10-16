# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| 3.x, newest version (`main`) | Yes - features, bug fixes and security fixes |
| 3.0, from its first stable release (`release/3.0`) | Bug fixes and security fixes for **3 years** from that release |
| 2.2 | Security fixes only |
| older | No |

Until the first stable 3.0 release, fixes come in the next pre-release from `main`.

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

Use GitHub's private vulnerability reporting: open the repository's **Security** tab
and choose **Report a vulnerability**. Include the ElastiCORE version, the DSL or
configuration that triggers the problem, and what an attacker could do with it.

You will get an acknowledgement within a few working days and a fix plan once the
report is confirmed. Fixed issues are credited in the release notes unless you ask
otherwise.

## What counts

ElastiCORE writes code into your project, so a problem in the *generated* code is
in scope as much as one in the generator or the runtime library - for example:

- generated access checks (`@access`, `@owner`) that let a user read or change
  another user's rows;
- a `@password` or `@writeonly` value that appears in a response, a search or the
  exported semantic model;
- generated queries that can be injected through a search request;
- the runtime library (`elasticore-springboot3` / `elasticore-springboot4`).

---

[한국어] 보안 문제는 **공개 이슈로 올리지 말고** GitHub 저장소의 **Security → Report a vulnerability** 로
비공개 보고해 주세요. 생성기와 런타임뿐 아니라 **생성된 코드**의 문제(남의 행을 읽는 권한 검사, 응답에
나오는 비밀번호, 검색으로 주입되는 쿼리 등)도 대상입니다. 3.x 최신 버전은 기능과 버그·보안 수정, 3.0 은 첫 정식
릴리스부터 3년간 버그·보안 수정(`release/3.0`), 2.2 는 보안 수정만 지원합니다. 첫 정식 3.0 전까지는 다음 사전
릴리스(pre-release)에서 고칩니다.
