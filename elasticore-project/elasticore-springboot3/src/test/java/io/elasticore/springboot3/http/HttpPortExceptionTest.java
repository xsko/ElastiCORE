package io.elasticore.springboot3.http;

import io.elasticore.runtime.port.ExternalService;
import io.elasticore.runtime.port.HttpEndpoint;
import io.elasticore.runtime.port.MultipartContentType;
import io.elasticore.springboot3.bean.ApplicationContextProvider;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An error status reaches the caller as one type, HttpPortException, with the
 * status, the headers, the body and the port and method that were called -
 * on the WebClient calls and on the multipart (RestTemplate) call alike.
 */
public class HttpPortExceptionTest {

    public static class Label {
        private String labelNo;

        public String getLabelNo() {
            return labelNo;
        }

        public void setLabelNo(String labelNo) {
            this.labelNo = labelNo;
        }
    }

    @ExternalService(protocol = "http", id = "pet.LabelApiPort", url = "http://localhost:1")
    public interface LabelApiPort {
        @HttpEndpoint(method = "POST", url = "/labels", contentType = "multipart/form-data", paramNames = "label")
        String print(@MultipartContentType("application/json") Label label);

        @HttpEndpoint(method = "GET", url = "/labels/{id}", paramNames = "id")
        Label find(String id);
    }

    private static final String BODY = "{\"code\":\"label.conflict\",\"detail\":\"the label is printed already\"}";

    private MockWebServer server;

    @BeforeEach
    void start() throws IOException {
        server = new MockWebServer();
        server.start();
        new ApplicationContextProvider().setApplicationContext(new StaticApplicationContext());
    }

    @AfterEach
    void stop() throws IOException {
        server.shutdown();
    }

    private LabelApiPort port(String url) {
        MockEnvironment env = new MockEnvironment().withProperty("pet.LabelApiPort.url", url);
        return HttpServiceProxyFactory.createService(
                LabelApiPort.class,
                LabelApiPort.class.getAnnotation(ExternalService.class),
                new DefaultListableBeanFactory(),
                env);
    }

    private MockResponse error(int status, String type, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", type)
                .setHeader("X-Request-Id", "req-" + status)
                .setBody(body);
    }

    private static void assertAnswer(HttpPortException e, int status, String type, String body) {
        assertEquals(status, e.getStatusCode());
        assertEquals(body, e.getResponseBody());
        assertEquals("req-" + status, e.getResponseHeader("X-Request-Id"));
        assertTrue(e.getResponseHeaders().getContentType().toString().startsWith(type));
    }

    /** HttpApiClient.exchange: no port around it, so no names - the status, headers and body are there. */
    @ParameterizedTest
    @ValueSource(ints = {404, 409, 500})
    void theWebClientCallThrowsTheAnswer(int status) {
        server.enqueue(error(status, "application/json", BODY));
        HttpPortException e = assertThrows(HttpPortException.class, () -> HttpApiClient.exchange(
                        HttpMethod.GET,
                        server.url("/").toString(),
                        "/labels/7",
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {})
                .block());
        assertAnswer(e, status, "application/json", BODY);
        assertNull(e.getPort());
        assertNull(e.getMethod());
        assertTrue(e.getMessage().startsWith("HTTP " + status + " "), e.getMessage());
        assertTrue(e.getMessage().endsWith(BODY), e.getMessage());
    }

    /** A port method over WebClient: an error answer that does not read as the reply. */
    @ParameterizedTest
    @ValueSource(ints = {404, 409, 500})
    void aPortCallThrowsAnAnswerThatDoesNotRead(int status) {
        String page = "<html>label service: " + status + "</html>";
        server.enqueue(error(status, "text/html", page));
        HttpPortException e = assertThrows(
                HttpPortException.class, () -> port(server.url("/").toString()).find("7"));
        assertAnswer(e, status, "text/html", page);
        assertEquals("pet.LabelApiPort", e.getPort());
        assertEquals("find", e.getMethod());
        assertTrue(e.getMessage().startsWith("pet.LabelApiPort.find: HTTP " + status), e.getMessage());
    }

    /** A multipart port method (sent with RestTemplate): the same exception, not HttpStatusCodeException. */
    @ParameterizedTest
    @ValueSource(ints = {404, 409, 500})
    void aMultipartPortCallThrowsTheSameAnswer(int status) {
        server.enqueue(error(status, "application/json", BODY));
        HttpPortException e = assertThrows(
                HttpPortException.class, () -> port(server.url("/").toString()).print(new Label()));
        assertAnswer(e, status, "application/json", BODY);
        assertEquals("pet.LabelApiPort", e.getPort());
        assertEquals("print", e.getMethod());
        assertTrue(e.getMessage().startsWith("pet.LabelApiPort.print: HTTP " + status + " "), e.getMessage());
    }

    /** A POST over WebClient, answered without a body: the status alone. */
    @Test
    void anAnswerWithoutABodyIsTheStatusAlone() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(409));
        HttpPortException e = assertThrows(HttpPortException.class, () -> HttpApiClient.exchange(
                        HttpMethod.POST,
                        server.url("/").toString(),
                        "/labels?copies=2",
                        new Label(),
                        new ParameterizedTypeReference<Map<String, Object>>() {})
                .block());
        assertEquals(409, e.getStatusCode());
        assertEquals("", e.getResponseBody());
        assertEquals("HTTP 409 Conflict", e.getMessage());
        assertEquals("/labels?copies=2", server.takeRequest().getPath());
    }

    /** A long body is cut in the message, kept whole by the getter. */
    @Test
    void theMessageShowsTheStartOfALongBody() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            body.append("0123456789");
        }
        HttpPortException e = new HttpPortException("pet.LabelApiPort", "find", 502, null, body.toString(), null);
        assertEquals(
                "pet.LabelApiPort.find: HTTP 502 Bad Gateway - " + body.substring(0, HttpPortException.BODY_IN_MESSAGE)
                        + "...",
                e.getMessage());
        assertEquals(1000, e.getResponseBody().length());
        assertTrue(e.getResponseHeaders().isEmpty());
    }

    /** A status without a reason phrase, a port without a method, no body. */
    @Test
    void theMessageSaysWhatIsKnown() {
        HttpPortException e = new HttpPortException("pet.LabelApiPort", null, 599, null, null, null);
        assertEquals("pet.LabelApiPort: HTTP 599", e.getMessage());
        assertEquals("", e.getResponseBody());
        assertNull(e.getResponseHeader("X-Request-Id"));
    }

    /** No answer at all stays the HTTP client's exception. */
    @Test
    void aCallThatGotNoAnswerIsNotAnHttpPortException() {
        assertThrows(WebClientRequestException.class, () -> port("http://localhost:1")
                .find("7"));
    }
}
