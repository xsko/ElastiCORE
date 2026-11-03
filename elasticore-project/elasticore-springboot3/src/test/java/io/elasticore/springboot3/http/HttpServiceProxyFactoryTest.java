package io.elasticore.springboot3.http;

import io.elasticore.runtime.port.ExternalService;
import io.elasticore.runtime.port.HttpEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpServiceProxyFactoryTest {

    @Mock
    private BeanFactory beanFactory;

    @Mock
    private Environment environment;

    private TestService testService;

    // Test interface
    interface TestService {
        @HttpEndpoint(method = "GET", url = "/api/users/{id}")
        UserResponse getUser(String id);

        @HttpEndpoint(method = "POST", url = "/api/users")
        UserResponse createUser(UserRequest request);

        @HttpEndpoint(method = "GET", url = "/api/search")
        SearchResponse searchUsers(String query, Integer page, Integer size);
    }

    // Test DTO classes
    record UserRequest(String name, String email) {}
    record UserResponse(String id, String name, String email) {}
    record SearchResponse(java.util.List<UserResponse> users, int totalCount) {}

    @ExternalService(id = "testService", url = "http://api.test.com" , protocol = "http")
    static class TestServiceImpl implements TestService {
        @Override
        public UserResponse getUser(String id) { return null; }
        @Override
        public UserResponse createUser(UserRequest request) { return null; }
        @Override
        public SearchResponse searchUsers(String query, Integer page, Integer size) { return null; }
    }

    @BeforeEach
    void setUp() {
        // Basic environment setup
        lenient().when(environment.getProperty("testService.url", "http://api.test.com"))
                .thenReturn("http://api.test.com");
        lenient().when(environment.getProperty("testService.authKey", ""))
                .thenReturn("test-auth-token");

        ExternalService externalService = TestServiceImpl.class.getAnnotation(ExternalService.class);
        testService = HttpServiceProxyFactory.createService(
                TestService.class,
                externalService,
                beanFactory,
                environment
        );
    }

    @Test
    @DisplayName("GET Request - Single User Retrieval Test")
    void getUser_ShouldHandlePathParameter() {
        try (MockedStatic<HttpApiClient> mockedHttpClient = mockStatic(HttpApiClient.class)) {
            // given
            String userId = "user123";
            UserResponse expectedResponse = new UserResponse("user123", "John Doe", "john@test.com");
            ResponseEntity<UserResponse> responseEntity = ResponseEntity.ok(expectedResponse);

            mockedHttpClient.when(() -> HttpApiClient.exchangeResponseEntity(
                    any(), any(), any(), any(), any(), any(), any(), any(),
                    anyBoolean(), any(), anyInt(), any()))
                    .thenReturn(Mono.just(responseEntity));

            // when
            UserResponse result = testService.getUser(userId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo("user123");
        }
    }

    @Test
    @DisplayName("POST Request - User Creation Test")
    void createUser_ShouldHandleRequestBody() {
        try (MockedStatic<HttpApiClient> mockedHttpClient = mockStatic(HttpApiClient.class)) {
            // given
            UserRequest request = new UserRequest("John Doe", "john@test.com");
            UserResponse expectedResponse = new UserResponse("newuser", "John Doe", "john@test.com");
            ResponseEntity<UserResponse> responseEntity = ResponseEntity.ok(expectedResponse);

            mockedHttpClient.when(() -> HttpApiClient.exchangeResponseEntity(
                    any(), any(), any(), any(), any(), any(), any(), any(),
                    anyBoolean(), any(), anyInt(), any()))
                    .thenReturn(Mono.just(responseEntity));

            // when
            UserResponse result = testService.createUser(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("John Doe");
        }
    }

    @Test
    @DisplayName("GET Request - Query Parameter Handling Test")
    void searchUsers_ShouldHandleQueryParameters() {
        try (MockedStatic<HttpApiClient> mockedHttpClient = mockStatic(HttpApiClient.class)) {
            // given
            String searchQuery = "John Doe";
            Integer page = 1;
            Integer size = 10;
            SearchResponse expectedResponse = new SearchResponse(
                    java.util.List.of(new UserResponse("1", "John Doe", "john@test.com")), 1);
            ResponseEntity<SearchResponse> responseEntity = ResponseEntity.ok(expectedResponse);

            mockedHttpClient.when(() -> HttpApiClient.exchangeResponseEntity(
                    any(), any(), any(), any(), any(), any(), any(), any(),
                    anyBoolean(), any(), anyInt(), any()))
                    .thenReturn(Mono.just(responseEntity));

            // when
            SearchResponse result = testService.searchUsers(searchQuery, page, size);

            // then
            assertThat(result).isNotNull();
            assertThat(result.totalCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Test Case with No Authentication Key")
    void shouldHandleNoAuthKey() {
        try (MockedStatic<HttpApiClient> mockedHttpClient = mockStatic(HttpApiClient.class)) {
            // given
            when(environment.getProperty("testService.authKey", ""))
                    .thenReturn("");
            UserResponse expectedResponse = new UserResponse("user123", "John Doe", "john@test.com");
            ResponseEntity<UserResponse> responseEntity = ResponseEntity.ok(expectedResponse);

            mockedHttpClient.when(() -> HttpApiClient.exchangeResponseEntity(
                    any(), any(), any(), any(), any(), any(), any(), any(),
                    anyBoolean(), any(), anyInt(), any()))
                    .thenReturn(Mono.just(responseEntity));

            // when
            UserResponse result = testService.getUser("user123");

            // then
            assertThat(result).isNotNull();
        }
    }

    @Test
    @DisplayName("Proxy Basic Methods Test")
    void proxyBasicMethods() {
        // toString test
        assertThat(testService.toString())
                .contains("Proxy");

        // equals test
        assertThat(testService)
                .isEqualTo(testService)
                .isNotEqualTo(new Object());

        // hashCode test - should be positive
        assertThat(testService.hashCode())
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("Invalid Server URL Configuration Test")
    void invalidServerUrl() {
        try (MockedStatic<HttpApiClient> mockedHttpClient = mockStatic(HttpApiClient.class)) {
            // given
            when(environment.getProperty("testService.url", "http://api.test.com"))
                    .thenReturn("invalid-url");

            mockedHttpClient
                    .when(() -> HttpApiClient.exchangeResponseEntity(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyInt(),
                            any()))
                    .thenThrow(new HttpPortException(null, null, 400, null, "Invalid URL", null));

            // when & then: the port and method are said of an answer that came without them
            assertThatThrownBy(() -> testService.getUser("user123"))
                    .isInstanceOf(HttpPortException.class)
                    .hasMessage("testService.getUser: HTTP 400 Bad Request - Invalid URL");
        }
    }
}