package io.elasticore.base.model.pub.rules.mongo;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.ControllerBaseRules;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.base.ServiceAccessRules;
import io.elasticore.base.model.pub.rules.base.SoftDelete;
import io.elasticore.base.model.pub.rules.mybatis.MyBatisServiceFileRules;
import io.elasticore.base.model.pub.rules.mybatis.MyBatisSql;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The CoreService of a MongoDB domain (v3/mongo_service.tmpl): the methods of
 * the JPA one, under the same names and signatures, so the controller, the
 * mapper, the DTOs and the access checks are the JPA ones unchanged.
 *
 * A search is a MongoTemplate query of the repository's Criteria (see
 * MongoRepositoryRules); a page is a count and a page of documents; a write
 * saves the whole document, the members it embeds with it. What JPA's
 * callbacks and auditing cover - a {@code @sequence} key, a {@code @genid},
 * the creation and modification times - the service does, as the MyBatis one
 * does; a {@code @sequence} key comes from the runtime's MongoSequences. A
 * {@code @deleted} entity's delete writes the time, and its reads leave such
 * documents out.
 */
public class MongoServiceFileRules implements CodeRules {

    @Rule(value = "mongo-service-vars", target = "service", order = 10)
    void vars(EntitySpec e) {
        SourceCode c = ctx(e);
        c.template("mongo_service");
        Syntax syntax = c.syntax();

        for (String slot : ServiceAccessRules.SLOTS) c.slot(slot);
        // Filled where the document is cleaned before it is written (an html field); empty otherwise.
        c.slot("deriveBeforeSave");
        for (String slot : io.elasticore.base.model.pub.rules.base.HtmlRules.SLOTS) c.slot(slot);
        for (String inline : ServiceAccessRules.INLINE) c.var(inline, "");

        String searchResultDto = e.searchResultDtoName();
        boolean hasCustomResult = searchResultDto != null;
        if (searchResultDto == null) searchResultDto = String.format("%sDTO", e.getName());
        boolean isSkipNull = !"false".equals(e.metaValue("dynamicupdate"));

        String repository = syntax.getter("helper", RuleText.uncapital(e.getName()));
        FieldSpec pk = e.pkField();

        c.var("persistence", "mongo")
                .var("notFoundException", "NotFoundException")
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
                .var("pkName", pk.name())
                .var(
                        "validateUpdate",
                        isSkipNull ? "BeanValidation.validatePresent(dto)" : "BeanValidation.validate(dto)");

        String type = syntax.nullableType(pk.typeName());
        boolean kotlin = syntax == Syntax.KOTLIN;
        // Kotlin: the key a caller hands in may be absent, which its type says;
        // the repository takes a value, so that is where it is asserted.
        c.var("pkDtoDefine", syntax.parameter(type, pk.name()))
                .var("pkDtoParamInfo", syntax.getter("dto", pk.name()))
                .var(
                        "pkDtoInfo",
                        kotlin ? syntax.notNull(syntax.getter("dto", pk.name())) : syntax.getter("dto", pk.name()))
                .var("pkDtoInfo2", kotlin ? syntax.notNull(pk.name()) : pk.name())
                .var("pkOfEach", syntax.getter("each", pk.name()))
                .var("pkOfEntity", syntax.getter("entity", pk.name()));
        boolean primitive = pk.hasDslAny("primitive", "prim");
        String read = syntax.getter("dto", pk.name());
        if (primitive && kotlin) {
            // No primitive declaration to lean on: a Long or an Int, compared with a zero of its own type.
            String zero = pk.typeName() != null
                            && pk.typeName().toLowerCase(java.util.Locale.ROOT).startsWith("long")
                    ? "0L"
                    : "0";
            c.var("pkPresent", read + " != null")
                    .var("pkPresentAny", "(" + read + " != null && " + read + " != " + zero + ")");
        } else {
            c.var("pkPresent", primitive ? "true" : read + " != null")
                    .var("pkPresentAny", primitive ? "(" + read + " != 0)" : read + " != null");
        }

        writeVars(c, e, pk);
        deleteVars(c, e, pk, repository);
    }

    /** What a create and an update set on the document before it is written. */
    private void writeVars(SourceCode c, EntitySpec e, FieldSpec pk) {
        Syntax syntax = c.syntax();
        String nl = "\n        ";

        StringBuilder createId = new StringBuilder();
        String next = MongoDocs.nextKey(pk, MongoDocs.collection(e), "mongo");
        if (next != null) {
            createId.append(nl).append(syntax.statement(syntax.setter("entity", pk.name(), next)));
        }
        String genId = MyBatisSql.genIdExpression(pk);
        if (genId != null) {
            createId.append(nl)
                    .append(String.format(
                            "if (%s == null) %s",
                            syntax.getter("entity", pk.name()),
                            syntax.statement(syntax.setter("entity", pk.name(), genId))));
        }
        c.var("createIdReset", createId.toString());
        c.var("maskKeep", io.elasticore.base.model.pub.rules.base.MaskedFields.keepStatements(syntax, e, "        "));

        FieldSpec version = MyBatisSql.version(e);
        if (version == null) {
            c.var("versionCheck", "").var("createVersionReset", "");
        } else {
            c.var(
                    "versionCheck",
                    nl
                            + syntax.statement(String.format(
                                    "io.elasticore.springboot3.validation.VersionCheck.check(dto, %s, %s, %s, %s)",
                                    RuleText.quote(version.name()),
                                    syntax.getter("entity", version.name()),
                                    syntax.javaClassLiteral(e.getName()),
                                    syntax.getter("dto", pk.name()))));
            c.var(
                    "createVersionReset",
                    version.hasDslAny("primitive", "prim")
                            ? ""
                            : nl + syntax.statement(syntax.setter("entity", version.name(), "null")));
        }

        StringBuilder create = new StringBuilder();
        StringBuilder update = new StringBuilder();
        MyBatisServiceFileRules.stamps(syntax, e, "entity", nl, create, update);
        c.var("stampCreate", create.toString()).var("stampUpdate", update.toString());

        // The members the document embeds: a key and the times, as their own documents would get them.
        StringBuilder members = new StringBuilder();
        boolean auditor = MyBatisSql.recordsAuditor(e);
        for (FieldSpec f : MongoDocs.memberLists(e)) {
            EntitySpec target = e.related(f.coreItemType());
            if (target == null) continue;
            String n2 = nl + "    ";
            StringBuilder each = new StringBuilder();
            FieldSpec mpk = target.pkField();
            String mNext = mpk == null ? null : MongoDocs.nextKey(mpk, MongoDocs.collection(target), "mongo");
            if (mNext != null) {
                each.append(n2)
                        .append(String.format(
                                "if (%s == null) %s",
                                syntax.getter("m", mpk.name()),
                                syntax.statement(syntax.setter("m", mpk.name(), mNext))));
            }
            String mGen = MyBatisSql.genIdExpression(mpk);
            if (mGen != null) {
                each.append(n2)
                        .append(String.format(
                                "if (%s == null) %s",
                                syntax.getter("m", mpk.name()),
                                syntax.statement(syntax.setter("m", mpk.name(), mGen))));
            }
            StringBuilder mCreate = new StringBuilder();
            MyBatisServiceFileRules.stamps(syntax, target, "m", n2, mCreate, new StringBuilder());
            each.append(mCreate);
            for (FieldSpec mf : MyBatisSql.fields(target)) {
                if (MyBatisSql.isValue(target, mf) && (MyBatisSql.createdBy(mf) || MyBatisSql.lastModifiedBy(mf))) {
                    auditor = true;
                }
            }
            if (each.length() == 0) continue;
            String list = syntax.getter("entity", f.name());
            String member = target.pkg("entity") + "." + target.getName();
            members.append(nl)
                    .append(
                            syntax == Syntax.KOTLIN
                                    ? String.format("for (m in %s ?: emptyList<%s>()) {", list, member)
                                    : String.format("if (%s != null) for (%s m : %s) {", list, member, list))
                    .append(each)
                    .append(nl)
                    .append("}");
        }
        c.var("membersPrepare", members.toString());
        c.var(
                "auditorMembers",
                !auditor
                        ? ""
                        : syntax == Syntax.KOTLIN
                                ? MyBatisServiceFileRules.AUDITOR_MEMBERS_KT
                                : MyBatisServiceFileRules.AUDITOR_MEMBERS);
    }

    /**
     * A delete: the document removed, or - with @deleted - the time it was deleted written.
     * ServiceAccessRules.me() reads liveFilter too: a deleted document is no one's session.
     */
    private void deleteVars(SourceCode c, EntitySpec e, FieldSpec pk, String repository) {
        Syntax syntax = c.syntax();
        if (syntax == Syntax.KOTLIN) {
            String id = syntax.notNull(pk.name());
            if (SoftDelete.applies(e)) {
                FieldSpec deleted = SoftDelete.field(e);
                String live = String.format(".filter { e -> %s == null }", syntax.getter("e", deleted.name()));
                c.var("liveFilter", live)
                        .var("deleteNote", ": the time it was deleted is written, and reads leave it out (@deleted)")
                        .var(
                                "deleteBody",
                                String.format(
                                        "%s.findById(%s)%s.ifPresent { e ->\n"
                                                + "            %s\n"
                                                + "            %s.save(e)\n"
                                                + "        }",
                                        repository,
                                        id,
                                        live,
                                        syntax.setter("e", deleted.name(), MyBatisSql.nowType(deleted)),
                                        repository))
                        .var("notThere", String.format("!%s.findById(%s)%s.isPresent", repository, id, live));
            } else {
                c.var("liveFilter", "")
                        .var("deleteNote", "")
                        .var("deleteBody", String.format("%s.deleteById(%s)", repository, id))
                        .var("notThere", String.format("!%s.existsById(%s)", repository, id));
            }
            return;
        }
        if (SoftDelete.applies(e)) {
            FieldSpec deleted = SoftDelete.field(e);
            String live = String.format(".filter(e -> %s == null)", syntax.getter("e", deleted.name()));
            c.var("liveFilter", live)
                    .var("deleteNote", ": the time it was deleted is written, and reads leave it out (@deleted)")
                    .var(
                            "deleteBody",
                            String.format(
                                    "%s.findById(%s)%s.ifPresent(e -> {\n"
                                            + "            %s\n"
                                            + "            %s.save(e);\n"
                                            + "        });",
                                    repository,
                                    pk.name(),
                                    live,
                                    syntax.statement(syntax.setter("e", deleted.name(), MyBatisSql.nowType(deleted))),
                                    repository))
                    .var("notThere", String.format("!%s.findById(%s)%s.isPresent()", repository, pk.name(), live));
        } else {
            c.var("liveFilter", "")
                    .var("deleteNote", "")
                    .var("deleteBody", String.format("%s.deleteById(%s);", repository, pk.name()))
                    .var("notThere", String.format("!%s.existsById(%s)", repository, pk.name()));
        }
    }

    /** The service keeps the methods the controller keeps, as the JPA one does. */
    @Rule(value = "mongo-service-method-filter", target = "service", order = 20)
    void methodFilter(EntitySpec e) {
        if (e.hasMeta("readonly")) {
            ctx(e).excludeMethod(
                            "save",
                            "update",
                            "batchUpdate",
                            "delete",
                            "deleteById",
                            "updateBySpecification",
                            "write",
                            "deleteRow",
                            "prepareMembers");
        }
        for (String id : ControllerBaseRules.restExcluded(e, true)) {
            ctx(e).excludeMethod(id);
        }
    }
}
