package ddd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.elasticore.sample.ddd.catalog.dto.BookSrchDTO;
import io.elasticore.sample.ddd.catalog.service.BookCoreService;
import io.elasticore.sample.ddd.catalog.service.BookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The plain v3 bundle (jpa-springboot-v3), generated into the catalog domain
 * and driven over HTTP.
 *
 * Its core service is the same skeleton the hexagonal one fills with ports,
 * filled here with the repository helper instead. Until this domain existed
 * that reading was only ever rendered as text in a unit test; this is where
 * it compiles, boots and answers requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogApiTest {

    private static final String BOOKS = "/api/v1/catalog/book";
    private static final String AUTHORS = "/api/v1/catalog/author";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private ApplicationContext context;

    private JsonNode postJson(String path, String body) throws Exception {
        return json.readTree(mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString());
    }

    private int status(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private long createBook(String title) throws Exception {
        long author = postJson(AUTHORS, "{\"name\":\"Ursula\"}").path("authorId").asLong();
        return postJson(BOOKS, "{\"title\":\"" + title + "\",\"price\":10.00,\"authorAuthorId\":" + author + "}")
                .path("bookId").asLong();
    }

    private JsonNode book(long id) throws Exception {
        return json.readTree(mvc.perform(get(BOOKS + "/" + id)).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("@repository: code of its own reads and writes Supplier through the helper, and there is no API")
    void aRepositoryWithoutAnApi() throws Exception {
        io.elasticore.sample.ddd.catalog.repository.CatalogRepositoryHelper helper =
                context.getBean(io.elasticore.sample.ddd.catalog.repository.CatalogRepositoryHelper.class);
        io.elasticore.sample.ddd.catalog.entity.Supplier supplier = new io.elasticore.sample.ddd.catalog.entity.Supplier();
        supplier.setSupplierId("SUP-1");
        supplier.setName("Acme Paper");
        helper.getSupplier().save(supplier);
        assertThat(helper.getSupplier().findById("SUP-1")).get()
                .extracting(io.elasticore.sample.ddd.catalog.entity.Supplier::getName).isEqualTo("Acme Paper");

        assertThat(status(get("/api/v1/catalog/supplier/SUP-1"))).isEqualTo(404);
        assertThat(context.getBeanNamesForType(io.elasticore.sample.ddd.catalog.repository.SupplierRepository.class)).hasSize(1);
        assertThat(classExists("io.elasticore.sample.ddd.catalog.service.SupplierCoreService")).isFalse();
        assertThat(classExists("io.elasticore.sample.ddd.catalog.controller.SupplierController")).isFalse();
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    @DisplayName("create, read, update and delete through the plain v3 service")
    void crud() throws Exception {
        long id = createBook("The Dispossessed");
        assertThat(id).isPositive();
        assertThat(book(id).path("title").asText()).isEqualTo("The Dispossessed");

        assertThat(status(put(BOOKS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + id + ",\"isbn\":\"9780060512750\"}"))).isEqualTo(200);
        assertThat(book(id).path("isbn").asText()).isEqualTo("9780060512750");
        assertThat(book(id).path("title").asText()).as("a partial update leaves the rest").isEqualTo("The Dispossessed");

        assertThat(status(delete(BOOKS + "/" + id))).isEqualTo(200);
        assertThat(status(get(BOOKS + "/" + id))).isEqualTo(404);
    }

    @Test
    @DisplayName("a stale version is 409 and the newer write stands")
    void staleVersion() throws Exception {
        long id = createBook("Lathe");
        long v0 = book(id).path("version").asLong();
        String first = "{\"bookId\":" + id + ",\"isbn\":\"1\",\"version\":" + v0 + "}";
        String second = "{\"bookId\":" + id + ",\"isbn\":\"2\",\"version\":" + v0 + "}";
        assertThat(status(put(BOOKS).contentType(MediaType.APPLICATION_JSON).content(first))).isEqualTo(200);
        assertThat(status(put(BOOKS).contentType(MediaType.APPLICATION_JSON).content(second))).isEqualTo(409);
        assertThat(book(id).path("isbn").asText()).isEqualTo("1");
    }

    @Test
    @DisplayName("a create cannot overwrite; a generated key the client invents is not used")
    void createOnlyCreates() throws Exception {
        long id = createBook("Original");
        int taken = status(post(BOOKS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + id + ",\"title\":\"Overwritten\"}"));
        assertThat(taken).isEqualTo(409);
        assertThat(book(id).path("title").asText()).isEqualTo("Original");

        JsonNode invented = postJson(BOOKS, "{\"bookId\":987654,\"title\":\"Fresh\"}");
        assertThat(invented.path("bookId").asLong())
                .as("a sequence key is the database's; persist would have refused it as detached")
                .isNotEqualTo(987654L).isPositive();
    }

    @Test
    @DisplayName("the audit trail is not the request's to write in plain v3 either")
    void auditFieldIsNotClientWritable() throws Exception {
        long id = createBook("Audited");
        status(put(BOOKS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + id + ",\"isbn\":\"9\",\"lastModifiedBy\":\"mallory\"}"));
        assertThat(book(id).path("lastModifiedBy").asText()).isNotEqualTo("mallory");
    }

    @Test
    @DisplayName("an update is validated for what it sends: 400")
    void invalidUpdate() throws Exception {
        long id = createBook("Valid");
        assertThat(status(put(BOOKS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + id + ",\"title\":\"" + "t".repeat(101) + "\"}"))).isEqualTo(400);
        assertThat(status(put(BOOKS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + id + ",\"price\":-1}"))).isEqualTo(400);
        assertThat(status(put(BOOKS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":424242,\"isbn\":\"x\"}"))).isEqualTo(404);
    }

    @Test
    @DisplayName("a fractional bound on a double holds on both sides: 400 outside, stored inside")
    void fractionalBoundOnADouble() throws Exception {
        long id = createBook("Rated");
        assertThat(status(put(BOOKS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + id + ",\"rating\":5.5}"))).isEqualTo(400);
        assertThat(status(put(BOOKS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + id + ",\"rating\":0.4}"))).isEqualTo(400);
        assertThat(status(put(BOOKS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + id + ",\"rating\":4.5}"))).isEqualTo(200);
        assertThat(book(id).path("rating").asDouble()).isEqualTo(4.5);
        assertThat(status(put(BOOKS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + id + ",\"rating\":5.0}"))).isEqualTo(200);
    }

    @Test
    @DisplayName("delete and update by condition go row by row, and one bean serves each type")
    void conditionalWrites() throws Exception {
        createBook("Bulk-A");
        createBook("Bulk-B");
        BookCoreService service = context.getBean(BookService.class);

        BookSrchDTO bulk = new BookSrchDTO();
        bulk.setTitle("Bulk-");
        io.elasticore.sample.ddd.catalog.dto.BookDTO change = new io.elasticore.sample.ddd.catalog.dto.BookDTO();
        change.setIsbn("BULK");
        assertThat(service.updateBySpecification(change, bulk)).isEqualTo(2);
        assertThat(service.countBySearch(bulk)).isEqualTo(2);
        assertThat(service.delete(bulk)).isEqualTo(2);
        assertThat(service.countBySearch(bulk)).isZero();

        assertThat(context.getBeansOfType(BookCoreService.class)).hasSize(1);
    }

    @Test
    @DisplayName("@onDelete(setNull) through the relation: deleting an author keeps the book, without its author")
    void onDeleteSetNull() throws Exception {
        long id = createBook("Orphaned");
        long author = book(id).path("authorAuthorId").asLong();
        assertThat(author).isPositive();

        assertThat(status(delete(AUTHORS + "/" + author))).isEqualTo(200);
        assertThat(status(get(AUTHORS + "/" + author))).isEqualTo(404);
        assertThat(book(id).path("title").asText()).isEqualTo("Orphaned");
        assertThat(book(id).path("authorAuthorId").asLong(0)).as(book(id).toString()).isZero();

        // and by a search, the other way a row goes
        long second = createBook("Orphaned-2");
        long secondAuthor = book(second).path("authorAuthorId").asLong();
        io.elasticore.sample.ddd.catalog.service.AuthorCoreService authors =
                context.getBean(io.elasticore.sample.ddd.catalog.service.AuthorService.class);
        io.elasticore.sample.ddd.catalog.dto.AuthorSrchDTO byName = new io.elasticore.sample.ddd.catalog.dto.AuthorSrchDTO();
        byName.setName("Ursula");
        assertThat(authors.delete(byName)).isPositive();
        assertThat(status(get(AUTHORS + "/" + secondAuthor))).isEqualTo(404);
        assertThat(book(second).path("authorAuthorId").asLong(0)).as(book(second).toString()).isZero();
    }
}
