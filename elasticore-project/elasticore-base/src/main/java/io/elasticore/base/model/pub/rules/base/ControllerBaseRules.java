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
         .var("searchDTOClassName", e.searchDtoClassName())
         .var("searchResultDTOClassName", searchResultDto)
         .var("searchReturnType", searchReturnType);

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
     *   meta: entity @expose @restExclude(deleteById,batchUpdate)
     *     - drops the listed endpoint methods
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
        return clean ? io.elasticore.base.model.pub.rules.SourceCode.withCallers(out) : out;
    }
}
