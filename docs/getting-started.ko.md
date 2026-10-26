<p align="right"><a href="getting-started.md">English</a> · <b>한국어</b></p>

# 시작하기

[README](../README.ko.md#5단계로-내-프로젝트에) 에 5단계가 있고, 이 문서는 거기서 뺀 것을 다룹니다.

## 필요한 것

내 프로젝트는 **Java 17 이상**, Spring Boot 4 이면 **Gradle 8.14 이상**(Boot 4 Gradle 플러그인의 요구)입니다.
ElastiCORE 는 Gradle 플러그인까지 Maven Central 에 있습니다. README 의 `build.gradle` 이 의존성을 Maven Central 에서
받으니, 플러그인도 받도록 내 프로젝트의 `settings.gradle` 에 적습니다:

```gradle
// settings.gradle
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

## Spring Initializr 로 시작했다면

Spring Web, Spring Data JPA, Validation, Lombok, H2 를 고릅니다(기본값인 Boot 4.1.1 도 됩니다). 그 `build.gradle` 은
그대로 두고(함께 생성된 테스트가 그 파일의 테스트 의존성을 씁니다) 빠진 것만 더합니다: `io.elasticore` 플러그인 줄,
`elasticore` 블록, ElastiCORE 의존성 두 줄과 springdoc.

## Spring Boot 3 이면

3.5.16 에서 확인했습니다. README 의 `build.gradle` 에서:

| Spring Boot 4 | Spring Boot 3 |
|---|---|
| Boot 플러그인 `4.0.8` | `3.5.16` |
| `io.elasticore.springboot4:elasticore-springboot4` | `io.elasticore.springboot3:elasticore-springboot3` |
| `spring-boot-starter-webmvc` | `spring-boot-starter-web` |
| springdoc `3.0.3` | `2.8.17` |

생성 코드는 Boot 플러그인 버전을 읽어 스스로 맞춥니다.

## 내 작업, 자세히

- **애플리케이션 클래스**: `com.acme.shop` 이나 그 위 패키지에 `@SpringBootApplication` 클래스를 둡니다. 그래야
  Spring 이 생성된 클래스를 찾습니다. Spring Initializr 는 group 과 artifact 로 정한 패키지에 만들어 줍니다
  (`com.acme` + `shop` 이면 `com.acme.shop`).
- **업무 로직**은 `ProductService.java` 에 씁니다. 컨트롤러처럼 한 번만 생성되는 파일이라, 고치기 전까지는 모델을
  따라 다시 생성되고, 한 번 고치면 그 뒤로는 다시 생성해도 건드리지 않습니다(로그에 `SKIP(user-owned)`).
- **로그인**: 상품을 바꾸려면 `ADMIN` 역할이 필요합니다(`@access(read=public)`: 읽기는 누구나, 나머지는 관리자).
  로그인 처리에서 `SessionCurrentUser.signIn(request, userId, "ADMIN")` 을 부르고(패키지는 Boot 4 에서도
  `io.elasticore.springboot3.security`), Spring Security 를 쓰면 대신 `CurrentUser` 빈을 등록합니다.
  모델에 맡길 수도 있습니다: `@login`·`@password` 필드가 있는 엔티티 줄에 `@signin` 을 쓰면 로그인, 로그아웃,
  "나는 누구" API 가 생성됩니다 - pet store 예제가 그렇게 합니다. `@signin` 엔티티의 조건(노출된 엔티티, 애플리케이션에
  하나, 비밀번호와 로그인은 본인이나 관리자만 변경)은
  [어노테이션 가이드](../elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-annotation-guide.md)에 있습니다.

## 실행

`./gradlew bootRun`. `POST /api/shop/product/search`, `GET /api/shop/product/{id}` 는 누구나 호출할 수 있고, 로그인
없이 상품을 만들면 401 "Sign in to create Product", 가격 0 은 400 "price must be positive" 로 답합니다. 이후 변경은
모델에서 시작합니다: `./gradlew build` 와 `bootRun` 은 컴파일 전에 `elcore3` 를 먼저 돌리므로 코드가 모델보다
뒤처지지 않습니다.

## ElastiCORE 를 소스에서 빌드하기

이 저장소를 받아 `./gradlew publishToMavenLocal` 을 실행하고(JDK 17~22, 컴파일에 쓰는 JDK 8·17 툴체인이 없으면
내려받습니다) 내 프로젝트의 두 저장소 목록 맨 앞에 `mavenLocal()` 을 넣습니다.
