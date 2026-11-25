package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.ArtifactContributor;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.RuleText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * A domain rule, named, and handed out to be asked.
 *
 * The last of Evans' tactical patterns the ruleset had nothing to say about.
 * A specification is a rule about a candidate that is worth a name of its
 * own - "a high-value order", "a lapsed customer" - so that the rule can be
 * stated once and asked in every place that cares, instead of the same
 * comparison being retyped in a service, a report and a test until one of
 * them is retyped differently.
 *
 *   Order:
 *     meta: entity @root @specification(highValue, totalAmount > 100)
 *
 * Each name becomes a static method on an OrderSpecifications class,
 * returning a {@code Predicate<Order>}. A predicate rather than a boolean
 * because combining is the point of the pattern: {@code highValue().and(
 * placedToday())} is a rule built from rules, which a pair of boolean methods
 * cannot be.
 *
 * <h3>What it is not</h3>
 *
 * Not a query. The obvious next thought is to emit a Spring Data
 * {@code Specification<Order>} so the rule can be pushed into SQL, and that
 * would be a genuinely better artifact - but only where the rule is
 * expressible in the criteria API, which not all of them are, and a pattern
 * that silently works in memory for some rules and in the database for others
 * has the worst property a generated thing can have: you cannot tell which
 * you got. It selects from what is already loaded, and says so.
 *
 * <h3>Why it shares the invariant's grammar</h3>
 *
 * The two are the same sentence used differently - an invariant refuses a
 * write when the rule is false, a specification hands the answer out. Reading
 * them with one {@link DddCondition} is what stops the DSL acquiring two
 * dialects of what looks like one language.
 *
 * Roots only, and Java only, for the reasons {@link DddDomainEventRules}
 * gives in full.
 */
public class DddSpecificationRules implements CodeRules, ArtifactContributor {

    /** One specifications class per root that declares any. */
    @Override
    public List<ContributedArtifact> artifactsFor(EntitySpec e) {
        if (declared(e).isEmpty()) return Collections.emptyList();

        return Collections.singletonList(
                ContributedArtifact.of("specifications", "entity", className(e)));
    }

    /** The class: one named, combinable rule per declaration. */
    @Rule(value = "ddd-specification", target = "specifications", order = 10)
    void specifications(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntaxAnyOf("ddd-specification", Syntax.JAVA, Syntax.KOTLIN, Syntax.TYPESCRIPT);

        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        boolean typescript = c.syntax() == Syntax.TYPESCRIPT;
        String root = typescript ? e.dtoClassName() : e.getName();
        String var = e.varName();

        if (typescript) {
            typescriptSpecifications(c, e, root, var);
            return;
        }

        c.addImport("java.util.function.Predicate");

        // A class of static rules is not something anybody should instantiate.
        // Java withholds the constructor; Kotlin says it with the object the
        // skeleton declares, so there is nothing to withhold here.
        if (!kotlin) {
            c.varLine("bodyList", "private %s() {", className(e))
             .varLine("bodyList", "}");
        }

        for (Specification spec : specificationsOf(e, var, c.syntax())) {
            c.varLine("bodyList", "")
             .varLine("bodyList", "/**")
             .varLine("bodyList", " * %s: %s", spec.name, spec.condition)
             .varLine("bodyList", " *")
             .varLine("bodyList", " * Combine it rather than restating it - and() and or() are why this")
             .varLine("bodyList", " * is a predicate and not a boolean.")
             .varLine("bodyList", " */")
             .varLine("bodyList",
                     kotlin ? "fun %2$s(): Predicate<%1$s> =" : "public static Predicate<%1$s> %2$s() {",
                     root, spec.name)
             .varLine("bodyList",
                     kotlin ? "    Predicate { %1$s -> %1$s != null && (%2$s) }"
                            : "    return %1$s -> %1$s != null && (%2$s);",
                     var, spec.expression);
            if (!kotlin) c.varLine("bodyList", "}");
        }

        c.var("packageName", e.pkg("entity"))
         .var("className", className(e))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /**
     * The same rules, asked in the browser.
     *
     * This is the half of the pattern that pays for itself twice. A domain
     * rule stated once on the server is retyped in the client the moment
     * anything on a screen depends on it - "is this a high-value order" ends
     * up in a service, a badge and a filter - and the copies drift.
     *
     * And in this language the hand-written copy is not merely a duplicate,
     * it is usually wrong: a decimal arrives as a string, so the obvious
     * order.totalAmount &gt; 100 compares a string with a number. TypeScript
     * accepts it and JavaScript answers false regardless of the amount. The
     * generated form says Number(...) because the grammar knows the type.
     *
     * Plain exported functions rather than something with .and(): a function
     * is already a value here, so combining is composing them, and inventing
     * a Predicate type to mirror the JVM would be translating the workaround
     * rather than the idea.
     */
    private void typescriptSpecifications(SourceCode c, EntitySpec e, String dto, String var) {
        // Written directly rather than through addImport: what a TypeScript
        // file imports is a module path, and the collected-import block is
        // built from qualified type names. The DTO rule says the same thing
        // about its own imports. The DTO is an interface, a type only: import type
        // (verbatimModuleSyntax).
        c.var("className", dto).var("importList", c.syntax().typeImportLine(dto) + "\n");

        for (Specification spec : specificationsOf(e, var, Syntax.TYPESCRIPT)) {
            c.varLine("bodyList", "/**")
             .varLine("bodyList", " * %s: %s", spec.name, spec.condition)
             .varLine("bodyList", " *")
             .varLine("bodyList", " * The server states this rule too, from the same declaration.")
             .varLine("bodyList", " */")
             .varLine("bodyList", "export function %s(%s: %s | null | undefined): boolean {",
                     spec.name, var, dto)
             .varLine("bodyList", "    return %s != null && (%s);", var, spec.expression)
             .varLine("bodyList", "}")
             .varLine("bodyList", "");
        }
    }

    /** One named rule: what it is called, what it said, and what that is in Java. */
    private static final class Specification {
        private final String name;
        private final String condition;
        private final String expression;

        private Specification(String name, String condition, String expression) {
            this.name = name;
            this.condition = condition;
            this.expression = expression;
        }
    }

    /**
     * The rules this entity declares, skipping any this rule set cannot read.
     *
     * Dropped rather than fatal, for the same reason a mistyped event
     * declaration is: one annotation should not stop a domain generating.
     */
    private List<Specification> specificationsOf(EntitySpec e, String var, Syntax syntax) {
        List<Specification> out = new ArrayList<Specification>();

        for (String text : declared(e)) {
            // Name first, condition after, separated by the first comma.
            // Not a colon, which reads well and cannot be used: the meta line
            // is a YAML scalar, and YAML takes "name: " in one as the start of
            // a mapping and refuses to load the file. Not an '=' either - the
            // DSL's own annotation parser reads that as a key/value list.
            int comma = text.indexOf(',');
            if (comma <= 0) continue;

            String name = text.substring(0, comma).trim();
            String condition = text.substring(comma + 1).trim();
            if (name.isEmpty() || !isIdentifier(name)) continue;

            String expression = DddCondition.expression(e, condition, var, false, syntax);
            if (expression == null) continue;

            out.add(new Specification(name, condition, expression));
        }
        return out;
    }

    /** The raw declarations, one per occurrence of the annotation. */
    private List<String> declared(EntitySpec e) {
        if (!e.hasMeta("specification") || !e.aggregateRoot()) return Collections.emptyList();

        List<String> out = new ArrayList<String>();
        for (String value : e.metaValues("specification")) {
            if (value != null && !value.trim().isEmpty()) out.add(value.trim());
        }
        return out;
    }

    private boolean isIdentifier(String name) {
        if (!Character.isJavaIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
        }
        return true;
    }

    private String className(EntitySpec e) {
        return e.getName() + "Specifications";
    }
}
