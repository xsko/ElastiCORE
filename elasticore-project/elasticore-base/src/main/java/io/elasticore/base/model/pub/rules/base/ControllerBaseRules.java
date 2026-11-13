package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Lines;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.SourceCode;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Variables of the REST controller artifact, ported from ControlSrcPublisher.
 * The class skeleton stays in the template; these rules only decide what goes
 * into its slots, plus which endpoint methods survive.
 *
 * JsonView variables are ALWAYS set - real values when the feature is on,
 * empty strings otherwise - because a slot left unset renders its whole line
 * blank, which is not what 2.x does.
 */
public class ControllerBaseRules implements CodeRules {

    @Rule(value = "controller-vars", target = "control", order = 10)
    void vars(EntitySpec e) {
        SourceCode c = ctx(e);

        // An @expose entity is served by the developer-owned extension class;
        // otherwise the core service is injected directly.
        String servicePostfix = e.hasMeta("expose") ? "Service" : "CoreService";

        // A label or description declared on the entity wins over its name.
        String entityLabel = e.metaValue("label");
        if (entityLabel == null) entityLabel = e.varName();
        String entityDesc = e.metaValue("description");
        if (entityDesc == null) entityDesc = e.metaValue("desc");
        if (entityDesc == null) entityDesc = entityLabel;

        // Paging is offered only when the search DTO declares a page size.
        // 2.x pages when the search DTO names a page size - which every
        // @expose entity's does - and a search DTO declared with its own
        // pageSize pages without @expose too (ControlSrcPublisher.isPageable).
        boolean paged = e.pageable() || (c.compatible() && e.searchPageable());
        String searchReturnType = paged ? "Page" : "List";

        // A dedicated search-result DTO takes precedence over the plain one.
        // The DTO 2.x chose through @template, when the runner found one.
        String dtoName = e.config(io.elasticore.base.model.pub.rules.verify.RulesGenRunner.COMPAT_SERVICE_DTO);
        if (dtoName == null) dtoName = String.format("%sDTO", e.getName());
        String searchResultDto = e.searchResultDtoName();
        if (searchResultDto == null) searchResultDto = dtoName;
        // And the search DTO 2.x chose for that pair: the one declared
        // @searchable of the entity (LedgerQuery), not the one the loader
        // derives from its @search fields (LedgerSrchDTO). The service took
        // it already; the controller searched with the other, and only a
        // template model declaring both showed it.
        String searchDtoName = e.config(io.elasticore.base.model.pub.rules.verify.RulesGenRunner.COMPAT_SERVICE_SRCH_DTO);
        if (searchDtoName == null) searchDtoName = e.searchDtoClassName();

        c.var("packageName", e.pkg("control"))
         .var("dtoPackageName", e.pkg("dto"))
         .var("servicePackageName", e.pkg("service"))
         .var("j2eePkgName", "jakarta")
         .var("entityClassName", e.getName())
         .var("className", "%sController", e.getName())
         .var("serviceClassName", "%s%s", e.getName(), servicePostfix)
         .var("mapperName", "%sMapper", e.domainClassName())
         .var("entityName", e.varName())
         .var("entityLabel", entityLabel)
         .var("entityDesc", entityDesc)
         .var("domainName", e.domainName())
         .var("apiPath", e.apiPath())
         .var("dtoClassName", dtoName)
         .var("keyDtoClassName", "%sKeyDTO", e.getName())
         .var("searchDTOClassName", searchDtoName)
         .var("searchResultDTOClassName", searchResultDto)
         .var("searchReturnType", searchReturnType);

        // The skeleton imports the DTOs it names one by one; a search-result
        // DTO is one more, and a model without one keeps its lines.
        c.slot("searchResultImport");
        if (!searchResultDto.equals(dtoName)) {
            c.varLine(
                    "searchResultImport", c.syntax().importLine(String.format("%s.%s", e.pkg("dto"), searchResultDto)));
        }

        primaryKeyVars(c, e);

        if (e.jsonView()) {
            // The import slot has a line to itself; the method slots sit
            // inline ahead of the mapping they precede, so each of those has
            // to close its own line.
            Syntax syntax = c.syntax();
            String readView = Lines.leading(String.format("    @JsonView(%s)",
                    syntax.classLiteral("Views.Read")));

            c.varLines("jsonViewImport", java.util.Arrays.asList(
                        "",
                        syntax.importLine("com.fasterxml.jackson.annotation.JsonView"),
                        syntax.importLine(String.format("%s.Views", e.pkg("dto")))))
             .var("createMethodJsonView", readView)
             .var("createParamJsonView", "@JsonView(%s) ", syntax.classLiteral("Views.Create"))
             .var("updateMethodJsonView", readView)
             .var("updateParamJsonView", "@JsonView(%s) ", syntax.classLiteral("Views.Update"))
             .var("readMethodJsonView", readView)
             .var("searchMethodJsonView", readView)
             .var("batchParamJsonView", "@JsonView(%s) ", syntax.classLiteral("Views.Update"));
        } else {
            c.var("createMethodJsonView", "")
             .var("readMethodJsonView", "")
             .var("searchMethodJsonView", "")
             .var("updateMethodJsonView", "")
             .var("createParamJsonView", "")
             .var("updateParamJsonView", "")
             .var("batchParamJsonView", "");
        }
    }

    /**
     * Path, parameter list and argument list of the by-id endpoints. Every
     * primary key field takes part, so a composite key produces
     * "/{a}/{b}" and a two-argument signature.
     */
    private void primaryKeyVars(SourceCode c, EntitySpec e) {
        StringBuilder path = new StringBuilder();
        StringBuilder params = new StringBuilder();
        StringBuilder args = new StringBuilder();

        String pkType = "";
        boolean isSinglePrimitivePk = false;
        String singlePrimitivePkType = "";
        int pkCount = 0;

        for (FieldSpec f : e.fields()) {
            if (!f.hasDsl("id") && !f.hasDsl("pk")) continue;
            pkCount++;

            if (path.length() > 0) {
                params.append(" ,");
                args.append(" ,");
            }
            path.append(String.format("/{%s}", f.name()));
            params.append(String.format("@PathVariable(%s) %s", RuleText.quote(f.name()),
                    c.syntax().parameter(f.typeName(), f.name())));
            args.append(f.name());

            if (pkType.isEmpty()) pkType = f.typeName();
        }

        if (pkCount == 1) {
            isSinglePrimitivePk = true;
            singlePrimitivePkType = pkType;
        } else if (pkCount > 1) {
            // A composite key is addressed through its generated identity class.
            pkType = String.format("%sIdentity", e.getName());
        }

        c.var("pkPathInfo", path.toString())
         .var("pkParamInfo", params.toString())
         .var("pkFieldInfo", args.toString())
         .var("pkType", pkType)
         .var("isSinglePrimitivePk", String.valueOf(isSinglePrimitivePk))
         .var("singlePrimitivePkType", singlePrimitivePkType);
    }

    /**
     * Endpoint selection per entity. The template stays the single source of
     * the method shapes; DSL meta annotations decide which methods survive:
     *
     *   meta: entity @expose @readonly
     *     - keeps only read/search/schemaInfo
     *   meta: entity @expose(20, methods="read,search")
     *     - serves only the methods listed (3.0 output; see exposeOmitted)
     *   meta: entity @expose @restExclude(deleteById,batchUpdate)
     *     - drops the listed endpoint methods (deprecated for methods)
     *
     * User rule sets may call excludeMethod/addMethod under any condition
     * of their own.
     */
    @Rule(value = "controller-method-filter", target = "control", order = 20)
    void methodFilter(EntitySpec e) {
        if (e.hasMeta("readonly")) {
            ctx(e).excludeMethod("create", "update", "batchUpdate", "deleteBatch", "deleteById");
        }
        for (String id : restExcluded(e, !ctx(e).compatible())) ctx(e).excludeMethod(id);
        if (!ctx(e).compatible()) {
            for (String id : exposeOmitted(e)) ctx(e).excludeMethod(id);
        }
    }

    /**
     * The inbound port promises only what the controller calls: a write the
     * entity does not serve is not on its use case either. The service keeps
     * every method - the domain may still call them - and implementing a port
     * that asks for less still compiles. Reads stay on the port: findById and
     * findBySearch are what the rest of the application reaches it for.
     * Before the port's surface (HexUseCaseRules, order 10).
     */
    @Rule(value = "expose-methods-port", target = "usecase", order = 5)
    void exposedPort(EntitySpec e) {
        if (ctx(e).compatible()) return;
        for (String id : exposeOmitted(e)) {
            if ("create".equals(id)) ctx(e).excludeMethod("save");
            else if ("update".equals(id)) ctx(e).excludeMethod("update");
            else if ("batchUpdate".equals(id)) ctx(e).excludeMethod("batchUpdate");
            else if ("deleteBatch".equals(id)) ctx(e).excludeMethod("delete");
            else if ("deleteById".equals(id)) ctx(e).excludeMethod("deleteById");
        }
    }

    /** The names @expose(methods="...") takes, and the controller methods each stands for. */
    private static final java.util.Map<String, String[]> EXPOSE_METHODS = exposeMethods();

    private static java.util.Map<String, String[]> exposeMethods() {
        java.util.Map<String, String[]> m = new java.util.LinkedHashMap<String, String[]>();
        m.put("create", new String[] {"create"});
        m.put("read", new String[] {"read"});
        m.put("search", new String[] {"search"});
        m.put("update", new String[] {"update", "batchUpdate"});
        m.put("delete", new String[] {"deleteBatch", "deleteById"});
        return java.util.Collections.unmodifiableMap(m);
    }

    /** The method names @expose(methods="...") accepts, in the order the guides list them. */
    public static java.util.Set<String> exposeMethodNames() {
        return EXPOSE_METHODS.keySet();
    }

    /** The methods @expose(methods="...") lists, as written, or null when it says none. Methods= is read too. */
    static String exposeMethodsText(EntitySpec e) {
        if (!e.hasMeta("expose")) return null;
        for (java.util.Map.Entry<String, String> en : e.metaProperties("expose").entrySet()) {
            if ("methods".equalsIgnoreCase(en.getKey())) return en.getValue();
        }
        return null;
    }

    private static final java.util.regex.Pattern NAME_SEPARATOR = java.util.regex.Pattern.compile("[,\\s]+");

    /** The names of a methods list, lower-cased: Read is read. */
    private static java.util.List<String> namesOf(String text) {
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (String name : NAME_SEPARATOR.split(text, -1)) {
            if (!name.isEmpty()) names.add(name.toLowerCase(java.util.Locale.ROOT));
        }
        return names;
    }

    /**
     * The controller section ids @expose(methods="...") leaves out: every
     * method of a name it does not list. Empty when it lists nothing - the
     * entity is served in full, as @expose alone has always done. A name it
     * does not know serves nothing (exposeProblems says so): a typo answers
     * with fewer endpoints, never with more.
     */
    public static java.util.Set<String> exposeOmitted(EntitySpec e) {
        java.util.Set<String> out = new java.util.LinkedHashSet<String>();
        String text = exposeMethodsText(e);
        if (text == null) return out;
        java.util.Set<String> kept = new java.util.HashSet<String>(namesOf(text));
        for (java.util.Map.Entry<String, String[]> en : EXPOSE_METHODS.entrySet()) {
            if (!kept.contains(en.getKey())) out.addAll(java.util.Arrays.asList(en.getValue()));
        }
        return out;
    }

    /**
     * What is wrong with an entity's endpoint selection, one sentence each,
     * for the run to print as DSL warnings: a name @expose(methods) does not
     * know, a list that names nothing, a parameter of @expose other than
     * methods, and @restExclude, which methods replaces.
     */
    public static java.util.List<String> exposeProblems(EntitySpec e) {
        if (!e.hasMeta("expose")) return java.util.Collections.emptyList();
        java.util.List<String> out = new java.util.ArrayList<String>();
        String names = String.join(", ", EXPOSE_METHODS.keySet());
        for (String key : e.metaProperties("expose").keySet()) {
            if (!"methods".equalsIgnoreCase(key) && !"pageSize".equalsIgnoreCase(key)) {
                out.add(String.format(
                        "[%s] @expose(%s=...): @expose takes pageSize=N and methods=\"...\"; %s is ignored.",
                        e.getName(), key, key));
            }
        }
        String text = exposeMethodsText(e);
        if (text != null) {
            boolean any = false;
            java.util.List<String> listed = namesOf(text);
            for (String name : listed) {
                if (EXPOSE_METHODS.containsKey(name)) {
                    any = true;
                } else {
                    out.add(String.format(
                            "[%s] @expose(methods=\"%s\"): %s is not one of %s; it serves nothing.",
                            e.getName(), text, name, names));
                }
            }
            if (!any) {
                out.add(String.format(
                        "[%s] @expose(methods=\"%s\") names none of %s: the controller serves" + " none of them.",
                        e.getName(), text, names));
            }
            if (e.hasMeta("readonly")) {
                for (String write : new String[] {"create", "update", "delete"}) {
                    if (listed.contains(write)) {
                        out.add(String.format(
                                "[%s] @expose(methods=\"%s\") lists %s, which @readonly takes away;"
                                        + " drop one of the two.",
                                e.getName(), text, write));
                    }
                }
            }
        }
        if (e.hasMeta("restexclude")) out.add(restExcludeDeprecation(e));
        return java.util.Collections.unmodifiableList(out);
    }

    /** What each controller section id answers, as the guides name it. */
    private static String endpointOf(String id) {
        switch (id) {
            case "create":
                return "POST /";
            case "read":
                return "GET /{id}";
            case "search":
                return "POST /search";
            case "update":
                return "PUT /";
            case "batchUpdate":
                return "PUT /batch";
            case "deleteBatch":
                return "POST /delete";
            case "deleteById":
                return "DELETE /{id}";
            default:
                return "GET /schema/info";
        }
    }

    /**
     * The deprecation of an entity's @restExclude, with the methods list that
     * serves the same endpoints - or, where none does (it takes PUT /batch and
     * keeps PUT /, or takes GET /schema/info, which every list keeps), the
     * nearest one and what it changes. The nearest serves fewer, never more.
     */
    public static String restExcludeDeprecation(EntitySpec e) {
        java.util.Set<String> gone = restExcluded(e, true);
        if (e.hasMeta("readonly")) {
            gone.addAll(java.util.Arrays.asList("create", "update", "batchUpdate", "deleteBatch", "deleteById"));
        }
        java.util.List<String> kept = new java.util.ArrayList<String>();
        java.util.List<String> alsoDropped = new java.util.ArrayList<String>();
        for (java.util.Map.Entry<String, String[]> en : EXPOSE_METHODS.entrySet()) {
            boolean all = true;
            for (String id : en.getValue()) all &= !gone.contains(id);
            if (all) {
                kept.add(en.getKey());
            } else {
                for (String id : en.getValue()) {
                    if (!gone.contains(id)) alsoDropped.add(endpointOf(id));
                }
            }
        }
        String list = String.join(",", kept);
        if (alsoDropped.isEmpty() && !gone.contains("schemaInfo")) {
            return String.format(
                    "[%s] @restExclude is deprecated: list what the controller serves instead,"
                            + " @expose(methods=\"%s\"). It still applies.",
                    e.getName(), list);
        }
        java.util.List<String> differs = new java.util.ArrayList<String>();
        if (!alsoDropped.isEmpty()) differs.add("it also drops " + String.join(", ", alsoDropped));
        if (gone.contains("schemaInfo")) differs.add("GET /schema/info stays");
        return String.format(
                "[%s] @restExclude is deprecated, and no @expose(methods) list serves the same"
                        + " endpoints: the nearest is @expose(methods=\"%s\"), but %s. It still applies.",
                e.getName(), list, String.join(" and ", differs));
    }

    /**
     * The method ids @restExclude names - section ids of the service
     * (save, update, batchUpdate, delete, deleteById, updateBySpecification)
     * and of the controller (create, read, search, update, batchUpdate,
     * deleteBatch, deleteById, schemaInfo). In the clean output a method
     * also goes with what it calls, so naming one never leaves a caller
     * that does not compile: update takes batchUpdate (it loops over
     * update), deleteById takes delete (the list delete calls it) and
     * deleteBatch, delete takes deleteBatch, save takes create. The 2.x
     * output reads the list as written.
     */
    public static java.util.Set<String> restExcluded(EntitySpec e, boolean clean) {
        java.util.Set<String> out = new java.util.LinkedHashSet<String>();
        String exclude = e.metaValue("restexclude");
        if (exclude != null && !exclude.trim().isEmpty()) {
            for (String id : exclude.split("[,\\s]+")) {
                if (!id.isEmpty()) out.add(id);
            }
        }
        // An abstract entity is never made as itself: its rows are its subclasses'.
        if (clean && e.isAbstractEntity()) out.add("save");
        return clean ? io.elasticore.base.model.pub.rules.SourceCode.withCallers(out) : out;
    }
}
