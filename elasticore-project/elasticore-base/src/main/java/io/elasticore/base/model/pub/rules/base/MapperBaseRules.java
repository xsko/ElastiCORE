package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.SourceCode;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Base rules for the per-entity section of the domain mapper, ported 1:1
 * from MapperSrcPublisher output. Produces two line blocks:
 *
 * "forward" - entity to DTO:
 *   mapping(E,DTO,skip) / mapping(E,DTO,skip,ctx) with per-field body,
 *   2-arg wrappers, toDTO pair, toXDTOList pair, toXDTOList(BiFunction)
 *
 * "reverse" - DTO to entity:
 *   mapping(DTO,E,...) 4 variants, EntityManager variants,
 *   toEntity pair, toEntity(EntityManager) pair,
 *   toXList pair, toXList(EntityManager) pair, toXList(BiFunction)
 *
 * Field handling mirrors 2.x exactly:
 * - each direction walks the fields of the model it writes INTO, so a
 *   template field the DTO dropped with "--" disappears from the reverse
 *   body while the DTO's own @ref fields never reach it
 * - a field converts through another DTO's mapper method, copies straight
 *   across, or drops out entirely when no mapping exists for its type
 * - forward: @ref(a.b) reads through the relation at the field's position;
 *   inherited fields are preceded once by the isSkip(entity, parent) boundary
 * - reverse: @ref fields write back at the END of the method - through the
 *   relation for a plain column, by attaching an instance for the relation's
 *   primary key - and there is no isSkip boundary
 *
 * Method separator lines carry four spaces, as in generated output.
 *
 * Instances keep per-run state: use a fresh instance per RuleEngine.run.
 */
public class MapperBaseRules implements CodeRules {

    public static final String FWD_BODY = "mapper-fwd-body";
    public static final String REV_BODY = "mapper-rev-body";
    public static final String REV_TAIL = "mapper-rev-tail";

    /** Separator between generated methods: two lines of four spaces. */
    private static final String SEP = "    ";

    /** Reverse body and tail again, for the EntityManager overload. */
    public static final String REV_BODY_EM = "mapper-rev-body-em";
    public static final String REV_TAIL_EM = "mapper-rev-tail-em";

    // ------------------------------------------------------------------
    // field walk - collect method bodies
    // ------------------------------------------------------------------

    /**
     * 2.x walks the fields of the model it writes INTO, so the forward pass
     * follows the DTO's fields and the reverse pass the entity's own, while
     * the @ref write-backs of the reverse pass follow the DTO's. A field with
     * no counterpart on the other side is skipped, which is how "--" removals
     * disappear from the reverse body.
     */
    @Rule(value = "mapper-fields", target = "mapper", order = 20)
    void fields(EntitySpec e) {
        SourceCode code = ctx(e);

        java.util.Set<String> entityNames =
                namesOf(e.allFields().isEmpty() ? e.fields() : e.allFields());
        java.util.Set<String> dtoNames = namesOf(e.dtoFields());

        forwardPass(code, e, entityNames);
        reversePass(code, e, dtoNames);
        reverseRefPass(code, e);
    }

    private static java.util.Set<String> namesOf(java.util.List<FieldSpec> list) {
        java.util.Set<String> names = new java.util.HashSet<String>();
        for (FieldSpec f : list) names.add(f.name());
        return names;
    }

    /** Entity to DTO: walks the DTO's fields. */
    private void forwardPass(SourceCode code, EntitySpec e, java.util.Set<String> entityNames) {
        boolean legacyBoundaryEmitted = false;
        String lastDeclaring = null;

        for (FieldSpec f : e.dtoFields()) {
            String ref = f.dslValue("ref");
            // The DTO dropped it for the identity field beside it.
            if (SearchDtoBaseRules.flattensAReferenceById(e, f)) continue;
            // Or because not everyone who reads this DTO may read that row.
            if (ServiceAccessRules.nestsGuarded(e, f)) continue;
            // Or because a rule left it out of the DTO (a related row's password).
            if (code.isRefHidden(f.name())) continue;
            if (ref != null && ref.contains(".")) {
                String[] path = ref.split("\\.");
                Syntax syntax = code.syntax();
                String relRead = syntax.getter("from", path[0]);
                code.line(FWD_BODY, "        if(hasValue(%s))", relRead)
                    .line(FWD_BODY, "            %s", syntax.statement(syntax.setter(
                            "to", f.name(), syntax.getter(relRead, path[1]))));
                continue;
            }

            Conversion conv = conversion(e, f, true, false);
            if (conv.skip) continue;
            // A DTO field the entity does not carry has nothing to read from.
            if (!entityNames.contains(f.name())) continue;
            // A password is never handed out.
            if (f.hasDsl("password")) continue;
            // Nor is any write-only value, in the clean output: a DTO read
            // back and saved by server code carried it in again - a stored
            // password hash arrived at the service as if it were a new one.
            // The compatible output keeps 2.x's copy.
            if (!code.compatible() && f.hasDslAny("writeonly", "nooutput")) continue;

            // Only the forward direction marks where an @abstract ancestor's
            // fields begin - one guard per ancestor, as the declaring model
            // changes along the walk. A spec built without model meta keeps
            // the single-boundary behavior.
            String declaring = f.declaringName();
            if (declaring != null) {
                if (lastDeclaring != null && !declaring.equals(lastDeclaring)
                        && f.declaringAbstract() && !declaring.equals(e.getName())) {
                    code.line(FWD_BODY, "        if(isSkip(%s,%s)) return;",
                            RuleText.quote(e.getName()), RuleText.quote(declaring));
                }
                lastDeclaring = declaring;
            } else if (f.isParentField() && !legacyBoundaryEmitted && e.parentName() != null) {
                code.line(FWD_BODY, "        if(isSkip(%s,%s)) return;",
                        RuleText.quote(e.getName()), RuleText.quote(e.parentName()));
                legacyBoundaryEmitted = true;
            }

            emitSetter(code, FWD_BODY, f, conv);
        }
    }

    /** DTO to entity: walks the entity's fields, once per overload. */
    private void reversePass(SourceCode code, EntitySpec e, java.util.Set<String> dtoNames) {
        java.util.List<FieldSpec> walk = e.allFields().isEmpty() ? e.fields() : e.allFields();
        for (FieldSpec f : walk) {
            if (f.dslValue("ref") != null) continue;

            // A field the DTO dropped with "--" has nothing to write from.
            if (!dtoNames.contains(f.name())) continue;
            if (ServiceAccessRules.nestsGuarded(e, f)) continue;
            // A field the persistence layer owns is read out, never written in;
            // in the clean output so is one the server sets (@readonly), and
            // one only a root's own methods change (WriteGuardRules, ddd-*).
            if (code.isSystemManaged(f.name())) continue;
            // So is a @formula column, computed by the database on read. In
            // every output, the 2.x-compatible one too (MapperSrcPublisher).
            if (f.hasDsl("formula")) continue;

            // The identity of a reference marked @updatable(false) (IdReferences
            // carries it over) is written when the row has none - on create -
            // and kept after: a review stays on its item.
            String once = !code.compatible() && f.hasDsl("idof") && "false".equals(f.dslValue("updatable"))
                    ? code.syntax().getter("to", f.name()) : null;

            Conversion plain = conversion(e, f, false, false);
            if (!plain.skip) emitSetter(code, REV_BODY, f, plain, code.mapperTarget(f.name()), once);

            Conversion managed = conversion(e, f, false, true);
            if (!managed.skip) emitSetter(code, REV_BODY_EM, f, managed, code.mapperTarget(f.name()), once);
        }
    }

    private void emitSetter(SourceCode code, String block, FieldSpec f, Conversion conv) {
        emitSetter(code, block, f, conv, "to");
    }

    private void emitSetter(SourceCode code, String block, FieldSpec f, Conversion conv, String target) {
        emitSetter(code, block, f, conv, target, null);
    }

    /** With unsetOnly, the field is written only while the target's value is null. */
    private void emitSetter(SourceCode code, String block, FieldSpec f, Conversion conv, String target, String unsetOnly) {
        if (unsetOnly != null) {
            code.line(block, "        if((c==null || c.fd(%s).checkEnable()) && %s == null)", RuleText.quote(f.name()), unsetOnly);
        } else {
            code.line(block, "        if(c==null || c.fd(%s).checkEnable())", RuleText.quote(f.name()));
        }

        // @forceupdate assigns straight through instead of going via setVal,
        // so the value is written even when it is null. A converted field goes
        // through its mapper method on the way.
        //
        // 2.x opened that call and never closed it, emitting code that does
        // not compile; only base types, which need no call at all, reached it
        // in practice. The call is closed here.
        Syntax syntax = code.syntax();
        String read = syntax.getter("from", f.name());

        if (f.hasDsl("forceupdate")) {
            String value = conv.method == null ? read : String.format("%s(%s)", conv.method, read);
            code.line(block, "        %s", syntax.statement(syntax.setter(target, f.name(), value)));
            return;
        }

        String write = syntax.setterRef(target, f.name());
        if (conv.func != null) {
            code.line(block, "        %s", syntax.statement(String.format(
                    "setVal(%s, %s, isSkipNull, %s)", read, write,
                    syntax.lambda("e", conv.func))));
        } else {
            code.line(block, "        %s", syntax.statement(String.format(
                    "setVal(%s, %s, isSkipNull)", read, write)));
        }
    }

    /**
     * The @ref write-backs of the reverse direction, walking the DTO's fields.
     * They trail the method body because they depend on the relation object
     * the body has already assigned.
     */
    private void reverseRefPass(SourceCode code, EntitySpec e) {
        for (FieldSpec f : e.dtoFields()) {
            String ref = f.dslValue("ref");
            if (ref == null || !ref.contains(".")) continue;
            if (SearchDtoBaseRules.flattensAReferenceById(e, f)) continue;
            if (code.isRefHidden(f.name())) continue;

            String[] path = ref.split("\\.");
            String relField = path[0];
            String childField = path[1];
            Syntax syntax = code.syntax();
            String relRead = syntax.getter("to", relField);

            // A cascading relation is written whole, so its parts are left alone.
            if (hasCascade(e, relField)) continue;

            // The relation's ENTITY type: the entity's field list names it,
            // while the DTO's own list carries the relation as its DTO.
            String relType = null;
            for (FieldSpec rel : e.allFields()) {
                if (rel.name().equals(relField)) {
                    relType = rel.typeName();
                    break;
                }
            }
            if (relType == null) relType = e.typeOfField(relField);
            String pk = relType == null ? null : e.pkFieldOf(relType);
            if (pk == null) pk = globalPkOf(relType);

            // A relation joined read-only on another field's column is not
            // written through: its key belongs to that field. The plain pass
            // writes it when the DTO names it the same; otherwise it is
            // written here, to that field.
            String owningField = compatibleOrNull(code, readOnlyJoinOwner(e, relField));
            if (pk != null && pk.equals(childField) && owningField != null) {
                if (!owningField.equals(f.name())) {
                    String write = code.syntax().setterRef(code.mapperTarget(owningField), owningField);
                    for (String block : new String[]{REV_TAIL, REV_TAIL_EM}) {
                        code.line(block, "        %s", code.syntax().statement(String.format(
                                "setVal(%s, %s, isSkipNull)", code.syntax().getter("from", f.name()), write)));
                    }
                }
            } else if (pk != null && pk.equals(childField)) {
                pkRefWriteBack(code, REV_TAIL, f, relField, childField, relType, false);
                pkRefWriteBack(code, REV_TAIL_EM, f, relField, childField, relType, true);
            } else {
                // Not the relation's key, so this would edit the related row
                // rather than point at it. A rule may decline that; nothing
                // does unless one asks, so the default output is unchanged.
                if (code.isRefReadOnly(f.name())) continue;

                String read = syntax.getter("from", f.name());
                for (String block : new String[]{REV_TAIL, REV_TAIL_EM}) {
                    code.line(block, "        if(hasValue(%s) && hasValue(%s))", read, relRead)
                        .line(block, "            %s", syntax.statement(
                                syntax.setter(relRead, childField, read)));
                }
            }
        }
    }

    /**
     * A @ref onto the relation's primary key identifies a row rather than
     * editing one, so the reverse direction attaches a reference instead of
     * writing through. With an EntityManager at hand that is a real proxy.
     */
    private void pkRefWriteBack(SourceCode code, String block, FieldSpec f,
                                String relField, String childField, String relType,
                                boolean withEntityManager) {
        Syntax syntax = code.syntax();
        String read = syntax.getter("from", f.name());
        String make = syntax.localVar(relType, "t", syntax.newInstance(relType, ""));
        String assign = syntax.statement(syntax.setter("t", childField, read));
        String target = code.mapperTarget(relField);
        String attach = syntax.statement(syntax.setter(target, relField, "t"));

        code.line(block, "        ").line(block, "        ")
            .line(block, "        if(hasValue(%s)){", read);

        if (withEntityManager) {
            code.line(block, "            if(em != null){")
                .line(block, "                %s", syntax.statement(syntax.setter(target, relField,
                        String.format("em.getReference(%s, %s)",
                                syntax.javaClassLiteral(relType), read))))
                .line(block, "            }")
                .line(block, "            else{")
                .line(block, "                %s", make)
                .line(block, "                %s", assign)
                .line(block, "                %s", attach)
                .line(block, "            }");
        } else {
            code.line(block, "            %s", make)
                .line(block, "            %s", assign)
                .line(block, "            %s", attach);
        }
        code.line(block, "        }");
    }

    /**
     * The field whose column a relation is joined on read-only (@joinBy), or
     * null. The compatible output keeps writing the relation, as 2.x does.
     */
    private static String readOnlyJoinOwner(EntitySpec e, String relField) {
        for (FieldSpec rel : e.allFields().isEmpty() ? e.fields() : e.allFields()) {
            if (!rel.name().equals(relField)) continue;
            String owner = rel.dslValueAny("joinby", "joinfield");
            return owner == null || owner.isEmpty() ? null : owner;
        }
        return null;
    }

    private static String compatibleOrNull(SourceCode code, String value) {
        return code.compatible() ? null : value;
    }

    private boolean hasCascade(EntitySpec e, String fieldName) {
        for (FieldSpec f : e.fields()) {
            if (f.name().equals(fieldName)) return f.hasDsl("cascade");
        }
        for (FieldSpec f : e.allFields()) {
            if (f.name().equals(fieldName)) return f.hasDsl("cascade");
        }
        return false;
    }

    // ------------------------------------------------------------------
    // per-element conversion
    // ------------------------------------------------------------------

    /**
     * Outcome of resolving a field: skipped, plain, or converted.
     *
     * A converted field keeps both the mapper method's name and the call the
     * per-element lambda makes, because the two assignment forms need
     * different things: setVal passes the element and the context, while a
     * straight assignment passes the value alone.
     */
    private static final class Conversion {
        final boolean skip;
        final String method;
        final String func;

        private Conversion(boolean skip, String method, String func) {
            this.skip = skip;
            this.method = method;
            this.func = func;
        }

        static final Conversion SKIP = new Conversion(true, null, null);
        static final Conversion PLAIN = new Conversion(false, null, null);

        static Conversion of(String method, String func) {
            return new Conversion(false, method, func);
        }
    }

    /**
     * Decides how one field crosses the boundary. A base type copies straight
     * across; a type backed by a DTO goes through that DTO's mapper method;
     * an enum copies straight across as well; anything else has no mapping and
     * drops out of the method entirely.
     */
    private Conversion conversion(EntitySpec e, FieldSpec f, boolean toDto, boolean withEntityManager) {
        if (f.isBaseType()) return Conversion.PLAIN;

        // @dtype names the DTO-side type explicitly and may itself be a list.
        String declared = f.dslValue("dtype");
        boolean isList;
        String checkType;
        if (declared != null && !declared.isEmpty()) {
            isList = declared.startsWith("List<");
            checkType = isList ? typeParameterOf(declared) : declared;
        } else {
            isList = f.isList();
            checkType = isList ? f.coreItemType() : f.typeName();
        }

        String mapper = e.isKnownDto(checkType) ? "" : foreignMapperOf(checkType, e);
        if (e.isKnownDto(checkType) || mapper != null) {
            if (mapper == null) mapper = "";
            if (isList) {
                // A list of entity-backed DTOs is not written back: the owning
                // side of the relation manages it.
                if (!toDto && mapper.isEmpty()
                        && !e.dtoTemplateEntity(checkType).isEmpty()) return Conversion.SKIP;
                String method = String.format("to%sList", checkType);
                return Conversion.of(method, String.format("%s%s(e,c%s)", mapper, method,
                        withEntityManager ? ",em" : ""));
            }
            String method = toDto ? "toDTO" : "toEntity";
            return Conversion.of(method, toDto ? String.format("%stoDTO(e,c)", mapper)
                    : (withEntityManager ? String.format("%stoEntity(e,c,em)", mapper)
                                         : String.format("%stoEntity(e,c)", mapper)));
        }

        // An enum needs no conversion; anything else has no mapper to call.
        // 2.x resolves the type through the global context, so an enum
        // inherited from another domain's base entity still passes through.
        if (e.isKnownEnum(checkType) || isGlobalEnum(checkType)) return Conversion.PLAIN;
        // A value object is immutable and has no identity: the same instance
        // can sit on both sides, and replacing it is how it changes. v3 and
        // Kotlin carry it in the DTO; 2.x does not, and its output stays as is.
        if (!isList && e.valueObjectsInDto() && e.isEmbeddableType(checkType) && !ctx(e).compatible()) {
            return Conversion.PLAIN;
        }
        return Conversion.SKIP;
    }

    /**
     * The "OtherMapper." prefix a cross-domain DTO maps through, "" for a
     * same-domain one the spec somehow missed, or null when the type is no
     * DTO at all. Mirrors 2.x getMapperName.
     */
    private static String foreignMapperOf(String typeName, EntitySpec e) {
        if (typeName == null) return null;
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            io.elasticore.base.model.dto.DataTransfer dto = ctx == null ? null
                    : ctx.findModelComponent(typeName, io.elasticore.base.model.dto.DataTransfer.class);
            if (dto == null) return null;
            String domainId = dto.getIdentity().getDomainId();
            if (domainId == null || domainId.equals(e.domainName())) return "";
            return String.format("%sMapper.", RuleText.capital(domainId));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Whether the loaded context knows the type as an enumeration. */
    private static boolean isGlobalEnum(String typeName) {
        if (typeName == null) return false;
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            return ctx != null && ctx.findModelComponent(typeName,
                    io.elasticore.base.model.enums.EnumModel.class) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The primary key field of an entity, resolved globally as 2.x does. */
    private static String globalPkOf(String entityName) {
        if (entityName == null) return null;
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            io.elasticore.base.model.entity.Entity entity = ctx == null ? null
                    : ctx.findModelComponent(entityName, io.elasticore.base.model.entity.Entity.class);
            if (entity == null || entity.getPkField() == null) return null;
            return entity.getPkField().getName();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String typeParameterOf(String listType) {
        int s = listType.indexOf('<');
        int t = listType.lastIndexOf('>');
        return (s < 0 || t < s) ? listType : listType.substring(s + 1, t).trim();
    }

    private static final String REV_TAIL_PK = "mapper-rev-tail-pk";
    private static final String REV_TAIL_PK_EM = "mapper-rev-tail-pk-em";

    // ------------------------------------------------------------------
    // assembly - build the two sections after all field rules ran
    // ------------------------------------------------------------------

}
