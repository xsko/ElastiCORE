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
        stamps(syntax, e, "entity", nl, create, update);
        c.var("stampCreate", create.toString()).var("stampUpdate", update.toString());
        c.var("auditorMembers", MyBatisSql.recordsAuditor(e) ? AUDITOR_MEMBERS : "");

        String repository = syntax.getter("helper", RuleText.uncapital(e.getName()));
        String key = syntax.getter("entity", pk.name());
        // A key and collections alone: no column to set, so no UPDATE - the row only has to be there.
        c.var("writeRow", MyBatisSql.hasColumnsToUpdate(e)
                ? String.format("if (%s.update(entity) == 0)\n            throw new OptimisticLockingFailureException(\"%s \" + %s"
                        + " + \" was changed by someone else; read it again\");", repository, e.getName(), key)
                : String.format("if (!%s.existsById(%s))\n            throw new EntityNotFoundException(\"%s \" + %s"
                        + " + \" is not there\");", repository, key, e.getName(), key));

        collectionVars(c, e, pk);
    }

    /** How many keys one IN list holds: Oracle takes 1000 at most, SQL Server 2100 parameters. */
    static final int KEYS_PER_READ = 500;

    /**
     * The collections the service reads with a row (loaded) and, for the
     * ones that hold the aggregate's own members, writes, replaces and
     * deletes with it - JPA's cascade and orphan removal, said out loud.
     */
    private void collectionVars(SourceCode c, EntitySpec e, FieldSpec pk) {
        Syntax syntax = c.syntax();
        String entity = e.getName();
        String keyType = syntax.nullableType(pk.typeName());
        String nl = "\n        ";
        java.util.List<FieldSpec> collections = MyBatisSql.collections(e);

        StringBuilder load = new StringBuilder();
        if (!collections.isEmpty()) {
            load.append(nl).append("if (rows.isEmpty()) return rows;")
                .append(nl).append(String.format("java.util.Map<%s, %s> byKey = new java.util.LinkedHashMap<>();", keyType, entity))
                .append(nl).append(String.format("for (%s row : rows) byKey.put(%s, row);", entity, syntax.getter("row", pk.name())))
                .append(nl).append(String.format("List<%s> keys = new ArrayList<>(byKey.keySet());", keyType));
            for (FieldSpec f : collections) {
                FieldSpec back = MyBatisSql.backField(e, f);
                EntitySpec target = e.related(f.coreItemType());
                String item = target.getName();
                String mapper = syntax.getter("helper", RuleText.uncapital(item));
                // Read through an id field (@joinBy), the member holds the root's key itself.
                FieldSpec backId = MyBatisSql.joinedBy(target, back);
                load.append(nl).append(String.format("for (%s row : rows) %s", entity,
                                syntax.statement(syntax.setter("row", f.name(), "new ArrayList<>()"))))
                    .append(nl).append(String.format("for (int from = 0; from < keys.size(); from += %d) {", KEYS_PER_READ))
                    .append(nl).append(String.format("    for (%s m : %s.findAllBy%sIn(keys.subList(from, Math.min(keys.size(), from + %d)))) {",
                            item, mapper, RuleText.capital(back.name()), KEYS_PER_READ))
                    .append(nl).append(String.format("        %s row = byKey.get(%s);", entity, backId != null
                            ? syntax.getter("m", backId.name())
                            : syntax.getter(syntax.getter("m", back.name()), pk.name())))
                    .append(nl).append("        if (row == null) continue;");
                if (backId == null) {
                    load.append(nl).append(String.format("        %s", syntax.statement(syntax.setter("m", back.name(), "row"))));
                }
                load.append(nl).append(String.format("        %s.add(m);", syntax.getter("row", f.name())))
                    .append(nl).append("    }")
                    .append(nl).append("}");
            }
        }
        c.var("loadBody", load.toString());

        // The aggregate's own members: written with it.
        StringBuilder before = new StringBuilder(), write = new StringBuilder(), delete = new StringBuilder();
        java.util.List<FieldSpec> members = new java.util.ArrayList<FieldSpec>();
        for (FieldSpec f : collections) {
            if (MyBatisSql.isMemberCollection(e, f)) members.add(f);
        }
        StringBuilder methods = new StringBuilder();
        if (!members.isEmpty()) {
            String n2 = "\n            ";
            String n3 = "\n                ";
            methods.append("\n\n    /**")
                   .append("\n     * The entity's own collections as a request found them, so writeMembers can")
                   .append("\n     * tell which of them the request replaced.")
                   .append("\n     */")
                   .append(String.format("\n    protected Object[] members(%s entity) {", entity))
                   .append("\n        return new Object[]{");
            for (int i = 0; i < members.size(); i++) {
                methods.append(i > 0 ? ", " : "").append(syntax.getter("entity", members.get(i).name()));
            }
            methods.append("};\n    }")
                   .append("\n\n    /**")
                   .append("\n     * Writes the members of the entity's aggregate, which have no service of")
                   .append("\n     * their own: every one when the entity is new (before is null), and a")
                   .append("\n     * collection the request replaced in place of the rows it had.")
                   .append("\n     */")
                   .append(String.format("\n    protected void writeMembers(%s entity, Object[] before) {", entity));
            for (int i = 0; i < members.size(); i++) {
                FieldSpec f = members.get(i);
                FieldSpec back = MyBatisSql.backField(e, f);
                EntitySpec target = e.related(f.coreItemType());
                String item = target.getName();
                String mapper = syntax.getter("helper", RuleText.uncapital(item));
                String list = syntax.getter("entity", f.name());
                methods.append(nl).append(String.format("if (before == null || %s != before[%d]) {", list, i))
                       .append(n2).append(String.format("if (before != null) %s.deleteAllBy%s(%s);", mapper,
                               RuleText.capital(back.name()), syntax.getter("entity", pk.name())))
                       .append(n2).append(String.format("if (%s != null) {", list))
                       .append(n2).append(String.format("    for (%s m : %s) {", item, list))
                       .append(n3).append(String.format("    %s", MyBatisSql.joinedBy(target, back) != null
                               ? syntax.statement(syntax.setter("m", MyBatisSql.joinedBy(target, back).name(),
                                       syntax.getter("entity", pk.name())))
                               : syntax.statement(syntax.setter("m", back.name(), "entity"))));
                FieldSpec mpk = target.pkField();
                if (MyBatisSql.generatedKey(mpk) && !mpk.hasDslAny("primitive", "prim")) {
                    methods.append(n3).append(String.format("    %s", syntax.statement(syntax.setter("m", mpk.name(), "null"))));
                }
                String genId = MyBatisSql.genIdExpression(mpk);
                if (genId != null) {
                    methods.append(n3).append(String.format("    if (%s == null) %s", syntax.getter("m", mpk.name()),
                            syntax.statement(syntax.setter("m", mpk.name(), genId))));
                }
                FieldSpec mVersion = MyBatisSql.version(target);
                if (mVersion != null && !mVersion.hasDslAny("primitive", "prim")) {
                    methods.append(n3).append(String.format("    %s", syntax.statement(syntax.setter("m", mVersion.name(), "null"))));
                }
                // A member is inserted afresh each time: what a create stamps.
                StringBuilder memberStamps = new StringBuilder();
                stamps(syntax, target, "m", n3 + "    ", memberStamps, new StringBuilder());
                methods.append(memberStamps);
                methods.append(n3).append(String.format("    %s.insert(m);", mapper))
                       .append(n2).append("    }")
                       .append(n2).append("}")
                       .append(nl).append("}");
            }
            methods.append("\n    }");

            before.append(nl).append("Object[] membersBefore = members(entity);");
            write.append(nl).append("writeMembers(entity, membersBefore);");
            for (FieldSpec f : members) {
                FieldSpec back = MyBatisSql.backField(e, f);
                String mapper = syntax.getter("helper", RuleText.uncapital(e.related(f.coreItemType()).getName()));
                delete.append(nl).append(String.format("%s.deleteAllBy%s(%s);", mapper, RuleText.capital(back.name()),
                        pk.name()));
            }
        }
        c.var("membersBefore", before.toString())
         .var("membersWrite", write.toString())
         .var("membersBeforeEach", before.toString().replace(nl, nl + "    "))
         .var("membersWriteEach", write.toString().replace(nl, nl + "    "))
         .var("membersCreate", members.isEmpty() ? "" : nl + "writeMembers(entity, null);")
         .var("membersDelete", delete.toString())
         .var("memberMethods", methods.toString());
    }

    /**
     * What a create and an update set of the auditing columns, as Spring
     * Data's auditing does for JPA: when the row was created and by whom
     * (kept once set), when it was last written and by whom.
     */
    public static void stamps(Syntax syntax, EntitySpec e, String var, String nl, StringBuilder create, StringBuilder update) {
        for (FieldSpec f : MyBatisSql.fields(e)) {
            if (!MyBatisSql.isValue(e, f)) continue;
            String value;
            if (MyBatisSql.createdDate(f) || MyBatisSql.lastModifiedDate(f)) {
                value = MyBatisSql.nowType(f);
            } else if (MyBatisSql.createdBy(f) || MyBatisSql.lastModifiedBy(f)) {
                value = String.format("(%s) currentAuditor()", syntax.nullableType(f.typeName()));
            } else {
                continue;
            }
            if (value == null) continue;
            String set = syntax.statement(syntax.setter(var, f.name(), value));
            if (MyBatisSql.createdDate(f) || MyBatisSql.createdBy(f)) {
                create.append(nl).append(String.format("if (%s == null) %s", syntax.getter(var, f.name()), set));
            } else {
                create.append(nl).append(set);
                update.append(nl).append(set);
            }
        }
    }

    /** Who writes, for @CreatedBy / @LastModifiedBy: the application's AuditorAware, as JPA auditing asks it. */
    static final String AUDITOR_MEMBERS = "\n\n"
            + "    /** Who is writing: the application's AuditorAware, when it has one. */\n"
            + "    private org.springframework.data.domain.AuditorAware<?> auditorAware;\n"
            + "\n"
            + "    @org.springframework.beans.factory.annotation.Autowired(required = false)\n"
            + "    protected void setAuditorAware(org.springframework.data.domain.AuditorAware<?> auditorAware) {\n"
            + "        this.auditorAware = auditorAware;\n"
            + "    }\n"
            + "\n"
            + "    /** The current auditor, or null without an AuditorAware or a signed-in one. */\n"
            + "    protected Object currentAuditor() {\n"
            + "        return auditorAware == null ? null : auditorAware.getCurrentAuditor().orElse(null);\n"
            + "    }";

    /** The service keeps the methods the controller keeps, as the JPA one does. */
    @Rule(value = "mybatis-service-method-filter", target = "service", order = 20)
    void methodFilter(EntitySpec e) {
        if (e.hasMeta("readonly")) {
            ctx(e).excludeMethod("save", "update", "batchUpdate", "delete", "deleteById",
                    "updateBySpecification", "write", "deleteRow", "members", "writeMembers");
        }
        for (String id : ControllerBaseRules.restExcluded(e, true)) {
            ctx(e).excludeMethod(id);
        }
    }
}
