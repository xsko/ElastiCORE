package io.elasticore.base.model.pub.rules.lang.kotlin;

import io.elasticore.base.model.pub.rules.base.MapperSpecRules;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.SourceCode;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The Kotlin shell of the mapper's toSpec pair.
 *
 * The conditions inside are the shared ones - which field searches by what is
 * decided from the model, and MapperSpecRules writes them through the seam.
 * What differs is the frame, and by more than spelling: the Java overload
 * reassigns its Specification parameter as it accumulates conditions, and a
 * Kotlin parameter cannot be reassigned. So the parameter is taken under
 * another name and the accumulator is a local the conditions can write to.
 */
public class KotlinMapperSpecShellRules implements CodeRules {

    private static final String BLOCK = MapperSpecRules.SPEC_BLOCK;

    @Rule(value = "kt-mapper-spec-open", target = "mapper", order = 100)
    void open(EntitySpec e) {
        String en = e.getName();
        String dto = e.searchDtoClassName();
        String spec = String.format("Specification<%s>", en);
        SourceCode c = ctx(e);
        c.requireSyntax(Syntax.KOTLIN, "kt-mapper-spec-open");

        c.line(BLOCK, "    @JvmStatic")
         .line(BLOCK, "    fun toSpec(searchDTO: %s): %s {", dto, spec)
         .line(BLOCK, "        val b = %s { r, q, c -> c.conjunction() }", spec)
         .line(BLOCK, "        return toSpec(searchDTO, b)")
         .line(BLOCK, "    }")
         .line(BLOCK, "    ")
         .line(BLOCK, "    ")
         .line(BLOCK, "    @JvmStatic")
         .line(BLOCK, "    fun toSpec(searchDTO: %s, spec: %s): %s {", dto, spec, spec)
         .line(BLOCK, "        var sp = spec");
    }

    @Rule(value = "kt-mapper-spec-close", target = "mapper", order = 120, phase = Rule.Phase.TAIL)
    void close(EntitySpec e) {
        String kept = MapperSpecRules.softDeleteCondition(ctx(e), e);
        if (kept != null) ctx(e).line(BLOCK, "        %s", kept);
        ctx(e).line(BLOCK, "        return sp")
              .line(BLOCK, "    }");
    }
}
