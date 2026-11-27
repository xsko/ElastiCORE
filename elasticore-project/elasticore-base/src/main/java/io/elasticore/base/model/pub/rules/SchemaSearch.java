package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules.SearchProperty;
import io.elasticore.base.model.pub.rules.mybatis.MyBatisSearchRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The search part of GET /schema/info from the model (env.yml {@code config: schemaInfo: model}, issue 373): what
 * POST .../search takes, as the generated search applies it - the page size, what a sort may name, and each
 * property of the search DTO, named as the search DTO names it (xFrom/xTo, an alias, xEmpty, xItem, words), with
 * the entity field it searches and its operator in the DSL's words.
 *
 * The properties are the search DTO's own decision (SearchDtoBaseRules.properties), recorded as it was written.
 * JPA's mapper (toSpec) applies that decision as it is. Under MyBatis and MongoDB the query is written from the
 * conditions MyBatisSearchRules reads off the same search DTO, as the mapper XML and the repository resolve them:
 * a property without such a condition searches by nothing there, and says op null - {@code @search(words: ...)},
 * one element of a list, a large value searched by anything but text (MyBatis), a {@code @formula}. The build says
 * each of those already (DSL_WARN).
 */
public final class SchemaSearch {

    private SchemaSearch() {}

    /** A search DTO as it was written: its properties, its page size and what its sort may name. */
    public static final class Dto {
        final List<SearchProperty> properties;
        final Integer pageSize;
        final List<String> sortable;

        /**
         * @param properties the properties it declares, in order (SearchDtoBaseRules.properties)
         * @param pageSize   the page a request gets when it names none; null when the DTO names none
         * @param sortable   what its sort may name (SearchSortList.listed); null when its sort is not checked
         */
        public Dto(List<SearchProperty> properties, Integer pageSize, List<String> sortable) {
            this.properties = properties;
            this.pageSize = pageSize;
            this.sortable = sortable;
        }
    }

    /**
     * What a MyBatis or MongoDB query applies: its conditions - each the operator (in MyBatisSearchRules' words, as
     * the query applies it), the entity path, the search DTO property and its type - and what its sort may name.
     */
    public static final class Query {
        final List<String[]> conditions;
        final Collection<String> sortable;

        public Query(List<String[]> conditions, Collection<String> sortable) {
            this.conditions = conditions;
            this.sortable = sortable;
        }
    }

    /**
     * The search part: {@code {pageSize, sortable, fields}}.
     *
     * @param paged whether the search answers a page (the controller's Page): without one there is no page size
     * @param query what a MyBatis or MongoDB query applies; null for JPA, whose mapper applies the DTO's decision
     */
    public static Map<String, Object> of(Dto dto, boolean paged, Query query) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("pageSize", paged ? dto.pageSize : null);
        out.put("sortable", sortable(dto.sortable, query == null ? null : query.sortable));
        Map<String, String[]> applied = query == null ? null : byProperty(query.conditions);
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        for (SearchProperty p : dto.properties) fields.put(p.name(), property(p, applied));
        out.put("fields", fields);
        return out;
    }

    /**
     * What a sort may name: what the search DTO lists, and of it - under MyBatis or MongoDB - only what the query
     * can sort by; null when nothing checks it (a project's own sort interface), then any property is tried.
     */
    static List<String> sortable(List<String> listed, Collection<String> query) {
        if (query == null) return listed == null ? null : new ArrayList<String>(listed);
        if (listed == null) return new ArrayList<String>(query);
        List<String> out = new ArrayList<String>();
        for (String s : listed) {
            if (query.contains(s)) out.add(s);
        }
        return out;
    }

    /** The conditions by the search DTO property each reads: a range by its two ends, isnull by its xEmpty. */
    static Map<String, String[]> byProperty(List<String[]> conditions) {
        Map<String, String[]> out = new HashMap<String, String[]>();
        for (String[] cond : conditions) {
            if ("between".equals(cond[0])) {
                out.put(cond[2] + "From", cond);
                out.put(cond[2] + "To", cond);
            } else if ("isnull".equals(cond[0])) {
                out.put(cond[2] + "Empty", cond);
            } else {
                out.put(cond[2], cond);
            }
        }
        return out;
    }

    /** One property: {@code {field, op, end?, in?}}. applied null: JPA. */
    static Map<String, Object> property(SearchProperty p, Map<String, String[]> applied) {
        Object field;
        String op;
        if (p.kind() == SearchProperty.Kind.WORDS) {
            // The fields each word is looked for in; only JPA's mapper looks (Words).
            field = new ArrayList<String>(p.words());
            op = applied == null ? "words" : null;
        } else if (applied == null) {
            field = declaredField(p);
            op = jpaOperator(p);
        } else {
            String[] cond = applied.get(p.name());
            op = cond == null ? null : word(cond[0], p);
            // A blind index is the column compared, not the field searched: the field it indexes.
            field = cond == null || cond[0].startsWith("bidx") ? queriedField(p) : cond[1];
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("field", field);
        out.put("op", op);
        if (p.end() != null) out.put("end", p.end());
        if (takesAList(p)) out.put("in", true);
        return out;
    }

    /**
     * The entity field a property searches, as JPA's mapper reads it: the path of its @ref, else the name its
     * condition gives it ({@code @search(like,comName)}), else its own.
     */
    static String declaredField(SearchProperty p) {
        String ref = p.field().dslValue("ref");
        if (ref != null && !ref.trim().isEmpty()) return ref.trim();
        if (p.alias() != null && !p.alias().isEmpty()) return p.alias();
        return p.field().name();
    }

    /**
     * The entity field a MyBatis or MongoDB query compares for a property, as MyBatisSearchRules names it: the path
     * of its @ref, else its own name - the name a condition gives (@search(eq,alias)) names the property only.
     */
    static String queriedField(SearchProperty p) {
        String ref = p.field().dslValue("ref");
        if (ref != null && !ref.trim().isEmpty()) return ref.trim();
        return p.field().name();
    }

    /**
     * The operator JPA's mapper applies for a property, in the DSL's words: its condition (null for a plain
     * property), an unknown one an equality, a lone % the end of the value - as MapperSpecRules writes them.
     */
    static String jpaOperator(SearchProperty p) {
        String condition = p.op();
        if (condition == null) return null;
        String op = "%".equals(condition) ? "suffix" : MyBatisSearchRules.operator(condition);
        return word(op == null ? "eq" : op, p);
    }

    /**
     * An operator of MyBatisSearchRules' as the DSL says it: eq, ne, gt, gte, lt, lte, like (contains ignoring
     * case - the 3.0 like on text), %_% (contains), __= (equal ignoring case), _% and __% (starts with), %_ and %__
     * (ends with), in (only for a list: one value is an equality), between, isnull, auto. A blind index compares by
     * equality.
     */
    static String word(String op, SearchProperty p) {
        switch (op) {
            case "ge":
                return "gte";
            case "le":
                return "lte";
            case "like":
                return "%_%";
            case "ilike":
                return "like";
            case "ieq":
                return "__=";
            case "prefix":
                return "_%";
            case "iprefix":
                return "__%";
            case "suffix":
                return "%_";
            case "isuffix":
                return "%__";
            case "bidx":
            case "bidxdigits":
                return "eq";
            case "in":
                return takesAList(p) ? "in" : "eq";
            default:
                return op;
        }
    }

    /** Whether the property takes a list of values: a list the search DTO declares as one. */
    static boolean takesAList(SearchProperty p) {
        return p.kind() == SearchProperty.Kind.VALUE && p.field().isList();
    }
}
