package io.elasticore.springboot3.schema;

import io.elasticore.springboot3.security.AccessPolicy;
import io.elasticore.springboot3.security.CurrentUser;
import io.elasticore.springboot3.security.SessionCurrentUser;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GET /schema/info answered from the model (config: schemaInfo: model, issue 373): the file the generator writes
 * (elasticore/desk-schema.json), the labels said in the request's language by the domain's label bundle, and the
 * keys the answer always had, read off the DTO as before.
 */
public class SchemaInfoTest {

    enum ItemStatus {
        ON_SALE("ON"),
        OFF("OF");

        private final String code;

        ItemStatus(String code) {
            this.code = code;
        }
    }

    /** The DTO the file names: what DTOUtils.getMetaInfo reads. */
    public static class ItemDTO {
        @Size(max = 30)
        private String name;

        private BigDecimal unitCost;
        private ItemStatus status;
        private Long productId;
        private String note;
    }

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    /** The names a class declares, without the synthetic ones a coverage or mutation agent adds to it ($jacocoData). */
    private static List<String> declared(java.util.Collection<String> names) {
        List<String> out = new java.util.ArrayList<String>();
        for (String n : names) {
            if (!n.startsWith("$")) out.add(n);
        }
        return out;
    }

    private static Map<String, Object> field(Map<String, Object> answer, String name) {
        return map(map(answer.get("fields")).get(name));
    }

    @Test
    public void theAnswerIsTheEntityAndItsFieldsInTheRequestsLanguage() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        Map<String, Object> answer = SchemaInfo.of("desk", "Item");
        assertEquals(1, answer.get("metaVersion"));
        assertEquals("ko", answer.get("language"));
        Map<String, Object> entity = map(answer.get("entity"));
        assertEquals("Item", entity.get("name"));
        assertEquals("판매 단위", entity.get("label"));
        assertEquals("Item for sale", map(entity.get("labels")).get("en"));
        assertEquals("/api/desk/item", entity.get("path"));
        assertEquals("itemId", entity.get("key"));

        assertEquals("단가", field(answer, "unitCost").get("label"));
        assertEquals("Unit cost", map(field(answer, "unitCost").get("labels")).get("en"));
        assertEquals(0, new BigDecimal("0.01").compareTo((BigDecimal)
                        field(answer, "unitCost").get("min")));
        // The DTO's own label of the field first.
        assertEquals("상품", field(answer, "productId").get("label"));
        List<Object> options = (List<Object>) field(answer, "status").get("options");
        assertEquals("판매 중", map(options.get(0)).get("label"));
        assertEquals("ON", map(options.get(0)).get("value"));
        assertEquals("Off", map(options.get(1)).get("label"));
        // The enumeration's name is the file's, not the answer's.
        assertFalse(field(answer, "status").containsKey("enum"));
        // An enumeration of another domain: named by that domain's label bundle (i18n/common-labels).
        List<Object> currency = (List<Object>) field(answer, "currency").get("options");
        assertEquals("원", map(currency.get(0)).get("label"));
        assertFalse(field(answer, "currency").containsKey("enumDomain"));
        // What the answer hands out cannot change the file the next answer is made from.
        assertThrows(
                UnsupportedOperationException.class,
                () -> map(map(answer.get("entity")).get("labels")).put("fr", "x"));
        assertEquals(
                "Item for sale",
                map(map(SchemaInfo.of("desk", "Item").get("entity")).get("labels"))
                        .get("en"));

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        Map<String, Object> en = SchemaInfo.of("desk", "Item");
        // The label bundle has an _en file: English is a language of its own here.
        assertEquals("en", en.get("language"));
        assertEquals("Item for sale", map(en.get("entity")).get("label"));
        assertEquals("Unit cost", field(en, "unitCost").get("label"));
        // No English label: the default file - the "--" text.
        assertEquals("이름", field(en, "name").get("label"));
        assertEquals(
                "On sale",
                map(((List<Object>) field(en, "status").get("options")).get(0)).get("label"));
        // No such key in any file: the file's label.
        assertEquals("productId", field(en, "productId").get("label"));
    }

    /** Nobody chose a language: the bundles' own wording, and no language named. */
    @Test
    public void withoutALanguageTheBundlesOwnWording() {
        LocaleContextHolder.setLocale(Locale.ROOT);
        Map<String, Object> answer = SchemaInfo.of("desk", "Item");
        assertNull(answer.get("language"));
        assertEquals("Unit cost", field(answer, "unitCost").get("label"));
        // A language without a file of its own: the default file, the "--" text.
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        assertEquals("단가", field(SchemaInfo.of("desk", "Item"), "unitCost").get("label"));
    }

    @Test
    public void eachFieldKeepsTheKeysItAlwaysHad() {
        Map<String, Object> answer = SchemaInfo.of("desk", "Item");
        // In the order the class declares them, then what only the file has.
        assertEquals(
                Arrays.asList("name", "unitCost", "status", "productId", "note", "currency", "written"),
                declared(map(answer.get("fields")).keySet()));
        Map<String, Object> name = field(answer, "name");
        assertEquals("String", name.get("type"));
        assertEquals(30, name.get("maxSize"));
        assertEquals("String", name.get("kind"));
        assertEquals(Boolean.TRUE, name.get("required"));
        assertEquals(30, name.get("maxLength"));
        assertEquals(
                Arrays.asList(
                        "type", "maxSize", "kind", "label", "labels", "required", "maxLength", "readOnly", "html"),
                Arrays.asList(name.keySet().toArray()));
        assertEquals("enumeration", field(answer, "status").get("type"));
        assertTrue(field(answer, "status").containsKey("enums"));
        // A field only the class has keeps only what it always said.
        assertEquals("String", field(answer, "note").get("type"));
        assertFalse(field(answer, "note").containsKey("kind"));
        assertEquals("String", field(answer, "written").get("kind"));
        assertFalse(field(answer, "written").containsKey("type"));
        assertEquals("Product", map(field(answer, "productId").get("ref")).get("entity"));
    }

    @Test
    public void aClassThatIsNotThereLeavesTheModelsFacts() {
        Map<String, Object> gone = SchemaInfo.of("desk", "Gone");
        assertEquals("Integer", field(gone, "goneId").get("kind"));
        assertEquals(1, map(gone.get("fields")).size());
        // No class and no fields at all: an empty field list.
        assertTrue(map(SchemaInfo.of("desk", "Bare").get("fields")).isEmpty());
    }

    /** What POST /search takes, after the fields, as the file has it; none where the controller serves no search. */
    @Test
    public void theSearchAsTheFileHasIt() {
        Map<String, Object> answer = SchemaInfo.of("desk", "Item");
        assertEquals(
                java.util.Arrays.asList("metaVersion", "language", "entity", "fields", "search", "actions"),
                new java.util.ArrayList<String>(answer.keySet()));
        Map<String, Object> search = map(answer.get("search"));
        assertEquals(20, search.get("pageSize"));
        assertEquals(java.util.Arrays.asList("itemId", "name"), search.get("sortable"));
        Map<String, Object> from = map(map(search.get("fields")).get("unitCostFrom"));
        assertEquals("unitCost", from.get("field"));
        assertEquals("between", from.get("op"));
        assertEquals("from", from.get("end"));
        assertFalse(SchemaInfo.of("desk", "Gone").containsKey("search"));
    }

    // ---- actions ----------------------------------------------------------------------------------------------

    /** A caller signed in with this identity and these roles. */
    private static CurrentUser user(Object id, String... roles) {
        final List<String> held = Arrays.asList(roles);
        return new CurrentUser() {
            @Override
            public boolean system() {
                return false;
            }

            @Override
            public Object id() {
                return id;
            }

            @Override
            public boolean hasRole(String role) {
                return id != null && held.contains(role);
            }
        };
    }

    private static final CurrentUser VISITOR = user(null);

    private static final CurrentUser SYSTEM = new CurrentUser() {
        @Override
        public boolean system() {
            return true;
        }

        @Override
        public Object id() {
            return null;
        }

        @Override
        public boolean hasRole(String role) {
            return false;
        }
    };

    private static Map<String, Object> actionsOf(CurrentUser user) {
        return map(SchemaInfo.of("desk", "Item", user).get("actions"));
    }

    private static Map<String, Object> expected(Object... keysAndValues) {
        Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < keysAndValues.length; i += 2) out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return out;
    }

    /**
     * {@code @owner(ownerId) @access(read=public, create=user, update=owner, delete=MANAGER&owner,
     * approve=BOSS|AUDITOR, archive=none)}, adminRole BOSS, and a lifecycle deciding delete and approve: each
     * caller gets what the policy lets them do, a row's conditions where the row decides.
     */
    @Test
    public void eachCallerIsToldWhatThePolicyLetsThemDo() {
        List<String> owner = Arrays.asList("owner");
        List<String> state = Arrays.asList("state");
        List<String> both = Arrays.asList("owner", "state");
        assertEquals(
                expected(
                        "read", true, "create", false, "update", false, "delete", false, "approve", false, "archive",
                        false),
                actionsOf(VISITOR));
        assertEquals(
                expected(
                        "read", true, "create", true, "update", owner, "delete", false, "approve", false, "archive",
                        false),
                actionsOf(user(7L, "USER")));
        assertEquals(
                expected(
                        "read", true, "create", true, "update", owner, "delete", both, "approve", false, "archive",
                        false),
                actionsOf(user(8L, "MANAGER")));
        // A role named in the entry: on every row; the lifecycle still decides.
        assertEquals(state, actionsOf(user(9L, "AUDITOR")).get("approve"));
        // The administrator is adminRole's - BOSS here, not ADMIN - and passes every level but none.
        assertEquals(
                expected(
                        "read", true, "create", true, "update", true, "delete", state, "approve", state, "archive",
                        false),
                actionsOf(user(1L, "BOSS")));
        assertEquals(actionsOf(user(7L, "USER")), actionsOf(user(7L, "ADMIN")));
        // The application itself (runAsSystem) passes every check, none too.
        assertEquals(
                expected(
                        "read", true, "create", true, "update", true, "delete", state, "approve", state, "archive",
                        true),
                actionsOf(SYSTEM));
        // What is handed out is the caller's alone.
        assertThrows(
                UnsupportedOperationException.class,
                () -> ((List<Object>) actionsOf(SYSTEM).get("delete")).add("x"));
    }

    /** A service that checks nothing (no @access, no @owner, no @password): everything, the lifecycle aside. */
    @Test
    public void aServiceThatChecksNothingLetsEveryoneIn() {
        assertEquals(
                expected("read", true, "delete", Arrays.asList("state")),
                map(SchemaInfo.of("desk", "Gone", VISITOR).get("actions")));
        // A file written without actions says none.
        assertFalse(SchemaInfo.of("desk", "Bare", VISITOR).containsKey("actions"));
    }

    /** No @access at all, with @owner: AccessPolicy's public for everything, as the service applies it. */
    @Test
    public void noDeclarationIsPublicForEverything() {
        AccessPolicy none = AccessPolicy.parse("Note", null, "ADMIN");
        for (String op : Arrays.asList("read", "create", "update", "delete", "close")) {
            assertEquals(Boolean.TRUE, SchemaInfo.action(none, VISITOR, op, true, false, null), op);
        }
    }

    /** What the service compares the owner by, and the create a visitor cannot key. */
    @Test
    public void theOwnerTheServiceComparesAndTheKeyAVisitorHasNot() {
        AccessPolicy policy = AccessPolicy.parse("Note", "read=owner, create=public, update=owner|EDITOR", "ADMIN");
        // An owner-level operation with no @owner identity to compare: the administrator's alone.
        assertEquals(Boolean.FALSE, SchemaInfo.action(policy, user(7L), "read", false, false, null));
        assertEquals(Arrays.asList("owner"), SchemaInfo.action(policy, user(7L), "read", true, false, null));
        assertEquals(Boolean.TRUE, SchemaInfo.action(policy, user(1L, "ADMIN"), "read", false, false, null));
        // owner|EDITOR: an EDITOR on every row, anyone else on their own.
        assertEquals(Boolean.TRUE, SchemaInfo.action(policy, user(7L, "EDITOR"), "update", true, false, null));
        assertEquals(Arrays.asList("owner"), SchemaInfo.action(policy, user(7L), "update", true, false, null));
        // A signed-in caller whose identity is not known: no row of theirs to compare.
        CurrentUser nameless = new CurrentUser() {
            @Override
            public boolean system() {
                return false;
            }

            @Override
            public Object id() {
                return null;
            }

            @Override
            public boolean hasRole(String role) {
                return false;
            }

            @Override
            public boolean signedIn() {
                return true;
            }
        };
        assertEquals(Boolean.FALSE, SchemaInfo.action(policy, nameless, "read", true, false, null));
        // Keyed by its owner: a visitor has no identity to key a new row by; the signed-in and the administrator do.
        assertEquals(Boolean.FALSE, SchemaInfo.action(policy, VISITOR, "create", true, true, null));
        assertEquals(Boolean.TRUE, SchemaInfo.action(policy, user(7L), "create", true, true, null));
        assertEquals(Boolean.TRUE, SchemaInfo.action(policy, VISITOR, "create", true, false, null));
        assertEquals(Boolean.TRUE, SchemaInfo.action(policy, SYSTEM, "create", true, true, null));
        // create is never the owner's to narrow: create=owner lets in whoever is signed in.
        AccessPolicy byOwner = AccessPolicy.parse("Note", "create=owner", "ADMIN");
        assertEquals(Boolean.TRUE, SchemaInfo.action(byOwner, user(7L), "create", true, false, null));
        assertEquals(Boolean.FALSE, SchemaInfo.action(byOwner, VISITOR, "create", true, false, null));
    }

    /**
     * DELETE /{key} without POST /delete: the controller reads the row (read) before the service deletes it
     * (delete), so the answer is both checks - refused by either, with the conditions of both.
     */
    @Test
    public void aDeleteThatReadsTheRowFirstIsBothChecks() {
        Map<String, Object> policy = new java.util.LinkedHashMap<String, Object>();
        policy.put("declaration", "read=owner|MANAGER, delete=user");
        policy.put("adminRole", "ADMIN");
        Map<String, Object> operations = new java.util.LinkedHashMap<String, Object>();
        operations.put("read", Arrays.asList());
        operations.put("delete", Arrays.asList("state"));
        Map<String, Object> facts = new java.util.LinkedHashMap<String, Object>();
        facts.put("policy", policy);
        facts.put("owner", "ownerId");
        facts.put("createNeedsSignIn", false);
        facts.put("deleteReadsFirst", true);
        facts.put("operations", operations);
        // A plain user: their own row only, read says; the lifecycle, delete says.
        assertEquals(
                Arrays.asList("owner", "state"),
                SchemaInfo.actions(facts, "Note", user(7L)).get("delete"));
        // A MANAGER reads every row: delete is delete's.
        assertEquals(
                Arrays.asList("state"),
                SchemaInfo.actions(facts, "Note", user(8L, "MANAGER")).get("delete"));
        assertEquals(Boolean.FALSE, SchemaInfo.actions(facts, "Note", VISITOR).get("delete"));
        // With POST /delete, delete alone.
        facts.put("deleteReadsFirst", false);
        assertEquals(
                Arrays.asList("state"),
                SchemaInfo.actions(facts, "Note", user(7L)).get("delete"));

        assertEquals(Boolean.TRUE, SchemaInfo.both(true, true));
        assertEquals(Boolean.FALSE, SchemaInfo.both(false, true));
        assertEquals(Boolean.FALSE, SchemaInfo.both(Arrays.asList("owner"), false));
        assertEquals(Arrays.asList("owner"), SchemaInfo.both(Arrays.asList("owner"), true));
        assertEquals(
                Arrays.asList("owner", "state"),
                SchemaInfo.both(Arrays.asList("owner"), Arrays.asList("owner", "state")));
    }

    /** Several CurrentUser beans, none primary: the one named currentUser, as the service's setter takes it. */
    @Test
    public void amongSeveralTheOneNamedAsTheServicesParameter() {
        try {
            CurrentUser named = user(1L, "BOSS");
            StaticWebApplicationContext context = new StaticWebApplicationContext();
            context.setServletContext(new MockServletContext());
            context.getBeanFactory().registerSingleton("other", user(2L));
            context.getBeanFactory().registerSingleton("currentUser", named);
            context.refresh();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            assertSame(named, SchemaInfo.currentUser());
            context.close();

            // Several, none of that name: the session's user.
            StaticWebApplicationContext two = new StaticWebApplicationContext();
            two.setServletContext(new MockServletContext());
            two.getBeanFactory().registerSingleton("one", user(1L));
            two.getBeanFactory().registerSingleton("other", user(2L));
            two.refresh();
            request.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, two);
            assertTrue(SchemaInfo.currentUser() instanceof SessionCurrentUser);
            two.close();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /** Who is asking, as the service takes it: the application's CurrentUser bean, else the session's user. */
    @Test
    public void theCallerIsTheServicesCurrentUser() {
        try {
            // No request: the session's user, nobody signed in.
            RequestContextHolder.resetRequestAttributes();
            assertTrue(SchemaInfo.currentUser() instanceof SessionCurrentUser);

            // A request whose session holds a sign-in: that user, in the answer too.
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession(true).setAttribute(SessionCurrentUser.ID, 8L);
            request.getSession()
                    .setAttribute(
                            SessionCurrentUser.ROLES, new java.util.LinkedHashSet<String>(Arrays.asList("MANAGER")));
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            CurrentUser session = SchemaInfo.currentUser();
            assertTrue(session instanceof SessionCurrentUser);
            assertEquals(8L, session.id());
            assertEquals(
                    Arrays.asList("owner", "state"),
                    map(SchemaInfo.of("desk", "Item").get("actions")).get("delete"));

            // The application's own bean - Spring Security's, say - when it has one.
            CurrentUser own = user(1L, "BOSS");
            StaticWebApplicationContext context = new StaticWebApplicationContext();
            context.setServletContext(new MockServletContext());
            context.getBeanFactory().registerSingleton("currentUser", own);
            context.refresh();
            request.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
            assertSame(own, SchemaInfo.currentUser());
            assertEquals(
                    Boolean.TRUE,
                    map(SchemaInfo.of("desk", "Item").get("actions")).get("update"));
            context.close();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    public void theAnswerIsPrivate() {
        ResponseEntity<Map<String, Object>> response = SchemaInfo.answer("desk", "Item");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("private", response.getHeaders().getCacheControl());
        assertEquals("Item", map(response.getBody().get("entity")).get("name"));
    }

    @Test
    public void whatTheFileDoesNotHaveIsSaid() {
        IllegalStateException entity = assertThrows(IllegalStateException.class, () -> SchemaInfo.of("desk", "Nope"));
        assertEquals("Nope is not in elasticore/desk-schema.json - generate the code again", entity.getMessage());
        IllegalStateException file = assertThrows(
                IllegalStateException.class, () -> SchemaInfo.read(SchemaInfoTest.class.getClassLoader(), "nowhere"));
        assertEquals(
                "elasticore/nowhere-schema.json is not on the classpath: GET /schema/info answers from it with env.yml"
                        + " config: schemaInfo: model - generate the code again",
                file.getMessage());
        assertEquals("elasticore/shop-schema.json", SchemaInfo.resource("shop"));
    }
}
