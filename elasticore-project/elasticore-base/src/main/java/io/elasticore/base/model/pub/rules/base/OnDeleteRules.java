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
 * setNull, then cascade. setNull is one JPQL update - a @Version moves ("update versioned"), audit fields and
 * listeners do not; a row this delete then removes is refreshed first. A cascaded row's own {@code @onDelete} references are carried out
 * first, then it is removed through JPA (its soft delete and delete guards apply): a comment's replies go with
 * it. Each row is visited once; rows that point to each other are refused (409 cycle). Soft-deleted rows are left out of what restrict
 * sees and cascade takes.
 *
 * <p>The work is a method per entity on the cascade's way, in the deleted root's own CoreService, so no service
 * is handed another: {@code onDeletePost(em, row)} (static in Java; a member in Kotlin, beside any companion
 * object the class has). JPA (Java, Kotlin) only; MyBatis and the 2.x-compatible output stop the build rather
 * than delete without it.
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
        String id = orDefault(
                c.varValue("pkDtoInfo2"),
                e.pkField() == null ? "id" : e.pkField().name());
        String method = "onDelete" + e.getName();

        // By its key: before the delete, after the access check. By a search: each row the delete removes,
        // as it removes it - the same rows, read once.
        c.varLine(
                "accessDelete",
                kt
                        ? String.format("%s.findById(%s).ifPresent { %s(%s, it) }", repo, id, method, em)
                        : String.format("%s.findById(%s).ifPresent(row -> %s(%s, row));", repo, id, method, em));
        c.var("deleteEachOpen", "%s(%s, ", method, em).var("deleteEachClose", ")");

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

        // Entities whose rows a versioned setNull of this plan moves: one this delete then removes is read again
        // first, or its delete would not match the version it was loaded with.
        Set<String> bumped = new LinkedHashSet<String>();
        for (String entity : visit) {
            for (EntitySpec.OnDeleteRef ref : e.onDeleteOf(entity)) {
                if ("setNull".equals(ref.mode) && ref.versioned) bumped.add(ref.entity);
            }
        }

        String emType = kt ? j2ee(c) + ".persistence.EntityManager" : "EntityManager";
        List<String> lines = new ArrayList<String>();
        lines.add("");
        lines.add("/**");
        lines.add(" * What the rows that point to a " + e.getName() + " ask when it is deleted (@onDelete), before");
        lines.add(" * it is: restrict refuses, setNull clears, cascade deletes them - in this transaction.");
        lines.add(" */");
        if (kt) {
            lines.add(String.format("protected fun %s(em: %s, row: Any): %s {", method, emType, e.getName()));
            // A row a cascade of this delete removed already (a reply found by the same search as its parent).
            lines.add(String.format("    if (!em.contains(row)) return row as %s", e.getName()));
            lines.add(String.format(
                    "    %s(em, row, java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>()))",
                    method));
            if (bumped.contains(e.getName())) lines.add("    em.refresh(row)");
            lines.add(String.format("    return row as %s", e.getName()));
        } else {
            lines.add(String.format("public static %s %s(EntityManager em, Object row) {", e.getName(), method));
            lines.add(String.format("    if (!em.contains(row)) return (%s) row;", e.getName()));
            lines.add(String.format(
                    "    %s(em, row, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>()));",
                    method));
            if (bumped.contains(e.getName())) lines.add("    em.refresh(row);");
            lines.add(String.format("    return (%s) row;", e.getName()));
        }
        lines.add("}");
        for (String entity : visit) {
            List<EntitySpec.OnDeleteRef> refs = e.onDeleteOf(entity);
            // A cascaded entity nothing points to with @onDelete is removed as it is: no method of its own.
            if (refs.isEmpty()) continue;
            method(lines, kt, emType, entity, refs, e, bumped);
        }
        for (String line : lines) c.varLine("joinFetchMethod", "%s", line);
    }

    /** One entity's work: its restricts, its setNulls, its cascades, each row once (seen). */
    private static void method(
            List<String> lines,
            boolean kt,
            String emType,
            String entity,
            List<EntitySpec.OnDeleteRef> refs,
            EntitySpec e,
            Set<String> bumped) {
        String key = refs.get(0).targetKey;
        lines.add("");
        if (kt) {
            lines.add(
                    String.format("private fun onDelete%s(em: %s, row: Any, seen: MutableSet<Any>) {", entity, emType));
            lines.add("    seen.add(row)");
            lines.add(String.format("    val id: Any? = (row as %s).%s", entity, key));
        } else {
            lines.add(String.format(
                    "private static void onDelete%s(EntityManager em, Object row, java.util.Set<Object> seen) {",
                    entity));
            lines.add("    seen.add(row);");
            lines.add(String.format("    Object id = ((%s) row).get%s();", entity, capitalize(key)));
        }
        for (EntitySpec.OnDeleteRef ref : refs) {
            if (!"restrict".equals(ref.mode)) continue;
            String exists =
                    String.format("\"select 1 from %s x where x.%s = :id%s\"", ref.entity, ref.where, live(ref));
            String refusal = String.format(
                    "\"restricted\", \"%s {0} is still pointed to by %s rows (%s.%s @onDelete(restrict))\", %s",
                    entity, ref.entity, ref.entity, ref.field, kt ? "id.toString()" : "String.valueOf(id)");
            if (kt) {
                lines.add(String.format(
                        "    if (em.createQuery(%s).setParameter(\"id\", id).setMaxResults(1).resultList.isNotEmpty())",
                        exists));
                lines.add(String.format(
                        "        throw io.elasticore.springboot3.validation.ConflictException(%s)", refusal));
            } else {
                lines.add(String.format(
                        "    if (!em.createQuery(%s).setParameter(\"id\", id).setMaxResults(1).getResultList().isEmpty())",
                        exists));
                lines.add(String.format(
                        "        throw new io.elasticore.springboot3.validation.ConflictException(%s);", refusal));
            }
        }
        for (EntitySpec.OnDeleteRef ref : refs) {
            if (!"setNull".equals(ref.mode)) continue;
            // Every row that holds it - a soft-deleted one too, which still holds its foreign key. One statement:
            // a domain entity does not let its reference be set from outside. A @Version moves (Hibernate's
            // "update versioned"), so a copy read before cannot write the reference back.
            String update = String.format(
                    "\"update %s%s x set x.%s = null where x.%s = :id\"",
                    ref.versioned ? "versioned " : "", ref.entity, ref.set, ref.where);
            lines.add(String.format(
                    "    em.createQuery(%s).setParameter(\"id\", id).executeUpdate()%s", update, kt ? "" : ";"));
        }
        for (EntitySpec.OnDeleteRef ref : refs) {
            if (!"cascade".equals(ref.mode)) continue;
            String select =
                    String.format("\"select x from %s x where x.%s = :id%s\"", ref.entity, ref.where, live(ref));
            boolean deeper = !e.onDeleteOf(ref.entity).isEmpty();
            // A row an earlier one took with it (a reply of a comment of the same post) is gone already,
            // and one on the way already (a cycle) is not taken again.
            if (kt) {
                lines.add(String.format(
                        "    for (each in em.createQuery(%s).setParameter(\"id\", id).resultList) {", select));
                lines.add("        if (each == null || !em.contains(each)) continue");
                lines.add("        if (!seen.add(each))");
                lines.add(String.format(
                        "            throw io.elasticore.springboot3.validation.ConflictException(%s)", cycle(ref)));
                if (deeper) lines.add(String.format("        onDelete%s(em, each, seen)", ref.entity));
                if (bumped.contains(ref.entity)) lines.add("        em.refresh(each)");
                for (EntitySpec.CountRef count : e.countsOf(ref.entity)) {
                    lines.add("        " + CountRules.touch(count, "(each as " + ref.entity + ")", true));
                }
                lines.add("        em.remove(each)");
            } else {
                lines.add(String.format(
                        "    for (Object each : em.createQuery(%s).setParameter(\"id\", id).getResultList()) {",
                        select));
                lines.add("        if (!em.contains(each)) continue;");
                lines.add("        if (!seen.add(each))");
                lines.add(String.format(
                        "            throw new io.elasticore.springboot3.validation.ConflictException(%s);",
                        cycle(ref)));
                if (deeper) lines.add(String.format("        onDelete%s(em, each, seen);", ref.entity));
                if (bumped.contains(ref.entity)) lines.add("        em.refresh(each);");
                // A removed row no longer counts where it was counted (@count).
                for (EntitySpec.CountRef count : e.countsOf(ref.entity)) {
                    lines.add("        " + CountRules.touch(count, "((" + ref.entity + ") each)", false) + ";");
                }
                lines.add("        em.remove(each);");
            }
            lines.add("    }");
        }
        lines.add("}");
    }

    /**
     * A row met again on its own way down: rows that point to each other, each holding the other's key, are
     * not deleted one by one - refused, rather than recursing without end or failing on the foreign key.
     */
    private static String cycle(EntitySpec.OnDeleteRef ref) {
        return String.format(
                "\"cycle\", \"%s rows point to each other through %s.%s - @onDelete(cascade) does not"
                        + " delete a cycle\"",
                ref.entity, ref.entity, ref.field);
    }

    /** The condition that leaves a soft-deleted row out, or nothing for an entity deleted outright. */
    private static String live(EntitySpec.OnDeleteRef ref) {
        return ref.deleted == null ? "" : " and x." + ref.deleted + " is null";
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
        stubQueries(c);
    }

    /**
     * Hands the generated Kotlin service test's mocked entity manager a query that answers itself, an empty list
     * and 0 - once, for @onDelete and @count alike - and an entity manager to mock where it had none.
     */
    static void stubQueries(SourceCode c) {
        if ("true".equals(c.varValue("queryStub"))) return;
        c.var("queryStub", "true");
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
        more.append(
                        "\n        // @onDelete and @count run their JPQL beside a write: nothing points to, or counts, the row.")
                .append("\n        val onDeleteQuery = org.mockito.Mockito.mock(jakarta.persistence.Query::class.java,")
                .append(" org.mockito.Answers.RETURNS_SELF)")
                .append(
                        "\n        org.mockito.Mockito.lenient().`when`(onDeleteQuery.resultList).thenReturn(emptyList<Any>())")
                .append("\n        org.mockito.Mockito.lenient().`when`(entityManager.createQuery(")
                .append("org.mockito.ArgumentMatchers.anyString())).thenReturn(onDeleteQuery)");
        c.var("mockSetUp", "%s", more.toString());
    }

    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String j2ee(SourceCode c) {
        return orDefault(c.varValue("j2eePkgName"), "jakarta");
    }

    private static String orDefault(String value, String otherwise) {
        return value == null || value.isEmpty() ? otherwise : value;
    }
}
