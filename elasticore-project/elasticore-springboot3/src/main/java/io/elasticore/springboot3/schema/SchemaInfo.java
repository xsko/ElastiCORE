package io.elasticore.springboot3.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.elasticore.runtime.utils.DTOUtils;
import io.elasticore.springboot3.security.AccessPolicy;
import io.elasticore.springboot3.security.CurrentUser;
import io.elasticore.springboot3.security.ForbiddenException;
import io.elasticore.springboot3.security.NotSignedInException;
import io.elasticore.springboot3.security.SessionCurrentUser;
import io.elasticore.springboot3.validation.ErrorMessages;
import org.springframework.context.ApplicationContext;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GET /schema/info answered from the model (env.yml {@code config: schemaInfo: model}, issue 373).
 *
 * The generated controller returns {@code SchemaInfo.answer("shop", "Order")}. The answer is read from the
 * {@code elasticore/<domain>-schema.json} the generator wrote beside the controllers - once per domain - and
 * says, with {@code metaVersion: 1}:
 * <ul>
 *   <li>{@code language}: the language the labels are said in, as the API's error messages choose it
 *       ({@link ErrorMessages#language()}); null for the bundles' own wording;</li>
 *   <li>{@code entity}: name, domain, label, labels, path, key, dto, searchDto;</li>
 *   <li>{@code fields}: every field the answer always had, with the keys it always had ({@code type},
 *       {@code description}, {@code maxSize}, {@code enums} - DTOUtils.getMetaInfo), and what the model says of
 *       it: kind, label, labels, required, maxLength, minLength, min, max, readOnly, html, the enumeration's
 *       options and what a reference points at (ref);</li>
 *   <li>{@code search}, where the controller serves POST /search: {@code pageSize}, {@code sortable} and
 *       {@code fields} - each property of the search DTO with the entity {@code field} it searches, its
 *       {@code op}, the {@code end} of a range and {@code in} for a list - as the generator wrote them;</li>
 *   <li>{@code actions}: what the caller of this request may do - {@code read}, {@code create}, {@code update},
 *       {@code delete} and each command, those the controller serves - decided now by the check the service
 *       applies: its AccessPolicy (the generator wrote its arguments) for the CurrentUser it takes (the
 *       application's bean, else the session's). {@code true}, {@code false}, or the conditions a row must
 *       meet: {@code ["owner"]}, {@code ["state"]}, {@code ["owner", "state"]}.</li>
 * </ul>
 * A label is said in the request's language: the domain's label bundle ({@code i18n/<domain>-labels}) for that
 * language, else its default file, else the DSL's "--" text. {@code labels} is the label in every other language
 * the DSL writes it in. The answer is {@code Cache-Control: private}: it is in the caller's language.
 */
public final class SchemaInfo {

    private SchemaInfo() {}

    private static final ObjectMapper JSON =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    /**
     * The files read, by class loader and domain: an application restarted by DevTools reads its own, and the
     * files of a loader no longer in use go with it.
     */
    private static final Map<ClassLoader, Map<String, Map<String, Object>>> FILES =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, Map<String, Map<String, Object>>>());

    /** The name the generated service's setter gives the bean it takes: setCurrentUser(CurrentUser currentUser). */
    private static final String BEAN = "currentUser";

    /** The domains' labels, as an export reads them. */
    private static final Map<String, ErrorMessages> LABELS = new ConcurrentHashMap<String, ErrorMessages>();

    /** The answer of GET /schema/info for an entity of a domain, as the generated controller returns it. */
    public static ResponseEntity<Map<String, Object>> answer(String domain, String entity) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.empty().cachePrivate())
                .body(of(domain, entity));
    }

    /** The answer's body, for the caller of this request. */
    public static Map<String, Object> of(String domain, String entity) {
        return of(domain, entity, currentUser());
    }

    /** The answer's body, for this caller. */
    static Map<String, Object> of(String domain, String entity, CurrentUser user) {
        return answer(file(domain), domain, entity, user);
    }

    /**
     * Who is asking, as the generated service takes it (@Autowired(required = false) setCurrentUser): the
     * application's CurrentUser bean - Spring Security's, say - the only one, the primary one, or among several
     * the one named currentUser; else the user the session holds (SessionCurrentUser).
     */
    static CurrentUser currentUser() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            try {
                ApplicationContext context =
                        org.springframework.web.servlet.support.RequestContextUtils.findWebApplicationContext(
                                servlet.getRequest());
                CurrentUser bean = context == null
                        ? null
                        : context.getBeanProvider(CurrentUser.class).getIfUnique();
                // Among several, none primary, @Autowired takes the one named as its parameter: currentUser.
                if (bean == null
                        && context != null
                        && context.containsBean(BEAN)
                        && context.isTypeMatch(BEAN, CurrentUser.class)) {
                    bean = context.getBean(BEAN, CurrentUser.class);
                }
                if (bean != null) return bean;
            } catch (RuntimeException | LinkageError none) {
                // No web application context to ask: the service's default.
            }
        }
        return new SessionCurrentUser();
    }

    /** A domain's labels: its label bundle beside its message bundle. */
    private static ErrorMessages labels(String domain) {
        return LABELS.computeIfAbsent(
                domain, d -> new ErrorMessages(null, "i18n/" + d + "-messages", "i18n/" + d + "-labels"));
    }

    /** The resource the generator writes for a domain. */
    static String resource(String domain) {
        return "elasticore/" + domain + "-schema.json";
    }

    private static Map<String, Object> file(String domain) {
        ClassLoader loader = loader();
        Map<String, Map<String, Object>> ofLoader;
        synchronized (FILES) {
            ofLoader = FILES.computeIfAbsent(loader, l -> new ConcurrentHashMap<String, Map<String, Object>>());
        }
        return ofLoader.computeIfAbsent(domain, d -> read(loader, d));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> read(ClassLoader loader, String domain) {
        String name = resource(domain);
        try (InputStream in = loader.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is not on the classpath: GET /schema/info answers from it"
                        + " with env.yml config: schemaInfo: model - generate the code again");
            }
            return (Map<String, Object>) readOnly(JSON.readValue(in, Map.class));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + name, e);
        }
    }

    /** The file as it is kept: no answer handed out can change what the next one is made from. */
    @SuppressWarnings("unchecked")
    static Object readOnly(Object value) {
        if (value instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> en : ((Map<String, Object>) value).entrySet()) {
                out.put(en.getKey(), readOnly(en.getValue()));
            }
            return Collections.unmodifiableMap(out);
        }
        if (value instanceof List) {
            List<Object> out = new ArrayList<Object>();
            for (Object o : (List<Object>) value) out.add(readOnly(o));
            return Collections.unmodifiableList(out);
        }
        return value;
    }

    /** The answer for an entity of the file, its labels said by the domain's label bundle. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> answer(Map<String, Object> file, String domain, String entity, CurrentUser user) {
        ErrorMessages labels = labels(domain);
        Map<String, Object> entities = (Map<String, Object>) file.get("entities");
        Map<String, Object> one = entities == null ? null : (Map<String, Object>) entities.get(entity);
        if (one == null) {
            throw new IllegalStateException(entity + " is not in " + resource(domain) + " - generate the code again");
        }
        Map<String, Object> declared = (Map<String, Object>) one.get("entity");
        String name = text(declared.get("name"));
        String dto = text(declared.get("dto"));

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("metaVersion", file.get("metaVersion"));
        Locale language = labels.language();
        out.put("language", language == null || Locale.ROOT.equals(language) ? null : language.toLanguageTag());

        Map<String, Object> said = new LinkedHashMap<String, Object>(declared);
        said.put("label", label(labels, text(declared.get("label")), dto, name));
        out.put("entity", said);

        Map<String, Object> facts = (Map<String, Object>) one.get("fields");
        if (facts == null) facts = Collections.emptyMap();
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        // The keys the answer always had, field by field, as the class declares them; then what the model says.
        for (Map.Entry<String, Object> en : reflected(text(one.get("class"))).entrySet()) {
            Map<String, Object> field = new LinkedHashMap<String, Object>((Map<String, Object>) en.getValue());
            Object model = facts.get(en.getKey());
            if (model != null) field.putAll(field(labels, (Map<String, Object>) model, en.getKey(), name, dto));
            fields.put(en.getKey(), field);
        }
        for (Map.Entry<String, Object> en : facts.entrySet()) {
            if (fields.containsKey(en.getKey())) continue;
            fields.put(en.getKey(), field(labels, (Map<String, Object>) en.getValue(), en.getKey(), name, dto));
        }
        out.put("fields", fields);
        // What POST /search takes, as the generator wrote it; absent where the controller serves no search.
        Object search = one.get("search");
        if (search != null) out.put("search", search);
        // What this caller may do, decided now by the service's own check; absent from a file without it.
        Object actions = one.get("actions");
        if (actions instanceof Map) out.put("actions", actions((Map<String, Object>) actions, name, user));
        return out;
    }

    /**
     * What the caller may do to the entity: each operation the controller serves, as the service decides it -
     * true, false, or the conditions a row must meet: {@code ["owner"]} (only a row of the caller's own),
     * {@code ["state"]} (only a row in a state the lifecycle allows), or both.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> actions(Map<String, Object> facts, String entity, CurrentUser user) {
        Map<String, Object> declared = (Map<String, Object>) facts.get("policy");
        // The service's own ACCESS, from its own arguments; no policy where the service checks nothing.
        AccessPolicy policy = declared == null
                ? null
                : AccessPolicy.parse(entity, text(declared.get("declaration")), text(declared.get("adminRole")));
        boolean owned = facts.get("owner") != null;
        boolean keyedByOwner = Boolean.TRUE.equals(facts.get("createNeedsSignIn"));
        boolean deleteReadsFirst = Boolean.TRUE.equals(facts.get("deleteReadsFirst"));
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Object operations = facts.get("operations");
        if (!(operations instanceof Map)) return out;
        for (Map.Entry<String, Object> en : ((Map<String, Object>) operations).entrySet()) {
            List<Object> row = en.getValue() instanceof List ? (List<Object>) en.getValue() : null;
            Object said = action(policy, user, en.getKey(), owned, keyedByOwner, row);
            // DELETE /{key} alone: the controller reads the row (read) before the service deletes it (delete).
            if (deleteReadsFirst && "delete".equals(en.getKey())) {
                said = both(action(policy, user, "read", owned, keyedByOwner, null), said);
            }
            out.put(en.getKey(), said);
        }
        return out;
    }

    /** What two checks made one after the other say: false if either refuses, else every condition of both. */
    @SuppressWarnings("unchecked")
    static Object both(Object first, Object then) {
        if (Boolean.FALSE.equals(first) || Boolean.FALSE.equals(then)) return false;
        java.util.Set<Object> conditions = new java.util.LinkedHashSet<Object>();
        if (first instanceof List) conditions.addAll((List<Object>) first);
        if (then instanceof List) conditions.addAll((List<Object>) then);
        if (conditions.isEmpty()) return true;
        return Collections.unmodifiableList(new ArrayList<Object>(conditions));
    }

    /** One operation: the service's checks in the order it makes them, then what the row's state decides. */
    static Object action(
            AccessPolicy policy,
            CurrentUser user,
            String operation,
            boolean owned,
            boolean keyedByOwner,
            List<Object> rowConditions) {
        List<Object> conditions = new ArrayList<Object>();
        if (policy != null) {
            // 401 or 403 before anything is read (ACCESS.require).
            if (!passes(policy, user, operation)) return false;
            if ("create".equals(operation)) {
                // A row keyed by its owner: a visitor has no identity to key it by.
                if (keyedByOwner && !policy.isAdmin(user) && !user.signedIn()) return false;
            } else if (!policy.owns(user, operation, null)) {
                // Not on a row of someone else's (ACCESS.owns): on the caller's own, where the service compares one.
                Object id = user.id();
                if (!owned || id == null || !policy.owns(user, operation, id)) return false;
                conditions.add("owner");
            }
        }
        if (rowConditions != null) conditions.addAll(rowConditions);
        if (conditions.isEmpty()) return true;
        return Collections.unmodifiableList(conditions);
    }

    /** Whether the caller passes ACCESS.require: the check the service makes first. */
    private static boolean passes(AccessPolicy policy, CurrentUser user, String operation) {
        try {
            policy.require(user, operation);
            return true;
        } catch (NotSignedInException | ForbiddenException refused) {
            return false;
        }
    }

    /** What the model says of a field, its labels said in the request's language. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> field(
            ErrorMessages labels, Map<String, Object> model, String field, String entity, String dto) {
        Map<String, Object> out = new LinkedHashMap<String, Object>(model);
        // The enumeration's name and domain are the file's, for its values' labels (Enum.CONSTANT, in the
        // label bundle of the domain that declares it); the answer has its options.
        String type = text(out.remove("enum"));
        String declaredIn = text(out.remove("enumDomain"));
        ErrorMessages valueLabels = declaredIn == null ? labels : labels(declaredIn);
        out.put("label", label(labels, text(model.get("label")), dto + "." + field, entity + "." + field));
        Object options = model.get("options");
        if (options instanceof List) {
            List<Object> said = new ArrayList<Object>();
            for (Object o : (List<Object>) options) {
                Map<String, Object> option = new LinkedHashMap<String, Object>((Map<String, Object>) o);
                option.put(
                        "label", label(valueLabels, text(option.get("label")), type + "." + text(option.get("name"))));
                said.add(option);
            }
            out.put("options", said);
        }
        return out;
    }

    /** A label in the request's language: the bundle's by the first key that has one, else the DSL's text. */
    private static String label(ErrorMessages labels, String dsl, String... keys) {
        for (String key : keys) {
            String said = labels.labelOrDefault(key);
            if (said != null && !said.trim().isEmpty()) return said.trim();
        }
        return dsl;
    }

    /** The answer the class has always given (DTOUtils.getMetaInfo); empty when the class is not there. */
    private static Map<String, Object> reflected(String className) {
        if (className == null) return Collections.emptyMap();
        try {
            return DTOUtils.getMetaInfo(Class.forName(className, false, loader()));
        } catch (ClassNotFoundException | LinkageError notThere) {
            return Collections.emptyMap();
        }
    }

    private static ClassLoader loader() {
        ClassLoader loader = ClassUtils.getDefaultClassLoader();
        return loader != null ? loader : SchemaInfo.class.getClassLoader();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
