package io.elasticore.base.model.pub.rules.lang.java;

import io.elasticore.base.model.entity.EntityAnnotation;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.RuleText;

import java.util.ArrayList;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The CoreService of the 2.x-compatible output, rendered through the 2.x
 * skeleton (service.tmpl) with the variables ServiceSrcPublisher.publish
 * gives it.
 *
 * This is the 2.x file. {@link JpaServiceFileRules} writes the v3 one, a
 * different artifact from a different skeleton, and the two are never
 * selected together.
 *
 * Ported as 2.x computes it: the three output shapes (list, page, and the
 * one an @expose or @service entity gets), the key expressions, a rolled-up
 * entity queried through its target, and the JOIN FETCH lines for relations
 * marked @fetchjoin or, with table.autoJoin, every relation to an entity.
 * The custom projection branches 2.x keeps in the skeleton are switched off
 * there too, so they are switched off here.
 */
public class JpaServiceCompatFileRules implements CodeRules {

    @Rule(value = "service-compat-file", target = "service", order = 90, phase = Rule.Phase.TAIL)
    void file(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntax(Syntax.JAVA, "service-compat-file");

        String orgEntityClassName = e.getName();
        // A rolled-up entity is stored in its target's table and read through
        // the target's repository, then narrowed back to its own type.
        String rollupTarget = e.metaValue("rollupTarget");
        String entityClassName = rollupTarget == null || rollupTarget.isEmpty()
                ? orgEntityClassName : rollupTarget;
        String mappingCode = "";
        String orgEntityConvert = "";
        if (!orgEntityClassName.equals(entityClassName)) {
            orgEntityConvert = String.format("(%s)", orgEntityClassName);
            String varName = RuleText.uncapital(entityClassName);
            mappingCode = String.format(".filter(%s -> %s instanceof %s).map(%s -> (%s) %s)",
                    varName, varName, orgEntityClassName, varName, orgEntityClassName, varName);
        }

        String dtoClassName = dtoName(e, io.elasticore.base.model.pub.rules.verify.RulesGenRunner.COMPAT_SERVICE_DTO, "%sDTO");

        String searchResultDto = e.searchResultDtoName();
        String toSearchListMappingName = "toDTO";
        if (searchResultDto != null) {
            toSearchListMappingName = String.format("to%s", searchResultDto);
        } else {
            searchResultDto = dtoClassName;
        }

        // Paged when the search DTO names a page size; an entity that is
        // @expose or @service gets the shape of its own instead.
        boolean isPageOutput = e.searchPageable();
        boolean isListOutput = !isPageOutput;
        boolean isExposeOutput = e.hasMeta("expose") || e.hasMeta("service");
        if (isExposeOutput) {
            isPageOutput = false;
            isListOutput = false;
        }

        boolean isSkipNull = !"false".equals(e.metaValue("dynamicupdate"));

        c.var("packageName", e.pkg("service"))
                .var("entityPackageName", e.pkg("entity"))
                .var("dtoPackageName", e.pkg("dto"))
                .var("repositoryPackageName", e.pkg("repository"))
                .var("j2eePkgName", "jakarta".equals(e.config("j2ee")) ? "jakarta" : "javax")
                .var("className", "%sCoreService", orgEntityClassName)
                .var("domainName", e.domainClassName())
                .var("entityClassName", entityClassName)
                .var("orgEntityClassName", orgEntityClassName)
                .var("orgEntityConvert", orgEntityConvert)
                .var("keyDtoClassName", "%sKeyDTO", orgEntityClassName)
                .var("dtoClassName", dtoClassName)
                .var("mappingCode", mappingCode)
                .var("mapperName", "%sMapper", e.domainClassName())
                .var("searchDTOClassName", searchDtoName(e))
                .var("isListOutput", String.valueOf(isListOutput))
                .var("isPageOutput", String.valueOf(isPageOutput))
                .var("isExposeOutput", String.valueOf(isExposeOutput))
                .var("isCustomPageOutput", "false")
                .var("isCustomListOutput", "false")
                .var("isSkipNull", String.valueOf(isSkipNull))
                // Spring Data JPA 4 (Spring Boot 4) has no delete(Specification): a delete by search
                // reads the rows and removes them. Spring Boot 3 keeps 2.x's bulk delete, byte for byte.
                .var(
                        "rowByRowDelete",
                        String.valueOf(io.elasticore.base.model.pub.rules.RulesConfig.springBootMajor() >= 4))
                .var("searchResultDTOClassName", searchResultDto)
                .var("selectColumnNmList", "")
                .var("toSearchListMappingName", toSearchListMappingName);

        keyVars(c, e);
        joinFetchVars(c, e);
    }

    /**
     * The key expressions, from every key field the entity has - inherited
     * ones included - as ServiceSrcPublisher's sb/sb2/sb3 build them. A
     * composite key is wrapped in its Identity class, which is imported.
     */
    private void keyVars(SourceCode c, EntitySpec e) {
        StringBuilder fromDto = new StringBuilder();
        StringBuilder fromArgs = new StringBuilder();
        StringBuilder define = new StringBuilder();
        int count = 0;
        for (FieldSpec f : e.allFields()) {
            if (!isKey(f)) continue;
            if (count > 0) {
                fromDto.append(" ,");
                fromArgs.append(" ,");
                define.append(" ,");
            }
            count++;
            fromDto.append("dto.get").append(f.capitalName()).append("()");
            fromArgs.append(f.name());
            define.append(typeName(f)).append(" ").append(f.name());
        }

        String pkDtoInfo = fromDto.toString();
        String pkDtoInfo2 = fromArgs.toString();
        if (count > 1) {
            String identity = String.format("%sIdentity", e.getName());
            pkDtoInfo = String.format("new %s(%s)", identity, fromDto);
            pkDtoInfo2 = String.format("new %s(%s)", identity, fromArgs);
            c.varLine("importList", "%s.%s", e.pkg("entity"), identity);
        }

        c.var("pkDtoParamInfo", fromDto.toString())
         .var("pkDtoInfo", pkDtoInfo)
         .var("pkDtoInfo2", pkDtoInfo2)
         .var("pkDtoDefine", define.toString());
    }

    private void joinFetchVars(SourceCode c, EntitySpec e) {
        List<String> fetches = new ArrayList<String>();
        for (FieldSpec f : fetchedRelations(e)) {
            fetches.add(String.format("root.fetch(%s, JoinType.LEFT);", RuleText.quote(f.name())));
        }
        c.var("needJoinProcess", String.valueOf(!fetches.isEmpty()))
         .var("allRelationshipFetches", String.join("\n                ", fetches));
    }

    /**
     * The relations fetched with the root: the entity's own fields whose
     * type is an entity of the domain, when table.autoJoin is on or the
     * field is marked @fetchjoin. The v3 service, which fetches none of
     * them, names the same ones in its warning.
     */
    static List<FieldSpec> fetchedRelations(EntitySpec e) {
        boolean autoJoin = autoJoin(e);
        List<FieldSpec> fetched = new ArrayList<FieldSpec>();
        for (FieldSpec f : e.fields()) {
            Field raw = f.raw();
            String itemType = raw != null ? raw.getTypeInfo().getCoreItemType() : f.coreItemType();
            if (itemType == null || !e.isKnownEntity(itemType)) continue;
            boolean fetchJoin = raw != null
                    ? raw.hasAnnotation(EntityAnnotation.FETCH_JOIN)
                    : f.hasDsl("fetchjoin");
            if (autoJoin || fetchJoin) fetched.add(f);
        }
        return fetched;
    }

    static boolean autoJoin(EntitySpec e) {
        return "true".equals(e.config("table.autoJoin"));
    }

    private static boolean isKey(FieldSpec f) {
        Field raw = f.raw();
        return raw != null ? raw.isPrimaryKey() : f.hasDsl("id") || f.hasDsl("pk");
    }

    private static String typeName(FieldSpec f) {
        Field raw = f.raw();
        return raw != null ? raw.getTypeInfo().getDefaultTypeName() : f.typeName();
    }

    /**
     * The DTO 2.x chose for the service: the one declared @template or
     * @searchable of the entity, as the runner found it, or the entity's
     * conventional name when the rules run without the runner.
     */
    private static String dtoName(EntitySpec e, String key, String fallback) {
        String chosen = e.config(key);
        return chosen != null ? chosen : String.format(fallback, e.getName());
    }

    private static String searchDtoName(EntitySpec e) {
        String chosen = e.config(io.elasticore.base.model.pub.rules.verify.RulesGenRunner.COMPAT_SERVICE_SRCH_DTO);
        return chosen != null ? chosen : e.searchDtoClassName();
    }
}
