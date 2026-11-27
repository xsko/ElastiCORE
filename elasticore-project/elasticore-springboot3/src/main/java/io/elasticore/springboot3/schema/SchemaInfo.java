package io.elasticore.springboot3.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.elasticore.runtime.utils.DTOUtils;
import io.elasticore.springboot3.validation.ErrorMessages;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;

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
 *       options and what a reference points at (ref).</li>
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

    /** The domains' labels, as an export reads them. */
    private static final Map<String, ErrorMessages> LABELS = new ConcurrentHashMap<String, ErrorMessages>();

    /** The answer of GET /schema/info for an entity of a domain, as the generated controller returns it. */
    public static ResponseEntity<Map<String, Object>> answer(String domain, String entity) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.empty().cachePrivate())
                .body(of(domain, entity));
    }

    /** The answer's body. */
    public static Map<String, Object> of(String domain, String entity) {
        return answer(file(domain), domain, entity);
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
    static Map<String, Object> answer(Map<String, Object> file, String domain, String entity) {
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
        return out;
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
