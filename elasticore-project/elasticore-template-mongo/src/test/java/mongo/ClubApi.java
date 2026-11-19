package mongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The club domain over a real MongoDB: a member signs in, and a member kept
 * under @deleted is no one's session - neither a new sign-in nor the session
 * it had. ClubApiTest runs it on a MongoDB Testcontainers starts;
 * ClubApiOnServerTest on the server ELASTICORE_IT_MONGODB_URI names.
 */
@SpringBootTest(classes = MongoSampleApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.mongodb.auto-index-creation=true")
public abstract class ClubApi {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    @Autowired
    MongoTemplate mongo;

    /** A database of its own for each run, so that runs never see each other's documents. */
    static String database() {
        return "club_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Test
    public void aMemberKeptUnderDeletedIsNoOnesSession() throws Exception {
        HttpClient kim = client();
        String mail = UUID.randomUUID().toString().substring(0, 8) + "@a.com";
        long kimId = body(send(kim, "POST", "/member", "{\"email\":\"" + mail + "\",\"secret\":\"pw-kim\"}"), 200)
                .get("memberId")
                .asLong();
        send(kim, "GET", "/member/me", null, 401);
        send(kim, "POST", "/member/signin", "{\"email\":\"" + mail + "\",\"secret\":\"pw-kim\"}", 200);
        assertEquals(kimId, body(send(kim, "GET", "/member/me", null), 200).get("memberId").asLong());

        // The member deletes themselves: the document is kept with the time, the login is no more.
        assertEquals("true", send(kim, "DELETE", "/member/" + kimId, null, 200).body());
        Document kept = mongo.getCollection("member").find(new Document("_id", kimId)).first();
        assertNotNull(kept);
        assertNotNull(kept.get("leftAt"), kept.toJson());
        send(client(), "POST", "/member/signin", "{\"email\":\"" + mail + "\",\"secret\":\"pw-kim\"}", 401);
        // Nor does the session it had still name it: /me reads it as the service reads every row.
        send(kim, "GET", "/member/me", null, 401);
        int status = send(kim, "GET", "/member/" + kimId, null).statusCode();
        assertTrue(status == 401 || status == 404, "the deleted member's session still read its document: " + status);
    }

    @Test
    public void aTakenLoginIsA409ThatNamesNoValue() throws Exception {
        HttpClient http = client();
        String mail = "taken-" + UUID.randomUUID().toString().substring(0, 8) + "@a.com";
        send(http, "POST", "/member", "{\"email\":\"" + mail + "\",\"secret\":\"pw\"}", 200);
        HttpResponse<String> taken = send(http, "POST", "/member", "{\"email\":\"" + mail + "\",\"secret\":\"x\"}", 409);
        // The kind of refusal, never the driver's text: not the value, not the index.
        assertEquals("unique", json.readTree(taken.body()).get("code").asText(), taken.body());
        assertFalse(taken.body().contains(mail), taken.body());
        assertFalse(taken.body().contains("E11000"), taken.body());
        assertFalse(taken.body().contains("index"), taken.body());
    }

    // ---- helpers ----------------------------------------------------------------------

    private static HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    private HttpResponse<String> send(HttpClient http, String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/club" + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(HttpClient http, String method, String path, String body, int status)
            throws Exception {
        HttpResponse<String> response = send(http, method, path, body);
        assertEquals(status, response.statusCode(), method + " " + path + ": " + response.body());
        return response;
    }

    private JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        return json.readTree(response.body());
    }
}
