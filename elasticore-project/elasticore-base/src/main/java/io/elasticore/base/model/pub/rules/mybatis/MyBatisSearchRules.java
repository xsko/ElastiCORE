package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The conditions of a search DTO, for the MyBatis mapper's WHERE clause.
 *
 * Read from the same search DTO the JPA mapper's toSpec reads, the same way
 * (MapperSpecRules): which fields search, by what, under which property. One
 * line each in the "search" block, as tab-separated words - the operator, the
 * entity path, the search DTO property and the value's type - which
 * MyBatisMapperRules turns into SQL once it knows the entity's columns.
 *
 * Operators: eq, ne, gt, ge, lt, le; like (contains), ilike (contains,
 * ignoring case), ieq (equal ignoring case); prefix, iprefix, suffix,
 * isuffix; in; between (a From/To pair); isnull (an xEmpty yes or no); auto
 * (like when the value carries a %, equal otherwise).
 */
public class MyBatisSearchRules implements CodeRules {

    public static final String TARGET = "mybatisSearch";
    public static final String BLOCK = "search";

    @Rule(value = "mybatis-search-field", target = TARGET, order = 10)
    void field(EntitySpec e, FieldSpec f) {
        if (!f.hasDsl("search") && !f.hasDslAny("searchable", "s", "srch", "filter")) return;
        if (SearchDtoBaseRules.flattensAReferenceById(e, f)) return;
        SourceCode c = ctx(e);
        // A @formula is JPA's SELECT expression, not a column of the table:
        // MyBatis cannot search by it. Left out of the conditions, it also
        // does not count as one, so an update by condition naming only it is
        // refused rather than run on every row.
        if (f.hasDsl("formula")) {
            SourceCode.dslWarnOnce(String.format("[%s] %s.%s: a @formula field is not searched under mode: mybatis"
                    + " - it is not a column of the table.", e.domainClassName(), e.getName(), f.name()));
            return;
        }

        String raw = f.dslValueAny("searchable", "search", "s", "srch", "filter");
        String condition = "auto";
        String prop = f.name();
        if (raw != null) {
            String[] items = raw.split(",");
            condition = items[0].trim();
            if (items.length > 1 && !items[1].trim().isEmpty()) prop = items[1].trim();
            if (condition.isEmpty()) condition = "auto";
        }
        condition = SearchDtoBaseRules.effectiveCondition(c, f, condition);

        String path = f.name();
        String ref = f.dslValue("ref");
        if (ref != null && !ref.isEmpty()) path = ref;

        String op = operator(condition);
        if (op == null) {
            // MapperSpecRules says why in its own warning; the search is by equality.
            op = "eq";
        }
        // On a single value the search DTO carries one value too, and JPA's
        // in() over it is an equality - so is this.
        if ("in".equals(op) && !f.isList()) op = "eq";
        if (f.isList()) {
            if (!"in".equals(op)) {
                SourceCode.dslWarnOnce(String.format("[%s] %s.%s: a search on one element of a collection is not"
                        + " written for MyBatis; the condition is left out.", e.domainClassName(), e.getName(), f.name()));
                return;
            }
        }
        String type = f.isList() ? f.coreItemType() : f.typeName();
        c.line(BLOCK, "%s\t%s\t%s\t%s", op, path, prop, type);
    }

    /** The condition as one of the operators MyBatisMapperRules writes, or null for an unknown one. */
    static String operator(String condition) {
        if (condition == null || condition.isEmpty() || "auto".equals(condition)) return "auto";
        if (SearchDtoBaseRules.isNullCheck(condition)) return "isnull";
        switch (condition) {
            case "eq": case "=": return "eq";
            case "!=": return "ne";
            case ">": return "gt";
            case ">=": return "ge";
            case "<": return "lt";
            case "<=": return "le";
            case "like": case "%_%": case "%%": return "like";
            case "%__%": return "ilike";
            case "__=": case "=__": return "ieq";
            case "in": return "in";
            case "between": case "~": return "between";
            default: break;
        }
        if (condition.length() > 1 && condition.startsWith("%")) return condition.endsWith("__") ? "isuffix" : "suffix";
        if (condition.length() > 1 && condition.endsWith("%")) return condition.startsWith("__") ? "iprefix" : "prefix";
        return null;
    }
}
