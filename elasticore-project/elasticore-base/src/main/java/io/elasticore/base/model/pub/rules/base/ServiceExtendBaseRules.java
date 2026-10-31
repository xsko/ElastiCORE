package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Base rules for the developer-owned service extension class.
 * Skeleton stays in service_extend.tmpl; rules provide the variables.
 */
public class ServiceExtendBaseRules implements CodeRules {

    @Rule(value = "service-extend-vars", target = "service_extend", order = 10)
    void vars(EntitySpec e) {
        SourceCode c = ctx(e);

        String searchResultDto = e.searchResultDtoName();
        // The DTOs 2.x chose for the service pair, when the runner found them
        // through @template / @searchable; the conventional names otherwise.
        String dtoName = e.config(io.elasticore.base.model.pub.rules.verify.RulesGenRunner.COMPAT_SERVICE_DTO);
        if (dtoName == null) dtoName = String.format("%sDTO", e.getName());
        String searchDtoName = e.config(io.elasticore.base.model.pub.rules.verify.RulesGenRunner.COMPAT_SERVICE_SRCH_DTO);
        if (searchDtoName == null) searchDtoName = e.searchDtoClassName();
        if (searchResultDto == null) searchResultDto = dtoName;

        c.var("packageName", e.pkg("service"))
                .var("entityPackageName", e.pkg("entity"))
                .var("dtoPackageName", e.pkg("dto"))
                .var("repositoryPackageName", e.pkg("repository"))
                .var("entityClassName", e.getName())
                .var("domainName", e.domainClassName())
                .var("dtoClassName", dtoName)
                .var("keyDtoClassName", "%sKeyDTO", e.getName())
                .var("searchDTOClassName", searchDtoName)
                .var("searchResultDTOClassName", searchResultDto)
                // The v3 skeleton carries an implements slot so a composed rule set
                // (the hexagonal one) can name an interface without owning the
                // template. Empty by default - a slot left unresolved would blank
                // the whole declaration line, and most projects compose nothing.
                .var("implementsClause", "")
                // What the extension is constructed with and hands to the core
                // service. The helper, unless a composed rule set gives the core
                // service ports instead (DddRepositoryPortRules) - the two
                // constructors have to agree, and this is where they are kept so.
                .var("extendImport", "%s.%sRepositoryHelper", e.pkg("repository"), e.domainClassName())
                .var(
                        "extendCtorParams",
                        c.syntax() == io.elasticore.base.model.pub.rules.Syntax.KOTLIN
                                ? String.format("helper: %sRepositoryHelper", e.domainClassName())
                                : String.format("%sRepositoryHelper helper", e.domainClassName()))
                .var("extendSuperArgs", "helper");
    }
}
