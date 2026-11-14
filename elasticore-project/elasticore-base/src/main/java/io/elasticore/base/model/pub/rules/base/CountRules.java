package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * {@code @count(Comment.post[, status == PUBLISHED])}: a count kept on a row of the rows of another entity that
 * point to it - a post's {@code commentCount} (issue 179).
 *
 * <p>Carried out by the CoreService of the counted entity (Comment): each write of a row - a save, an update
 * (the row it pointed to before, and the one it points to now), a delete by key, by search, an update by
 * condition, a command that moves its state - says which row's count it touched, and the runtime's {@code
 * Counts} counts it again, once per row, just before the transaction commits: an update of the counting row
 * with a count of what points to it, after locking it. A row as written is read at the commit. A row an
 * {@code @onDelete(cascade)} removes says so too.
 *
 * <p>The counting field is the model's: no request writes it, a save of its row does not write it back, and a
 * new row starts at 0 (SpecFactory.prepareCounts). JPA (Java, Kotlin) only.
 */
public class CountRules implements CodeRules {

    /** Before the commands are written (30): a command that moves the state touches the counts too. */
    @Rule(value = "service-count-commands", target = "service", order = 25)
    void commands(EntitySpec e) {
        if (e.countsOf(e.getName()).isEmpty()) return;
        SourceCode c = ctx(e);
        if (c.compatible() || "mybatis".equals(c.varValue("persistence"))) return;
        boolean kt = c.syntax() == Syntax.KOTLIN;
        c.var("countsAfterCommand", "%s(%s, entity)%s", method(e), em(c), kt ? "" : ";");
    }

    @Rule(value = "service-count", target = "service", order = 91, phase = Rule.Phase.TAIL)
    void count(EntitySpec e) {
        List<EntitySpec.CountRef> refs = e.countsOf(e.getName());
        if (refs.isEmpty()) return;
        SourceCode c = ctx(e);
        if (c.compatible() || "mybatis".equals(c.varValue("persistence"))) {
            ModelCheck.fail(String.format(
                    "[%s] @count of %s rows (%s) is not kept %s yet - keep the domain on JPA (output v3)",
                    e.domainClassName(),
                    e.getName(),
                    refs.get(0).counter,
                    c.compatible() ? "in the 2.x-compatible output" : "under mode: mybatis"));
            return;
        }
        boolean kt = c.syntax() == Syntax.KOTLIN;
        String em = em(c);
        String repo = orDefault(c.varValue("cmdRef"), "repository");
        String query = orDefault(c.varValue("qryRef"), repo);
        String id = orDefault(
                c.varValue("pkDtoInfo2"),
                e.pkField() == null ? "id" : e.pkField().name());
        String mapper = orDefault(c.varValue("mapperName"), e.domainClassName() + "Mapper");
        String open = orDefault(c.varValue("accessSpecOpen"), "");
        String close = orDefault(c.varValue("accessSpecClose"), "");
        String method = method(e);
        String end = kt ? "" : ";";

        // The row as it is written (save, update, update by condition), as it was (update), and as it goes.
        c.varLine("deriveBeforeSave", "%sWritten(%s, entity)%s", method, em, end);
        c.varLine("accessUpdate", "%s(%s, entity)%s", method, em, end);
        c.varLine(
                "accessUpdateAll",
                kt
                        ? String.format("for (row in %s.findAll(spec)) %s(%s, row)", query, method, em)
                        : String.format(
                                "for (%s row : %s.findAll(spec)) %s(%s, row);", e.getName(), query, method, em));
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
                                "for (%s row : %s.findAll(%s%s.toSpec(searchDTO)%s)) %s(%s, row);",
                                e.getName(), query, open, mapper, close, method, em));

        String emType =
                kt ? orDefault(c.varValue("j2eePkgName"), "jakarta") + ".persistence.EntityManager" : "EntityManager";
        c.varLine("joinFetchMethod", "");
        c.varLine("joinFetchMethod", "/**");
        c.varLine(
                "joinFetchMethod",
                " * @count: the counts of the rows a %s points to, counted again before the",
                e.getName());
        c.varLine("joinFetchMethod", " * transaction commits.");
        c.varLine("joinFetchMethod", " */");
        if (kt) {
            c.varLine("joinFetchMethod", "private fun %s(em: %s, row: %s?) {", method, emType, e.getName());
            c.varLine("joinFetchMethod", "    if (row == null) return");
        } else {
            c.varLine("joinFetchMethod", "private static void %s(EntityManager em, %s row) {", method, e.getName());
            c.varLine("joinFetchMethod", "    if (row == null) return;");
        }
        for (EntitySpec.CountRef ref : refs) {
            c.varLine("joinFetchMethod", "    %s%s", touch(ref, "row", kt), end);
        }
        c.varLine("joinFetchMethod", "}");
        // As the row is written: the row it points to is read when the transaction commits.
        c.varLine("joinFetchMethod", "");
        if (kt) {
            c.varLine("joinFetchMethod", "private fun %sWritten(em: %s, row: %s?) {", method, emType, e.getName());
            c.varLine("joinFetchMethod", "    if (row == null) return");
        } else {
            c.varLine(
                    "joinFetchMethod",
                    "private static void %sWritten(EntityManager em, %s row) {",
                    method,
                    e.getName());
            c.varLine("joinFetchMethod", "    if (row == null) return;");
        }
        for (EntitySpec.CountRef ref : refs) {
            c.varLine("joinFetchMethod", "    %s%s", touchLater(ref, "row", kt), end);
        }
        c.varLine("joinFetchMethod", "}");
    }

    /**
     * The generated service test (Kotlin): a write now counts again through the mocked entity manager, at once,
     * there being no transaction - it is handed a query that answers itself.
     */
    @Rule(value = "service-test-count", target = "service_test", order = 91, phase = Rule.Phase.TAIL)
    void countInTheServiceTest(EntitySpec e) {
        if (e.countsOf(e.getName()).isEmpty()) return;
        SourceCode c = ctx(e);
        if (c.syntax() != Syntax.KOTLIN) return;
        OnDeleteRules.stubQueries(c);
    }

    /** Counts.touch for one count, of the row the expression is, as the language writes it. */
    public static String touch(EntitySpec.CountRef ref, String row, boolean kt) {
        return String.format(
                "io.elasticore.springboot3.domain.Counts.touch(em, \"%s\", \"%s\", %s)",
                literal(ref.lock, kt), literal(ref.jpql, kt), ref.idOf(row, kt));
    }

    /** Counts.touchLater: the row's reference read when the transaction commits, after beforeSave and listeners. */
    static String touchLater(EntitySpec.CountRef ref, String row, boolean kt) {
        return kt
                ? String.format(
                        "io.elasticore.springboot3.domain.Counts.touchLater(em, \"%s\", \"%s\", %s) { r -> %s }",
                        literal(ref.lock, true), literal(ref.jpql, true), row, ref.idOf("r", true))
                : String.format(
                        "io.elasticore.springboot3.domain.Counts.touchLater(em, \"%s\", \"%s\", %s, r -> %s)",
                        literal(ref.lock, false), literal(ref.jpql, false), row, ref.idOf("r", false));
    }

    /** The text inside a string literal of the language: its quotes, backslashes and (Kotlin) dollars escaped. */
    private static String literal(String text, boolean kt) {
        String out = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return kt ? out.replace("$", "\\$") : out;
    }

    private static String method(EntitySpec e) {
        return "countsOf" + e.getName();
    }

    private static String em(SourceCode c) {
        return orDefault(c.varValue("emRef"), "entityManager");
    }

    private static String orDefault(String value, String otherwise) {
        return value == null || value.isEmpty() ? otherwise : value;
    }
}
