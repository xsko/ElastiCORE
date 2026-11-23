package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.SourceCode;

import java.util.List;

/**
 * What a 3.0 search DTO lists for its sort: the properties the search's response shows, its key and
 * its default order (SpecFactory works them out, SortablePaths says the rule). The generated
 * SortableObject refuses a sort on any other property - 400, code no-such-property - before the query
 * is built, through the runtime's SortCheck, for sortCode and sortColumn alike, and so for every
 * caller of the search: the controller's search and export, GraphQL, the application's own code.
 *
 * <p>A project that names its own sort interface (env.yml interface.sortable) keeps it: the search
 * DTO cannot override what that interface may not declare, so the sort is not checked, and the run
 * says so.
 */
public final class SearchSortList {

    /** The comment above the list in the search DTO. */
    public static final String DOC =
            "What a sort may name: the values the search's response shows, its key and its default order.";

    private SearchSortList() {}

    /** The properties the search DTO lists, or null when it lists none (then any property sorts, as before). */
    public static List<String> listed(EntitySpec e) {
        List<String> sortable = e.sortableProperties();
        if (sortable == null) return null;
        String custom = e.sortableInterface();
        if (custom != null && !custom.trim().isEmpty()) {
            SourceCode.dslWarnOnce(String.format(
                    "[%s] %s: env.yml interface.sortable names the project's own sort interface (%s), so its sort is"
                            + " not kept to what the search's response shows - a sort on a value the response leaves"
                            + " out (a password, a @writeonly field) gives that value's order away. Check each"
                            + " property in its getSort() with io.elasticore.springboot3.dto.SortCheck.sortable(...).",
                    e.domainClassName(), e.searchDtoClassName(), custom.trim()));
            return null;
        }
        return sortable;
    }
}
