package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.core.Items;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.repo.Method;

import java.util.ArrayList;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Base rules for the repository artifact, ported from RepositoryFilePublisher.
 * The class skeleton stays in repository.tmpl exactly as in 2.x; rules only
 * provide the variables.
 *
 * The importList and classAnnotationList slots are deliberately left unset,
 * as 2.x leaves them - their lines render blank in generated output.
 */
public class RepositoryBaseRules implements CodeRules {

    @Rule(value = "repo-vars", target = "repository", order = 10)
    void vars(EntitySpec e) {
        // Composite keys (@IdClass) use the generated <Entity>Identity class.
        String identity = e.pkCount() > 1
                ? String.format("%sIdentity", e.getName())
                : ctx(e).syntax().typeName(e.pkField().typeName());

        ctx(e).var("packageName", e.pkg("repository"))
              .var("entityPackageName", e.pkg("entity"))
              .var("targetModel", e.getName())
              .var("identity", identity)
              .var("className", "%sRepository", e.getName());
    }

    /**
     * The declared query methods, plus the DTO and enum imports they may
     * reference. 2.x adds those two imports only when the repository actually
     * declares methods, so a plain repository keeps a minimal import list.
     */
    @Rule(value = "repo-methods", target = "repository", order = 20)
    void methods(EntitySpec e) {
        SourceCode c = ctx(e);
        methodLines(c, e);
        if (!c.hasVarLines("methodList")) return;

        if (e.hasDtoModels()) ctx(e).var("dtoPackageName", e.pkg("dto"));
        if (e.pkg("enumeration") != null) ctx(e).var("enumPackageName", e.pkg("enumeration"));
    }

    /**
     * With {@code @deleted}, the reads Spring Data writes itself leave a deleted row out,
     * redeclared with a query (Spring Data runs a redeclared CRUD method's
     * {@code @Query}). The searches leave it out through the mapper's toSpec; the
     * delete is Hibernate's @SQLDelete on the entity. Nothing filters what
     * names such a row - an order line still reads the product it names.
     */
    @Rule(value = "repo-soft-delete", target = "repository", order = 21)
    void softDelete(EntitySpec e) {
        SourceCode c = ctx(e);
        if (c.compatible() || !SoftDelete.applies(e)) return;
        boolean kt = c.syntax() == Syntax.KOTLIN;
        String entity = e.getName();
        String live = "e." + SoftDelete.field(e).name() + " IS NULL";
        String byId = String.format(
                "FROM %s e WHERE e.%s = :id AND %s", entity, e.pkField().name(), live);
        String all = String.format("FROM %s e WHERE %s", entity, live);
        String id = c.syntax().typeName(e.pkField().typeName());
        c.varLine("methodList", "/** @deleted: a deleted row is not found by its key. */");
        query(
                c,
                kt,
                "SELECT e " + byId,
                kt
                        ? "override fun findById(@Param(\"id\") id: " + id + "): java.util.Optional<" + entity + ">"
                        : "java.util.Optional<" + entity + "> findById(@Param(\"id\") " + id + " id)");
        query(
                c,
                kt,
                "SELECT COUNT(e) > 0 " + byId,
                kt
                        ? "override fun existsById(@Param(\"id\") id: " + id + "): Boolean"
                        : "boolean existsById(@Param(\"id\") " + id + " id)");
        c.varLine("methodList", "/** @deleted: every row but the deleted ones. */");
        query(
                c,
                kt,
                "SELECT e " + all,
                kt ? "override fun findAll(): List<" + entity + ">" : "java.util.List<" + entity + "> findAll()");
        query(
                c,
                kt,
                "SELECT e " + all,
                kt
                        ? "override fun findAll(sort: org.springframework.data.domain.Sort): List<" + entity + ">"
                        : "java.util.List<" + entity + "> findAll(org.springframework.data.domain.Sort sort)");
        query(
                c,
                kt,
                "SELECT e " + all,
                kt
                        ? "override fun findAll(pageable: Pageable): Page<" + entity + ">"
                        : "Page<" + entity + "> findAll(Pageable pageable)");
        query(c, kt, "SELECT COUNT(e) " + all, kt ? "override fun count(): Long" : "long count()");
    }

    private static void query(SourceCode c, boolean kt, String jpql, String signature) {
        if (!kt) c.varLine("methodList", "@Override");
        c.varLine("methodList", "@Query(%s)", RuleText.quote(jpql))
                .varLine("methodList", "%s%s", signature, kt ? "" : ";")
                .varLine("methodList", "");
    }

    /**
     * One entry per emitted line, mirroring getMethodInfo: a query that writes
     * is marked @Modifying and transactional, a declared query becomes a
     * @Query whose parameters are named, and every method is followed by a
     * blank separator entry.
     */
    private void methodLines(SourceCode c, EntitySpec e) {
        for (Method method : e.queryMethods()) {
            if (!method.isEnable()) continue;

            boolean isSelect = method.getQueryInfo() == null || method.getQueryInfo().isSelectQuery();
            if (!isSelect) {
                c.varLine("methodList", "@org.springframework.data.jpa.repository.Modifying");
            }

            boolean namedParams = false;
            if (method.getQuery() != null && method.isNeedQueryAnnotation()) {
                // A conditional query is assembled at runtime, not declared here.
                if (method.getQuery().indexOf("/* if:") > 0) continue;

                if (!isSelect) {
                    c.varLine("methodList", "@jakarta.transaction.Transactional");
                }
                String query = io.elasticore.base.util.StringUtils
                        .splitByDoubleQuotation(method.getQuery());
                c.varLine("methodList", "@Query(nativeQuery=%s, value=%s)", method.isNative(), query);
                namedParams = true;
            }

            // A signature, so the language says how it is written: Java puts
            // the return type first and closes with a semicolon, Kotlin names
            // the function and puts the return type last.
            Syntax syntax = c.syntax();
            c.varLine("methodList", "%s", syntax.statement(syntax.methodSignature(
                            "", returnType(e, method),
                            method.getName(),
                            parameters(syntax, method, namedParams))))
             .varLine("methodList", "");
        }

        // A query a test declared with EntitySpec.query: written the way the
        // loaded ones are, so a rule's effect on it can be seen in the file.
        Syntax syntax = c.syntax();
        for (io.elasticore.base.model.pub.rules.QuerySpec q : e.declaredQueries()) {
            if (!q.isSelect()) {
                c.varLine("methodList", "@org.springframework.data.jpa.repository.Modifying");
            }
            if (q.sql() != null) {
                if (!q.isSelect()) c.varLine("methodList", "@jakarta.transaction.Transactional");
                c.varLine("methodList", "@Query(nativeQuery=%s, value=%s)", q.isNative(), RuleText.quote(q.sql()));
            }
            c.varLine("methodList", "%s", syntax.statement(syntax.methodSignature(
                            "", q.returnType(), q.name(), "")))
             .varLine("methodList", "");
        }
    }

    /**
     * A query that projects onto a DTO cannot be typed as that DTO: JPA hands
     * back raw tuples, so 2.x declares the row shape instead.
     */
    private String returnType(EntitySpec e, io.elasticore.base.model.repo.Method method) {
        boolean compatible = ctx(e).compatible();
        String declared = method.getReturnType();
        if (!compatible) declared = pageableOrList(e, method, declared);
        return projectionType(e, declared, compatible);
    }

    /**
     * A page of a projection is only what Spring Data can count: a JPQL
     * query without GROUP BY, from which it derives the count query. For a
     * native or grouping one it would count wrongly or refuse to start, so
     * the rows come back as a list - still paged by the Pageable when one is
     * passed - and the DSL is told why.
     */
    public static String pageableOrList(EntitySpec e, io.elasticore.base.model.repo.Method m, String declared) {
        if (declared == null || !declared.startsWith("Page<")) return declared;
        String item = typeArgument(declared);
        if (item == null || !e.isKnownDto(item.trim())) return declared;
        String query = m.getQuery();
        boolean grouped = query != null && query.toLowerCase(java.util.Locale.ROOT).matches("(?s).*\\bgroup\\s+by\\b.*");
        if (!m.isNative() && !grouped) return declared;
        String key = e.getName() + "." + m.getName();
        if (WARNED_PAGES.add(key)) {
            io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                    "[%s] repository %s.%s: a %s projection cannot be counted for a Page; it is returned as"
                            + " List<%s>. Page it yourself or drop %s.",
                    e.domainClassName(), e.getName(), m.getName(), m.isNative() ? "native" : "grouped",
                    item.trim(), m.isNative() ? "nativeQuery" : "GROUP BY"));
        }
        return "List<" + item.trim() + ">";
    }

    private static final java.util.Set<String> WARNED_PAGES =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /**
     * What the repository returns for a declared type. A DTO projection is
     * raw rows: Object[] as 2.x declares it, or - in v3 and Kotlin - named
     * Tuples, which TupleMapper turns into the DTO by column alias, so the
     * layer above can be typed with the DTO the DSL named.
     */
    /**
     * The type argument of a one-argument generic - List, Page, Optional -
     * or null for a plain type. StringUtils.findParameterType reads List only,
     * which is what 2.x asks of it and must go on answering.
     */
    public static String typeArgument(String type) {
        if (type == null) return null;
        String t = type.trim();
        int open = t.indexOf('<');
        if (open < 0 || !t.endsWith(">")) return null;
        return t.substring(open + 1, t.length() - 1).trim();
    }

    public static String projectionType(EntitySpec e, String declared, boolean compatible) {
        String param = compatible ? io.elasticore.base.util.StringUtils.findParameterType(declared)
                : typeArgument(declared);
        String item = param != null ? param : declared;
        if (item == null || !e.isKnownDto(item.trim())) return declared;
        if (compatible) return param != null ? "List<Object[]>" : declared;
        return param != null ? declared.replace(param, "jakarta.persistence.Tuple") : "jakarta.persistence.Tuple";
    }

    /**
     * Parameter list of one method. A declared query binds its parameters by
     * name, and a pageable JPQL query takes the Pageable as a trailing one.
     * The separator is " ," as 2.x writes it.
     */
    private String parameters(Syntax syntax, Method method, boolean namedParams) {
        StringBuilder sb = new StringBuilder();
        Items<Field> params = method.getParams();
        if (params != null) {
            for (Field f : params.getItemList()) {
                if (sb.length() > 0) sb.append(" ,");
                if (namedParams) {
                    sb.append(String.format("@Param(%s) ", RuleText.quote(f.getName())));
                }
                sb.append(syntax.parameter(f.getTypeInfo().getDefaultTypeName(), f.getName()));
            }
        }
        if (!method.isNative() && method.isPageable()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(" ").append(syntax.parameter("Pageable", "pageable"));
        }
        return sb.toString();
    }
}
