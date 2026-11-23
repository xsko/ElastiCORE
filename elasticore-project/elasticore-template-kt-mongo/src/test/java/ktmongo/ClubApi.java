package ktmongo;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The club domain in Kotlin over a real MongoDB: what the shop does not ask
 * for - signing in, a member's own documents, an admin-only field, a password
 * kept as a hash, @deleted on a login, html cleaned before it is saved, the
 * service's auditing, a key the service makes, money, every search condition,
 * a compound index and a read-only entity. ClubApiTest runs it on a MongoDB
 * Testcontainers starts; ClubApiOnServerTest on the server
 * ELASTICORE_IT_MONGODB_URI names.
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

    // ---- signing in, and what a member owns ------------------------------------------

    @Test
    public void aMemberSignsInAndReadsOnlyWhatIsTheirs() throws Exception {
        HttpClient kim = client();
        HttpClient lee = client();
        String kimMail = unique() + "@a.com";
        // A visitor asking for the admin grade gets the default; the password is kept as its hash.
        JsonNode created = body(send(kim, "POST", "/member",
                "{\"email\":\"" + kimMail + "\",\"secret\":\"pw-kim\",\"grade\":\"A\"}"), 200);
        int kimId = created.get("memberId").asInt();
        assertTrue(kimId >= 1, created.toString());
        assertEquals("U", created.get("grade").asText(), created.toString());
        Document stored = mongo.getCollection("member").find(new Document("_id", kimId)).first();
        assertNotNull(stored);
        // @column(mail): the field's name in the document.
        assertEquals(kimMail, stored.get("mail"), stored.toJson());
        assertNotEquals("pw-kim", stored.get("secret"), stored.toJson());
        assertNotNull(stored.get("joinedAt"), stored.toJson());
        String leeMail = unique() + "@a.com";
        int leeId = body(send(lee, "POST", "/member",
                "{\"email\":\"" + leeMail + "\",\"secret\":\"pw-lee\"}"), 200).get("memberId").asInt();
        assertEquals(kimId + 1, leeId, "an int key from the sequence");
        // The same email again: the unique index refuses it.
        HttpResponse<String> taken = send(lee, "POST", "/member", "{\"email\":\"" + kimMail + "\",\"secret\":\"x\"}", 409);
        // The kind of refusal, never the driver's text (E11000, the index and the value).
        assertEquals("unique", json.readTree(taken.body()).get("code").asText(), taken.body());
        assertFalse(taken.body().contains(kimMail), taken.body());
        assertFalse(taken.body().contains("E11000"), taken.body());

        // A visitor has no me; a wrong password is a 401.
        send(kim, "GET", "/member/me", null, 401);
        send(kim, "POST", "/member/signin", "{\"email\":\"" + kimMail + "\",\"secret\":\"wrong\"}", 401);
        JsonNode signedIn = body(send(kim, "POST", "/member/signin",
                "{\"email\":\"" + kimMail + "\",\"secret\":\"pw-kim\"}"), 200);
        assertEquals(kimId, signedIn.get("memberId").asInt(), signedIn.toString());
        assertEquals(kimId, body(send(kim, "GET", "/member/me", null), 200).get("memberId").asInt());

        // Read is the owner's: another member's document is not found, a search finds only one's own.
        assertEquals(kimId, body(send(kim, "GET", "/member/" + kimId, null), 200).get("memberId").asInt());
        send(kim, "GET", "/member/" + leeId, null, 404);
        JsonNode mine = body(send(kim, "POST", "/member/search", "{}"), 200);
        assertEquals(1, mine.get("totalElements").asInt(), mine.toString());
        assertEquals(kimId, mine.get("content").get(0).get("memberId").asInt(), mine.toString());
        // Nor changed or deleted by another.
        send(kim, "PUT", "/member", "{\"memberId\":" + leeId + ",\"email\":\"taken@a.com\"}", 404);
        send(kim, "DELETE", "/member/" + leeId, null, 404);

        // A note is its writer's: the owner is whoever is signed in, whatever the request says.
        JsonNode note = body(send(kim, "POST", "/note", "{\"title\":\"Kim's\",\"ownerId\":" + leeId
                + ",\"body\":\"<p>hello<script>alert(1)</script></p>\"}"), 200);
        long noteId = note.get("noteId").asLong();
        assertEquals(kimId, note.get("ownerId").asInt(), note.toString());
        // html: cleaned to its safelist before it is written.
        assertFalse(note.get("body").asText().contains("script"), note.toString());
        assertTrue(note.get("body").asText().contains("hello"), note.toString());
        // @LastModifiedDate stamped by the service; @CreatedBy empty without an AuditorAware.
        assertFalse(note.get("changedAt").isNull(), note.toString());
        assertNull(note.get("writtenBy"), note.toString());
        send(lee, "POST", "/member/signin", "{\"email\":\"" + leeMail + "\",\"secret\":\"pw-lee\"}", 200);
        send(lee, "GET", "/note/" + noteId, null, 404);
        assertEquals(1, total(kim, "note", "{\"title\":\"kim\"}"));
        assertEquals(0, total(lee, "note", "{\"title\":\"kim\"}"));

        // @deleted on the login: the member deletes themselves, the document is kept, the login is no more.
        assertEquals("true", send(kim, "DELETE", "/member/" + kimId, null, 200).body());
        Document kept = mongo.getCollection("member").find(new Document("_id", kimId)).first();
        assertNotNull(kept);
        assertNotNull(kept.get("leftAt"), kept.toJson());
        send(client(), "POST", "/member/signin", "{\"email\":\"" + kimMail + "\",\"secret\":\"pw-kim\"}", 401);
        // @signin(refresh=request): the session of a member kept under @deleted ends with the next request.
        send(kim, "GET", "/member/me", null, 401);
        // Nor can it still read what it owned.
        int status = send(kim, "GET", "/note/" + noteId, null).statusCode();
        assertTrue(status == 401 || status == 404, "a visitor read the deleted member's note: " + status);
    }

    // ---- a key the service makes, money and every condition -----------------------------

    @Test
    public void everyConditionNarrowsTheGadgets() throws Exception {
        HttpClient http = client();
        String tag = unique();
        String[] rows = {
            "{\"label\":\"Red " + tag + "\",\"code\":\"AB\",\"head\":\"Pre1\",\"ihead\":\"Ipre\",\"tail\":\"x-End\","
                    + "\"itail\":\"x-IEND\",\"kind\":\"k1\",\"rank\":1,\"floor\":1,\"ceiling\":1,\"top\":1,\"other\":1,"
                    + "\"grade\":\"U\",\"price\":\"12.50\"}",
            "{\"label\":\"Blue " + tag + "\",\"code\":\"cd\",\"head\":\"Other\",\"ihead\":\"other\",\"tail\":\"y\","
                    + "\"itail\":\"y\",\"kind\":\"k2\",\"rank\":5,\"floor\":5,\"ceiling\":5,\"top\":5,\"other\":5,"
                    + "\"grade\":\"A\",\"price\":\"99.99\"}"
        };
        List<String> keys = new ArrayList<String>();
        for (String row : rows) {
            JsonNode g = body(send(http, "POST", "/gadget", row), 200);
            // @genid: the service makes a 12-character key.
            assertEquals(12, g.get("gadgetId").asText().length(), g.toString());
            keys.add(g.get("gadgetId").asText());
        }
        JsonNode red = body(send(http, "GET", "/gadget/" + keys.get(0), null), 200);
        assertEquals(0, new java.math.BigDecimal("12.50").compareTo(red.get("price").decimalValue()), red.toString());
        assertEquals("U", red.get("grade").asText(), red.toString());
        Document stored = mongo.getCollection("gadget").find(new Document("_id", keys.get(0))).first();
        assertNotNull(stored);
        // The enum by its code.
        assertEquals("U", stored.get("grade"), stored.toJson());

        String mine = "\"label\":\"" + tag + "\"";
        assertEquals(2, total(http, "gadget", "{" + mine + "}"));
        assertEquals(1, total(http, "gadget", "{" + mine + ",\"code\":\"ab\"}"));
        assertEquals(1, total(http, "gadget", "{" + mine + ",\"head\":\"Pre\"}"));
        assertEquals(0, total(http, "gadget", "{" + mine + ",\"head\":\"pre\"}"));
        assertEquals(1, total(http, "gadget", "{" + mine + ",\"ihead\":\"IPR\"}"));
        assertEquals(1, total(http, "gadget", "{" + mine + ",\"tail\":\"End\"}"));
        assertEquals(1, total(http, "gadget", "{" + mine + ",\"itail\":\"iend\"}"));
        assertEquals(1, total(http, "gadget", "{" + mine + ",\"kind\":\"k2\"}"));
        assertEquals(1, total(http, "gadget", "{" + mine + ",\"rank\":1}"));
        assertEquals(1, total(http, "gadget", "{" + mine + ",\"floor\":5}"));
        assertEquals(1, total(http, "gadget", "{" + mine + ",\"ceiling\":5}"));
        assertEquals(2, total(http, "gadget", "{" + mine + ",\"top\":5}"));
        assertEquals(1, total(http, "gadget", "{" + mine + ",\"other\":1}"));
        // An enum is searched by the code it is stored by.
        assertEquals(1, total(http, "gadget", "{" + mine + ",\"grade\":\"A\"}"));

        JsonNode sorted = body(send(http, "POST", "/gadget/search", "{" + mine + ",\"sortCode\":\"rank-\"}"), 200);
        assertEquals(5, sorted.get("content").get(0).get("rank").asInt(), sorted.toString());

        // @index(name=ix_rank, columnList="rank DESC, label"): made at startup.
        boolean found = false;
        for (Document index : mongo.getCollection("gadget").listIndexes()) {
            if ("ix_rank".equals(index.get("name"))) {
                found = true;
                assertEquals(new Document("rank", -1).append("label", 1), index.get("key"), index.toJson());
            }
        }
        assertTrue(found, "ix_rank was not created");
    }

    // ---- a read-only entity -------------------------------------------------------------

    @Test
    public void aReadOnlyEntityIsReadAndNotWritten() throws Exception {
        HttpClient http = client();
        mongo.getCollection("notice").insertOne(new Document("_id", 7L).append("text", "Closed on Monday"));
        JsonNode notice = body(send(http, "GET", "/notice/7", null), 200);
        assertEquals("Closed on Monday", notice.get("text").asText(), notice.toString());
        assertEquals(1, total(http, "notice", "{}"));
        // No create, update or delete endpoint: nothing at the path, or not that method there.
        send(http, "POST", "/notice", "{\"text\":\"x\"}", 404);
        send(http, "PUT", "/notice", "{\"noticeId\":7,\"text\":\"x\"}", 404);
        send(http, "DELETE", "/notice/7", null, 405);
        assertEquals(1, mongo.getCollection("notice").countDocuments());
        assertEquals("Closed on Monday", body(send(http, "GET", "/notice/7", null), 200).get("text").asText());
    }

    /**
     * A search sorts only by what its response shows (the Kotlin output): no response shows the @password, so a sort
     * on it is refused (400 no-such-property) - sortCode and sortColumn alike - before MongoDB is asked.
     */
    @Test
    public void aSortOnThePasswordIsRefused() throws Exception {
        HttpClient member = client();
        String mail = unique() + "@a.com";
        send(member, "POST", "/member", "{\"email\":\"" + mail + "\",\"secret\":\"pw-sort\"}", 200);
        send(member, "POST", "/member/signin", "{\"email\":\"" + mail + "\",\"secret\":\"pw-sort\"}", 200);
        for (String sort : new String[] {"{\"sortCode\":\"secret+\"}", "{\"sortColumn\":\"secret\"}"}) {
            JsonNode refused = body(send(member, "POST", "/member/search", sort), 400);
            assertEquals("no-such-property", refused.get("code").asText(), refused.toString());
            assertTrue(refused.get("detail").asText().contains("cannot be sorted by 'secret'"), refused.toString());
        }
        JsonNode mine = body(send(member, "POST", "/member/search", "{\"sortCode\":\"email+,joinedAt-\"}"), 200);
        assertEquals(1, mine.get("content").size(), mine.toString());
    }

    // ---- helpers ----------------------------------------------------------------------

    private static HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long total(HttpClient http, String entity, String search) throws Exception {
        return body(send(http, "POST", "/" + entity + "/search", search), 200).get("totalElements").asLong();
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
