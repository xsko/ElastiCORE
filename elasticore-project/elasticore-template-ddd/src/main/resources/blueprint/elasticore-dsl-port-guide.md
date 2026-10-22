# ElastiCORE Port DSL Guide

## 1. Port 개요

Port는 외부 시스템과의 연결 인터페이스를 YAML DSL로 정의하는 ElastiCORE의 핵심 기능입니다.
개발자는 YAML만 작성하면 프레임워크가 Java 인터페이스를 생성하고, 런타임에 Spring 프록시
팩토리가 동적 구현체를 만듭니다.

### 헥사고날에서 이것이 무엇인가

**아웃바운드 포트입니다.** 생성물은 인터페이스뿐이고 어댑터는 런타임 프록시라 손으로
쓰는 어댑터 클래스가 없습니다 — 애플리케이션은 인터페이스만 알고 기술은 밖에 있는,
정확히 port/adapter 의 모양입니다.

육각형에는 방향이 둘이고 생성물도 둘로 나뉩니다:

| 방향 | 무엇 | 네임스페이스 |
|---|---|---|
| 인바운드 | `OrderUseCase` — 애플리케이션에 들어가는 문 (`hex-usecase` 규칙) | `inboundPort` |
| **아웃바운드 (이 문서)** | `port:` 로 선언하는 http / dbms / gRPC | `port` |
| 아웃바운드 (영속) | `OrderRepositoryPort` (`ddd-repository-port` 규칙) | `domainRepository` |

> **이 포트는 애플리케이션의 것입니다.** 계약이 application DTO 로 쓰이고, 페이징하면
> Spring 의 `Page` 도 씁니다. 그래서 `domainService` 의 `uses:` 에 적으면 경고와 함께
> 무시됩니다 — 도메인 코드가 위 계층에 의존하게 되기 때문입니다. 도메인이 갖는
> 아웃바운드 포트는 리포지터리 포트이고, 둘은 교환 가능하지 않습니다.

### 지원 타입

| 타입 | 용도 | 런타임 구현 |
|------|------|------------|
| HTTP | 외부 REST API 통신 | `HttpServiceProxyFactory` |
| DBMS | 데이터베이스 SQL 실행 | `DbmsServiceProxyFactory` |
| gRPC | Protocol Buffer 기반 통신 | Proto 서비스 생성 |

### 공통 DSL 구조
```yaml
port:
  ServiceName:
    meta: <type> [options]
    methods:
      methodName:
        meta: method [annotations]
        params:
          paramName: ParamType
        return: ReturnType
```

---

## 2. HTTP Port

> **어느 방식인가.** 같은 DSL 로 두 가지 클라이언트가 나옵니다.
> - **런타임 프록시** (기본, 2.1~2.6): 생성물은 `@ExternalService` 인터페이스이고 구현은 ElastiCORE 런타임의 프록시입니다.
>   애플리케이션 클래스에 `@EnableElastiCore` 가 있어야 빈이 됩니다 (5장).
> - **Spring HTTP 서비스 클라이언트** (`httpPort: spring`, Spring Boot 4, 2.7): Spring 이 구현하는 `@HttpExchange`
>   인터페이스입니다. `@EnableElastiCore` 가 필요 없습니다. 규칙 엔진(`elcore3`)의 Spring Boot 4 프로젝트에서 권합니다.
>
> 두 방식 모두 **상대가 오류로 답해도 예외 없이 값으로 돌려줍니다.** 실패를 어떻게 다룰지는 2.8 을 보세요.

### 2.1 기본 구조

```yaml
port:
  ExternalApiService:
    meta: http @url("https://api.example.com") @auth("Bearer token")
    methods:
      getUser:
        meta: method @get("/api/users/{userId}")
        params:
          userId: long
        return: UserDTO

      createUser:
        meta: method @post("/api/users")
        params:
          body: UserRequest @body
        return: UserResponse

      updateUser:
        meta: method @HttpEndpoint(url="/api/users/{id}", method="PUT")
        params:
          id: long
          body: UserRequest @body
        return: UserResponse
```

### 2.2 HTTP 어노테이션

#### Port 레벨
- `@url("baseUrl")` - HTTP 서버 기본 URL
- `@auth("token")` - 기본 인증 토큰 (Authorization 헤더에 설정)
- `@timeout(connect=.., read=.., write=.., response=..)` - 이 Port의 통신 타임아웃 기본값 (단위: ms)
  - `connect`: 연결 수립 타임아웃 (기본 10000)
  - `read`: 응답 수신(read) 타임아웃 (기본 90000)
  - `write`: 요청 전송(write) 타임아웃 (기본 90000)
  - `response`: 전체 응답 타임아웃 (기본 60000)
  - 하위 항목은 모두 선택사항이며, 지정한 항목만 어노테이션에 반영됩니다.
  - 여기서 지정한 값은 생성 코드(`@ExternalService`)에 baked되며, `application.yml`에서 다시 오버라이드할 수 있습니다.

```yaml
# @timeout 사용 예시 — connect/read만 지정
port:
  PaymentApiService:
    meta: http @url("https://api.payment.example.com") @timeout(connect=3000, read=15000)
    methods:
      pay:
        meta: method @post("/pay")
        params:
          body: PayRequest @body
        return: PayResponse
```

#### Method 레벨 (단축)
- `@get("/path")` - GET 요청
- `@post("/path")` - POST 요청
- `@put("/path")` - PUT 요청
- `@delete("/path")` - DELETE 요청

#### Method 레벨 (상세)
- `@HttpEndpoint(url="/path", method="POST", contentType="application/json")`
  - `url`: 엔드포인트 경로 (`{param}` 경로 변수 지원)
  - `method`: HTTP 메서드 (GET, POST, PUT, DELETE, PATCH)
  - `contentType`: Content-Type (기본값: `application/json`)

#### 파라미터
- `@body` - 요청 본문으로 전송
- 경로 변수: URL에 `{paramName}`이 있으면 자동 치환
- 그 외: 쿼리 파라미터로 전송

### 2.3 Runtime Properties (application.yml / application.properties)

HTTP Port의 런타임 동작을 `application.yml` 또는 `application.properties`에서 서비스별로 오버라이드할 수 있습니다.
Property 키는 `{domainName}.{PortServiceName}.{속성명}` 패턴을 따릅니다.

#### 지원 속성 목록

| 속성명 | 기본값 | 설명 |
|--------|--------|------|
| `url` | `@url()` 어노테이션 값 | 기본 URL 오버라이드 |
| `authKey` | `@auth()` 어노테이션 값 | Authorization 헤더 값 (raw) |
| `apiKey` | `""` | Bearer API 키 (`Bearer {apiKey}` 형태로 설정됨, authKey보다 우선) |
| `ignoreSSL` | `false` | SSL 인증서 검증 비활성화 (개발/테스트 환경용) |
| `proxyHost` | (없음) | HTTP 프록시 서버 호스트 |
| `proxyPort` | `0` | HTTP 프록시 서버 포트 |
| `connectTimeout` | `10000` | 연결 수립 타임아웃 (ms) |
| `readTimeout` | `90000` | 응답 수신(read) 타임아웃 (ms) |
| `writeTimeout` | `90000` | 요청 전송(write) 타임아웃 (ms) |
| `responseTimeout` | `60000` | 전체 응답 타임아웃 (ms) |

#### application.yml 예시

```yaml
# 기본 설정
payment.PaymentApiService:
  url: https://api.payment.example.com/v2
  authKey: Bearer sk_live_xxx
  ignoreSSL: false

# 프록시 서버 경유 설정 (예: 사내망에서 외부 API 호출)
insurance.InsuranceApiService:
  url: https://api.insurer.example.com
  proxyHost: proxy.example.com
  proxyPort: 3128
  ignoreSSL: true

# API Key 방식 인증
ai.OpenAiService:
  apiKey: sk-proj-xxxxxxxxxxxx

# ── 타임아웃: 전역 기본값 + Port별 오버라이드 ──
# 전역 기본값 (모든 Port에 적용되는 base)
elasticore:
  http:
    connectTimeout: 5000
    readTimeout: 30000
    writeTimeout: 30000
    responseTimeout: 30000

# 특정 Port만 개별 오버라이드 (느린 외부 API라 길게 잡는 경우)
report.ReportApiService:
  url: https://slow-report.example.com
  readTimeout: 120000
  responseTimeout: 120000
```

#### application.properties 예시

```properties
# 프록시 설정
insurance.InsuranceApiService.url=https://api.insurer.example.com
insurance.InsuranceApiService.proxyHost=proxy.example.com
insurance.InsuranceApiService.proxyPort=3128
insurance.InsuranceApiService.ignoreSSL=true

# URL 오버라이드
payment.PaymentApiService.url=https://api.payment.example.com/v2
```

#### 프록시 설정 상세

프록시는 HTTP 타입(Forward Proxy)을 지원하며, WebClient(Reactor Netty) 경로와 RestTemplate(Multipart) 경로 모두에 적용됩니다.

**적용 범위**:
- **일반 요청 (WebClient)**: Reactor Netty의 `ProxyProvider.Proxy.HTTP`로 프록시 연결
- **Multipart 요청 (RestTemplate)**: `SimpleClientHttpRequestFactory.setProxy()`로 프록시 연결

**사용 사례**:
1. **사내망 → 외부 API**: 보안 정책상 프록시 서버를 경유해야 하는 경우
2. **망분리 환경**: 내부/외부 네트워크 간 프록시 게이트웨이 필요 시
3. **트래픽 모니터링**: 프록시를 통한 API 호출 모니터링/감사

**참고**: `proxyHost`가 null이거나 빈 문자열이면 프록시 없이 직접 연결합니다.

#### 속성 우선순위

인증 관련 속성의 적용 우선순위:
1. `apiKey` → `Authorization: Bearer {apiKey}` 헤더 설정
2. `authKey` → `Authorization: {authKey}` 헤더 설정 (raw)
3. 메서드 인자의 `HttpAuthProvider` 인스턴스
4. Spring Bean `{id}.httpAuthProvider`
5. Spring Bean `{id}.httpTransformFactory`

#### 타임아웃 설정 상세

타임아웃은 `connect / read / write / response` 4종을 각각 독립적으로 설정할 수 있으며, 단위는 모두 **밀리초(ms)** 입니다.
WebClient(Reactor Netty) 경로와 RestTemplate(Multipart) 경로 모두에 적용됩니다 (Multipart는 connect/read만 사용).

**값 결정 우선순위 (각 항목별 독립 적용, 위에서부터 우선)**:
1. **Port별 런타임 property** — `{domainName}.{PortServiceName}.{connectTimeout|readTimeout|writeTimeout|responseTimeout}` (application.yml, 재빌드 없이 변경 가능)
2. **DSL `@timeout` 어노테이션 값** — Port 정의에 baked된 Port별 기본값
3. **전역 런타임 property** — `elasticore.http.{connectTimeout|readTimeout|writeTimeout|responseTimeout}` (모든 Port의 base)
4. **프레임워크 기본값** — connect=10000, read=90000, write=90000, response=60000

즉 **전역(`elasticore.http.*`)을 base로 깔고, 그 위에 Port별(DSL 또는 property)로 덮어쓰는** 구조입니다.
같은 Port에 DSL `@timeout` 값과 Port별 property가 모두 있으면 **property가 우선**합니다(운영 중 재빌드 없이 조정 가능).

**사용 사례**:
1. **전역 정책 + 예외 Port**: 전역으로 30초를 깔고, 느린 리포트 API만 120초로 상향
2. **빠른 실패가 필요한 Port**: 결제/인증 등 응답이 빨라야 하는 Port를 `connectTimeout=3000, readTimeout=10000`으로 짧게 설정
3. **운영 중 임시 조정**: 외부 API 지연 장애 시 `application.yml`의 Port별 property만 늘려 재배포로 대응

### 2.4 인증 설정 (상세)

**정적 토큰 (YAML)**:
```yaml
meta: http @url("https://api.example.com") @auth("Bearer sk_test_123")
```

**Properties 파일 설정**:
```properties
# {domainName}.{portServiceName}.authKey
payment.PaymentApiAdapter.authKey=Bearer sk_live_xxx
```

**동적 인증 (HttpAuthProvider Bean)**:
```java
@Configuration
public class AuthConfig {
    @Bean("payment.PaymentApiAdapter.httpAuthProvider")
    public HttpAuthProvider paymentAuthProvider() {
        return (inputObj, headers) -> {
            String token = generateDynamicToken();
            headers.add(Map.of("Authorization", "Bearer " + token));
            return null; // null이면 inputObj 그대로 사용
        };
    }
}
```
Bean 이름 규칙: `{domainName}.{portServiceName}.httpAuthProvider`

### 2.5 생성 코드

YAML 정의로부터 `@ExternalService` 어노테이션이 붙은 Java 인터페이스가 생성됩니다.
`HttpServiceProxyFactory`가 동적 프록시로 구현을 제공하며, `@Autowired`로 주입하여 사용합니다.

### 2.6 Multipart Form-Data (파일 업로드)

#### 기본 Multipart 요청
```yaml
port:
  FileUploadService:
    meta: http @url("https://api.example.com")
    methods:
      uploadFile:
        meta: method @HttpEndpoint(url="/upload", method="POST", contentType="multipart/form-data")
        params:
          description: string
          file: org.springframework.core.io.FileSystemResource
        return: UploadResponse
```

#### Per-Field Content-Type 지정 (Postman 스타일)

Postman에서 multipart/form-data 요청 시 각 필드에 개별 Content-Type을 지정하는 것처럼,
ElastiCORE Port DSL도 `@contentType` 어노테이션으로 필드별 Content-Type을 지정할 수 있습니다.

**사용 사례**: JSON 데이터와 파일을 함께 전송할 때, JSON 데이터 파트에 `application/json` Content-Type 지정

```yaml
port:
  RentClaimService:
    meta: http @url("http://api.example.com")
    methods:
      submitClaim:
        meta: method @HttpEndpoint(url="/claims/submit", method="POST", contentType="multipart/form-data") -- 렌트 청구
        params:
          data: ClaimRequest @contentType(application/json)
          file: org.springframework.core.io.FileSystemResource
        return: ClaimResponse
```

**생성되는 Java 인터페이스**:
```java
@ExternalService(protocol="http", id="domain.RentClaimService", url="http://api.example.com")
public interface RentClaimService {

    @HttpEndpoint(url="/claims/submit", method="POST", contentType="multipart/form-data", paramNames="data,file")
    ClaimResponse submitClaim(
        @MultipartContentType("application/json") ClaimRequest data,
        org.springframework.core.io.FileSystemResource file
    );
}
```

**@contentType 어노테이션 옵션**:
| 값 | 용도 |
|----|------|
| `application/json` | JSON 객체 전송 (DTO → JSON 자동 직렬화) |
| `text/plain` | 일반 텍스트 전송 |
| `application/xml` | XML 전송 |
| (생략) | 파일의 경우 자동 감지 (application/octet-stream 등) |

**HTTP 요청 형식 (Wire Format)**:
```http
POST /claims/submit HTTP/1.1
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Length: 1234

------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="data"
Content-Type: application/json; charset=UTF-8

{"claimId":"12345","amount":50000,"description":"렌트 청구"}
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="file"; filename="document.pdf"
Content-Type: application/pdf

(binary content)
------WebKitFormBoundary7MA4YWxkTrZu0gW--
```

**런타임 동작**:
1. `@MultipartContentType` 어노테이션이 감지되면 `MultipartRequest` 객체 생성
2. JSON 타입 필드는 자동으로 JSON 문자열로 직렬화
3. `RestTemplate`을 사용하여 `Content-Length` 헤더 자동 계산 (Chunked Encoding 대신)
4. 일부 레거시 서버의 호환성 보장 (Chunked Encoding 미지원 서버 대응)

**사용 예시 (Service Layer)**:
```java
@Service
public class ClaimService {

    @Autowired
    private RentClaimService rentClaimService;

    public ClaimResponse submitClaim(ClaimRequest request, File pdfFile) {
        FileSystemResource fileResource = new FileSystemResource(pdfFile);
        return rentClaimService.submitClaim(request, fileResource);
    }
}
```

**Properties 설정 (선택)**:
```properties
# SSL 인증서 검증 비활성화 (개발용)
domain.RentClaimService.ignoreSSL=true

# API 서버 URL 오버라이드
domain.RentClaimService.url=https://prod-api.example.com

# 프록시 서버 경유 (사내망 → 외부 API)
domain.RentClaimService.proxyHost=proxy.example.com
domain.RentClaimService.proxyPort=3128
```

### 2.7 Spring HTTP 서비스 클라이언트로 생성 (`httpPort: spring`, Spring Boot 4)

Spring Boot 4 프로젝트는 HTTP 포트를 ElastiCORE 런타임 대신 **Spring이 구현하는 `@HttpExchange` 인터페이스**로 생성할 수 있습니다(ElastiCORE 3.0 규칙엔진, `elcore3`). 포트 DSL은 그대로이고, 도메인의 `env.yml`에 한 줄을 더합니다.

```yaml
elasticore:
  config:
    httpPort: spring        # 이 도메인의 HTTP 포트를 Spring HTTP 서비스 클라이언트로
```

```gradle
implementation 'org.springframework.boot:spring-boot-starter-restclient'   // Spring HTTP 서비스 클라이언트
```

Spring Boot 3에서 켜면 경고를 내고 기존 방식으로 생성합니다(서비스 레지스트리는 Spring Framework 7 기능). DBMS 포트는 해당 없습니다.

**생성되는 것** (`erp` 도메인, 포트 `CreditCheckPort`):

| 파일 | 내용 |
|---|---|
| `CreditCheckPort.java` | `@HttpExchange` 인터페이스 |
| `ErpHttpServices.java` (포트 패키지) | `@ImportHttpServices(group = "erp--credit-check-port", ...)`로 등록, 인증 연결 |
| `src/main/resources/<포트 패키지 경로>/erp-http-services.properties` | 그룹별 `base-url`, `connect-timeout`, `read-timeout` 기본값 (DSL의 `@url`, `@timeout`) |

그룹 이름은 `<도메인>--<포트>`(소문자, 단어는 `-`)입니다. 다시 `httpPort`를 끄면 위 두 파일은 지워지고 포트는 예전 방식으로 생성됩니다.

```java
@HttpExchange
public interface CreditCheckPort {

    @GetExchange(url = "/partners/{bizNo}/credit")
    CreditResultDTO checkCredit(@PathVariable("bizNo") String bizNo);

    // 검색 DTO를 GET 쿼리로: 호출하는 쪽의 메서드는 그대로, 펼치는 일은 default 메서드가
    default List<CreditResultDTO> searchCredits(String grade, StockReceiptDTO cond) {
        return searchCreditsExchange(grade, HttpQuery.of(cond));
    }

    @GetExchange(url = "/credits")
    List<CreditResultDTO> searchCreditsExchange(@RequestParam(name = "grade", required = false) String grade,
                                                @RequestParam MultiValueMap<String, String> ecQuery);

    @PostExchange(url = "/partners/{bizNo}/review")
    CreditResultDTO requestReview(@PathVariable("bizNo") String bizNo, @RequestBody(required = false) CreditResultDTO body);
}
```

**요청은 ElastiCORE 런타임과 똑같이 나갑니다**:

| 파라미터 | 보내는 곳 |
|---|---|
| 단순 값(문자열·숫자·boolean)이 URL의 `{이름}`에 있음 | 경로 |
| 그 밖의 단순 값 | 쿼리 파라미터 |
| 객체, GET | 객체의 속성을 쿼리 파라미터로 (null 제외, 컬렉션은 반복) |
| 객체, 그 외 메서드 | JSON 본문 (null이면 본문 없음) |
| 객체, `multipart/form-data` + `@contentType` | 파트 |

4xx/5xx 응답도 런타임의 일반 요청처럼 **예외 없이** 응답 본문을 반환값으로 돌려줍니다 (생성된 설정이
`PortAuth.passErrorBodies` 를 겁니다). 답이 아예 없으면(연결 거부, 시간 초과) `RestClientException` 입니다. 2.8 을 보세요.

**설정은 예전 키가 그대로 통합니다** (`{domain}.{Port}.*`). 새 키로도 씁니다:

| 예전 키 (계속 동작) | Spring Boot 4 키 |
|---|---|
| `erp.CreditCheckPort.url` | `spring.http.serviceclient.erp--credit-check-port.base-url` (그룹 이름 그대로, `--` 두 개) |
| `erp.CreditCheckPort.connectTimeout`, `.readTimeout` (ms), 없으면 `elasticore.http.connectTimeout`, `.readTimeout` | `...connect-timeout`, `...read-timeout` |
| `erp.CreditCheckPort.authKey`, `.apiKey` | (같은 키, apiKey가 우선) |
| bean `erp.CreditCheckPort.httpAuthProvider` | (같은 빈 - 런타임처럼 Authorization이 든 헤더 목록을 받아 고침. 본문 인자는 `null`) |

**base URL 은 기동할 때 한 번 정해집니다.** Spring 은 HTTP 서비스 클라이언트를 애플리케이션이 뜰 때 만들므로, 그때의
`base-url`(예전 키 `erp.CreditCheckPort.url` 도)이 끝까지 쓰입니다. 런타임 프록시는 `{id}.url` 을 **호출할 때마다** 읽습니다.

- 기본값이 서버 자신을 가리키면(같은 서버 안의 가짜 상대, `http://localhost:${server.port}/fake-courier`) 고정 포트로 띄울 때만
  맞습니다. `@SpringBootTest(webEnvironment = RANDOM_PORT)` 의 포트는 클라이언트가 만들어진 뒤에 정해지므로 **포트 0 으로
  호출합니다** (`I/O error on POST request for "http://localhost:0/..."`) - 테스트는 2.8 의 방법으로 하세요.
- 운영 중 주소를 바꾸려면 재기동합니다.

**이런 포트는 예전 방식으로 남습니다** (빌드 로그에 `stays on the ElastiCORE port runtime`과 이유가 나옴). Spring 클라이언트로는 런타임과 같은 요청을 보낼 수 없는 경우입니다:
- `application/x-www-form-urlencoded` (객체를 form 필드로 펼침)
- `@contentType` 없는 `multipart/form-data` (객체의 필드를 각각 파트로)
- GET이 아닌데 객체 파라미터가 둘 이상 (런타임은 마지막 것만 보냄)
- 배열 파라미터 (런타임은 `ids=1,2,3` 한 값으로 보냄)
- URL의 `{이름}`에 맞는 단순 값 파라미터가 없음 (런타임은 그 부분을 지움)

**지원하지 않는 것** (`httpPort: spring`일 때):
- `@timeout(write=...)`, `@timeout(response=...)` - Spring HTTP 클라이언트에 해당 설정이 없어 경고만 하고 적용하지 않습니다.
- `ignoreSSL`, `proxyHost/proxyPort` - `spring.http.serviceclient.<group>.ssl.bundle`이나 JVM 프록시 설정을 씁니다.
- `HttpAuthProvider`가 요청 본문을 바꾸는 것, `HttpTransformerFactory` - 헤더만 반영됩니다.
- 멀티파트 파트별 `@contentType`의 값 - 객체 파트는 JSON, 파일 파트는 파일 형식으로 나갑니다.

### 2.8 실패 처리 - 상대가 실패했을 때

포트는 **실패를 판단하지 않습니다.** 상대가 오류 상태(4xx/5xx)로 답하면 그 본문을 반환값으로 돌려주고, 답이 없으면 HTTP
클라이언트의 예외를 그대로 던집니다. 어느 쪽도 이 API 를 부른 클라이언트에게 "다른 시스템 탓" 이라고 말하지 않습니다 -
실패라고 판단하는 것은 포트를 부르는 코드의 몫입니다.

| 상대가 | 런타임 프록시 (2.1~2.6) | `httpPort: spring` (2.7) |
|---|---|---|
| 200, 읽을 수 있는 본문 | 반환값 | 반환값 |
| 4xx/5xx, JSON 본문 | **반환값** (본문을 반환 타입으로 읽음 - 없는 필드는 `null`) | **반환값** (같음) |
| 4xx/5xx, 반환 타입으로 읽을 수 없는 본문 (HTML 오류 페이지 등) | 예외 (읽기 실패) | 예외 (읽기 실패) |
| 답이 없음 - 연결 거부, 시간 초과 | 예외 (`WebClientRequestException` 등) | `RestClientException` (`ResourceAccessException`) |
| `multipart/form-data` 요청이 4xx/5xx | 예외 (`HttpStatusCodeException` - RestTemplate 로 보냄) | (반환값, 위와 같음) |

예외를 그대로 두면 API 는 500 입니다. 그래서 순진하게 쓰면 **틀린 채로 성공합니다**: 택배사가 500 을 답해도 운송장 번호가
`null` 인 응답이 돌아와 주문이 "배송됨" 으로 저장되고 200 이 나갑니다. (확인: `elasticore-springboot3` 의
`PortFailureContractTest`)

**502 로 답하고 되돌리기** - 런타임의 `io.elasticore.springboot3.http.PortCallException` (502 Bad Gateway) 를 던집니다.
생성된 `XxxApiErrors` 가 502, `"code": "upstream"`, 던진 이유를 `detail` 로 답합니다 (확인: Pet Store
`ShopFlowTest.aRefusalHandCodeThrowsIsSaidByItsCode`). unchecked 예외이므로 트랜잭션 안에서 던지면 Spring 의 기본 규칙대로
그 트랜잭션이 되돌려집니다 - 상태를 바꾼 뒤 부른다면 그 메서드가 트랜잭션이어야 합니다.

```java
// PurchaseOrderService (개발자 파일) - 배송 명령이 택배사를 부른다
@Override
@Transactional                          // 상태 변경과 호출을 한 트랜잭션으로: 실패하면 함께 되돌림
public PurchaseOrderDTO ship(String orderId) {
    PurchaseOrderDTO shipped = super.ship(orderId);
    ShipmentReply reply;
    try {
        reply = courier.ship(new ShipmentRequest(orderId));
    } catch (RestClientException unreachable) {          // 런타임 프록시면 WebClientException 도
        throw new PortCallException("The courier could not be reached", unreachable);
    }
    if (reply == null || reply.getTrackingNo() == null) {  // 오류 상태도 값으로 오므로 내용으로 판단
        throw new PortCallException("The courier did not accept the shipment");
    }
    ...
}
```

- `PortCallException(code, message, args...)` 로 메시지 코드를 주면 요청 언어로 번역됩니다 (`elasticore-rule-guide.md`
  "손으로 쓴 거절을 번역하기"). 상대의 상태와 본문을 남기려면 `PortCallException(port, upstreamStatus, upstreamBody, message, cause)`.
- `ResponseStatusException` 이므로, 이 예외보다 먼저 생성된(그래서 처리기가 없는) `XxxApiErrors` 에서도 502 로 나갑니다
  (본문은 Spring Boot 의 것).
- 포트 전체에 한 번에 걸고 싶으면(`httpPort: spring`) 그 그룹의 `RestClientHttpServiceGroupConfigurer` 에 상태를 보는
  interceptor 를 더해 `PortCallException` 을 던지게 할 수도 있습니다. 설정으로 켜는 실패 정책은 아직 없습니다 (3.1 예정).

**테스트 - 가짜 상대**

- 포트를 목으로 바꿉니다: `@MockitoBean CourierApiPort courier;` (Spring Boot 3.4 이상, 이전은 `@MockBean`). 성공, 오류
  본문(`null` 필드), 예외를 각각 돌려주게 해서 502 와 되돌림을 확인합니다.
- 같은 서버 안의 가짜 컨트롤러(`/fake-courier`)를 부르게 하려면 **고정 포트**로 띄웁니다
  (`webEnvironment = DEFINED_PORT`, 비어 있는 포트를 `server.port` 로). `httpPort: spring` 은 base URL 을 기동할 때 정하므로
  `RANDOM_PORT` 와 같이 쓰면 포트 0 을 부릅니다 (2.7).
- 바깥의 가짜 서버(`MockWebServer`, WireMock)를 쓰면 그 주소를 `{domain}.{Port}.url` 에 넣습니다.

---

## 3. DBMS Port

### 3.1 기본 구조

```yaml
port:
  OrderDbService:
    meta: dbms @datasource("order")
    methods:
      findOrders:
        params:
          input: OrderSearchDTO
        return: List<OrderDTO>
        query: |
          SELECT * FROM orders WHERE status = :status
```

### 3.2 DBMS 어노테이션 및 옵션

| 옵션 | 기본값 | 설명 |
|------|--------|------|
| `@datasource("name")` | (기본 데이터소스) | 사용할 EntityManagerFactory |
| `nativeQuery` | `true` | Native SQL(`true`) 또는 JPQL(`false`) |
| `pageable` | `false` | 페이징 처리 활성화 |
| `ref` | - | 다른 메서드의 SQL 참조 |

### 3.3 반환 타입과 실행 방식

| 반환 타입 | 실행 메서드 | 용도 |
|----------|------------|------|
| `List<DTO>` | `inqueryList` | 전체 결과 목록 |
| `Page<DTO>` | `inqueryPage` | 페이징 결과 (pageable 필수) |
| `DTO` (단일 객체) | `inqueryFirst` | 단건 조회 |
| `int` + update/delete/insert 메서드명 | `executeUpdate` | DML 실행 (영향 행 수 반환) |
| `int` + update/delete/insert 메서드명 + `list<DTO>` 파라미터 | `executeBatchUpdate` | 배치 DML 실행 (요소별 반복 실행, 총 영향 행 수 반환) |
| `int` (기타 메서드명) | `inqueryFirst` | 단일 숫자 조회 |
| `long`, `double`, `float` | `inqueryFirst` | Primitive 숫자 반환 |
| `string` | `inqueryFirst` | 단일 문자열 반환 |
| `boolean` | `inqueryFirst` | 불린 반환 |

**DML 메서드명 규칙**: 메서드명에 `update`, `delete`, `insert`가 포함되면 `executeUpdate`로 실행됩니다.

```yaml
# DML 예시
updateStatus:
  params:
    id: string
    newStatus: string
  return: int
  query: |
    UPDATE orders SET status = :newStatus WHERE id = :id

# 집계 예시
countByStatus:
  params:
    status: string
  return: int
  query: |
    SELECT COUNT(*) FROM orders WHERE status = :status
```

#### List 입력 배치 실행 (Multi-row Insert/Update/Delete)

DML 메서드(`insert`/`update`/`delete` 포함 메서드명, `return: int`)의 파라미터가
`list<DTO>` 타입이면, 프레임워크가 내부적으로 List의 각 요소를 순회하며
SQL을 반복 실행합니다. 각 요소가 한 번의 실행에 대한 파라미터 소스가 되며,
모든 실행의 영향 행 수 합계가 반환됩니다.

```yaml
insertOrderBatch:
  params:
    items: list<OrderInputDTO>      # DTO 리스트 -> 요소별 반복 실행
  return: int
  nativeQuery: true
  query: |
    INSERT INTO orders (order_no, product_code, quantity, price)
    VALUES (:orderNo, :productCode, :quantity, :price)
```

생성되는 인터페이스:
```java
Integer insertOrderBatch(List<OrderInputDTO> items);
```

사용 예시:
```java
List<OrderInputDTO> items = List.of(
    new OrderInputDTO("ORD-001", "P-100", 2, 15000),
    new OrderInputDTO("ORD-002", "P-200", 1, 32000)
);
int affected = orderPortService.insertOrderBatch(items);  // 2 (총 영향 행 수)
```

**동작 규칙**:
- 모든 요소는 **단일 트랜잭션** 내에서 실행됩니다 (하나라도 실패하면 전체 롤백)
- 정적 SQL이면 Query 객체를 1회 생성 후 파라미터만 재바인딩하여 반복 실행 (성능 최적화)
- 동적 SQL(`${...}` 치환 또는 `/* if */` 조건 블록)이면 요소마다 SQL을 재처리
- 빈 List 입력 시 SQL을 실행하지 않고 0을 반환
- List 요소 중 null은 건너뜀
- DTO 요소뿐 아니라 `Map` 요소의 List도 지원
- **단순 값 List는 배치 대상이 아님**: `list<long>`, `list<string>` 등 단순 타입 List는
  기존처럼 IN 절 바인딩(`WHERE id IN (:ids)`) 용도로 한 번만 실행됩니다

```yaml
# 단순 값 List는 IN 절 바인딩 (배치 아님)
deleteByIds:
  params:
    ids: list<long>
  return: int
  query: |
    DELETE FROM orders WHERE id IN (:ids)
```

### 3.4 Dynamic SQL - JEXL 조건부 블록

SQL에 조건부 블록을 추가하여 파라미터 값에 따라 동적으로 쿼리를 구성합니다.

#### 인라인 조건 (한 줄)
```sql
AND status = :status /* if:status */
AND name LIKE :name /* if:name */
```
- 조건이 단순 식별자(예: `status`)이면 해당 파라미터의 null/empty 여부로 판단
- String 타입: null 또는 빈 문자열이면 조건 제거
- 기타 타입: null이면 조건 제거

#### 블록 조건 (여러 줄)
```sql
/* if:status */
  AND status = :status
  AND active = true
/* if-end */
```
- `/* if:조건 */`으로 시작, `/* if-end */`로 종료
- 블록 내 모든 줄이 조건에 따라 포함/제거

#### JEXL 표현식
```sql
AND min_age >= :minAge /* if:input.getMinAge() > 0 */

/* if:input.searchCriteria != null */
  AND criteria = :searchCriteria
  AND sub_criteria = :subCriteria
/* if-end */
```
- `input` 변수로 입력 객체에 접근 가능
- boolean 결과를 반환하는 모든 JEXL 표현식 사용 가능
- 복잡한 비즈니스 로직 표현: `input.getType() == 'VIP' && input.getAmount() > 10000`

### 3.5 Template Variable Substitution (${...})

```yaml
query: |
  SELECT * FROM ${tableName}
  WHERE category = :category
  AND id = :id
```
- `${param}`: 입력 객체에서 값을 추출하여 **SQL 문자열에 직접 치환** (위의 `tableName`: 서버가 정하는 값)
- `:param`: JPA Named Parameter로 안전하게 바인딩
- **보안 주의**: `${...}`는 SQL Injection에 취약 - 사용자 입력을 직접 전달하면 안됨
- 용도: 동적 테이블명, 동적 컬럼명 등 DDL 수준의 변경이 필요할 때만 사용

### 3.6 sortCode (정렬)

입력 객체에서 정렬 정보를 자동 추출하여 `ORDER BY` 절을 생성합니다.

**추출 우선순위**:
1. Map의 `sortCode` 키
2. 객체의 `getSortCode()` 메서드
3. 객체의 `getSortColumn()` + `getSortAscending()` 메서드

**형식**: `field1+,field2-`
- `+` = ASC (오름차순)
- `-` = DESC (내림차순)
- 방향을 생략하면 ASC
- 쉼표로 다중 필드 구분

**예시**: `name+,createdDate-` → `ORDER BY name ASC, createdDate DESC`

sortCode는 조건부 SQL 처리가 완료된 이후에 쿼리 끝에 추가됩니다. 조건 블록(`/* if:... */`)이 있는 SQL에만
추가되고, 조건 블록이 없는 SQL에서는 정렬 정보를 쓰지 않습니다.

**필드에 쓸 수 있는 값** (Since: 3.0, 2.2.261014): 컬럼명과 방향만 허용합니다.
- 컬럼명: 문자·숫자·`_`·`$`, 첫 글자는 문자나 `_`(그 뒤로는 태국어·힌디어 모음 기호 같은 결합 문자도 가능).
  `o.created_at`처럼 `.`으로 한정 가능(`.` 뒤도 같은 규칙). 방향까지 256자 이내, 항목은 32개까지(정렬값 전체 8,224자까지).
  컬럼명과 방향 사이의 공백은 괜찮습니다(`name -`)
- 그 밖의 값 - 컬럼명 안의 공백, 따옴표, 괄호, 주석, `;`, 컬럼 순번(`1+`), 따옴표로 감싼 이름(`"order"+`),
  식(`LOWER(name)+`) - 은 SQL을 실행하지 않고 `BadRequestException`으로 거부합니다 (v3로 생성한 API는 400으로 응답)
- 정렬값은 요청에서 그대로 오고 SQL 문자열에 들어가므로, 이 검사가 SQL Injection을 막습니다.
  컬럼명이면 SELECT에 없는 컬럼으로도 정렬할 수 있으니, 민감한 컬럼이 있는 테이블이면 서비스에서
  허용할 정렬값을 정해 두고 그 외 값은 버립니다.

### 3.7 SQL 파일 핫리로드

- **개발 모드** (`src/main/resources/` 경로의 파일이 존재할 때):
  - 매 요청마다 파일의 `lastModified`를 확인
  - 변경 감지 시 SQL을 다시 로드
  - 서버 재시작 없이 SQL 수정 즉시 반영
- **운영 모드** (JAR 패키징):
  - 클래스패스에서 1회 로드 후 영구 캐시
  - 리로드 없음 (성능 최적화)
- 프로젝트 루트 탐지: `target/`, `build/`, `out/production/` 디렉토리 기준

### 3.8 Query Reference (ref)

```yaml
findOrders:
  params:
    input: OrderSearchDTO
  return: List<OrderDTO>
  nativeQuery: true
  query: |
    SELECT * FROM orders WHERE status = :status /* if:status */

findOrdersPaged:
  params:
    input: OrderSearchDTO
  return: Page<OrderDTO>
  pageable: true
  ref: findOrders
```
- `ref`로 지정한 메서드의 SQL을 재사용
- 페이징 버전이 비페이징 쿼리를 참조할 때 유용
- 중복 SQL 관리 제거

### 3.9 다중 데이터소스

```yaml
# env.yml
config:
  datasource:
    name: "order"
```

```yaml
# port YAML
port:
  OrderDbService:
    meta: dbms @datasource("order")
```

- `EntityManagerFactory` 빈 이름으로 데이터소스 지정
- 풀네임(`orderEntityManagerFactory`) 또는 약칭(`order`) 모두 사용 가능
- 단일 데이터소스인 경우 `@datasource` 생략 가능

### 3.10 Native Query vs JPQL

- `nativeQuery: true` (기본값): 데이터베이스 네이티브 SQL로 실행
- `nativeQuery: false`: JPQL로 실행 (예: `SELECT u FROM User u WHERE u.name = :name`)

### 3.11 결과 매핑

**DTO 매핑**:
- 컬럼명을 camelCase로 변환하여 필드 매칭 시도
- 변환 실패 시 원본 컬럼명으로 매칭 (대소문자 무시)
- 예: `user_name` → `userName` 필드 매칭

**Map 매핑** (`Map` 반환 타입 사용 시):
- `snake_case` 컬럼명 → `camelCase` 키로 자동 변환

**날짜/시간 변환**:
- `java.sql.Date` → `LocalDate`
- `java.sql.Timestamp` → `LocalDateTime` 또는 `Instant`
- `java.sql.Time` → `LocalTime`

### 3.12 파라미터 입력 처리

**단일 비원시 파라미터**: DTO 객체 그대로 전달
```yaml
params:
  input: OrderSearchDTO   # OrderSearchDTO 객체의 getter로 파라미터 추출
```

**다중 파라미터 또는 원시 타입**: `Map<String, Object>`로 자동 패키징
```yaml
params:
  status: string          # Map.put("status", status)
  minAmount: int          # Map.put("minAmount", minAmount)
```

### 3.13 Pageable 자동 추출

입력 객체에서 페이징 정보를 자동 감지합니다 (우선순위순):

1. 입력 객체가 `Pageable` 인스턴스인 경우 직접 사용
2. `getPageable()` 메서드가 있으면 호출
3. Map: `pageNumber`/`page` + `sizeVal`/`size` 키
4. 객체: 동일 이름의 필드

**기본값**: page=0, size=50

### 3.14 완전한 DBMS Port 예시

```yaml
port:
  RentalDbService:
    meta: dbms @datasource("rental")
    methods:
      findRentals:
        meta: method -- 렌탈 검색 (페이징)
        params:
          input: RentalSearchDTO
        return: Page<RentalDTO>
        pageable: true
        query: |
          SELECT r.*, c.com_name
          FROM rentals r
          JOIN companies c ON r.com_id = c.com_id
          WHERE 1=1
          AND r.status = :status /* if:status */
          /* if:input.getMinAmount() > 0 */
            AND r.amount >= :minAmount
          /* if-end */

      findRentalsAll:
        meta: method -- 렌탈 전체 목록
        params:
          input: RentalSearchDTO
        return: List<RentalDTO>
        ref: findRentals

      countByStatus:
        params:
          status: string
        return: int
        query: "SELECT COUNT(*) FROM rentals WHERE status = :status"

      updateStatus:
        params:
          rentalId: string
          newStatus: string
        return: int
        query: "UPDATE rentals SET status = :newStatus WHERE rental_id = :rentalId"
```

---

## 4. gRPC Port

```yaml
port:
  UserGrpcService:
    meta: proto @server @client
    methods:
      getUser:
        meta: method -- 사용자 조회
        params:
          input: UserProtoInput
        return: UserProtoOutput

      listUsers:
        meta: method -- 사용자 목록
        params:
          input: UserListRequest
        return: UserListResponse
```

- `@server`: gRPC 서버 코드 생성
- `@client`: gRPC 클라이언트 코드 생성
- Protocol Buffer 서비스 정의(.proto)가 자동 생성됨

---

## 5. 환경 설정

### build.gradle
```gradle
dependencies {
    // 버전은 README 의 설치 안내와 같게 (생성기와 런타임은 같은 버전)
    implementation 'io.elasticore:elasticore-base:<version>'
    implementation 'io.elasticore.springboot3:elasticore-springboot3:<version>'   // Boot 4: io.elasticore.springboot4:elasticore-springboot4

    // DBMS Port 사용 시
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // httpPort: spring (2.7) 사용 시
    implementation 'org.springframework.boot:spring-boot-starter-restclient'
}
```
런타임 프록시의 HTTP 클라이언트(WebClient)는 런타임 라이브러리가 실행할 때 함께 가져옵니다. 코드에서 그 타입을 쓰려면
(예: `WebClientException` 을 잡으려면) `spring-boot-starter-webflux` 를 직접 적으세요.

### Application 클래스
런타임 프록시(2.1~2.6, DBMS 포트)는 `@EnableElastiCore` 가 빈으로 등록합니다. `httpPort: spring` 포트만 쓰면 필요 없습니다.

```java
@SpringBootApplication
@EnableElastiCore          // io.elasticore.springboot3.EnableElastiCore - Boot 4 런타임도 같은 패키지
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```
`@ComponentScan("io.elasticore.springboot3")` 로는 포트가 등록되지 않습니다 - 등록기는 컴포넌트가 아니라 `@EnableElastiCore`
가 가져오는 것입니다. 그 스캔이 등록하는 것은 예전과 같이 `ApplicationContextProvider` 와 `DbTransactionGateway` 뿐이고,
API 오류 처리기(`RequestShapeAdvice`)는 스캔에 잡히지 않습니다 - 생성된 `XxxApiErrors` 가 가져올 때만 생깁니다.

### env.yml (Port 네임스페이스)
```yaml
env:
  config:
    domainName: demo
    framework: springboot
    mode: jpa

  namespace:
    entity: com.example.domain.model
    dto: com.example.application.dto
    repository: com.example.infrastructure.persistence
    service: com.example.application
    inboundPort: com.example.application.port.in
    port: com.example.application.port.out
```
`port` 네임스페이스를 지정해야 생성된 인터페이스가 해당 패키지에 위치합니다.

> **`domain` 아래에 두지 마세요.** 이 인터페이스는 application DTO 를 참조하므로
> 도메인 패키지에 놓이면 도메인이 위 계층을 임포트하게 됩니다. 헥사고날에서
> 인터페이스는 application 쪽에, 어댑터가 infrastructure 에 있습니다 — 그리고 여기서
> 어댑터는 런타임 프록시라 생성 파일이 없으므로, **생성되는 유일한 파일이
> `application.port.out` 에 놓이는 것이 맞습니다.**

### 프록시를 빈으로 등록해야 합니다

생성물은 인터페이스이고 구현체는 런타임 프록시입니다. **아무도 등록하지 않은 프록시는
주입할 수 없는 포트입니다:**

```
No qualifying bean of type 'ShopReportService'
```

애플리케이션에서는 `@EnableElastiCore` 등록기가 스캔으로 처리합니다. 등록기는
**`@EnableElastiCore` 가 붙은 클래스의 패키지**와, 호출 스택에 있는 `@SpringBootApplication` 클래스의 패키지를
봅니다. 그래서 그 애플리케이션 클래스를 띄우는 `@SpringBootTest` 도 포트 빈을 받습니다 (3.0.261022-rc1 부터 - 전에는
호출 스택만 보아 테스트 컨텍스트에서는 아무것도 등록하지 않았습니다). 두 패키지에서 같은 인터페이스를 찾으면 한 번만
등록합니다.

`@EnableElastiCore` 가 없는 구성(테스트가 런타임 조각만 가져오는 경우)에서는 직접 등록합니다. 직접 선언한
`@Bean` 이 있으면 등록기는 그 포트를 등록하지 않으므로, 예전처럼 테스트에 적어 둔 `@Bean` 도 그대로 동작합니다:

```java
@Bean
ShopReportService shopReportService(BeanFactory beanFactory, Environment environment) {
    return DbmsServiceProxyFactory.createService(
            ShopReportService.class,
            ShopReportService.class.getAnnotation(DbmsService.class),
            beanFactory, environment);
}
```

`elasticore-template-ddd` 의 `DddSampleApp` 이 이 형태를 그대로 보여줍니다.

**프록시를 감싸려면 `@Primary`.** 직접 선언한 빈이 `@Primary` 이면 등록기의 프록시는 남습니다. 타입으로 주입하는 곳은
감싼 빈을 받고, 감싼 빈은 이름(인터페이스의 단순 이름)으로 프록시를 받습니다:

```java
@Bean
@Primary
CourierPort retryingCourier(@Qualifier("CourierPort") CourierPort proxy) {
    return parcel -> retry(() -> proxy.ship(parcel));
}
```

`@Primary` 가 아닌 빈을 선언하면 프록시는 등록되지 않으므로, 그때는 이름 `"CourierPort"` 로 주입할 수 없습니다.
`@Primary` 인 가짜(테스트에서 포트를 통째로 바꾸는 빈)도 프록시를 남기므로 `List<CourierPort>`·`getBeansOfType` 은 둘을 받습니다 -
하나만 있어야 하면 가짜에서 `@Primary` 를 빼세요.
포트 인터페이스를 구현한 클래스(가짜, 래퍼)가 스캔하는 패키지에 있어도 프록시로 등록하지 않습니다 - 인터페이스만 포트입니다.

### application.properties
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/maindb
spring.datasource.username=user
spring.datasource.password=password
```

---

## 6. Best Practices

### SQL Injection 방지
```yaml
# 안전: Named Parameter 사용
query: |
  SELECT * FROM users WHERE name = :name

# 위험: Template Variable 사용 (사용자 입력 금지)
query: |
  SELECT * FROM ${tableName} WHERE id = :id
```
- `:param`은 JPA가 PreparedStatement로 처리하므로 안전
- `${param}`은 문자열 직접 치환이므로 SQL Injection에 취약
- `${...}`는 시스템이 제어하는 값(테이블명, 스키마명)에만 사용

### 핫리로드 활용
- 개발 시 SQL 수정 후 서버 재시작 없이 즉시 확인 가능
- 운영 환경에서는 자동으로 캐시 모드로 전환되어 성능 영향 없음

### 페이징 패턴
- 기본 쿼리 정의 후 `ref`로 페이징 버전 생성 (3.8 참조)
- `Page<T>` 반환 시 자동으로 `SELECT COUNT(*)` 쿼리 실행
- 입력 객체의 `page`/`size` 필드에서 페이징 정보 자동 추출

### sortCode 활용
- 입력 DTO에 `sortCode` 필드 추가: `"name+,createdDate-"` 형식
- SQL에 ORDER BY 절 불필요 (프레임워크가 자동 추가). 정렬값을 받는 SQL에 ORDER BY를 쓰면 그 뒤에
  ORDER BY가 한 번 더 붙어 오류가 나므로 쓰지 않습니다
- 클라이언트가 동적으로 정렬 기준 변경 가능. 컬럼명이 아닌 값은 거부됩니다 (3.6 참조)

### JEXL 조건 사용 지침
- 단순 null 체크: 파라미터명만 지정 (`/* if:status */`)
- 복잡한 조건: `input.` 접두사로 객체 접근 (`/* if:input.getAmount() > 0 */`)
- 매우 복잡한 비즈니스 로직은 서비스 레이어에서 처리 권장

### 메서드 네이밍 규칙
| 접두사 | 용도 | 반환 타입 |
|--------|------|----------|
| `find`, `get`, `search` | 조회 | `List<T>`, `Page<T>`, `T` |
| `count` | 건수 조회 | `int`, `long` |
| `exists`, `check` | 존재 여부 | `boolean` |
| `update` | 수정 | `int` |
| `insert`, `create` | 등록 | `int` |
| `delete`, `remove` | 삭제 | `int` |

### 동적 SQL 패턴
- `WHERE 1=1` 패턴으로 조건 조합 안전하게 처리
- 관련 조건은 블록(`/* if:... */` ~ `/* if-end */`)으로 묶어서 관리
- NULL 값이면 해당 조건 자동 제거
- DML 메서드명 규칙 준수 (`update`, `delete`, `insert` 접두사)

### HTTP Port - Multipart 요청 패턴

**JSON + 파일 전송 시 @contentType 사용**:
```yaml
params:
  jsonData: RequestDTO @contentType(application/json)  # JSON 직렬화
  attachment: org.springframework.core.io.FileSystemResource  # 파일
```
- DTO 객체를 JSON으로 자동 직렬화하여 multipart 파트로 전송
- 파일은 `FileSystemResource` 또는 `Resource` 타입 사용
- Content-Type 미지정 시 파일은 자동 감지

**레거시 서버 호환성**:
- `@contentType`을 사용하면 자동으로 `RestTemplate` 기반 전송으로 전환
- `Content-Length` 헤더가 자동 계산되어 Chunked Encoding 미지원 서버와 호환
- WebFlux 환경에서도 multipart 요청 안정성 보장

**디버깅 팁**:
```properties
# HTTP Port 디버그 로그 활성화
logging.level.io.elasticore.springboot3.http.HttpServiceProxyFactory=DEBUG
logging.level.io.elasticore.springboot3.http.HttpApiClient=DEBUG
```

### HTTP Port - 인증 패턴

**정적 토큰 (개발/테스트용)**:
```yaml
meta: http @url("https://api.example.com") @auth("Bearer test_token")
```

**Properties 기반 (환경별 분리)**:
```properties
# application-dev.properties
domain.ServiceName.authKey=Bearer dev_token

# application-prod.properties
domain.ServiceName.authKey=Bearer prod_token
```

**동적 토큰 (OAuth, JWT 갱신 등)**:
```java
@Bean("{domain}.{ServiceName}.httpAuthProvider")
public HttpAuthProvider authProvider(TokenService tokenService) {
    return (input, headers) -> {
        String token = tokenService.getValidToken();
        headers.add(Map.of("Authorization", "Bearer " + token));
        return null;
    };
}
```
- Bean 이름 규칙: `{domainName}.{PortServiceName}.httpAuthProvider`
- `return null`이면 원본 입력 객체 사용
- 입력 객체 변환이 필요하면 변환된 객체 반환