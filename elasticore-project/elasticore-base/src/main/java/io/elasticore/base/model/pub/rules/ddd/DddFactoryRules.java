package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.ArtifactContributor;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.RuleText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Where an aggregate is made, when a constructor is not enough.
 *
 * A root with an invariant cannot be built by setting fields one at a time -
 * it is invalid in between, and something has to be responsible for the
 * moment it becomes valid. Evans calls that a factory, and it is the natural
 * companion of {@link DddInvariantRules}: the invariant says what must be
 * true, the factory is where making it true lives.
 *
 *   Order:
 *     meta: entity @root @invariant(items.size > 0, '...') @factory(place)
 *
 * Each name becomes a static method on an OrderFactory class - place(...) -
 * and the class carries the generate-once marker: the first run writes the
 * signature, which follows the model until the developer edits the file;
 * after that only the model's changes that merge cleanly come in (a new
 * required field next to a body they wrote does not - the file is kept and
 * the build says so). The generated text itself still says "written once":
 * changing a line of a file developers own would leave every one who edited
 * that line kept, out of the merge, on each build.
 *
 * <h3>What is generated is a place, not a behaviour</h3>
 *
 * This is the honest scope, and chapter 5 said so before any of it existed:
 * the DSL declares data and does not declare behaviour, so what can be
 * generated is not the behaviour but the place to write it. The generated
 * body throws, rather than returning a half-built aggregate - a stub that
 * quietly returns something is a stub that reaches production.
 *
 * The signature is the aggregate's own required fields, which is the most a
 * model can say about what making one takes. The developer changes it; the
 * file is theirs after the first write.
 *
 * <h3>Why a class of its own</h3>
 *
 * A static method on the root would be regenerated with the root, taking the
 * developer's body with it. Generate-once is a property of a file, so a file
 * is what it needs. It sits beside the domain model for the same reason the
 * events do - a factory for an aggregate is part of that aggregate's domain,
 * not of the layer that happens to call it.
 *
 * Roots only, and Java only, for the reasons {@link DddDomainEventRules}
 * gives in full.
 */
public class DddFactoryRules implements CodeRules, ArtifactContributor {

    /** One factory class per root that declares any, beside the domain model. */
    @Override
    public List<ContributedArtifact> artifactsFor(EntitySpec e) {
        if (declaredFactories(e).isEmpty()) return Collections.emptyList();

        return Collections.singletonList(
                ContributedArtifact.of("factory", "entity", factoryName(e)));
    }

    /**
     * The factory: one static method per declared name, each throwing until
     * the developer writes it.
     */
    @Rule(value = "ddd-factory", target = "factory", order = 10)
    void factoryClass(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntaxAnyOf("ddd-factory", Syntax.JAVA, Syntax.KOTLIN);

        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String root = e.getName();
        List<FieldSpec> required = requiredFields(e);
        for (FieldSpec f : required) {
            if (f.typeName() != null && f.typeName().indexOf('.') > 0) c.addImport(f.typeName());
        }

        // Nobody should hold one of these. Java withholds the constructor;
        // Kotlin's skeleton declares an object, which says the same thing
        // without a member to write.
        if (!kotlin) {
            c.varLine("bodyList", "private %s() {", factoryName(e))
             .varLine("bodyList", "}");
        }

        for (String name : declaredFactories(e)) {
            c.varLine("bodyList", "")
             .varLine("bodyList", "/**")
             .varLine("bodyList", " * Makes %s, already valid.", root)
             .varLine("bodyList", " *")
             .varLine("bodyList", " * The signature is what the model could say - the root's required")
             .varLine("bodyList", " * fields. Change it to what making one actually takes: this file is")
             .varLine("bodyList", " * written once and is yours from then on.")
             .varLine("bodyList", " */")
             .varLine("bodyList",
                     kotlin ? "fun %2$s(%3$s): %1$s {" : "public static %1$s %2$s(%3$s) {",
                     root, name, parameters(required, c.syntax()))
             .varLine("bodyList",
                     kotlin ? "    throw UnsupportedOperationException(" : "    throw new UnsupportedOperationException(")
             .varLine("bodyList",
                     kotlin ? "            \"%s.%s is where a valid %s is made; write it.\")"
                            : "            \"%s.%s is where a valid %s is made; write it.\");",
                     factoryName(e), name, root)
             .varLine("bodyList", "}");
        }

        c.var("packageName", e.pkg("entity"))
         .var("className", factoryName(e))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /**
     * The root's required fields, which is the most the model can say about
     * what making one takes.
     *
     * The generated identity is left out: a factory that is handed the id it
     * is about to have has not made anything. Collections are left out too -
     * a member is added through the root, and the aggregate's own methods are
     * how that is done.
     */
    private List<FieldSpec> requiredFields(EntitySpec e) {
        List<FieldSpec> out = new ArrayList<FieldSpec>();
        for (FieldSpec f : e.fields()) {
            if (!f.required() || f.isList()) continue;
            if (f.hasDsl("id") || f.hasDsl("genid") || f.hasDsl("sequence")) continue;
            out.add(f);
        }
        return out;
    }

    /**
     * The parameter list, types written the way the file already imports
     * them: an import and a fully qualified use of the same type in one
     * signature reads as though they were two different types.
     */
    private String parameters(List<FieldSpec> fields, Syntax syntax) {
        StringBuilder sb = new StringBuilder();
        for (FieldSpec f : fields) {
            if (sb.length() > 0) sb.append(", ");
            if (syntax == Syntax.KOTLIN) {
                sb.append(f.name()).append(": ").append(simpleName(f.typeName()));
            } else {
                sb.append(simpleName(f.typeName())).append(' ').append(f.name());
            }
        }
        return sb.toString();
    }

    private String simpleName(String type) {
        if (type == null) return null;
        int dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }

    /**
     * The factory names the entity declares, in order.
     *
     * A member's declaration is ignored rather than refused, for the same
     * reason its event declaration is: an entity moved inside a boundary
     * should not stop generating.
     */
    private List<String> declaredFactories(EntitySpec e) {
        if (!e.hasMeta("factory") || !e.aggregateRoot()) return Collections.emptyList();

        List<String> out = new ArrayList<String>();
        for (String declared : e.metaValues("factory")) {
            if (declared == null) continue;
            for (String part : declared.split(",")) {
                String name = part.trim();
                if (!name.isEmpty()) out.add(name);
            }
        }
        return out;
    }

    private String factoryName(EntitySpec e) {
        return e.getName() + "Factory";
    }
}
