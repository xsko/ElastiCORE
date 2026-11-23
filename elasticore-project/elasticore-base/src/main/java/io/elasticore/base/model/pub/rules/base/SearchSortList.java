package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.verify.RuntimePackages;

import java.util.List;

/**
 * What a 3.0 search DTO lists for its sort: the properties the search's response shows, its key and
 * its default order (SpecFactory works them out, SortablePaths says the rule). The generated
 * SortableObject - or the runtime's io.elasticore.springboot3.dto.SortableObject, which the init
 * wizard's env.yml names as interface.sortable - refuses a sort on any other property (400, code
 * no-such-property) before the query is built, through the runtime's SortCheck, for sortCode and
 * sortColumn alike, and so for every caller of the search: the controller's search and export,
 * GraphQL, the application's own code.
 *
 * <p>A project that names a sort interface of its own keeps it: the search DTO cannot override what
 * that interface may not declare, so the sort is not checked, and the run says so.
 */
public final class SearchSortList {

    /** The comment above the list in the search DTO. */
    public static final String DOC =
            "What a sort may name: the values the search's response shows, its key and its default order.";

    /** The runtime's sort interface, which declares sortableProperties() as the generated one does. */
    public static final String RUNTIME_SORTABLE = "io.elasticore.springboot3.dto.SortableObject";

    private SearchSortList() {}

    /** The properties the search DTO lists, or null when it lists none (then any property sorts, as before). */
    public static List<String> listed(EntitySpec e) {
        List<String> sortable = e.sortableProperties();
        if (sortable == null) return null;
        String custom = e.sortableInterface();
        if (custom != null && !custom.trim().isEmpty() && !isRuntimeSortable(custom.trim())) {
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

    /** Whether the interface is the runtime's, by its name or the one runtimePackages renames it to. */
    static boolean isRuntimeSortable(String name) {
        if (RUNTIME_SORTABLE.equals(name)) return true;
        try {
            RuntimePackages packages = RuntimePackages.fromSystemProperties();
            return packages != null && name.equals(packages.rewrite(RUNTIME_SORTABLE));
        } catch (IllegalArgumentException ex) {
            // The run itself refuses the mapping before it gets here.
            return false;
        }
    }
}
