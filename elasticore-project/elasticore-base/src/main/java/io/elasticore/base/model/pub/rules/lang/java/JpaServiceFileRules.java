package io.elasticore.base.model.pub.rules.lang.java;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.SourceCode;

import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * CoreService rendering for the 3.0 clean mode (v3/service.tmpl).
 *
 * The v3 service skeleton keeps every method shape in the template, and
 * MethodSections recognizes each method (javadoc section + signature name)
 * so endpoint selection stays consistent across artifacts: an entity marked
 * @readonly loses its write methods in the CONTROLLER and in the SERVICE,
 * leaving no dead service code behind.
 *
 * Method ids equal the method names; overloads share one id:
 *   findAll, findById, findBySearch, listBySearch, countBySearch,
 *   save, update, batchUpdate, delete, deleteById, updateBySpecification
 *
 * This is the isExposeOutput shape of 2.x's ServiceSrcPublisher - the only
 * one that applies here, since RulesGenRunner only emits a v3 CoreService
 * for an entity that is @expose or @service to begin with. 2.x's plain
 * isListOutput/isPageOutput branches exist for entities that get a service
 * without either meta, which v3 never does.
 *
 * Not ported: the JOIN FETCH auto-optimization (withJoinFetches(), the
 * needJoinProcess/allRelationshipFetches machinery) and the custom
 * cb.construct(...) projection overloads (isCustomListOutput/
 * isCustomPageOutput). Both are query-shape optimizations layered on top of
 * the same public method surface, not API a caller depends on - deferred
 * rather than silently dropped.
 */
public class JpaServiceFileRules implements CodeRules {

    @Rule(value = "v3-service-vars", target = "service", order = 10)
    void vars(EntitySpec e) {
        SourceCode c = ctx(e);

        // The access and password slots: empty unless the DSL asks for them
        // (ServiceAccessRules), and an empty paragraph takes its line with it.
        for (String slot : io.elasticore.base.model.pub.rules.base.ServiceAccessRules.SLOTS) c.slot(slot);
        c.slot("deriveBeforeSave");
        for (String inline : io.elasticore.base.model.pub.rules.base.ServiceAccessRules.INLINE) c.var(inline, "");

        // A dedicated search-result DTO takes precedence over the plain one,
        // the same fallback ControllerBaseRules and ServiceTestBaseRules use.
        String searchResultDto = e.searchResultDtoName();
        boolean hasCustomResult = searchResultDto != null;
        if (searchResultDto == null) searchResultDto = String.format("%sDTO", e.getName());
        String toSearchListMappingName = hasCustomResult
                ? String.format("to%s", searchResultDto) : "toDTO";

        // dynamicupdate defaults on; only an explicit @dynamicupdate(false)
        // turns off skip-null merging in the mapper's update path.
        boolean isSkipNull = !"false".equals(e.metaValue("dynamicupdate"));

        c.var("packageName", e.pkg("service"))
         .var("entityPackageName", e.pkg("entity"))
         .var("dtoPackageName", e.pkg("dto"))
         .var("repositoryPackageName", e.pkg("repository"))
         .var("j2eePkgName", "jakarta")
         .var("entityClassName", e.getName())
         .var("className", "%sCoreService", e.getName())
         .var("domainName", e.domainClassName())
         .var("mapperName", "%sMapper", e.domainClassName())
         .var("dtoClassName", "%sDTO", e.getName())
         .var("keyDtoClassName", "%sKeyDTO", e.getName())
         .var("searchDTOClassName", e.searchDtoClassName())
         .var("searchResultDTOClassName", searchResultDto)
         .var("toSearchListMappingName", toSearchListMappingName)
         .var("isSkipNull", String.valueOf(isSkipNull))
         // The helper holds its repositories as fields with generated
         // accessors in Java and as properties in Kotlin, so how the service
         // reaches one is the language's business.
         .var("repositoryRef", c.syntax().getter("helper",
                 io.elasticore.base.model.pub.rules.base.RuleText.uncapital(e.getName())));

        String pkFromDto = pkVars(c, e);
        dependencyVars(c, e);
        writeGuardVars(c, e, isSkipNull, pkFromDto);
    }

    /**
     * What the service is handed, and how its methods reach it.
     *
     * The default is the domain's repository helper, as it always was: one
     * Spring Data repository answers both the aggregate's own life (find one,
     * save, delete) and the searches. The names the methods use - cmdRef for
     * the first, qryRef for the second, emRef for the EntityManager the
     * mapper resolves references with - are separate so a composed rule set
     * can hand the service ports instead without owning the template; the
     * hexagonal one does (DddRepositoryPortRules).
     */
    private void dependencyVars(SourceCode c, EntitySpec e) {
        Syntax syntax = c.syntax();
        boolean kotlin = syntax == Syntax.KOTLIN;
        String helper = String.format("%sRepositoryHelper", e.domainClassName());
        String repository = syntax.getter("helper",
                io.elasticore.base.model.pub.rules.base.RuleText.uncapital(e.getName()));

        c.varLine("dependencyImportList", kotlin ? "import %s.*" : "import %s.*;", e.pkg("repository"));
        if (kotlin) {
            c.var("ctorParams", "protected val helper: %s", helper);
        } else {
            c.varLine("dependencyList", "protected final %s helper;", helper)
             .varLine("dependencyList", "")
             .varLine("dependencyList", "public %sCoreService(%s helper) {", e.getName(), helper)
             .varLine("dependencyList", "    this.helper = helper;")
             .varLine("dependencyList", "}");
        }
        c.var("cmdRef", repository)
         .var("qryRef", repository)
         .var("emRef", syntax.getter("helper", "entityManager"))
         // What a saved row is passed through before the reply is made from
         // it: nothing, unless a rule set needs the row read back.
         .var("readBackOpen", "")
         .var("readBackClose", "");
    }

    /**
     * Whether a field is declared as a primitive - lowercase in Java, or
     * marked @primitive, which is how a Kotlin model asks for a non-null one.
     */
    private static boolean primitive(FieldSpec f) {
        if (f.hasDslAny("primitive", "prim")) return true;
        String type = f.typeName();
        return type != null && !type.isEmpty() && Character.isLowerCase(type.charAt(0));
    }

    private static boolean numeric(String typeName) {
        String t = io.elasticore.base.model.pub.rules.base.RuleText.simpleName(typeName).toLowerCase();
        return t.equals("long") || t.equals("integer") || t.equals("int") || t.equals("short");
    }

    /**
     * A key the database generates is the database's on a create.
     *
     * A client that sends one for a row that does not exist would otherwise
     * reach persist() with the key set, which JPA refuses as a detached
     * entity. An existing one is refused before this (DuplicateKeyException).
     */
    private void idResetVar(SourceCode c, EntitySpec e) {
        StringBuilder reset = new StringBuilder();
        for (FieldSpec pk : e.pkFields()) {
            String gen = pk.generationType();
            boolean generated = (gen != null && !gen.isEmpty()) || pk.hasDsl("sequence") || pk.hasDsl("identity");
            if (!generated) continue;
            if (primitive(pk)) continue;
            reset.append("\n        ").append(c.syntax().statement(c.syntax().setter("entity", pk.name(), "null")));
        }
        c.var("createIdReset", reset.toString());
    }

    /**
     * The checks a write makes before it touches a row.
     *
     * Validation: a create is checked whole, as the controller's @Valid would;
     * an update only for what it sends, because a skip-null merge leaves
     * every absent field as it was (BeanValidation.validatePresent).
     *
     * The optimistic lock: when the entity declares a version, the version
     * the request carries is compared with the row's before the merge. The
     * DTO's version is read by name at run time, so a DTO declared without
     * one still compiles and is simply not checked.
     *
     * A create never carries a version into the row: with it null, Spring
     * Data persists rather than merges, so a create cannot update.
     */
    private void writeGuardVars(SourceCode c, EntitySpec e, boolean isSkipNull, String pk) {
        Syntax syntax = c.syntax();
        boolean kotlin = syntax == Syntax.KOTLIN;

        c.var("validateUpdate", isSkipNull
                ? "BeanValidation.validatePresent(dto)" : "BeanValidation.validate(dto)");

        FieldSpec version = null;
        for (FieldSpec f : e.allFields()) {
            if (f.isVersion()) { version = f; break; }
        }
        idResetVar(c, e);
        // A counter, not a timestamp: a date version loses its fraction of a
        // second in the DTO's JSON format and would never compare equal.
        if (version != null && !numeric(version.typeName())) version = null;
        if (version == null) {
            c.var("versionCheck", "").var("createVersionReset", "");
            return;
        }

        String check = String.format("io.elasticore.springboot3.validation.VersionCheck.check(dto, %s, %s, %s, %s)",
                io.elasticore.base.model.pub.rules.base.RuleText.quote(version.name()),
                syntax.getter("entity", version.name()),
                syntax == Syntax.KOTLIN ? e.getName() + "::class.java" : e.getName() + ".class", pk);
        // Appended to the line before it, so an entity without a version gets
        // no empty line where the check would have been.
        String nl = "\n";
        c.var("versionCheck", nl + "        " + syntax.statement(check));

        // A primitive version cannot be cleared; it starts at zero anyway.
        c.var("createVersionReset", primitive(version) ? ""
                : nl + "        " + syntax.statement(syntax.setter("entity", version.name(), "null")));
    }

    /**
     * The by-id method parameter list and the identifier expressions built
     * from it, ported from ServiceSrcPublisher's sb/sb2/sb3. A composite key
     * (more than one @id/@pk field) is passed as separate parameters and
     * wrapped into the generated &lt;Entity&gt;Identity only where a single
     * id argument is actually needed - the JpaRepository calls.
     *
     *   pkDtoDefine    "Type1 field1 ,Type2 field2"   - findById/deleteById's own parameter list
     *   pkDtoParamInfo "dto.getField1() ,dto.getField2()" - calling deleteById(...) from a KeyDTO
     *   pkDtoInfo      identifier built from a DTO instance (update/batchUpdate)
     *   pkDtoInfo2     identifier built from the raw parameters (inside findById/deleteById)
     */
    private String pkVars(SourceCode c, EntitySpec e) {
        Syntax syntax = c.syntax();
        List<FieldSpec> pks = e.pkFields();
        boolean composite = pks.size() > 1;

        StringBuilder define = new StringBuilder();
        StringBuilder fromDto = new StringBuilder();
        StringBuilder fromArgs = new StringBuilder();
        // The identifier a caller hands in may be absent - the DTO it came
        // from declares it so - which is what the parameter says. The lookup
        // it is then handed to takes a value, so that is where it is asserted.
        StringBuilder idFromDto = new StringBuilder();
        StringBuilder idFromArgs = new StringBuilder();
        for (int i = 0; i < pks.size(); i++) {
            FieldSpec f = pks.get(i);
            if (i > 0) {
                define.append(" ,");
                fromDto.append(" ,");
                fromArgs.append(" ,");
                idFromDto.append(" ,");
                idFromArgs.append(" ,");
            }
            define.append(syntax.parameter(syntax.nullableType(f.typeName()), f.name()));
            fromDto.append(syntax.getter("dto", f.name()));
            fromArgs.append(f.name());
            idFromDto.append(syntax.notNull(syntax.getter("dto", f.name())));
            idFromArgs.append(syntax.notNull(f.name()));
        }

        String pkDtoInfo;
        String pkDtoInfo2;
        if (composite) {
            String identityType = String.format("%sIdentity", e.getName());
            pkDtoInfo = syntax.newInstance(identityType, fromDto.toString());
            pkDtoInfo2 = syntax.newInstance(identityType, fromArgs.toString());
        } else {
            pkDtoInfo = idFromDto.toString();
            pkDtoInfo2 = idFromArgs.toString();
        }

        StringBuilder present = new StringBuilder();
        StringBuilder presentAny = new StringBuilder();
        for (int i = 0; i < pks.size(); i++) {
            FieldSpec pk = pks.get(i);
            // A @primitive key is a long in the DTO and cannot be null: it is
            // always "present", and "set" means other than its zero.
            boolean primitive = pk.hasDslAny("primitive", "prim");
            String read = syntax.getter("dto", pk.name());
            if (i > 0) { present.append(" && "); presentAny.append(" || "); }
            if (primitive && syntax == Syntax.KOTLIN) {
                // Kotlin has no primitive declaration to lean on: the DTO field
                // is a Long or an Int, possibly nullable, so it keeps its null
                // check and compares with a zero of its own type.
                String zero = pk.typeName() != null && pk.typeName().toLowerCase().startsWith("long") ? "0L" : "0";
                present.append(read + " != null");
                presentAny.append("(" + read + " != null && " + read + " != " + zero + ")");
            } else {
                present.append(primitive ? "true" : read + " != null");
                presentAny.append(primitive ? "(" + read + " != 0)" : read + " != null");
            }
        }
        c.var("pkPresent", present.toString())
         .var("pkPresentAny", presentAny.toString());

        c.var("pkDtoDefine", define.toString())
         .var("pkDtoParamInfo", fromDto.toString())
         .var("pkDtoInfo", pkDtoInfo)
         .var("pkDtoInfo2", pkDtoInfo2);
        return pkDtoInfo;
    }

    /**
     * Mirror of the controller endpoint selection for the service layer:
     * a @readonly entity keeps only query methods; @restExclude ids that
     * name service methods drop those too.
     */
    @Rule(value = "v3-service-method-filter", target = "service", order = 20)
    void methodFilter(EntitySpec e) {
        if (e.hasMeta("readonly")) {
            ctx(e).excludeMethod("save", "update", "batchUpdate", "delete", "deleteById",
                    "updateBySpecification");
        }
        // With update goes batchUpdate, which calls it (restExcluded).
        for (String id : io.elasticore.base.model.pub.rules.base.ControllerBaseRules.restExcluded(e, true)) {
            ctx(e).excludeMethod(id);
        }
    }
}
