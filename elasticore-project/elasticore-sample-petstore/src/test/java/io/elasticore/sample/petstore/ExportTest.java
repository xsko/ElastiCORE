package io.elasticore.sample.petstore;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST .../export (issue 271) over HTTP on the pet store - JPA, the hexagon, Spring Boot 4 and Jackson 3: Item and
 * PurchaseOrder list export in @expose(methods). The rows the search finds, the columns and titles asked for or the
 * labels of the request's language, the same @owner filter as the search, a text a spreadsheet would run written as
 * text, and 406 for an .xlsx workbook - the pet store has no Apache POI.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExportTest {

    private static final String BOM = "\uFEFF";

    private static final String CARD = "\"creditCard\":{\"creditCardNumber\":\"4111111111111111\",\"creditCardType\":\"VISA\",\"creditCardExpDate\":\"12/30\"}";

    @Autowired MockMvc mvc;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpSession signIn(String login, String password) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/petstore/customer/signin"),
                        "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    /** The CSV an export answers, its rows as lines; the byte order mark checked and taken off. */
    private List<String> csv(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("text/csv;charset=UTF-8", response.getContentType());
        byte[] bytes = response.getContentAsByteArray();
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(bytes, 3));
        String text = new String(bytes, StandardCharsets.UTF_8).substring(BOM.length());
        assertTrue(text.endsWith("\r\n"), text);
        return new ArrayList<String>(Arrays.asList(text.substring(0, text.length() - 2).split("\r\n", -1)));
    }

    @Test
    void theRowsTheSearchFindsInTheColumnsAskedFor() throws Exception {
        String search = "{\"name\":\"Puppy\"}";
        String found = mvc.perform(json(post("/api/petstore/item/search"), search))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(found, "$.content[*].itemId");
        assertFalse(ids.isEmpty());

        List<String> lines = csv(json(post("/api/petstore/item/export"),
                "{\"search\":" + search + ",\"sortCode\":\"itemId+\",\"columns\":[\"itemId\",\"name\",\"productName\",\"unitCost\"]}"));
        // No Accept-Language: English - the _en label (@label(en=Unit cost)), else the name.
        assertEquals("itemId,name,productName,Unit cost", lines.get(0));
        assertEquals(ids.size() + 1, lines.size(), lines.toString());
        List<Integer> exported = new ArrayList<Integer>();
        for (String line : lines.subList(1, lines.size())) exported.add(Integer.valueOf(line.split(",")[0]));
        List<Integer> sorted = new ArrayList<Integer>(ids);
        java.util.Collections.sort(sorted);
        assertEquals(sorted, exported);
        // As the response shows it: money as its decimal, a @ref value by its name.
        assertTrue(lines.contains("1006,Male Puppy,Goldfish,12.00"), lines.toString());

        // Titles of the request: as given, one a column.
        List<String> titled = csv(json(post("/api/petstore/item/export"),
                "{\"search\":" + search + ",\"columns\":[\"name\",\"itemId\"],\"headers\":[\"Pet\",\"No.\"]}"));
        assertEquals("Pet,No.", titled.get(0));
        assertTrue(titled.contains("Male Puppy,1006"), titled.toString());
    }

    @Test
    void theTitlesAreTheLabelsOfTheRequestsLanguage() throws Exception {
        String body = "{\"search\":{\"name\":\"Puppy\"},\"columns\":[\"name\",\"unitCost\"]}";
        assertEquals("name,단가",
                csv(json(post("/api/petstore/item/export"), body).header("Accept-Language", "ko")).get(0));
        assertEquals("name,Unit cost",
                csv(json(post("/api/petstore/item/export"), body).header("Accept-Language", "en")).get(0));
        // Every column, in the response DTO's order.
        String all = csv(json(post("/api/petstore/item/export"), "{\"search\":{\"name\":\"Puppy\"}}")).get(0);
        assertEquals("productName,itemId,name,description,imagePath,Unit cost,productId", all);
    }

    @Test
    void aCustomerExportsTheirOwnOrdersOnly() throws Exception {
        MockHttpSession admin = signIn("admin", "admin");
        MockHttpSession user = signIn("user", "user");
        String cart = "{\"lines\":[{\"itemId\":1000,\"quantity\":1}]," + CARD + "}";
        String mine = JsonPath.read(mvc.perform(json(post("/api/petstore/purchaseOrder"), cart).session(user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.orderId");
        // An administrator names the customer an order is for: the admin's own (1).
        String adminCart = "{\"customerId\":1,\"lines\":[{\"itemId\":1000,\"quantity\":1}]," + CARD + "}";
        String theirs = JsonPath.read(mvc.perform(json(post("/api/petstore/purchaseOrder"), adminCart).session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.orderId");

        String body = "{\"columns\":[\"orderId\",\"customerId\",\"status\"]}";
        List<String> users = csv(json(post("/api/petstore/purchaseOrder/export"), body).session(user));
        assertEquals("orderId,customerId,Order status", users.get(0));
        // The enumeration as the JSON writes it: its code.
        assertTrue(users.contains(mine + ",2,PLC"), users.toString());
        for (String line : users.subList(1, users.size())) assertTrue(line.contains(",2,"), users.toString());
        assertFalse(String.join("\n", users).contains(theirs), users.toString());
        // Asking for someone else's: none of theirs.
        List<String> asked = csv(json(post("/api/petstore/purchaseOrder/export"),
                "{\"search\":{\"customerId\":1},\"columns\":[\"orderId\"]}").session(user));
        assertEquals(1, asked.size(), asked.toString());

        // An administrator's search is not narrowed.
        List<String> admins = csv(json(post("/api/petstore/purchaseOrder/export"), body).session(admin));
        String all = String.join("\n", admins);
        assertTrue(all.contains(mine) && all.contains(theirs), all);

        // A visitor may not search orders, so may not export them.
        mvc.perform(json(post("/api/petstore/purchaseOrder/export"), body)).andExpect(status().isUnauthorized());
    }

    @Test
    void aTextASpreadsheetWouldRunIsWrittenAsText() throws Exception {
        MockHttpSession admin = signIn("admin", "admin");
        String created = mvc.perform(json(post("/api/petstore/item"),
                        "{\"name\":\"=HYPERLINK(\\\"x\\\")\",\"description\":\"-1+2, said \\\"no\\\"\",\"unitCost\":5,\"productId\":1000}")
                        .session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(created, "$.itemId");
        List<String> lines = csv(json(post("/api/petstore/item/export"),
                "{\"search\":{\"itemId\":" + id + "},\"columns\":[\"itemId\",\"name\",\"description\"]}"));
        assertEquals(2, lines.size(), lines.toString());
        assertEquals(id + ",\"'=HYPERLINK(\"\"x\"\")\",\"'-1+2, said \"\"no\"\"\"", lines.get(1));
    }

    @Test
    void anXlsxWorkbookWithoutApachePoiIsNotAcceptable() throws Exception {
        mvc.perform(json(post("/api/petstore/item/export"), "{\"format\":\"xlsx\"}"))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("not-acceptable"));
        mvc.perform(json(post("/api/petstore/item/export"), "{}")
                        .accept("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(status().isNotAcceptable());
        // A column the response does not have, or a format there is none of: 400.
        mvc.perform(json(post("/api/petstore/item/export"), "{\"columns\":[\"password\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("export.no-such-column"));
        // Asked with Accept: text/csv, a refusal is still the problem body.
        mvc.perform(json(post("/api/petstore/item/export"), "{\"columns\":[\"password\"]}").accept("text/csv"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("export.no-such-column"));
        mvc.perform(json(post("/api/petstore/item/export"), "{\"format\":\"pdf\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("export.format"));
    }
}
