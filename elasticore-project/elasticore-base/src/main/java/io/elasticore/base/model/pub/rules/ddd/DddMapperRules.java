package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.base.MapperBaseRules;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The boundary, kept where it is easiest to lose: the mapper.
 *
 * {@link DddReferenceRules} makes a reference to another aggregate lazy, and
 * the entity it writes says so in a comment. The mapper then contradicts it.
 * A DTO field declared "@ref(customer.firstName)" is read from the related
 * entity going out and assigned back coming in, so saving an Order edits a
 * Customer - the exact transaction an aggregate exists to prevent, written by
 * the same generator that had just promised otherwise.
 *
 * Reading is fine and stays: showing the customer's name on an order is what
 * a view is for. Only the way back is declined.
 *
 * Assigning the relation is also fine and also stays. Where the ref names the
 * related entity's key, the mapper points the reference at that row instead of
 * editing one - reference by identity, which is the form this boundary wants.
 * That branch is untouched; this rule only reaches the one that writes.
 *
 * Runs before the mapper's own rules at 20, because what it states is read
 * while they emit.
 */
public class DddMapperRules implements CodeRules {

    @Rule(value = "ddd-mapper-ref", target = "mapper", order = 10)
    void refsAcrossBoundaryAreReadOnly(EntitySpec e) {
        SourceCode c = ctx(e);

        // A single member is written by membersArriveWithTheirRoot instead:
        // the base mapper would build a new one on every update and leave the
        // row it replaced behind.
        for (FieldSpec f : e.fields()) {
            if (singleMember(e, f) != null) c.systemManaged(f.name());
        }

        for (FieldSpec f : e.dtoFields()) {
            String ref = f.dslValue("ref");
            if (ref == null || ref.indexOf('.') < 0) continue;

            if (leavesAggregate(e, ref.substring(0, ref.indexOf('.')))) {
                c.refReadOnly(f.name());
            }
        }
    }

    /**
     * The way into the aggregate that the boundary took away.
     *
     * The base mapper does not write a collection of entity-backed DTOs back,
     * and says why: the owning side of the relation manages it. That is right
     * for the model it was written for, where a member has an endpoint of its
     * own and is posted to it.
     *
     * These rules removed that endpoint. The reasoning still holds and the
     * premise no longer does, so the root has to accept its members with
     * itself or they cannot be sent at all - which is what the sample found:
     * an order was created and its lines silently dropped.
     *
     * Runs after the mapper has written the body it appends to.
     *
     * Java only, because what it calls is: the root's replaceX() comes from
     * {@link DddCollectionRules}, which states there why it has no Kotlin
     * form yet. Writing this half without that one would emit a call to a
     * method nothing generated - and in Java syntax, into a Kotlin mapper.
     */
    @Rule(value = "ddd-mapper-members", target = "mapper", order = 30)
    void membersArriveWithTheirRoot(EntitySpec e) {
        SourceCode c = ctx(e);
        io.elasticore.base.model.pub.rules.Syntax syntax = c.syntax();
        if (syntax != io.elasticore.base.model.pub.rules.Syntax.JAVA
                && syntax != io.elasticore.base.model.pub.rules.Syntax.KOTLIN) {
            return;
        }

        java.util.Set<String> written = new java.util.HashSet<String>();

        for (FieldSpec f : e.fields()) {
            if (!f.isList() || !written.add(f.name())) continue;
            String[] member = listMember(e, f);
            if (member == null) continue;
            writeMembers(c, f, member[0], member[1], MapperBaseRules.REV_BODY, false);
            writeMembers(c, f, member[0], member[1], MapperBaseRules.REV_BODY_EM, true);
        }

        for (FieldSpec f : e.fields()) {
            String memberEntity = singleMember(e, f);
            if (memberEntity == null || !written.add(f.name())) continue;
            writeMember(c, f, memberEntity, MapperBaseRules.REV_BODY, false);
            writeMember(c, f, memberEntity, MapperBaseRules.REV_BODY_EM, true);
        }
    }

    /**
     * What arrives with the root is validated with it.
     *
     * The mapper writes these members into the aggregate, so their own
     * constraints - a line's @min(1) qty - are the request's to meet. Bean
     * Validation stops at a nested object unless told to cascade, and without
     * it a line with qty 0 was stored under a DSL that said it could not be.
     */
    @Rule(value = "ddd-dto-members-valid", target = "dto", order = 22)
    void membersAreValidatedWithTheirRoot(EntitySpec e, FieldSpec f) {
        String memberDto = f.coreItemType();
        if (memberDto == null) return;
        String memberEntity;
        if (!memberDto.endsWith("DTO") && e.isKnownEntity(memberDto) && f.dslValue("dtype") != null) {
            // Taken from the template entity: the type names the member itself.
            memberEntity = memberDto;
        } else {
            if (!memberDto.endsWith("DTO")) return;
            memberEntity = e.dtoTemplateEntity(memberDto);
        }
        if (memberEntity == null || memberEntity.isEmpty()) return;
        if (!e.knowsAggregateOf(memberEntity) || !e.sameAggregate(memberEntity)) return;
        if (!e.getName().equals(e.aggregateOf(memberEntity))) return;
        ctx(e).field(f).ensure(io.elasticore.base.model.pub.rules.AnnoCode.of("jakarta.validation.Valid"));
    }

    /**
     * {member DTO, member entity} of a collection of this aggregate's
     * members, which membersArriveWithTheirRoot writes; null for any other.
     */
    private static String[] listMember(EntitySpec e, FieldSpec f) {
        // This spec views a DTO, so the collection names a DTO type. The
        // aggregate is a fact about entities, so the type is resolved back
        // to the one it stands for before the boundary is asked about it.
        String memberDto = f.coreItemType();
        String memberEntity = e.dtoTemplateEntity(memberDto);
        // A collection the DTO takes from its template entity names the
        // entity itself; its DTO is the one @dtype says, else <Entity>DTO.
        if ((memberEntity == null || memberEntity.isEmpty()) && e.isKnownEntity(memberDto)) {
            memberEntity = memberDto;
            String dtype = f.dslValue("dtype");
            if (dtype != null && dtype.contains("<") && dtype.endsWith(">")) {
                memberDto = dtype.substring(dtype.indexOf('<') + 1, dtype.length() - 1).trim();
            } else {
                memberDto = memberEntity + "DTO";
            }
        }
        if (memberEntity == null || memberEntity.isEmpty()) return null;
        if (!e.sameAggregate(memberEntity)) return null;
        return new String[]{memberDto, memberEntity};
    }

    /**
     * Says which relations the root writes its members through, before the
     * rules at 10 ask: a member is changed through its root by design, and
     * a rule that keeps another entity's row out of the mapping
     * (access-mapper-nested) leaves these to the root.
     */
    @Rule(value = "ddd-mapper-member-marks", target = "mapper", order = 5)
    void markWhatTheRootWrites(EntitySpec e) {
        SourceCode c = ctx(e);
        for (FieldSpec f : e.fields()) {
            if ((f.isList() && listMember(e, f) != null) || singleMember(e, f) != null) {
                c.writtenByRoot(f.name());
            }
        }
    }

    /**
     * A member's @password and @access(admin) fields are not written from a
     * DTO. A member has no service of its own here: it arrives with its root,
     * and the root's service hashes and checks the root's own fields only -
     * a password a request sent through the root was stored as sent, and a
     * field only the administrator writes was the root's owner's to set.
     * Declared on the root, they are hashed and checked.
     */
    @Rule(value = "ddd-mapper-member-guards", target = "mapper", order = 10)
    void membersKeepWhatOnlyTheirServiceMayWrite(EntitySpec e) {
        if (e.aggregateRoot()) return;
        SourceCode c = ctx(e);
        for (FieldSpec f : e.fields()) {
            boolean password = f.hasDsl("password");
            String access = f.dslValue("access");
            boolean admin = access != null && "admin".equalsIgnoreCase(access.trim());
            if (!password && !admin) continue;
            c.systemManaged(f.name());
            SourceCode.dslWarnOnce(String.format(
                    "[%s] %s.%s is not written from a DTO: %s is a member of %s's aggregate and arrives with it,"
                            + " and the root's service %s only its own fields. Declare it on %s.",
                    e.domainClassName(), e.getName(), f.name(), e.getName(), e.ownAggregate(),
                    password ? "hashes a @password of" : "checks @access(admin) on", e.ownAggregate()));
        }
    }

    /**
     * The entity a DTO field holds when it is one member of this aggregate -
     * a to-one inside the boundary, like a customer's VIP profile - or null.
     */
    private String singleMember(EntitySpec e, FieldSpec f) {
        if (f.isList() || f.isBaseType()) return null;
        String memberDto = f.coreItemType();
        if (memberDto == null || !memberDto.endsWith("DTO")) return null;
        String memberEntity = e.dtoTemplateEntity(memberDto);
        if (memberEntity == null || memberEntity.isEmpty()) return null;
        if (!e.knowsAggregateOf(memberEntity) || !e.sameAggregate(memberEntity)) return null;
        // From the root to its member only. The member's reference back to
        // its root is the same aggregate too, and writing through it would
        // let a request edit the root by way of one of its parts.
        if (!e.getName().equals(e.aggregateOf(memberEntity))) return null;
        return memberEntity;
    }

    /**
     * One member, changed where it stands.
     *
     * The member the root already has is updated in place, so it keeps its
     * identity and its row; only a root that has none gets a new one. A
     * member that is replaced wholesale on every request is a new row each
     * time and an abandoned one behind it.
     */
    private void writeMember(SourceCode c, FieldSpec f, String item, String block, boolean withEntityManager) {
        io.elasticore.base.model.pub.rules.Syntax syntax = c.syntax();
        String read = syntax.getter("from", f.name());
        String current = syntax.getter("to", f.name());
        String call = withEntityManager ? "mapping(d, m, isSkipNull, c, em)" : "mapping(d, m, isSkipNull, c)";
        String memberDto = f.coreItemType();
        // The member's identity is its own, never the request's: a different
        // id would alter the key of a loaded row, and an echoed one on a
        // create would persist a detached instance.
        String pk = c.getEntity().keyFieldOf(item);

        if (syntax == io.elasticore.base.model.pub.rules.Syntax.KOTLIN) {
            c.line(block, "        if(c==null || c.fd(%s).checkEnable())", RuleText.quote(f.name()))
             .line(block, "        if(hasValue(%s)){", read)
             .line(block, "            val d = %s!!", read)
             .line(block, "            var m = %s", current);
            if (pk != null) c.line(block, "            d.%s = m?.%s", pk, pk);
            c.line(block, "            if (m == null) m = %s()", item)
             .line(block, "            " + call)
             .line(block, "            %s.%s = m", c.mapperTarget(f.name()), f.name());
            String ktOwner = c.getEntity().ownerFieldOf(item);
            if (ktOwner != null) c.line(block, "            m.%s = to", ktOwner);
            c.line(block, "        }");
            return;
        }

        c.line(block, "        if(c==null || c.fd(%s).checkEnable())", RuleText.quote(f.name()))
         .line(block, "        if(hasValue(%s)){", read)
         .line(block, "            %s d = %s;", memberDto, read)
         .line(block, "            %s m = %s;", item, current);
        if (pk != null) {
            c.line(block, "            %s;", syntax.setter("d", pk,
                    String.format("m != null ? %s : null", syntax.getter("m", pk))));
        }
        c.line(block, "            if (m == null) m = new %s();", item)
         .line(block, "            " + call + ";")
         .line(block, "            %s;", syntax.setter(c.mapperTarget(f.name()), f.name(), "m"));
        // And the member says whose it is: a new member written through its
        // root would otherwise have no owner on its side of the relation.
        String owner = c.getEntity().ownerFieldOf(item);
        if (owner != null) c.line(block, "            %s;", syntax.setter("m", owner, "to"));
        c.line(block, "        }");
    }

    /**
     * The one block that hands an aggregate its members, in either language.
     *
     * Written out per language rather than through the seam because these are
     * bodies: the loop, the local and the collection read differ in more than
     * spelling, and a Kotlin property that is nullable needs the assertion
     * that a Java getter does not.
     */
    private void writeMembers(SourceCode c, FieldSpec f, String memberDto,
                              String item, String block, boolean withEntityManager) {
        String read = c.syntax().getter("from", f.name());
        String call = withEntityManager ? "mapping(d, m, isSkipNull, c, em)"
                                        : "mapping(d, m, isSkipNull, c)";

        if (c.syntax() == io.elasticore.base.model.pub.rules.Syntax.KOTLIN) {
            c.line(block, "        if(c==null || c.fd(%s).checkEnable())", RuleText.quote(f.name()))
             .line(block, "        if(hasValue(%s)){", read)
             .line(block, "            val members = java.util.ArrayList<%s>()", item)
             .line(block, "            for(d in %s!!){", read)
             .line(block, "                val m = %s()", item)
             .line(block, "                %s", call)
             .line(block, "                members.add(m)")
             .line(block, "            }")
             .line(block, "            %s.replace%s(members)", c.mapperTarget(f.name()), f.capitalName())
             .line(block, "        }");
            return;
        }

        c.line(block, "        if(c==null || c.fd(%s).checkEnable())", RuleText.quote(f.name()))
         .line(block, "        if(hasValue(%s)){", read)
         .line(block, "            java.util.List<%s> members = new java.util.ArrayList<%s>();", item, item)
         .line(block, "            for(%s d : %s){", memberDto, read)
         .line(block, "                %s m = new %s();", item, item)
         .line(block, "                %s;", call)
         .line(block, "                members.add(m);")
         .line(block, "            }")
         .line(block, "            %s.replace%s(members);", c.mapperTarget(f.name()), f.capitalName())
         .line(block, "        }");
    }

    /**
     * Whether the ref reads through a relation to another entity.
     *
     * Only that much is decided here. Whether the relation leaves the
     * aggregate is already settled by the time the statement is read: the
     * mapper skips a cascading relation - one whose rows are part of what is
     * being saved - before it ever asks, and answers a ref naming the
     * relation's key by pointing the reference rather than writing through it.
     * What is left to decline is exactly the case this boundary forbids.
     *
     * The relation is looked for the way the mapper looks for it, the entity's
     * full field list first and its declared type second, because a field
     * arriving through @extend is absent from one and named by the other.
     */
    private boolean leavesAggregate(EntitySpec e, String relationField) {
        FieldSpec relation = relationNamed(e, relationField);
        String type = relation != null && relation.isEntityRef()
                ? relation.coreItemType()
                : e.typeOfField(relationField);
        if (type == null || !e.isKnownEntity(type)) return false;

        if (e.knowsAggregateOf(type)) return !e.sameAggregate(type);

        // Nothing known about the type: the mapper has already skipped the
        // cascading case by the time it asks, so what is left crosses.
        return relation == null || !relation.hasDsl("cascade");
    }

    /**
     * The relation the ref reads through, looked for the way the mapper looks
     * for it - the entity's full field list first and its own second, because
     * a field arriving through @extend is in one and not the other.
     */
    private FieldSpec relationNamed(EntitySpec e, String name) {
        for (FieldSpec rel : e.allFields()) {
            if (rel.name().equals(name)) return rel;
        }
        for (FieldSpec rel : e.fields()) {
            if (rel.name().equals(name)) return rel;
        }
        return null;
    }
}
