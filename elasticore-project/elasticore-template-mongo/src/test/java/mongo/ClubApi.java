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

    /**
     * {@code @signin(refresh=password)}: a password change ends the sessions opened before it; the
     * session that made the change keeps going.
     */
    @Test
    public void aPasswordChangeEndsTheOtherSessions() throws Exception {
        String mail = "pw-" + UUID.randomUUID().toString().substring(0, 8) + "@a.com";
        long id = body(send(client(), "POST", "/member", "{\"email\":\"" + mail + "\",\"secret\":\"pw-1\"}"), 200)
                .get("memberId")
                .asLong();
        HttpClient changer = client();
        HttpClient other = client();
        send(changer, "POST", "/member/signin", "{\"email\":\"" + mail + "\",\"secret\":\"pw-1\"}", 200);
        send(other, "POST", "/member/signin", "{\"email\":\"" + mail + "\",\"secret\":\"pw-1\"}", 200);
        send(other, "GET", "/member/me", null, 200);

        send(changer, "PUT", "/member", "{\"memberId\":" + id + ",\"email\":\"" + mail + "\",\"secret\":\"pw-2\"}", 200);
        send(changer, "GET", "/member/me", null, 200);
        send(changer, "GET", "/member/me", null, 200);
        send(other, "GET", "/member/me", null, 401);
        send(other, "PUT", "/member", "{\"memberId\":" + id + ",\"email\":\"" + mail + "\",\"secret\":\"pw-3\"}", 401);
        send(client(), "POST", "/member/signin", "{\"email\":\"" + mail + "\",\"secret\":\"pw-2\"}", 200);
    }

    /**
     * A member who signed in through another provider has no password; setting one is not a change,
     * so the other sessions stay. A change after that is one.
     */
    @Test
    public void aFirstPasswordIsNotAChange() throws Exception {
        long id = 900_000_000L + (long) (Math.random() * 99_999_999L);
        String mail = "provider-" + UUID.randomUUID().toString().substring(0, 8) + "@a.com";
        mongo.getCollection("member").insertOne(new Document("_id", id).append("email", mail));
        HttpClient first = client();
        HttpClient second = client();
        send(first, "POST", "/provider/signin/" + id, null, 200);
        send(second, "POST", "/provider/signin/" + id, null, 200);
        send(first, "GET", "/member/me", null, 200);

        send(second, "PUT", "/member", "{\"memberId\":" + id + ",\"email\":\"" + mail + "\",\"secret\":\"pw-1\"}", 200);
        assertNotNull(mongo.getCollection("member").find(new Document("_id", id)).first().get("secret"));
        send(first, "GET", "/member/me", null, 200);
        send(second, "GET", "/member/me", null, 200);

        send(second, "PUT", "/member", "{\"memberId\":" + id + ",\"email\":\"" + mail + "\",\"secret\":\"pw-2\"}", 200);
        send(first, "GET", "/member/me", null, 401);
        send(second, "GET", "/member/me", null, 200);
    }

    /**
     * A search sorts only by what its response shows: the document's own @password is in it, but no response shows
     * it, so a sort on it is refused (400 no-such-property) - sortCode and sortColumn alike - before MongoDB is asked.
     */
    @Test
    public void aSortOnThePasswordIsRefused() throws Exception {
        HttpClient member = client();
        String mail = "sorter-" + UUID.randomUUID().toString().substring(0, 8) + "@a.com";
        send(member, "POST", "/member", "{\"email\":\"" + mail + "\",\"secret\":\"pw-sort\"}", 200);
        send(member, "POST", "/member/signin", "{\"email\":\"" + mail + "\",\"secret\":\"pw-sort\"}", 200);
        for (String sort : new String[] {"{\"sortCode\":\"secret+\"}", "{\"sortColumn\":\"secret\",\"sortAscending\":false}"}) {
            JsonNode refused = body(send(member, "POST", "/member/search", sort), 400);
            assertEquals("no-such-property", refused.get("code").asText(), refused.toString());
            assertTrue(refused.get("detail").asText().contains("cannot be sorted by 'secret'"), refused.toString());
        }
        JsonNode mine = body(send(member, "POST", "/member/search", "{\"sortCode\":\"email+,memberId-\"}"), 200);
        assertEquals(1, mine.get("content").size(), mine.toString());
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
