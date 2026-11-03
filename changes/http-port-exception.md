### Bug Fixes (runtime - HTTP port error status)

#### An HTTP port call that gets an error status throws one exception, HttpPortException, with the status, headers and body
- Why: a service has to tell a 404 from a 409 from a 500 and log what the other side said (issue #75). The runtime threw
  three unrelated types for an error status, and two of them dropped the body: `HttpApiClient.exchange` raised
  `HttpApiClient.WebClientException` with a fixed message ("4xx Client Error"), a `multipart/form-data` port method
  raised Spring's `HttpStatusCodeException` (RestTemplate), and a port method whose error body did not read as its
  return type raised a raw decoding exception. The `WebClientResponseException -> IllegalStateException` mapping in
  `HttpServiceProxyFactory.invoke_old` never ran.
- After: `io.elasticore.springboot3.http.HttpPortException` (unchecked) carries `getStatusCode()`, `getResponseBody()`
  (text), `getResponseHeaders()` / `getResponseHeader(name)`, `getPort()` and `getMethod()`; its message is
  `erp.CreditCheckPort.check: HTTP 409 Conflict - <body, cut at 500 characters>`. `HttpApiClient.exchange`, the
  multipart call and an error answer the proxy cannot read as the return type all throw it; the proxy adds the port and
  method. `HttpApiClient.WebClientException` is removed (no guide, template or generated code named it). Unchanged: an
  error answer with a body that reads as the return type is still the method's return value (port guide 2.8), a call
  that got no answer is still `WebClientRequestException`, and `httpPort: spring` is untouched. No generated file
  changes. Code that caught `HttpApiClient.WebClientException` or `HttpStatusCodeException` from a port catches
  `HttpPortException` now.
- Checked: `HttpPortExceptionTest` (MockWebServer answering 404, 409 and 500 with a JSON or HTML body and a header:
  type, status, body, header, port and method on `HttpApiClient.exchange`, on a port method over WebClient and on a
  multipart port method; a long body cut in the message; no answer stays `WebClientRequestException`),
  `PortFailureContractTest` and `HttpServiceProxyFactoryTest` updated; `elasticore-springboot3` (244) and
  `elasticore-springboot4` (245) tests pass; formatCheck, diffErrorprone, diffCoverage (84.7%) and diffMutation
  (81.4%) pass.
- Files: `elasticore-springboot3/.../http/HttpPortException.java` (new), `HttpApiClient.java`,
  `HttpServiceProxyFactory.java`, their tests; `elasticore-dsl-port-guide.md` (English and Korean) 2.8.
