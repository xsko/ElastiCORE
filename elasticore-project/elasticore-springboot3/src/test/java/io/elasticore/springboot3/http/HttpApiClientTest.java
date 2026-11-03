package io.elasticore.springboot3.http;

import io.elasticore.runtime.port.HttpAuthProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Integration tests against live https://httpbin.org - disabled for offline/CI determinism; enable manually with network access")
class HttpApiClientTest {

    @Test
    @DisplayName("Create WebClient - Ignore SSL")
    void createWebClientIgnoreSSL() {
        WebClient client = HttpApiClient.createWebClient("https://test.com", true);
        assertNotNull(client);
    }

    @Test
    @DisplayName("Create WebClient - Default SSL")
    void createWebClientDefault() {
        WebClient client = HttpApiClient.createWebClient("https://test.com");
        assertNotNull(client);
    }

    @Test
    @DisplayName("GET Request - With Query Parameters")
    void getRequestWithQueryParams() {
        Map<String, String> query = new HashMap<>();
        query.put("name", "test");
        query.put("age", "30");

        Mono<Map<String, Object>> response = HttpApiClient.exchange(
                HttpMethod.GET,
                "https://httpbin.org",
                "/get?fixedParam=value",
                query,
                new ParameterizedTypeReference<>() {}
        );

        StepVerifier.create(response)
                .assertNext(map -> {
                    assertTrue(map.containsKey("args"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("POST Request - With Request Body")
    void postRequestWithBody() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "tester");
        body.put("password", "pass");

        Mono<Map<String, Object>> response = HttpApiClient.exchange(
                HttpMethod.POST,
                "https://httpbin.org",
                "/post",
                body,
                new ParameterizedTypeReference<>() {}
        );

        StepVerifier.create(response)
                .assertNext(map -> {
                    assertTrue(map.containsKey("json"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Add Custom Headers")
    void headerInjection() {
        Map<String, String> header1 = Map.of("X-Test-Header", "test");
        List<Map<String, String>> headers = List.of(header1);

        Mono<Map<String, Object>> response = HttpApiClient.exchange(
                HttpMethod.GET,
                "https://httpbin.org",
                "/headers",
                null,
                new ParameterizedTypeReference<>() {},
                headers
        );

        StepVerifier.create(response)
                .assertNext(map -> {
                    assertTrue(map.containsKey("headers"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Multipart Upload Request")
    void multipartUploadRequest() {
        MultiValueMap<String, String> multipartData = new LinkedMultiValueMap<>();
        multipartData.add("field1", "value1");
        multipartData.add("field2", "value2");

        Mono<Map<String, Object>> response = HttpApiClient.exchange(
                HttpMethod.POST,
                "https://httpbin.org",
                "/post",
                multipartData,
                new ParameterizedTypeReference<>() {},
                null,
                MediaType.MULTIPART_FORM_DATA
        );

        StepVerifier.create(response)
                .assertNext(map -> assertTrue(map.containsKey("form")))
                .verifyComplete();
    }

    @Test
    @DisplayName("Apply AuthProvider")
    void authProviderApplied() {
        HttpAuthProvider mockProvider = (sendObj, headers) -> {
            headers.add(Map.of("Authorization", "Bearer testtoken"));
            return sendObj;
        };

        Mono<Map<String, Object>> response = HttpApiClient.exchange(
                HttpMethod.GET,
                "https://httpbin.org",
                "/headers",
                null,
                new ParameterizedTypeReference<>() {},
                null,
                mockProvider,
                MediaType.APPLICATION_JSON_VALUE
        );

        StepVerifier.create(response)
                .assertNext(map -> assertTrue(map.containsKey("headers")))
                .verifyComplete();
    }

    @Test
    @DisplayName("Throw HttpPortException on 4xx Errors")
    void clientErrorHandled() {
        Mono<Map<String, Object>> response = HttpApiClient.exchange(
                HttpMethod.GET,
                "https://httpbin.org",
                "/status/404",
                null,
                new ParameterizedTypeReference<>() {}
        );

        StepVerifier.create(response).expectError(HttpPortException.class).verify();
    }

    @Test
    @DisplayName("Use exchangeResponseEntity Method")
    void testExchangeResponseEntity() {
        Mono<ResponseEntity<Map<String, Object>>> response = HttpApiClient.exchangeResponseEntity(
                HttpMethod.GET,
                "https://httpbin.org",
                "/get",
                null,
                new ParameterizedTypeReference<>() {},
                null
        );

        StepVerifier.create(response)
                .assertNext(entity -> {
                    assertEquals(HttpStatus.OK, entity.getStatusCode());
                    assertNotNull(entity.getBody());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Use exchangeForFlux Method")
    void testExchangeForFlux() {
        Map<String, String> query = new HashMap<>();
        query.put("name", "test");

        Flux<Map<String, Object>> response = HttpApiClient.exchangeForFlux(
                HttpMethod.GET,
                "https://httpbin.org",
                "/stream/2",
                query,
                new ParameterizedTypeReference<>() {},
                null,
                MediaType.APPLICATION_JSON
        );

        StepVerifier.create(response)
                .expectNextCount(2) // stream/2 returns 2 items
                .verifyComplete();
    }
}
