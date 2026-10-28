package io.elasticore.base.model.pub.rules.base;

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
 * The mapper's Java skeleton: the mapping, toDTO, toEntity and list methods
 * that surround the per-field lines {@link MapperBaseRules} collected.
 *
 * Separated from the field walk because the two answer different questions.
 * The walk decides what maps to what, which owes nothing to the language; this
 * is a fixed set of methods parameterised by four names, and every line of it
 * is Java. A language that writes them differently supplies its own rule set
 * in place of this one rather than this one branching.
 *
 * Method separator lines carry four spaces, as in generated output.
 */
public class MapperAssembleRules implements CodeRules {

    /** Separator between generated methods: two lines of four spaces. */
    private static final String SEP = "    ";

    @Rule(value = "mapper-assemble", target = "mapper", order = 90, phase = Rule.Phase.TAIL)
    void assemble(EntitySpec e) {
        String en = e.getName();
        String dto = e.dtoClassName();
        String mapper = String.format("%sMapper", e.domainClassName());

        // The entity's primary DTO owns the plain toDTO name; a second DTO of
        // the same entity is reached by its own name, or the two would declare
        // the same method twice. 2.x keeps the taken names in mapperMethodSet.
        String toDto = dto.equals(String.format("%sDTO", en))
                ? "toDTO" : String.format("to%s", dto);

        // List<ADTO> and List<BDTO> erase to the same parameter, so the
        // reverse list methods of a second DTO need a name of their own.
        String revList = e.mapperOrdinal() > 1
                ? String.format("%s%d", en, e.mapperOrdinal()) : en;
        SourceCode code = ctx(e);

        // ---- forward section ----
        String fwd = "forward";
        code.line(fwd, "    public static void mapping(%s from, %s to, boolean isSkipNull){", en, dto)
            .line(fwd, "        mapping(from,to,isSkipNull,(MappingContext) null);")
            .line(fwd, "    }")
            .line(fwd, "    public static void mapping(%s from, %s to, boolean isSkipNull, MappingContext c){", en, dto)
            .line(fwd, "        if(c!=null && !c.checkEnable()) return;")
            .line(fwd, "        checkPermission(from, to);")
            .line(fwd, "        if(from ==null || to ==null) return;");
        copy(code, FWD_BODY, fwd);
        code.line(fwd, "    }")
            .line(fwd, SEP).line(fwd, SEP)
            .line(fwd, "    public static void mapping(%s from, %s to){", en, dto)
            .line(fwd, "        mapping(from,to,false);")
            .line(fwd, "    }")
            .line(fwd, "    public static void mapping(%s from, %s to, MappingContext c){", en, dto)
            .line(fwd, "        mapping(from,to,false,c);")
            .line(fwd, "    }")
            .line(fwd, SEP).line(fwd, SEP)
            .line(fwd, "    public static %s %s(%s from){", dto, toDto, en)
            .line(fwd, "        return %s(from,MappingContext.withGuard(2,null));", toDto)
            .line(fwd, "    }")
            .line(fwd, "    public static %s %s(%s from,MappingContext c1){", dto, toDto, en)
            .line(fwd, "        MappingContext c=c1!=null?c1.getChild():null;")
            .line(fwd, "        if(c!=null && !c.checkEnable()) return null;")
            .line(fwd, "        if(from==null) return null;")
            .line(fwd, "        %s to = new %s();", dto, dto)
            .line(fwd, "        mapping(from,to,c);")
            .line(fwd, "        return to;")
            .line(fwd, "    }")
            .line(fwd, SEP).line(fwd, SEP)
            .line(fwd, "    public static List<%s> to%sList(List<%s> fromList){", dto, dto, en)
            .line(fwd, "        return to%sList(fromList,(MappingContext) null);", dto)
            .line(fwd, "    }")
            .line(fwd, "    public static List<%s> to%sList(List<%s> fromList, MappingContext c){", dto, dto, en)
            .line(fwd, "        if(c!=null && !c.checkEnable(1)) return null;")
            .line(fwd, "        if(fromList==null) return null;")
            .line(fwd, "        return fromList.stream().map(e->%s.%s(e,c)).collect(Collectors.toList());", mapper, toDto)
            .line(fwd, "    }")
            .line(fwd, SEP).line(fwd, SEP)
            .line(fwd, "    public static List<%s> to%sList(List<%s> fromList, BiFunction<%s, %s, %s> modifier){",
                    dto, dto, en, en, dto, dto)
            .line(fwd, "        if(fromList==null) return null;")
            .line(fwd, "        return fromList.stream()")
            .line(fwd, "            .map(from -> {")
            .line(fwd, "                %s to = %s(from);", dto, toDto)
            .line(fwd, "                return modifier.apply(from, to);")
            .line(fwd, "            }")
            .line(fwd, "            )")
            .line(fwd, "            .filter(Objects::nonNull)")
            .line(fwd, "            .collect(Collectors.toList());")
            .line(fwd, "        ")
            .line(fwd, "    }");

        // ---- reverse section ----
        String rev = "reverse";
        code.line(rev, "    public static void mapping(%s from, %s to, boolean isSkipNull){", dto, en)
            .line(rev, "        mapping(from,to,isSkipNull,(MappingContext) null);")
            .line(rev, "    }")
            .line(rev, "    public static void mapping(%s from, %s to, boolean isSkipNull, MappingContext c){", dto, en);
        reverseBody(code, rev, false);
        code.line(rev, "    }")
            .line(rev, SEP).line(rev, SEP)
            .line(rev, "    public static void mapping(%s from, %s to){", dto, en)
            .line(rev, "        mapping(from,to,false);")
            .line(rev, "    }")
            .line(rev, "    public static void mapping(%s from, %s to, MappingContext c){", dto, en)
            .line(rev, "        mapping(from,to,false,c);")
            .line(rev, "    }")
            .line(rev, SEP).line(rev, SEP);
        // The EntityManager overloads attach a JPA reference for a relation's
        // key; a MyBatis row holds the key itself, which the plain ones set.
        boolean em = !io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(e);
        if (em) {
        code.line(rev, "    public static void mapping(%s from, %s to, EntityManager em){", dto, en)
            .line(rev, "        mapping(from,to,false,null,em);")
            .line(rev, "    }")
            .line(rev, "    public static void mapping(%s from, %s to, boolean isSkipNull, EntityManager em){", dto, en)
            .line(rev, "        mapping(from,to,isSkipNull,null,em);")
            .line(rev, "    }")
            .line(rev, "    public static void mapping(%s from, %s to, MappingContext c, EntityManager em){", dto, en)
            .line(rev, "        mapping(from,to,false,c,em);")
            .line(rev, "    }")
            .line(rev, "    public static void mapping(%s from, %s to, boolean isSkipNull, MappingContext c, EntityManager em){", dto, en);
        reverseBody(code, rev, true);
        code.line(rev, "    }")
            .line(rev, SEP).line(rev, SEP);
        }
        code.line(rev, "    public static %s toEntity(%s from){", en, dto)
            .line(rev, "        return toEntity(from,MappingContext.withGuard(2,null));")
            .line(rev, "    }")
            .line(rev, "    public static %s toEntity(%s from,MappingContext c1){", en, dto)
            .line(rev, "        MappingContext c=c1!=null?c1.getChild():null;")
            .line(rev, "        if(c!=null && !c.checkEnable()) return null;")
            .line(rev, "        if(from==null) return null;")
            .line(rev, "        %s to = new %s();", en, en)
            .line(rev, "        mapping(from,to,c);")
            .line(rev, "        return to;")
            .line(rev, "    }")
            .line(rev, SEP).line(rev, SEP);
        if (em) {
        code.line(rev, "    public static %s toEntity(%s from, EntityManager em){", en, dto)
            .line(rev, "        return toEntity(from,MappingContext.withGuard(2,null),em);")
            .line(rev, "    }")
            .line(rev, "    public static %s toEntity(%s from, MappingContext c1, EntityManager em){", en, dto)
            .line(rev, "        MappingContext c=c1!=null?c1.getChild():null;")
            .line(rev, "        if(c!=null && !c.checkEnable()) return null;")
            .line(rev, "        if(from==null) return null;")
            .line(rev, "        %s to = new %s();", en, en)
            .line(rev, "        mapping(from,to,c,em);")
            .line(rev, "        return to;")
            .line(rev, "    }")
            .line(rev, SEP).line(rev, SEP);
        }
        code.line(rev, "    public static List<%s> to%sList(List<%s> fromList){", en, revList, dto)
            .line(rev, "        return to%sList(fromList,(MappingContext) null);", revList)
            .line(rev, "    }")
            .line(rev, "    public static List<%s> to%sList(List<%s> fromList, MappingContext c){", en, revList, dto)
            .line(rev, "        if(c!=null && !c.checkEnable(1)) return null;")
            .line(rev, "        if(fromList==null) return null;")
            .line(rev, "        return fromList.stream().map(e->%s.toEntity(e,c)).collect(Collectors.toList());", mapper)
            .line(rev, "    }")
            .line(rev, SEP).line(rev, SEP);
        if (em) {
        code.line(rev, "    public static List<%s> to%sList(List<%s> fromList, EntityManager em){", en, revList, dto)
            .line(rev, "        return to%sList(fromList,(MappingContext) null,em);", revList)
            .line(rev, "    }")
            .line(rev, "    public static List<%s> to%sList(List<%s> fromList, MappingContext c, EntityManager em){", en, revList, dto)
            .line(rev, "        if(c!=null && !c.checkEnable(1)) return null;")
            .line(rev, "        if(fromList==null) return null;")
            .line(rev, "        return fromList.stream().map(e->%s.toEntity(e,c,em)).collect(Collectors.toList());", mapper)
            .line(rev, "    }")
            .line(rev, SEP).line(rev, SEP);
        }
        code.line(rev, "    public static List<%s> to%sList(List<%s> fromList, BiFunction<%s, %s, %s> modifier){",
                    en, revList, dto, dto, en, en)
            .line(rev, "        if(fromList==null) return null;")
            .line(rev, "        return fromList.stream()")
            .line(rev, "            .map(from -> {")
            .line(rev, "                %s to = toEntity(from);", en)
            .line(rev, "                return modifier.apply(from, to);")
            .line(rev, "            }")
            .line(rev, "            )")
            .line(rev, "            .filter(Objects::nonNull)")
            .line(rev, "            .collect(Collectors.toList());")
            .line(rev, "        ")
            .line(rev, "    }");
    }

    /**
     * Body of a reverse mapping method. The EntityManager overload converts
     * through the em-aware methods and attaches a managed reference for a
     * primary-key @ref, so it gets its own collected lines.
     */
    private void reverseBody(SourceCode code, String block, boolean withEntityManager) {
        code.line(block, "        if(c!=null && !c.checkEnable()) return;")
            .line(block, "        checkPermission(from, to);")
            .line(block, "        if(from ==null || to ==null) return;");
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
