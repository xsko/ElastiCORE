# ElastiCORE Port DSL Guide

## 1. Port Overview

A port is a core ElastiCORE feature: it defines the interface to an external system in the YAML DSL.
You write only YAML. The framework generates a Java interface, and at runtime a Spring proxy
factory creates the implementation dynamically.

### What this is in hexagonal architecture

**This is an outbound port.** The generated code is only the interface, and the adapter is a runtime proxy, so there is no
hand-written adapter class - the application knows only the interface and the technology stays outside,
which is exactly the shape of port/adapter.

A hexagon has two directions, and the generated code is split in two:

| Direction | What | Namespace |
|---|---|---|
| Inbound | `OrderUseCase` - the door into the application (`hex-usecase` rule) | `inboundPort` |
| **Outbound (this document)** | http / dbms / gRPC declared with `port:` | `port` |
| Outbound (persistence) | `OrderRepositoryPort` (`ddd-repository-port` rule) | `domainRepository` |

> **This port belongs to the application.** Its contract is written in application DTOs, and when it pages
> it also uses Spring's `Page`. So if you list it in the `uses:` of a `domainService`, it is ignored with a warning -
> the domain code would depend on a layer above it. The outbound port that the domain owns is the repository port,
> and the two are not interchangeable.

### Supported types

| Type | Use | Runtime implementation |
|------|-----|------------------------|
| HTTP | Communication with an external REST API | `HttpServiceProxyFactory` |
| DBMS | Run SQL against a database | `DbmsServiceProxyFactory` |
| gRPC | Communication based on Protocol Buffers | Proto service generation |

### Common DSL structure
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

> **Which way.** The same DSL produces two kinds of client.
> - **Runtime proxy** (default, 2.1 to 2.6): the generated code is an `@ExternalService` interface, and the implementation is a proxy in the ElastiCORE runtime.
>   In a Spring Boot application, the auto-configuration of the runtime library registers it as a bean - `@EnableElastiCore` is
>   no longer needed from 3.0.261027-rc2 (chapter 5).
> - **Spring HTTP service client** (`httpPort: spring`, Spring Boot 4, 2.7): an `@HttpExchange`
>   interface that Spring implements. Recommended for Spring Boot 4 projects on the rule engine (`elcore3`).
>
> With both ways, **if the other side answers with an error, you get the answer as a value, without an exception.** See 2.8 for how to handle failures.

### 2.1 Basic structure

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

### 2.2 HTTP annotations

#### Port level
- `@url("baseUrl")` - base URL of the HTTP server
- `@auth("token")` - default authentication token (set in the Authorization header)
- `@timeout(connect=.., read=.., write=.., response=..)` - default communication timeouts for this port (unit: ms)
  - `connect`: connection timeout (default 10000)
  - `read`: response read timeout (default 90000)
  - `write`: request write timeout (default 90000)
  - `response`: overall response timeout (default 60000)
  - All sub-items are optional, and only the ones you give are put in the annotation.
  - The values you give here are baked into the generated code (`@ExternalService`), and you can override them again in `application.yml`.

```yaml
# @timeout example - only connect/read given
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

#### Method level (short form)
- `@get("/path")` - GET request
- `@post("/path")` - POST request
- `@put("/path")` - PUT request
- `@delete("/path")` - DELETE request

#### Method level (detailed)
- `@HttpEndpoint(url="/path", method="POST", contentType="application/json")`
  - `url`: endpoint path (`{param}` path variables are supported)
  - `method`: HTTP method (GET, POST, PUT, DELETE, PATCH)
  - `contentType`: Content-Type (default: `application/json`)

#### Parameters
- `@body` - sent as the request body
- Path variable: if the URL has `{paramName}`, it is substituted automatically
- Anything else: sent as a query parameter

### 2.3 Runtime properties (application.yml / application.properties)

You can override the runtime behavior of an HTTP port per service in `application.yml` or `application.properties`.
Property keys follow the pattern `{domainName}.{PortServiceName}.{propertyName}`.

#### Supported properties

| Property | Default | Description |
|--------|--------|------|
| `url` | value of the `@url()` annotation | Overrides the base URL |
| `authKey` | value of the `@auth()` annotation | Authorization header value (raw) |
| `apiKey` | `""` | Bearer API key (set as `Bearer {apiKey}`, takes precedence over authKey) |
| `ignoreSSL` | `false` | Disables SSL certificate verification (for development/test environments) |
| `proxyHost` | (none) | HTTP proxy server host |
| `proxyPort` | `0` | HTTP proxy server port |
| `connectTimeout` | `10000` | Connection timeout (ms) |
| `readTimeout` | `90000` | Response read timeout (ms) |
| `writeTimeout` | `90000` | Request write timeout (ms) |
| `responseTimeout` | `60000` | Overall response timeout (ms) |

#### application.yml example

```yaml
# Basic settings
payment.PaymentApiService:
  url: https://api.payment.example.com/v2
  authKey: Bearer sk_live_xxx
  ignoreSSL: false

# Through a proxy server (e.g. calling an external API from an internal network)
insurance.InsuranceApiService:
  url: https://api.insurer.example.com
  proxyHost: proxy.example.com
  proxyPort: 3128
  ignoreSSL: true

# API key authentication
ai.OpenAiService:
  apiKey: sk-proj-xxxxxxxxxxxx

# -- Timeouts: global defaults + per-port overrides --
# Global defaults (the base for all ports)
elasticore:
  http:
    connectTimeout: 5000
    readTimeout: 30000
    writeTimeout: 30000
    responseTimeout: 30000

# Override only a specific port (when you allow a long time for a slow external API)
report.ReportApiService:
  url: https://slow-report.example.com
  readTimeout: 120000
  responseTimeout: 120000
```

#### application.properties example

```properties
# Proxy settings
insurance.InsuranceApiService.url=https://api.insurer.example.com
insurance.InsuranceApiService.proxyHost=proxy.example.com
insurance.InsuranceApiService.proxyPort=3128
insurance.InsuranceApiService.ignoreSSL=true

# URL override
payment.PaymentApiService.url=https://api.payment.example.com/v2
```

#### Proxy settings in detail

The proxy supports the HTTP type (forward proxy), and applies to both the WebClient (Reactor Netty) path and the RestTemplate (Multipart) path.

**Scope**:
- **Normal requests (WebClient)**: connect through the proxy with Reactor Netty's `ProxyProvider.Proxy.HTTP`
- **Multipart requests (RestTemplate)**: connect through the proxy with `SimpleClientHttpRequestFactory.setProxy()`

**Use cases**:
1. **Internal network to external API**: when a security policy requires going through a proxy server
2. **Network-separated environment**: when a proxy gateway is needed between the internal and external networks
3. **Traffic monitoring**: monitoring/auditing API calls through the proxy

**Note**: if `proxyHost` is null or an empty string, the port connects directly without a proxy.

#### Property priority

The priority of the authentication properties:
1. `apiKey` -> sets the `Authorization: Bearer {apiKey}` header
2. `authKey` -> sets the `Authorization: {authKey}` header (raw)
3. An `HttpAuthProvider` instance in the method arguments
4. Spring bean `{id}.httpAuthProvider`
5. Spring bean `{id}.httpTransformFactory`

#### Timeout settings in detail

The four timeouts `connect / read / write / response` can be set independently, and the unit is always **milliseconds (ms)**.
They apply to both the WebClient (Reactor Netty) path and the RestTemplate (Multipart) path (Multipart uses only connect/read).

**How a value is decided (applied independently per item; higher in the list wins)**:
1. **Per-port runtime property** - `{domainName}.{PortServiceName}.{connectTimeout|readTimeout|writeTimeout|responseTimeout}` (application.yml, can be changed without a rebuild)
2. **DSL `@timeout` annotation value** - the per-port default baked into the port definition
3. **Global runtime property** - `elasticore.http.{connectTimeout|readTimeout|writeTimeout|responseTimeout}` (the base for all ports)
4. **Framework default** - connect=10000, read=90000, write=90000, response=60000

In other words, **the global value (`elasticore.http.*`) is the base, and the per-port value (DSL or property) overrides it.**
If the same port has both a DSL `@timeout` value and a per-port property, **the property wins** (so you can adjust it in operation without a rebuild).

**Use cases**:
1. **Global policy + exception port**: set 30 seconds globally, and raise only the slow report API to 120 seconds
2. **Ports that need to fail fast**: set a short `connectTimeout=3000, readTimeout=10000` on ports that must answer quickly, such as payment/authentication
3. **Temporary adjustment in operation**: when an external API is slow and causes an outage, raise only the per-port property in `application.yml` and redeploy

### 2.4 Authentication settings (details)

**Static token (YAML)**:
```yaml
meta: http @url("https://api.example.com") @auth("Bearer sk_test_123")
```

**Properties file setting**:
```properties
# {domainName}.{portServiceName}.authKey
payment.PaymentApiAdapter.authKey=Bearer sk_live_xxx
```

**Dynamic authentication (HttpAuthProvider bean)**:
```java
@Configuration
public class AuthConfig {
    @Bean("payment.PaymentApiAdapter.httpAuthProvider")
    public HttpAuthProvider paymentAuthProvider() {
        return (inputObj, headers) -> {
            String token = generateDynamicToken();
            headers.add(Map.of("Authorization", "Bearer " + token));
            return null; // if null, inputObj is used as it is
        };
    }
}
```
Bean name rule: `{domainName}.{portServiceName}.httpAuthProvider`

### 2.5 Generated code

From the YAML definition, a Java interface annotated with `@ExternalService` is generated.
`HttpServiceProxyFactory` provides the implementation as a dynamic proxy, and you inject it with `@Autowired`.

### 2.6 Multipart form-data (file upload)

#### Basic multipart request
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

#### Per-field Content-Type (Postman style)

In Postman, you can set a separate Content-Type on each field of a multipart/form-data request.
The ElastiCORE Port DSL does the same with the `@contentType` annotation, per field.

**Use case**: when you send JSON data together with a file, set the `application/json` Content-Type on the JSON data part

```yaml
port:
  RentClaimService:
    meta: http @url("http://api.example.com")
    methods:
      submitClaim:
        meta: method @HttpEndpoint(url="/claims/submit", method="POST", contentType="multipart/form-data") -- rental claim
        params:
          data: ClaimRequest @contentType(application/json)
          file: org.springframework.core.io.FileSystemResource
        return: ClaimResponse
```

**Generated Java interface**:
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

**@contentType annotation options**:
| Value | Use |
|----|------|
| `application/json` | Send a JSON object (DTO -> JSON, serialized automatically) |
| `text/plain` | Send plain text |
| `application/xml` | Send XML |
| (omitted) | For a file, detected automatically (application/octet-stream, etc.) |

**HTTP request format (wire format)**:
```http
POST /claims/submit HTTP/1.1
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Length: 1234

------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="data"
Content-Type: application/json; charset=UTF-8

{"claimId":"12345","amount":50000,"description":"rental claim"}
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="file"; filename="document.pdf"
Content-Type: application/pdf

(binary content)
------WebKitFormBoundary7MA4YWxkTrZu0gW--
```

**Runtime behavior**:
1. When the `@MultipartContentType` annotation is detected, a `MultipartRequest` object is created
2. JSON-typed fields are serialized to a JSON string automatically
3. `RestTemplate` is used, so the `Content-Length` header is calculated automatically (instead of chunked encoding)
4. Compatibility with some legacy servers is kept (servers that do not support chunked encoding)

**Usage example (service layer)**:
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

**Properties settings (optional)**:
```properties
# Disable SSL certificate verification (for development)
domain.RentClaimService.ignoreSSL=true

# Override the API server URL
domain.RentClaimService.url=https://prod-api.example.com

# Through a proxy server (internal network -> external API)
domain.RentClaimService.proxyHost=proxy.example.com
domain.RentClaimService.proxyPort=3128
```

### 2.7 Generate a Spring HTTP service client (`httpPort: spring`, Spring Boot 4)

A Spring Boot 4 project can generate HTTP ports as **`@HttpExchange` interfaces that Spring implements** instead of the ElastiCORE runtime (ElastiCORE 3.0 rule engine, `elcore3`). The port DSL stays the same, and you add one line to the domain's `env.yml`.

```yaml
elasticore:
  config:
    httpPort: spring        # HTTP ports of this domain as Spring HTTP service clients
```

```gradle
implementation 'org.springframework.boot:spring-boot-starter-restclient'   // Spring HTTP service client
```

If you turn it on in Spring Boot 3, it gives a warning and generates the old way (the service registry is a Spring Framework 7 feature). DBMS ports do not apply.

**What is generated** (`erp` domain, port `CreditCheckPort`):

| File | Content |
|---|---|
| `CreditCheckPort.java` | the `@HttpExchange` interface |
| `ErpHttpServices.java` (port package) | registered with `@ImportHttpServices(group = "erp--credit-check-port", ...)`, wires the authentication |
| `src/main/resources/<port package path>/erp-http-services.properties` | per-group defaults for `base-url`, `connect-timeout`, `read-timeout` (from the DSL's `@url`, `@timeout`) |

The group name is `<domain>--<port>` (lower case, words joined with `-`). If you turn `httpPort` off again, the two files above are deleted and the port is generated the old way.

```java
@HttpExchange
public interface CreditCheckPort {

    @GetExchange(url = "/partners/{bizNo}/credit")
    CreditResultDTO checkCredit(@PathVariable("bizNo") String bizNo);

    // Search DTO as GET query: the caller's method stays the same, and a default method does the expanding
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

**The request goes out exactly as with the ElastiCORE runtime**:

| Parameter | Where it is sent |
|---|---|
| Simple value (string, number, boolean) that is in the URL as `{name}` | Path |
| Any other simple value | Query parameter |
| Object, GET | The object's properties as query parameters (null excluded, collections repeated) |
| Object, any other method | JSON body (no body if null) |
| Object, `multipart/form-data` + `@contentType` | Part |

Like a normal runtime request, a 4xx/5xx response returns the response body as the return value **without an exception** (the generated configuration
sets `PortAuth.passErrorBodies`). If there is no answer at all (connection refused, timeout), it is a `RestClientException`. See 2.8.

**The old setting keys keep working as they are** (`{domain}.{Port}.*`). You can also use the new keys:

| Old key (still works) | Spring Boot 4 key |
|---|---|
| `erp.CreditCheckPort.url` | `spring.http.serviceclient.erp--credit-check-port.base-url` (the group name as it is, with two `--`) |
| `erp.CreditCheckPort.connectTimeout`, `.readTimeout` (ms), otherwise `elasticore.http.connectTimeout`, `.readTimeout` | `...connect-timeout`, `...read-timeout` |
| `erp.CreditCheckPort.authKey`, `.apiKey` | (same keys, apiKey wins) |
| bean `erp.CreditCheckPort.httpAuthProvider` | (same bean - like the runtime, it receives the header list that holds Authorization and changes it. The body argument is `null`) |

**The base URL is fixed once, at startup.** Spring creates the HTTP service client when the application starts, so the `base-url`
at that time (the old key `erp.CreditCheckPort.url` too) is used to the end. The runtime proxy reads `{id}.url` **on every call**.

- If the default points at the server itself (a fake counterpart inside the same server, `http://localhost:${server.port}/fake-courier`), it
  is correct only when you start on a fixed port. The port of `@SpringBootTest(webEnvironment = RANDOM_PORT)` is decided after the client is created, so **the call
  goes to port 0** (`I/O error on POST request for "http://localhost:0/..."`) - test it the way 2.8 describes.
- To change the address in operation, restart.

**These ports stay the old way** (the build log shows `stays on the ElastiCORE port runtime` and the reason). These are the cases where a Spring client cannot send the same request as the runtime:
- `application/x-www-form-urlencoded` (the object is expanded into form fields)
- `multipart/form-data` without `@contentType` (each field of the object as a part)
- Not GET, and more than one object parameter (the runtime sends only the last one)
- Array parameter (the runtime sends it as the one value `ids=1,2,3`)
- No simple-value parameter matches the URL's `{name}` (the runtime erases that part)

**Not supported** (when `httpPort: spring`):
- `@timeout(write=...)`, `@timeout(response=...)` - the Spring HTTP client has no such setting, so it only warns and does not apply them.
- `ignoreSSL`, `proxyHost/proxyPort` - use `spring.http.serviceclient.<group>.ssl.bundle` or the JVM proxy settings.
- An `HttpAuthProvider` that changes the request body, `HttpTransformerFactory` - only the headers take effect.
- The value of the per-part `@contentType` of a multipart - an object part goes out as JSON, and a file part goes out with the file's type.

### 2.8 Handling failure - when the other side fails

A port **does not judge failure.** If the other side answers with an error status (4xx/5xx), it returns that body as the return value, and if there is no answer, it throws the HTTP
client's exception as it is. Neither way tells the client that called this API "it is another system's fault" -
judging that something failed is the job of the code that calls the port.

| The other side | Runtime proxy (2.1 to 2.6) | `httpPort: spring` (2.7) |
|---|---|---|
| 200, readable body | return value | return value |
| 4xx/5xx, JSON body | **return value** (the body is read as the return type - missing fields are `null`) | **return value** (same) |
| 4xx/5xx, body that cannot be read as the return type (HTML error page, etc.) | exception (read failure) | exception (read failure) |
| No answer - connection refused, timeout | exception (`WebClientRequestException`, etc.) | `RestClientException` (`ResourceAccessException`) |
| `multipart/form-data` request gets 4xx/5xx | exception (`HttpStatusCodeException` - sent with RestTemplate) | (return value, as above) |

If you leave the exception alone, the API answers 500. So if you use it naively, **it succeeds while being wrong**: even if the courier answers 500, a response with a tracking
number of `null` comes back, the order is saved as "shipped", and 200 goes out. (Verified: `PortFailureContractTest` in
`elasticore-springboot3`)

**Answer 502 and roll back** - throw the runtime's `io.elasticore.springboot3.http.PortCallException` (502 Bad Gateway).
The generated `XxxApiErrors` answers 502, `"code": "upstream"`, and the reason it was thrown as `detail` (verified: Pet Store
`ShopFlowTest.aRefusalHandCodeThrowsIsSaidByItsCode`). It is an unchecked exception, so if you throw it inside a transaction, Spring's default rule
rolls that transaction back - if you call after changing state, that method must be transactional.

```java
// PurchaseOrderService (developer file) - the ship command calls the courier
@Override
@Transactional                          // state change and call in one transaction: if it fails, both are rolled back
public PurchaseOrderDTO ship(String orderId) {
    PurchaseOrderDTO shipped = super.ship(orderId);
    ShipmentReply reply;
    try {
        reply = courier.ship(new ShipmentRequest(orderId));
    } catch (RestClientException unreachable) {          // with the runtime proxy, WebClientException too
        throw new PortCallException("The courier could not be reached", unreachable);
    }
    if (reply == null || reply.getTrackingNo() == null) {  // an error status also comes as a value, so judge by the content
        throw new PortCallException("The courier did not accept the shipment");
    }
    ...
}
```

- If you give a message code with `PortCallException(code, message, args...)`, it is translated to the request language (`elasticore-rule-guide.md`
  "Translating a hand-written refusal"). To keep the other side's status and body, use `PortCallException(port, upstreamStatus, upstreamBody, message, cause)`.
- It is a `ResponseStatusException`, so it goes out as 502 even from an `XxxApiErrors` generated before this exception (and so has no handler for it)
  (the body is Spring Boot's).
- If you want to apply it to a whole port at once (`httpPort: spring`), you can also add an interceptor that looks at the status to that group's `RestClientHttpServiceGroupConfigurer`
  and have it throw `PortCallException`. There is not yet a failure policy you can turn on by setting (planned for 3.1).

**Testing - a fake counterpart**

- Replace the port with a mock: `@MockitoBean CourierApiPort courier;` (Spring Boot 3.4 or later, `@MockBean` before that). Make it return a success, an error
  body (`null` fields), and an exception in turn, and check the 502 and the rollback.
- To make it call a fake controller inside the same server (`/fake-courier`), start on a **fixed port**
  (`webEnvironment = DEFINED_PORT`, with a free port as `server.port`). `httpPort: spring` fixes the base URL at startup, so
  used with `RANDOM_PORT` it calls port 0 (2.7).
- If you use an outside fake server (`MockWebServer`, WireMock), put its address in `{domain}.{Port}.url`.

---

## 3. DBMS Port

### 3.1 Basic structure

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

### 3.2 DBMS annotations and options

| Option | Default | Description |
|------|--------|------|
| `@datasource("name")` | (default data source) | The EntityManagerFactory to use |
| `nativeQuery` | `true` | Native SQL (`true`) or JPQL (`false`) |
| `pageable` | `false` | Enables paging |
| `ref` | - | Refers to the SQL of another method |

### 3.3 Return types and how they run

| Return type | Execution method | Use |
|----------|------------|------|
| `List<DTO>` | `inqueryList` | The whole result list |
| `Page<DTO>` | `inqueryPage` | Paged result (pageable required) |
| `DTO` (single object) | `inqueryFirst` | Single-row query |
| `int` + update/delete/insert method name | `executeUpdate` | Run DML (returns the number of affected rows) |
| `int` + update/delete/insert method name + `list<DTO>` parameter | `executeBatchUpdate` | Run batch DML (runs once per element, returns the total number of affected rows) |
| `int` (other method name) | `inqueryFirst` | Query a single number |
| `long`, `double`, `float` | `inqueryFirst` | One number (the generated types are `Long`, `Double`, `Float`) |
| `decimal` | `inqueryFirst` | One number, `java.math.BigDecimal` (totals, amounts) |
| `string` | `inqueryFirst` | Returns a single string |
| `boolean` | `inqueryFirst` | Returns a boolean |

A method that returns one value converts the first column of the result row to that type. If the column value is NULL (`SUM` over an empty set) or there is no row, it is
`null`. If the parameter is one value (`long`, `string`, date, enum), it is bound to `:name` in the SQL by that name, and if it is one DTO, it is
bound by its getters (From 3.0.261027-rc2 - before that, a single `long` parameter, `long` and `decimal` returns, and a `string` return did not work). To bind
by name, the parameter names must be kept in the compiled class (`-parameters`) - the Gradle and Maven plugins of Spring Boot turn it on.
You can also use `decimal` in a list, such as `List<decimal>`.

**DML method name rule**: if the method name contains `update`, `delete` or `insert`, it runs with `executeUpdate`.

```yaml
# DML example
updateStatus:
  params:
    id: string
    newStatus: string
  return: int
  query: |
    UPDATE orders SET status = :newStatus WHERE id = :id

# Aggregate example
countByStatus:
  params:
    status: string
  return: int
  query: |
    SELECT COUNT(*) FROM orders WHERE status = :status
```

#### Batch execution with a List input (Multi-row Insert/Update/Delete)

If a parameter of a DML method (a method name containing `insert`/`update`/`delete`, `return: int`)
has the type `list<DTO>`, the framework iterates over the elements of the List internally and
runs the SQL repeatedly. Each element is the parameter source for one execution,
and the sum of the affected rows of all executions is returned.

```yaml
insertOrderBatch:
  params:
    items: list<OrderInputDTO>      # DTO list -> runs once per element
  return: int
  nativeQuery: true
  query: |
    INSERT INTO orders (order_no, product_code, quantity, price)
    VALUES (:orderNo, :productCode, :quantity, :price)
```

Generated interface:
```java
Integer insertOrderBatch(List<OrderInputDTO> items);
```

Example:
```java
List<OrderInputDTO> items = List.of(
    new OrderInputDTO("ORD-001", "P-100", 2, 15000),
    new OrderInputDTO("ORD-002", "P-200", 1, 32000)
);
int affected = orderPortService.insertOrderBatch(items);  // 2 (total affected rows)
```

**Rules**:
- All elements run in a **single transaction** (if one fails, everything rolls back)
- For static SQL, the Query object is created once and only the parameters are rebound on each run (performance optimization)
- For dynamic SQL (`${...}` substitution or `/* if */` condition blocks), the SQL is processed again for each element
- For an empty List, no SQL is run and 0 is returned
- A null element in the List is skipped
- A List of `Map` elements is supported as well as DTO elements
- **A List of simple values is not a batch target**: a List of a simple type such as `list<long>` or `list<string>`
  runs only once, as before, for IN clause binding (`WHERE id IN (:ids)`)

```yaml
# A List of simple values is bound to the IN clause (not a batch)
deleteByIds:
  params:
    ids: list<long>
  return: int
  query: |
    DELETE FROM orders WHERE id IN (:ids)
```

### 3.4 Dynamic SQL - JEXL conditional blocks

Add conditional blocks to the SQL to build the query dynamically from the parameter values.

#### Inline condition (one line)
```sql
AND status = :status /* if:status */
AND name LIKE :name /* if:name */
```
- If the condition is a simple identifier (for example `status`), it is decided by whether that parameter is null/empty
- String type: if it is null or an empty string, the condition is removed
- Other types: if it is null, the condition is removed

#### Block condition (multiple lines)
```sql
/* if:status */
  AND status = :status
  AND active = true
/* if-end */
```
- Starts with `/* if:condition */` and ends with `/* if-end */`
- All lines in the block are included or removed according to the condition

#### JEXL expressions
```sql
AND min_age >= :minAge /* if:input.getMinAge() > 0 */

/* if:input.searchCriteria != null */
  AND criteria = :searchCriteria
  AND sub_criteria = :subCriteria
/* if-end */
```
- The `input` variable gives access to the input object
- Any JEXL expression that returns a boolean can be used
- Express complex business logic: `input.getType() == 'VIP' && input.getAmount() > 10000`

### 3.5 Template Variable Substitution (${...})

```yaml
query: |
  SELECT * FROM ${tableName}
  WHERE category = :category
  AND id = :id
```
- `${param}`: takes the value from the input object and **substitutes it directly into the SQL string** (`tableName` above: a value the server decides)
- `:param`: bound safely as a JPA Named Parameter
- **Security warning**: `${...}` is vulnerable to SQL Injection - never pass user input to it directly
- Use it only when you need a DDL-level change, such as a dynamic table name or column name

### 3.6 sortCode (sorting)

The sort information is extracted from the input object automatically to build the `ORDER BY` clause.

**Extraction priority**:
1. The `sortCode` key of a Map
2. The `getSortCode()` method of the object
3. The `getSortColumn()` + `getSortAscending()` methods of the object

**Format**: `field1+,field2-`
- `+` = ASC (ascending)
- `-` = DESC (descending)
- If the direction is omitted, it is ASC
- Separate multiple fields with commas

**Example**: `name+,createdDate-` -> `ORDER BY name ASC, createdDate DESC`

sortCode is appended to the end of the query after the conditional SQL processing is finished. It is appended only to SQL that has condition blocks (`/* if:... */`);
for SQL without condition blocks, the sort information is not used.

**Allowed values for a field** (Since: 3.0, 2.2.261014): only column names and directions are allowed.
- Column name: letters, digits, `_` and `$`; the first character is a letter or `_` (after that, combining characters such as Thai or Hindi vowel signs are also allowed).
  It can be qualified with `.`, as in `o.created_at` (the same rule applies after the `.`). Up to 256 characters including the direction, up to 32 items (up to 8,224 characters for the whole sort value).
  Spaces between the column name and the direction are fine (`name -`)
- Any other value - a space inside a column name, quotes, parentheses, comments, `;`, a column number (`1+`), a quoted name (`"order"+`),
  an expression (`LOWER(name)+`) - is refused with a `BadRequestException` and the SQL is not run (an API generated with v3 answers with 400)
- The sort value comes from the request as it is and goes into the SQL string, so this check is what prevents SQL Injection.
  Because any column name is accepted, you can sort by a column that is not in the SELECT. If the table has sensitive columns,
  decide in the service which sort values are allowed and drop all other values.

### 3.7 SQL file hot reload

- **Development mode** (when the file under the `src/main/resources/` path exists):
  - Checks the `lastModified` of the file on every request
  - Reloads the SQL when a change is detected
  - A change to the SQL takes effect at once, without a server restart
- **Production mode** (JAR packaging):
  - Loads once from the classpath and caches permanently
  - No reload (performance optimization)
- Project root detection: based on the `target/`, `build/`, `out/production/` directories

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
- Reuses the SQL of the method given in `ref`
- Useful when a paged version refers to a non-paged query
- Removes duplicate SQL management

### 3.9 Multiple data sources

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

- The data source is given by the name of the `EntityManagerFactory` bean
- You can use either the full name (`orderEntityManagerFactory`) or the short name (`order`)
- With a single data source, `@datasource` can be omitted

### 3.10 Native Query vs JPQL

- `nativeQuery: true` (default): runs as the native SQL of the database
- `nativeQuery: false`: runs as JPQL (for example `SELECT u FROM User u WHERE u.name = :name`)

### 3.11 Result mapping

**DTO mapping**:
- Converts the column name to camelCase and tries to match a field
- If the conversion fails, matches by the original column name (case-insensitive)
- Example: `user_name` -> matches the field `userName`

**Map mapping** (when a `Map` return type is used):
- `snake_case` column names are converted automatically to `camelCase` keys

**Date/time conversion**:
- `java.sql.Date` -> `LocalDate`
- `java.sql.Timestamp` -> `LocalDateTime` or `Instant`
- `java.sql.Time` -> `LocalTime`

### 3.12 Parameter input handling

**A single non-primitive parameter**: the DTO object is passed as it is
```yaml
params:
  input: OrderSearchDTO   # parameters are extracted with the getters of the OrderSearchDTO object
```

**Multiple parameters or primitive types**: packaged automatically as `Map<String, Object>`
```yaml
params:
  status: string          # Map.put("status", status)
  minAmount: int          # Map.put("minAmount", minAmount)
```

### 3.13 Automatic Pageable extraction

The paging information is detected from the input object automatically (in priority order):

1. If the input object is an instance of `Pageable`, it is used directly
2. If it has a `getPageable()` method, it is called
3. Map: the keys `pageNumber`/`page` + `sizeVal`/`size`
4. Object: fields with the same names

**Defaults**: page=0, size=50

### 3.14 A complete DBMS Port example

```yaml
port:
  RentalDbService:
    meta: dbms @datasource("rental")
    methods:
      findRentals:
        meta: method -- rental search (paged)
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
        meta: method -- all rentals
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
        meta: method -- get a user
        params:
          input: UserProtoInput
        return: UserProtoOutput

      listUsers:
        meta: method -- list users
        params:
          input: UserListRequest
        return: UserListResponse
```

- `@server`: generates gRPC server code
- `@client`: generates gRPC client code
- The Protocol Buffer service definition (.proto) is generated automatically

---

## 5. Environment setup

### build.gradle
```gradle
dependencies {
    // Use the same version as the install instructions in the README (the generator and the runtime have the same version)
    implementation 'io.elasticore:elasticore-base:<version>'
    implementation 'io.elasticore.springboot3:elasticore-springboot3:<version>'   // Boot 4: io.elasticore.springboot4:elasticore-springboot4

    // When using a DBMS Port
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // When using httpPort: spring (2.7)
    implementation 'org.springframework.boot:spring-boot-starter-restclient'
}
```
The HTTP client (WebClient) of the runtime proxy is pulled in by the runtime library when it runs. To use that type in your code
(for example to catch `WebClientException`), add `spring-boot-starter-webflux` yourself.

### Application class
You write nothing more. The runtime library (`elasticore-springboot3` / `-springboot4`) has a Spring Boot auto-configuration
(`ElastiCoreAutoConfiguration`), so when it is in the dependencies it registers the following at startup (From 3.0.261027-rc2):

- Runtime proxy ports (HTTP in 2.1 to 2.6, DBMS in chapter 3) - found in the package of the `@SpringBootApplication` class and below it.
  This is the same place where Spring Boot looks for entities and repositories
- The `DbTransactionGateway` with which DBMS ports run SQL - when the application has exactly one `EntityManagerFactory` (or one
  `@Primary` one)
- The `ApplicationContextProvider` with which the proxy finds it, and the startup banner

```java
@SpringBootApplication     // this is all - ports are found in this package (com.example) and below it
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

- **`@EnableElastiCore` still works.** Even when it is present, the ports and the beans above are registered only once. You do not need to remove it
- **What you registered yourself wins.** A `DbTransactionGateway` or `ApplicationContextProvider` registered with
  `@ComponentScan("io.elasticore.springboot3")` or `@Bean`, and a `@Bean` you declared for a port, are used as they are, and the auto-configuration
  leaves that place empty
- **If a port is outside the package of the application class**, it is not found (`No qualifying bean`) - this is the same as the
  rule by which Spring Boot finds entities, and a component scan widened with `@SpringBootApplication(scanBasePackages = ...)` does not reach it either. Put the application
  class in a higher package, add `@AutoConfigurationPackage(basePackages = "com.example.ports")` (this annotation also widens the place where
  Spring Boot looks for entities and Spring Data repositories), add `@EnableElastiCore` to a class in that package,
  or register the port as a `@Bean` (below)
- **If there is no port at all**, the auto-configuration registers nothing (no banner either)
- **Two ports with the same simple name** (`shop.NotificationPort`, `billing.NotificationPort`) are both registered under their full names, and a warning
  is logged. Inject by type, or use the full name, as in `@Qualifier("com.example.shop.NotificationPort")`. (If `@EnableElastiCore` is on a class that is
  only in the package of one of those ports, that registrar registers that port first under the short name - this does not matter if you inject
  by type)
- **If there is more than one `EntityManagerFactory`**, make one of them `@Primary`. Otherwise the executor of the DBMS ports
  (`DbTransactionGateway`) is not created - you can also register a `DbmsSqlExecutor` bean yourself
- **To turn it off:** `elasticore.autoconfigure.enabled=false` (then register with `@EnableElastiCore`). To turn off only the banner:
  `elasticore.banner.enabled=false`
- A Spring context started without Spring Boot does not read the auto-configuration - add `@EnableElastiCore` to its configuration class

Up to 3.0.261022-rc1, `@EnableElastiCore` was required for the runtime proxy ports to become beans. `@ComponentScan("io.elasticore.springboot3")`
did not register the ports (only `ApplicationContextProvider` and `DbTransactionGateway`). The API error handler (`RequestShapeAdvice`) is not created
in either way; it exists only when a generated `XxxApiErrors` imports it.

### env.yml (Port namespace)
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
You must set the `port` namespace for the generated interface to be placed in that package.

> **Do not put it under `domain`.** This interface refers to application DTOs, so
> if it is placed in the domain package, the domain imports a layer above it. In hexagonal,
> the interface is on the application side and the adapter is in infrastructure - and here the
> adapter is a runtime proxy, so there is no generated file, and **it is correct that the only generated file
> is placed in `application.port.out`.**

### You must register the proxy as a bean

What is generated is an interface, and the implementation is a runtime proxy. **A proxy that nobody registered
is a port that cannot be injected:**

```
No qualifying bean of type 'ShopReportService'
```

In a Spring Boot application the auto-configuration handles it by scanning ("Application class" above). The place it looks is
**the package of the `@SpringBootApplication` class and below it**, and a `@SpringBootTest` that starts that class looks at the same place, so it
gets the port beans. If you added `@EnableElastiCore`, that registrar also looks at **the package of the class that annotation is on**.
If the same interface is found in several places, it is registered only once.

If the port is outside those packages, or the test is a configuration that imports only runtime pieces, register it yourself. If there is a
`@Bean` you declared, the registrar does not register that port, so a `@Bean` written in a test the old way still works:

```java
@Bean
ShopReportService shopReportService(BeanFactory beanFactory, Environment environment) {
    return DbmsServiceProxyFactory.createService(
            ShopReportService.class,
            ShopReportService.class.getAnnotation(DbmsService.class),
            beanFactory, environment);
}
```

`DddSampleApp` in `elasticore-template-ddd` shows exactly this form. Its test application class is in the `ddd`
package and the generated ports are under `io.elasticore.sample.ddd`, so the scan of the auto-configuration does not reach them.

**To wrap the proxy, use `@Primary`.** If the bean you declared is `@Primary`, the proxy of the registrar remains. Places that inject by type
get the wrapping bean, and the wrapping bean gets the proxy by name (the simple name of the interface):

```java
@Bean
@Primary
CourierPort retryingCourier(@Qualifier("CourierPort") CourierPort proxy) {
    return parcel -> retry(() -> proxy.ship(parcel));
}
```

If you declare a bean that is not `@Primary`, the proxy is not registered, so you cannot inject it by the name `"CourierPort"` then.
A `@Primary` fake (a bean that replaces a port as a whole in a test) also leaves the proxy, so `List<CourierPort>` and `getBeansOfType` get both -
if you need only one, remove `@Primary` from the fake.
A class that implements the port interface (a fake, a wrapper) is not registered as a proxy even if it is in a package that is scanned - only interfaces are ports.

### application.properties
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/maindb
spring.datasource.username=user
spring.datasource.password=password
```

---

## 6. Best Practices

### Preventing SQL Injection
```yaml
# Safe: uses a Named Parameter
query: |
  SELECT * FROM users WHERE name = :name

# Dangerous: uses a Template Variable (no user input)
query: |
  SELECT * FROM ${tableName} WHERE id = :id
```
- `:param` is handled by JPA as a PreparedStatement, so it is safe
- `${param}` is direct string substitution, so it is vulnerable to SQL Injection
- Use `${...}` only for values the system controls (table names, schema names)

### Using hot reload
- During development, you can see an SQL change at once, without restarting the server
- In production it switches to cache mode automatically, with no effect on performance

### Paging pattern
- Define the base query, then create the paged version with `ref` (see 3.8)
- When `Page<T>` is returned, a `SELECT COUNT(*)` query is run automatically
- The paging information is extracted automatically from the `page`/`size` fields of the input object

### Using sortCode
- Add a `sortCode` field to the input DTO: in the format `"name+,createdDate-"`
- The SQL needs no ORDER BY clause (the framework adds it automatically). If you write an ORDER BY in SQL that receives a sort value, another
  ORDER BY is appended after it and causes an error, so do not write one
- The client can change the sort criteria dynamically. A value that is not a column name is refused (see 3.6)

### JEXL condition guidelines
- Simple null check: give only the parameter name (`/* if:status */`)
- Complex condition: access the object with the `input.` prefix (`/* if:input.getAmount() > 0 */`)
- Very complex business logic is better handled in the service layer

### Method naming rules
| Prefix | Purpose | Return type |
|--------|------|----------|
| `find`, `get`, `search` | Query | `List<T>`, `Page<T>`, `T` |
| `count` | Count | `int`, `long` |
| `exists`, `check` | Existence | `boolean` |
| `update` | Update | `int` |
| `insert`, `create` | Insert | `int` |
| `delete`, `remove` | Delete | `int` |

### Dynamic SQL pattern
- Combine conditions safely with the `WHERE 1=1` pattern
- Manage related conditions by grouping them in a block (`/* if:... */` to `/* if-end */`)
- If the value is NULL, the condition is removed automatically
- Follow the DML method naming rules (`update`, `delete`, `insert` prefixes)

### HTTP Port - Multipart request pattern

**Use @contentType to send JSON + a file**:
```yaml
params:
  jsonData: RequestDTO @contentType(application/json)  # JSON serialization
  attachment: org.springframework.core.io.FileSystemResource  # file
```
- The DTO object is serialized to JSON automatically and sent as a multipart part
- For a file, use the `FileSystemResource` or `Resource` type
- If no Content-Type is given, the file type is detected automatically

**Legacy server compatibility**:
- If you use `@contentType`, sending switches automatically to the `RestTemplate` base
- The `Content-Length` header is calculated automatically, so it is compatible with servers that do not support Chunked Encoding
- Multipart requests are also stable in a WebFlux environment

**Debugging tip**:
```properties
# Enable the HTTP Port debug log
logging.level.io.elasticore.springboot3.http.HttpServiceProxyFactory=DEBUG
logging.level.io.elasticore.springboot3.http.HttpApiClient=DEBUG
```

### HTTP Port - Authentication pattern

**Static token (for development/testing)**:
```yaml
meta: http @url("https://api.example.com") @auth("Bearer test_token")
```

**Properties-based (separate per environment)**:
```properties
# application-dev.properties
domain.ServiceName.authKey=Bearer dev_token

# application-prod.properties
domain.ServiceName.authKey=Bearer prod_token
```

**Dynamic token (OAuth, JWT refresh, and so on)**:
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
- Bean name rule: `{domainName}.{PortServiceName}.httpAuthProvider`
- If it returns `null`, the original input object is used
- If the input object needs to be converted, return the converted object
