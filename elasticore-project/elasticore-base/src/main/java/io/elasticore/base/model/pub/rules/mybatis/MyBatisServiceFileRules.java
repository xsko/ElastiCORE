package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.ControllerBaseRules;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.base.ServiceAccessRules;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The CoreService of a MyBatis domain (v3/mybatis_service.tmpl): the methods
 * of the JPA one, under the same names and signatures, so the controller and
 * the extension service are the JPA ones unchanged.
 *
 * What differs is underneath. A search hands the search DTO to the mapper,
 * whose XML holds the conditions, rather than building a Specification; a
 * page is a count and a page of rows; a write is an INSERT or an UPDATE of
 * the row, where JPA flushes the changes it tracked. The moments JPA's
 * callbacks and auditing cover - a generated id (@genid), a creation and a
 * modification time - are the service's to stamp.
 *
 * The access checks are ServiceAccessRules', which reads "persistence" to
 * narrow a search to the owner's rows through the mapper.
 */
public class MyBatisServiceFileRules implements CodeRules {

    @Rule(value = "mybatis-service-vars", target = "service", order = 10)
    void vars(EntitySpec e) {
        SourceCode c = ctx(e);
        c.template("mybatis_service");
        Syntax syntax = c.syntax();

        for (String slot : ServiceAccessRules.SLOTS) c.slot(slot);
        for (String inline : ServiceAccessRules.INLINE) c.var(inline, "");

        String searchResultDto = e.searchResultDtoName();
        boolean hasCustomResult = searchResultDto != null;
        if (searchResultDto == null) searchResultDto = String.format("%sDTO", e.getName());
        boolean isSkipNull = !"false".equals(e.metaValue("dynamicupdate"));

        String repository = syntax.getter("helper", RuleText.uncapital(e.getName()));
        FieldSpec pk = e.pkField();

        c.var("persistence", "mybatis")
         .var("packageName", e.pkg("service"))
         .var("entityPackageName", e.pkg("entity"))
         .var("dtoPackageName", e.pkg("dto"))
         .var("repositoryPackageName", e.pkg("repository"))
         .var("entityClassName", e.getName())
         .var("className", "%sCoreService", e.getName())
         .var("domainName", e.domainClassName())
         .var("mapperName", "%sMapper", e.domainClassName())
         .var("repositoryClassName", "%sRepository", e.getName())
         .var("dtoClassName", "%sDTO", e.getName())
         .var("keyDtoClassName", "%sKeyDTO", e.getName())
         .var("searchDTOClassName", e.searchDtoClassName())
         .var("searchResultDTOClassName", searchResultDto)
         .var("toSearchListMappingName", hasCustomResult ? "to" + searchResultDto : "toDTO")
         .var("isSkipNull", String.valueOf(isSkipNull))
         .var("cmdRef", repository)
         .var("qryRef", repository)
         .var("ownerArg", "null")
         .var("validateUpdate", isSkipNull
                 ? "BeanValidation.validatePresent(dto)" : "BeanValidation.validate(dto)");

        String type = syntax.nullableType(pk.typeName());
        c.var("pkDtoDefine", syntax.parameter(type, pk.name()))
         .var("pkDtoParamInfo", syntax.getter("dto", pk.name()))
         .var("pkDtoInfo", syntax.getter("dto", pk.name()))
         .var("pkDtoInfo2", pk.name())
         .var("pkOfEach", syntax.getter("each", pk.name()))
         .var("pkOfEntity", syntax.getter("entity", pk.name()));
        boolean primitive = pk.hasDslAny("primitive", "prim");
        String read = syntax.getter("dto", pk.name());
        c.var("pkPresent", primitive ? "true" : read + " != null")
         .var("pkPresentAny", primitive ? "(" + read + " != 0)" : read + " != null");

        writeVars(c, e, pk);
    }

    /** What a create and an update set on the row before it is written. */
    private void writeVars(SourceCode c, EntitySpec e, FieldSpec pk) {
        Syntax syntax = c.syntax();
        String nl = "\n        ";

        StringBuilder createId = new StringBuilder();
        if (MyBatisSql.generatedKey(pk) && !pk.hasDslAny("primitive", "prim")) {
            createId.append(nl).append(syntax.statement(syntax.setter("entity", pk.name(), "null")));
        }
        String genId = MyBatisSql.genIdExpression(pk);
        if (genId != null) {
            createId.append(nl).append(String.format("if (%s == null) %s", syntax.getter("entity", pk.name()),
                    syntax.statement(syntax.setter("entity", pk.name(), genId))));
        }
        c.var("createIdReset", createId.toString());

        FieldSpec version = MyBatisSql.version(e);
        if (version == null) {
            c.var("versionCheck", "").var("createVersionReset", "");
        } else {
            c.var("versionCheck", nl + syntax.statement(String.format(
                    "io.elasticore.springboot3.validation.VersionCheck.check(dto, %s, %s, %s.class, %s)",
                    RuleText.quote(version.name()), syntax.getter("entity", version.name()), e.getName(),
                    syntax.getter("dto", pk.name()))));
            c.var("createVersionReset", version.hasDslAny("primitive", "prim") ? ""
                    : nl + syntax.statement(syntax.setter("entity", version.name(), "null")));
        }

        StringBuilder create = new StringBuilder();
        StringBuilder update = new StringBuilder();
        for (FieldSpec f : e.fields()) {
            if (!MyBatisSql.isValue(e, f)) continue;
            String now = nowOf(f);
            if (now == null) continue;
            String set = syntax.statement(syntax.setter("entity", f.name(), now));
            if (MyBatisSql.createdDate(f)) {
                create.append(nl).append(String.format("if (%s == null) %s", syntax.getter("entity", f.name()), set));
            } else if (MyBatisSql.lastModifiedDate(f)) {
                create.append(nl).append(set);
                update.append(nl).append(set);
            }
        }
        c.var("stampCreate", create.toString()).var("stampUpdate", update.toString());
    }

    /** The current time in the field's type, for a creation or modification stamp. */
    private static String nowOf(FieldSpec f) {
        if (!MyBatisSql.createdDate(f) && !MyBatisSql.lastModifiedDate(f)) return null;
        return MyBatisSql.nowType(f);
    }

    /** The service keeps the methods the controller keeps, as the JPA one does. */
    @Rule(value = "mybatis-service-method-filter", target = "service", order = 20)
    void methodFilter(EntitySpec e) {
        if (e.hasMeta("readonly")) {
            ctx(e).excludeMethod("save", "update", "batchUpdate", "delete", "deleteById",
                    "updateBySpecification", "write");
        }
        for (String id : ControllerBaseRules.restExcluded(e, true)) {
            ctx(e).excludeMethod(id);
        }
    }
}
