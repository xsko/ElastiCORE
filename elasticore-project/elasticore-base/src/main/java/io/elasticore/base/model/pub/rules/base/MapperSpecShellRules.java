package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The Java shell of the mapper's toSpec pair: the two overloads that open and
 * the return that closes, around the conditions {@link MapperSpecRules} emits.
 *
 * Separated for the same reason the mapper's own skeleton was: the conditions
 * are decided from the model and written through the seam, while this is a
 * fixed pair of methods. The second one reassigns its Specification parameter,
 * which Java allows and Kotlin does not - so a language that cannot supplies
 * its own shell rather than this one branching.
 */
public class MapperSpecShellRules implements CodeRules {

    private static final String BLOCK = MapperSpecRules.SPEC_BLOCK;

    @Rule(value = "mapper-spec-open", target = "mapper", order = 100)
    void open(EntitySpec e) {
        String en = e.getName();
        String dto = e.searchDtoClassName();
        SourceCode c = ctx(e);

        Syntax syntax = c.syntax();
        String spec = String.format("Specification<%s>", en);

        c.line(BLOCK, "    %s{", syntax.methodSignature("public static", spec, "toSpec",
                        syntax.parameter(dto, "searchDTO")))
         .line(BLOCK, "        %s", syntax.localVar(spec, "b",
                        syntax.sam(spec, "r, q, c", "c.conjunction()")))
         .line(BLOCK, "        %s", syntax.statement("return toSpec(searchDTO, b)"))
         .line(BLOCK, "    }")
         .line(BLOCK, "    ")
         .line(BLOCK, "    ")
         .line(BLOCK, "    %s{", syntax.methodSignature("public static", spec, "toSpec",
                        String.format("%s, %s", syntax.parameter(dto, "searchDTO"),
                                syntax.parameter(spec, "sp"))));
    }

    @Rule(value = "mapper-spec-close", target = "mapper", order = 120, phase = Rule.Phase.TAIL)
    void close(EntitySpec e) {
        String kept = MapperSpecRules.softDeleteCondition(ctx(e), e);
        if (kept != null) ctx(e).line(BLOCK, "        %s", kept);
        ctx(e).line(BLOCK, "        %s", ctx(e).syntax().statement("return sp"))
              .line(BLOCK, "    }");
    }

}
