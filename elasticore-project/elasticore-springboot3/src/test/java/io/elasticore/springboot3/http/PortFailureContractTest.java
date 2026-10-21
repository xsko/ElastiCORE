package io.elasticore.springboot3.http;

import io.elasticore.runtime.port.ExternalService;
import io.elasticore.runtime.port.HttpEndpoint;
import io.elasticore.springboot3.bean.ApplicationContextProvider;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a port does when the other system fails - the contract the port
 * guide states (2.8): an error answer comes back as a value, a call that got
 * no answer is an exception of the HTTP client's. Neither is a 502 until the
 * application says so (PortCallException).
 */
public class PortFailureContractTest {

    public static class ShipmentReply {
        private String trackingNo;

        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    }

    @ExternalService(protocol = "http", id = "pet.CourierApiPort", url = "http://localhost:1")
    public interface CourierApiPort {
        @HttpEndpoint(method = "POST", url = "/shipments")
        ShipmentReply ship(ShipmentReply request);
    }

    private MockWebServer courier;

    @BeforeEach
    void start() throws IOException {
        courier = new MockWebServer();
        courier.start();
        new ApplicationContextProvider().setApplicationContext(new StaticApplicationContext());
    }

    @AfterEach
    void stop() throws IOException {
        courier.shutdown();
    }

    private CourierApiPort runtimePort(String url) {
        MockEnvironment env = new MockEnvironment().withProperty("pet.CourierApiPort.url", url);
        return HttpServiceProxyFactory.createService(CourierApiPort.class,
                CourierApiPort.class.getAnnotation(ExternalService.class), new DefaultListableBeanFactory(), env);
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body);
    }

    /** Runtime mode: a 5xx with a JSON body is the method's return value. */
    @Test
    public void theRuntimeReturnsAnErrorAnswerAsAValue() {
        courier.enqueue(json(500, "{\"error\":\"courier down\"}"));
        ShipmentReply reply = runtimePort(courier.url("/").toString()).ship(new ShipmentReply());
        assertNotNull(reply);
        assertNull(reply.getTrackingNo());

        courier.enqueue(json(200, "{\"trackingNo\":\"TRK-7\"}"));
        assertEquals("TRK-7", runtimePort(courier.url("/").toString()).ship(new ShipmentReply()).getTrackingNo());
    }

    /** Runtime mode: an answer that does not read as the reply, or no answer at all, is an exception. */
    @Test
    public void theRuntimeThrowsWhenThereIsNoReplyToRead() {
        courier.enqueue(new MockResponse().setResponseCode(503).setHeader("Content-Type", "text/html")
                .setBody("<html>Service Unavailable</html>"));
        assertThrows(RuntimeException.class, () -> runtimePort(courier.url("/").toString()).ship(new ShipmentReply()));
        assertThrows(org.springframework.web.reactive.function.client.WebClientRequestException.class,
                () -> runtimePort("http://localhost:1").ship(new ShipmentReply()));
    }

    /** httpPort: spring: an error answer that does not read as the reply is a RestClientException too. */
    @Test
    public void theSpringClientThrowsWhenTheAnswerDoesNotRead() {
        courier.enqueue(new MockResponse().setResponseCode(503).setHeader("Content-Type", "text/html")
                .setBody("<html>Service Unavailable</html>"));
        RestClient.Builder builder = RestClient.builder().baseUrl(courier.url("/").toString());
        PortAuth.passErrorBodies(builder);
        assertThrows(org.springframework.web.client.RestClientException.class, () -> builder.build().post()
                .uri("/shipments").body(new ShipmentReply()).retrieve().body(ShipmentReply.class));
    }

    /** Runtime mode, multipart/form-data: sent with RestTemplate, whose error handler throws on a 4xx/5xx. */
    @Test
    public void aMultipartCallThrowsOnAnErrorAnswer() {
        courier.enqueue(json(500, "{\"error\":\"courier down\"}"));
        java.util.Map<String, Object> parts = new java.util.LinkedHashMap<String, Object>();
        parts.put("label", "parcel");
        assertThrows(org.springframework.web.client.HttpStatusCodeException.class, () ->
                HttpApiClient.sendMultipartWithRestTemplate(org.springframework.http.HttpMethod.POST,
                        courier.url("/labels").toString(), new MultipartRequest(parts, null),
                        java.util.Collections.<java.util.Map<String, String>>emptyList(), String.class, false));
    }

    /**
     * httpPort: spring: the generated client installs PortAuth.passErrorBodies,
     * so a 5xx is read as the reply too; no answer is a RestClientException.
     */
    @Test
    public void theSpringClientReturnsAnErrorAnswerAsAValue() {
        courier.enqueue(json(500, "{\"error\":\"courier down\"}"));
        RestClient.Builder builder = RestClient.builder().baseUrl(courier.url("/").toString());
        PortAuth.passErrorBodies(builder);
        ShipmentReply reply = builder.build().post().uri("/shipments").body(new ShipmentReply())
                .retrieve().body(ShipmentReply.class);
        assertNotNull(reply);
        assertNull(reply.getTrackingNo());

        RestClient.Builder nowhere = RestClient.builder().baseUrl("http://localhost:1");
        PortAuth.passErrorBodies(nowhere);
        assertThrows(ResourceAccessException.class, () -> nowhere.build().post().uri("/shipments")
                .body(new ShipmentReply()).retrieve().body(ShipmentReply.class));
    }
}
