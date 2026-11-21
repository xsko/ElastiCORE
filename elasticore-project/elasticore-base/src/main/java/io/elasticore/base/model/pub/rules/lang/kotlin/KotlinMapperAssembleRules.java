package io.elasticore.base.model.pub.rules.lang.kotlin;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

import static io.elasticore.base.model.pub.rules.base.MapperBaseRules.FWD_BODY;
import static io.elasticore.base.model.pub.rules.base.MapperBaseRules.REV_BODY;
import static io.elasticore.base.model.pub.rules.base.MapperBaseRules.REV_BODY_EM;
import static io.elasticore.base.model.pub.rules.base.MapperBaseRules.REV_TAIL;
import static io.elasticore.base.model.pub.rules.base.MapperBaseRules.REV_TAIL_EM;

/**
 * The mapper's Kotlin skeleton, in place of MapperAssembleRules.
 *
 * The per-field lines are the shared ones - MapperBaseRules decides what maps
 * to what and writes it through the language seam - and what this supplies is
 * the fixed set of methods around them.
 *
 * Two things are not spelling. A mapper is a class of statics in Java and a
 * singleton object here, so the members are functions of the object and carry
 * @JvmStatic for callers on the Java side. And where Java disambiguates an
 * overload by casting a null argument, Kotlin states the type of the null.
 */
public class KotlinMapperAssembleRules implements CodeRules {

    /** Separator between generated methods: two lines of four spaces. */
    private static final String SEP = "    ";

    @Rule(value = "kt-mapper-assemble", target = "mapper", order = 90, phase = Rule.Phase.TAIL)
    void assemble(EntitySpec e) {
        String en = e.getName();
        String dto = e.dtoClassName();
        String mapper = String.format("%sMapper", e.domainClassName());

        String toDto = dto.equals(String.format("%sDTO", en))
                ? "toDTO" : String.format("to%s", dto);
        String revList = e.mapperOrdinal() > 1
                ? String.format("%s%d", en, e.mapperOrdinal()) : en;
        SourceCode code = ctx(e);

        // ---- forward section ----
        String fwd = "forward";
        code.line(fwd, "    @JvmStatic")
            .line(fwd, "    fun mapping(from: %s, to: %s, isSkipNull: Boolean) {", en, dto)
            .line(fwd, "        mapping(from, to, isSkipNull, null as MappingContext?)")
            .line(fwd, "    }")
            .line(fwd, "    @JvmStatic")
            .line(fwd, "    fun mapping(from: %s?, to: %s?, isSkipNull: Boolean, c: MappingContext?) {", en, dto);
        guard(code, fwd);
        copy(code, FWD_BODY, fwd);
        code.line(fwd, "    }")
            .line(fwd, SEP).line(fwd, SEP)
            .line(fwd, "    @JvmStatic")
            .line(fwd, "    fun mapping(from: %s, to: %s) {", en, dto)
            .line(fwd, "        mapping(from, to, false)")
            .line(fwd, "    }")
            .line(fwd, "    @JvmStatic")
            .line(fwd, "    fun mapping(from: %s, to: %s, c: MappingContext?) {", en, dto)
            .line(fwd, "        mapping(from, to, false, c)")
            .line(fwd, "    }")
            .line(fwd, SEP).line(fwd, SEP);

        toOne(code, fwd, toDto, en, dto, false);
        toList(code, fwd, mapper, toDto, en, dto, String.format("to%sList", dto), false);
        toListModifier(code, fwd, toDto, en, dto, String.format("to%sList", dto), en, dto);

        // ---- reverse section ----
        String rev = "reverse";
        code.line(rev, "    @JvmStatic")
            .line(rev, "    fun mapping(from: %s, to: %s, isSkipNull: Boolean) {", dto, en)
            .line(rev, "        mapping(from, to, isSkipNull, null as MappingContext?)")
            .line(rev, "    }")
            .line(rev, "    @JvmStatic")
            .line(rev, "    fun mapping(from: %s?, to: %s?, isSkipNull: Boolean, c: MappingContext?) {", dto, en);
        reverseBody(code, rev, false);
        code.line(rev, "    }")
                .line(rev, SEP)
                .line(rev, SEP)
                .line(rev, "    @JvmStatic")
                .line(rev, "    fun mapping(from: %s, to: %s) {", dto, en)
                .line(rev, "        mapping(from, to, false)")
                .line(rev, "    }")
                .line(rev, "    @JvmStatic")
                .line(rev, "    fun mapping(from: %s, to: %s, c: MappingContext?) {", dto, en)
                .line(rev, "        mapping(from, to, false, c)")
                .line(rev, "    }")
                .line(rev, SEP)
                .line(rev, SEP);
        // The EntityManager overloads attach a JPA reference for a relation's
        // key; a MongoDB document holds the key itself, which the plain ones set.
        boolean em = !io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(e)
                && !io.elasticore.base.model.pub.rules.mongo.MongoDocs.applies(e);
        if (em) {
            code.line(rev, "    @JvmStatic")
                    .line(rev, "    fun mapping(from: %s, to: %s, em: EntityManager?) {", dto, en)
                    .line(rev, "        mapping(from, to, false, null, em)")
                    .line(rev, "    }")
                    .line(rev, "    @JvmStatic")
                    .line(rev, "    fun mapping(from: %s, to: %s, isSkipNull: Boolean, em: EntityManager?) {", dto, en)
                    .line(rev, "        mapping(from, to, isSkipNull, null, em)")
                    .line(rev, "    }")
                    .line(rev, "    @JvmStatic")
                    .line(rev, "    fun mapping(from: %s, to: %s, c: MappingContext?, em: EntityManager?) {", dto, en)
                    .line(rev, "        mapping(from, to, false, c, em)")
                    .line(rev, "    }")
                    .line(rev, "    @JvmStatic")
                    .line(
                            rev,
                            "    fun mapping(from: %s?, to: %s?, isSkipNull: Boolean, c: MappingContext?,"
                                    + " em: EntityManager?) {",
                            dto,
                            en);
        reverseBody(code, rev, true);
        code.line(rev, "    }")
            .line(rev, SEP).line(rev, SEP);
        }

        // An abstract entity has no instance to make: a DTO can only update a row that is there.
        if (e.isKnownAbstractEntity(en)) return;
        toOne(code, rev, "toEntity", dto, en, false);
        if (em) toOne(code, rev, "toEntity", dto, en, true);
        toList(code, rev, mapper, "toEntity", dto, en, String.format("to%sList", revList), false);
        if (em) toList(code, rev, mapper, "toEntity", dto, en, String.format("to%sList", revList), true);
        toListModifier(code, rev, "toEntity", dto, en, String.format("to%sList", revList), dto, en);
    }

    /** The three checks every mapping method opens with. */
    private void guard(SourceCode code, String block) {
        code.line(block, "        if (c != null && !c.checkEnable()) return")
            .line(block, "        checkPermission(from, to)")
            .line(block, "        if (from == null || to == null) return");
    }

    /** The pair that makes one target from one source. */
    private void toOne(SourceCode code, String block, String name,
                       String from, String to, boolean withEntityManager) {
        String emParam = withEntityManager ? ", em: EntityManager?" : "";
        String emArg = withEntityManager ? ", em" : "";

        code.line(block, "    @JvmStatic")
            .line(block, "    fun %s(from: %s?%s): %s? {", name, from, emParam, to)
            .line(block, "        return %s(from, MappingContext.withGuard(2, null)%s)", name, emArg)
            .line(block, "    }")
            .line(block, "    @JvmStatic")
            .line(block, "    fun %s(from: %s?, c1: MappingContext?%s): %s? {", name, from, emParam, to)
            .line(block, "        val c = c1?.getChild()")
            .line(block, "        if (c != null && !c.checkEnable()) return null")
            .line(block, "        if (from == null) return null")
            .line(block, "        val to = %s()", to)
            .line(block, "        mapping(from, to, c%s)", emArg)
            .line(block, "        return to")
            .line(block, "    }")
            .line(block, SEP).line(block, SEP);
    }

    /** The pair that maps a list, straight through the single-value method. */
    private void toList(SourceCode code, String block, String mapper, String one,
                        String from, String to, String name, boolean withEntityManager) {
        String emParam = withEntityManager ? ", em: EntityManager?" : "";
        String emArg = withEntityManager ? ", em" : "";

        code.line(block, "    @JvmStatic")
            .line(block, "    fun %s(fromList: List<%s>?%s): List<%s>? {", name, from, emParam, to)
            .line(block, "        return %s(fromList, null as MappingContext?%s)", name, emArg)
            .line(block, "    }")
            .line(block, "    @JvmStatic")
            .line(block, "    fun %s(fromList: List<%s>?, c: MappingContext?%s): List<%s>? {",
                    name, from, emParam, to)
            .line(block, "        if (c != null && !c.checkEnable(1)) return null")
            .line(block, "        if (fromList == null) return null")
            .line(block, "        return fromList.mapNotNull { e -> %s.%s(e, c%s) }", mapper, one, emArg)
            .line(block, "    }")
            .line(block, SEP).line(block, SEP);
    }

    /** The list method that hands each pair to a caller's function. */
    private void toListModifier(SourceCode code, String block, String one,
                                String from, String to, String name,
                                String fromArg, String toArg) {
        code.line(block, "    @JvmStatic")
            .line(block, "    fun %s(fromList: List<%s>?,"
                            + " modifier: java.util.function.BiFunction<%s, %s, %s>): List<%s>? {",
                    name, from, fromArg, toArg, toArg, to)
            .line(block, "        if (fromList == null) return null")
            .line(block, "        return fromList.mapNotNull { from ->")
            .line(block, "            val to = %s(from) ?: return@mapNotNull null", one)
            .line(block, "            modifier.apply(from, to)")
            .line(block, "        }")
            .line(block, "    }");
    }

    /**
     * Body of a reverse mapping method. The EntityManager overload converts
     * through the em-aware methods and attaches a managed reference for a
     * primary-key @ref, so it gets its own collected lines.
     */
    private void reverseBody(SourceCode code, String block, boolean withEntityManager) {
        guard(code, block);
        copy(code, withEntityManager ? REV_BODY_EM : REV_BODY, block);
        copy(code, withEntityManager ? REV_TAIL_EM : REV_TAIL, block);
    }

    private static void copy(SourceCode code, String from, String to) {
        // Iterate over a snapshot: no-arg line() must not format the text.
        for (String line : new java.util.ArrayList<String>(code.blockLines(from))) {
            code.line(to, line);
        }
    }
}
