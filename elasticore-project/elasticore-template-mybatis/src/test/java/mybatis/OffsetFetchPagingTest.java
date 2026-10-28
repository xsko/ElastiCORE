package mybatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The page as Oracle, Tibero, SQL Server and DB2 write it - OFFSET ... ROWS
 * FETCH NEXT ... ROWS ONLY - chosen by the database id. Run on H2 in its
 * Oracle mode with the id said to be oracle: the branch's SQL, on a database
 * that reads that syntax, rather than Oracle itself.
 */
@SpringBootTest(classes = MyBatisSampleApp.class,
        properties = "spring.datasource.url=jdbc:h2:mem:ora;MODE=Oracle;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(OffsetFetchPagingTest.Oracle.class)
public class OffsetFetchPagingTest {

    @TestConfiguration
    static class Oracle {
        /** What the runtime library's provider answers for an Oracle connection. */
        @Bean
        DatabaseIdProvider oracleId() {
            return dataSource -> "oracle";
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    MyBatisSampleApp.StatementCounter statements;

    @Test
    public void aPageIsReadWithOffsetFetch() throws Exception {
        long id = post("/api/shop/customer", "{\"name\":\"Oh\",\"email\":\"" + UUID.randomUUID() + "@a.com\"}")
                .get("customerId").asLong();
        for (int i = 1; i <= 5; i++) {
            post("/api/shop/purchaseOrder", "{\"orderNo\":\"OF-" + i + "\",\"status\":\"N\",\"amount\":" + (i * 10)
                    + ",\"customerCustomerId\":" + id + "}");
        }
        statements.sql.clear();
        JsonNode page = post("/api/shop/purchaseOrder/search",
                "{\"customerCustomerId\":" + id + ",\"pageNumber\":1,\"pageSize\":2,\"sortCode\":\"amount+\"}");
        String last = statements.sql.get(statements.sql.size() - 1).replaceAll("\\s+", " ");
        assertTrue(last.contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"), last);
        assertFalse(last.contains("LIMIT"), last);
        List<Integer> amounts = new ArrayList<Integer>();
        for (JsonNode row : page.get("content")) amounts.add(row.get("amount").asInt());
        assertEquals(Arrays.asList(30, 40), amounts);
        assertEquals(5, page.get("totalElements").asInt());
    }

    private JsonNode post(String path, String content) throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON).content(content)).andReturn();
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return json.readTree(r.getResponse().getContentAsString());
    }
}
