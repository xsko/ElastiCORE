package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * {@code @onDelete(cascade | restrict | setNull)}: what becomes of the rows of other entities that point to a
 * row when it is deleted (issue 180).
 *
 * <p>Written on the side that points ({@code post: Post @onDelete(cascade)} on Comment) and carried out by the
 * CoreService of the side that is deleted, before each delete it makes, in the same transaction: every
 * restrict first - a 409 {@code restricted} naming what still points, rather than the foreign key's - then
 * setNull, then cascade. A cascaded row is removed through JPA, so its own soft delete and delete guards
 * apply, after what its own {@code @onDelete} references ask: a comment's replies go with it.
 *
 * <p>The work is a static method per entity on the cascade's way, in the deleted root's own CoreService, so no
 * service is handed another: {@code onDeletePost(em, row)}, {@code onDeleteComment(em, row)}. JPA (Java,
 * Kotlin) only; MyBatis and the 2.x-compatible output stop the build rather than delete without it.
 */
public class OnDeleteRules implements CodeRules {

    @Rule(value = "service-on-delete", target = "service", order = 90, phase = Rule.Phase.TAIL)
    void onDelete(EntitySpec e) {
        if (e.onDeleteOf(e.getName()).isEmpty()) return;
        SourceCode c = ctx(e);
        if (c.compatible() || "mybatis".equals(c.varValue("persistence"))) {
            ModelCheck.fail(String.format(
                    "[%s] @onDelete on references to %s is not carried out %s yet - keep the"
                            + " domain on JPA (output v3), or delete the rows that point to it yourself",
                    e.domainClassName(),
                    e.getName(),
                    c.compatible() ? "in the 2.x-compatible output" : "under mode: mybatis"));
            return;
        }
        boolean kt = c.syntax() == Syntax.KOTLIN;
        String em = orDefault(c.varValue("emRef"), "entityManager");
        String repo = orDefault(c.varValue("cmdRef"), "repository");
        String query = orDefault(c.varValue("qryRef"), repo);
        String id = orDefault(
                c.varValue("pkDtoInfo2"),
                e.pkField() == null ? "id" : e.pkField().name());
        String mapper = orDefault(c.varValue("mapperName"), e.domainClassName() + "Mapper");
        String open = orDefault(c.varValue("accessSpecOpen"), "");
        String close = orDefault(c.varValue("accessSpecClose"), "");
        String method = "onDelete" + e.getName();

        // The two ways a row goes: by its key, and by a search.
        c.varLine(
                "accessDelete",
                kt
                        ? String.format("%s.findById(%s).ifPresent { %s(%s, it) }", repo, id, method, em)
                        : String.format("%s.findById(%s).ifPresent(row -> %s(%s, row));", repo, id, method, em));
        c.varLine(
                "accessDeleteBySearch",
                kt
                        ? String.format(
                                "for (row in %s.findAll(%s%s.toSpec(searchDTO)%s)) %s(%s, row)",
                                query, open, mapper, close, method, em)
                        : String.format(
                                "for (Object row : %s.findAll(%s%s.toSpec(searchDTO)%s)) %s(%s, row);",
                                query, open, mapper, close, method, em));

        // The entities on the cascade's way, each once: a comment's replies are comments.
        Set<String> visit = new LinkedHashSet<String>();
        List<String> todo = new ArrayList<String>();
        todo.add(e.getName());
        while (!todo.isEmpty()) {
            String next = todo.remove(0);
            if (!visit.add(next)) continue;
            for (EntitySpec.OnDeleteRef ref : e.onDeleteOf(next)) {
                if ("cascade".equals(ref.mode)) todo.add(ref.entity);
            }
        }
        List<String> lines = new ArrayList<String>();
        if (kt) lines.add("companion object {");
        String in = kt ? "    " : "";
        for (String entity : visit) {
            List<EntitySpec.OnDeleteRef> refs = e.onDeleteOf(entity);
            // A cascaded entity nothing points to with @onDelete is removed as it is: no method of its own.
            if (refs.isEmpty()) continue;
            String key = refs.get(0).targetKey;
            lines.add("");
            lines.add(in + "/**");
            lines.add(in + " * What the rows that point to a " + entity
                    + " ask when it is deleted (@onDelete), before it");
            lines.add(in + " * is: restrict refuses, setNull clears, cascade deletes them - in this transaction.");
            lines.add(in + " */");
            if (kt) {
                lines.add(in + "@JvmStatic");
                lines.add(in
                        + String.format(
                                "fun onDelete%s(em: %s.persistence.EntityManager, row: Any) {", entity, j2ee(c)));
                lines.add(in + String.format("    val id: Any? = (row as %s).%s", entity, key));
            } else {
                lines.add(String.format("public static void onDelete%s(EntityManager em, Object row) {", entity));
                lines.add(String.format(
                        "    Object id = ((%s) row).get%s();",
                        entity, Character.toUpperCase(key.charAt(0)) + key.substring(1)));
            }
            for (EntitySpec.OnDeleteRef ref : refs) {
                if (!"restrict".equals(ref.mode)) continue;
                String exists = String.format("\"select 1 from %s x where x.%s = :id\"", ref.entity, ref.where);
                String refusal = String.format(
                        "\"restricted\", \"%s {0} is still pointed to by %s rows (%s.%s"
                                + " @onDelete(restrict))\", %s",
                        entity, ref.entity, ref.entity, ref.field, kt ? "id.toString()" : "String.valueOf(id)");
                if (kt) {
                    lines.add(in
                            + String.format(
                                    "    if (em.createQuery(%s).setParameter(\"id\", id).setMaxResults(1)"
                                            + ".resultList.isNotEmpty())",
                                    exists));
                    lines.add(in
                            + String.format(
                                    "        throw io.elasticore.springboot3.validation.ConflictException(%s)",
                                    refusal));
                } else {
                    lines.add(String.format(
                            "    if (!em.createQuery(%s).setParameter(\"id\", id).setMaxResults(1)"
                                    + ".getResultList().isEmpty())",
                            exists));
                    lines.add(String.format(
                            "        throw new io.elasticore.springboot3.validation.ConflictException(%s);", refusal));
                }
            }
            for (EntitySpec.OnDeleteRef ref : refs) {
                if (!"setNull".equals(ref.mode)) continue;
                String update = String.format(
                        "\"update %s x set x.%s = null where x.%s = :id\"", ref.entity, ref.set, ref.where);
                lines.add(in
                        + String.format(
                                "    em.createQuery(%s).setParameter(\"id\", id).executeUpdate()%s",
                                update, kt ? "" : ";"));
            }
            for (EntitySpec.OnDeleteRef ref : refs) {
                if (!"cascade".equals(ref.mode)) continue;
                String select = String.format("\"select x from %s x where x.%s = :id\"", ref.entity, ref.where);
                boolean deeper = !e.onDeleteOf(ref.entity).isEmpty();
                // A row an earlier one took with it (a reply of a comment of the same post) is gone already.
                if (kt) {
                    lines.add(in
                            + String.format(
                                    "    for (each in em.createQuery(%s).setParameter(\"id\", id).resultList) {",
                                    select));
                    lines.add(in + "        if (!em.contains(each)) continue");
                    if (deeper) lines.add(in + String.format("        onDelete%s(em, each!!)", ref.entity));
                    lines.add(in + "        em.remove(each)");
                } else {
                    lines.add(String.format(
                            "    for (Object each : em.createQuery(%s).setParameter(\"id\", id)"
                                    + ".getResultList()) {",
                            select));
                    lines.add("        if (!em.contains(each)) continue;");
                    if (deeper) lines.add(String.format("        onDelete%s(em, each);", ref.entity));
                    lines.add("        em.remove(each);");
                }
                lines.add(in + "    }");
            }
            lines.add(in + "}");
        }
        if (kt) lines.add("}");
        for (String line : lines) c.varLine("joinFetchMethod", "%s", line);
    }

    /**
     * The generated service test (Kotlin): its deletes now run the JPQL of onDelete through the mocked entity
     * manager, which answers null for a query. It is handed one that answers itself, an empty list and 0 - and,
     * where the test had no entity manager to mock, one, given by the mocked repository helper.
     */
    @Rule(value = "service-test-on-delete", target = "service_test", order = 90, phase = Rule.Phase.TAIL)
    void onDeleteInTheServiceTest(EntitySpec e) {
        if (e.onDeleteOf(e.getName()).isEmpty()) return;
        SourceCode c = ctx(e);
        if (c.syntax() != Syntax.KOTLIN) return;
        String setUp = orDefault(c.varValue("mockSetUp"), "");
        StringBuilder more = new StringBuilder(setUp);
        if (!"true".equals(c.varValue("entityManagerMock"))) {
            c.varLine("mockDeclarationList", "")
                    .varLine("mockDeclarationList", "    @Mock")
                    .varLine(
                            "mockDeclarationList",
                            "    private lateinit var entityManager: jakarta.persistence.EntityManager");
            c.var("entityManagerMock", "true");
            more.append(
                    "\n        org.mockito.Mockito.lenient().`when`(helper.entityManager).thenReturn(entityManager)");
        }
        more.append("\n        // @onDelete runs its JPQL before a delete: no row points to the one deleted.")
                .append("\n        val onDeleteQuery = org.mockito.Mockito.mock(jakarta.persistence.Query::class.java,")
                .append(" org.mockito.Answers.RETURNS_SELF)")
                .append(
                        "\n        org.mockito.Mockito.lenient().`when`(onDeleteQuery.resultList).thenReturn(emptyList<Any>())")
                .append("\n        org.mockito.Mockito.lenient().`when`(entityManager.createQuery(")
                .append("org.mockito.ArgumentMatchers.anyString())).thenReturn(onDeleteQuery)");
        c.var("mockSetUp", "%s", more.toString());
    }

    private static String j2ee(SourceCode c) {
        return orDefault(c.varValue("j2eePkgName"), "jakarta");
    }

    private static String orDefault(String value, String otherwise) {
        return value == null || value.isEmpty() ? otherwise : value;
    }
}
