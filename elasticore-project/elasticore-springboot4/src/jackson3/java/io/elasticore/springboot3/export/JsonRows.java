package io.elasticore.springboot3.export;

import io.elasticore.springboot3.mask.Mask;
import io.elasticore.springboot3.mask.MaskJackson;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationContext;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.PropertyWriter;

/**
 * A response DTO's rows as the response's JSON writes them, on Jackson 3 (Spring Boot 4): through the application's
 * JsonMapper - its modules, its features, the {@code @Mask} module and the JsonView the search answers with - so an
 * export shows what the search's JSON shows, masked where that is masked. elasticore-springboot3 has the same class
 * on Jackson 2.
 */
final class JsonRows {

    private final ObjectMapper mapper;

    /** Each column's writer by its name, of the type last asked for. */
    private final Map<String, BeanPropertyWriter> writers = new HashMap<String, BeanPropertyWriter>();

    JsonRows(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** The application's JsonMapper; without one (no context, no bean), a default one that masks. */
    static JsonRows of(ApplicationContext context) {
        ObjectMapper found = null;
        if (context != null) {
            found = context.getBeanProvider(ObjectMapper.class).getIfAvailable();
        }
        if (found == null) {
            found = JsonMapper.builder().addModule(MaskJackson.module()).build();
        }
        return new JsonRows(found);
    }

    /**
     * The properties the JSON of a type writes - in the view, when one is given - in the order the class declares
     * its fields (a property without a field of its own after them, in Jackson's order).
     */
    List<String> columns(Class<?> type, Class<?> view) {
        ValueSerializer<Object> serializer = mapper._serializationContext().findValueSerializer(type);
        List<String> names = new ArrayList<String>();
        writers.clear();
        boolean inViewByDefault = mapper.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION);
        for (Iterator<PropertyWriter> it = serializer.properties(); it.hasNext(); ) {
            PropertyWriter property = it.next();
            if (property instanceof BeanPropertyWriter writer) {
                if (view != null && !ExportColumns.inView(writer.getViews(), view, inViewByDefault)) continue;
                writers.put(writer.getName(), writer);
            }
            names.add(property.getName());
        }
        return ExportColumns.inDeclarationOrder(type, names);
    }

    /**
     * A row's values, a column each: the text of a JSON string, a JSON number as a BigDecimal, a boolean, null
     * where the JSON has nothing, and an object or a list as its JSON text. Typed: where the JSON writes a date as
     * text and the property is not masked, the date itself, for a sheet's date cell.
     */
    List<Object> values(Object row, Class<?> view, List<String> columns, boolean typed) {
        byte[] json = (view == null ? mapper.writer() : mapper.writerWithView(view)).writeValueAsBytes(row);
        Map<String, Object> found = new LinkedHashMap<String, Object>();
        try (JsonParser parser = mapper.createParser(json)) {
            if (parser.nextToken() == JsonToken.START_OBJECT) {
                while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                    String name = parser.currentName();
                    found.put(name, value(parser, parser.nextToken()));
                }
            }
        }
        List<Object> out = new ArrayList<Object>(columns.size());
        for (String column : columns) {
            Object value = found.get(column);
            if (typed && value instanceof String) {
                BeanPropertyWriter writer = writers.get(column);
                if (writer != null && writer.getAnnotation(Mask.class) == null) {
                    Object raw = raw(writer, row);
                    if (ExportColumns.isDate(raw)) value = raw;
                }
            }
            out.add(value);
        }
        return out;
    }

    private static Object raw(BeanPropertyWriter writer, Object row) {
        try {
            return writer.get(row);
        } catch (Exception unreadable) {
            return null;
        }
    }

    private Object value(JsonParser parser, JsonToken token) {
        if (token == null) return null;
        return switch (token) {
            case VALUE_STRING -> parser.getString();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> ExportColumns.number(parser.getString());
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case START_OBJECT, START_ARRAY -> structure(parser);
            case VALUE_EMBEDDED_OBJECT -> String.valueOf(parser.getEmbeddedObject());
            default -> null;
        };
    }

    /** An object or a list, as its JSON text. */
    private String structure(JsonParser parser) {
        StringWriter text = new StringWriter();
        try (JsonGenerator generator = mapper.createGenerator(text)) {
            generator.copyCurrentStructure(parser);
        }
        return text.toString();
    }
}
