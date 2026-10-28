package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.MapperBaseRules;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.ddd.DddMapperRules;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The members of an aggregate arrive with their root, in a MyBatis domain.
 *
 * The layered mapper does not write a collection of entity-backed DTOs back:
 * under JPA its rows are posted to their own endpoint, or cascade. A member -
 * an order's line, which has no service or endpoint of its own - can then
 * only be sent with its root, so the root's DTO carries its lines and this
 * writes them onto the entity: a new list (the service sees it was replaced),
 * each member pointing back at the root. The MyBatis service writes them
 * (MyBatisServiceFileRules). The members' own constraints are checked with
 * the root's DTO (@Valid), as they are where DDD rules write the same.
 */
public class MyBatisMapperMemberRules implements CodeRules {

    /** Says, before the rules at 10 ask, which relations the root writes its members through. */
    @Rule(value = "mybatis-mapper-member-marks", target = "mapper", order = 5)
    void marks(EntitySpec e) {
        if (!MyBatisSql.applies(e)) return;
        for (FieldSpec f : e.fields()) {
            if (f.isList() && member(e, f) != null) ctx(e).writtenByRoot(f.name());
        }
    }

    /**
     * {member DTO, member entity} of a collection the service writes with
     * its root (MyBatisSql.isMemberOf: the root's own, no service of its
     * own), or null - the same collections, so a member the mapper fills is
     * one the service stores.
     */
    static String[] member(EntitySpec e, FieldSpec f) {
        String[] member = DddMapperRules.listMember(e, f);
        if (member == null || !e.getName().equals(e.aggregateOf(member[1])) || MyBatisSql.hasOwnService(member[1])) {
            return null;
        }
        return member;
    }

    @Rule(value = "mybatis-mapper-members", target = "mapper", order = 30)
    void members(EntitySpec e) {
        if (!MyBatisSql.applies(e)) return;
        SourceCode c = ctx(e);
        if (c.syntax() != Syntax.JAVA) return;
        java.util.Set<String> written = new java.util.HashSet<String>();
        for (FieldSpec f : e.fields()) {
            if (!f.isList() || !written.add(f.name())) continue;
            String[] member = member(e, f);
            if (member == null) continue;
            String read = c.syntax().getter("from", f.name());
            String owner = e.ownerFieldOf(member[1]);
            // Not hasValue(), which an empty list fails: [] replaces the members with none.
            c.line(MapperBaseRules.REV_BODY, "        if(c==null || c.fd(%s).checkEnable())", RuleText.quote(f.name()))
             .line(MapperBaseRules.REV_BODY, "        if(%s != null){", read)
             .line(MapperBaseRules.REV_BODY, "            java.util.List<%s> members = new java.util.ArrayList<%s>();", member[1], member[1])
             .line(MapperBaseRules.REV_BODY, "            for(%s d : %s){", member[0], read)
             .line(MapperBaseRules.REV_BODY, "                %s m = new %s();", member[1], member[1])
             .line(MapperBaseRules.REV_BODY, "                mapping(d, m, isSkipNull, c);");
            if (owner != null) {
                c.line(MapperBaseRules.REV_BODY, "                %s;", c.syntax().setter("m", owner, "to"));
            }
            c.line(MapperBaseRules.REV_BODY, "                members.add(m);")
             .line(MapperBaseRules.REV_BODY, "            }")
             .line(MapperBaseRules.REV_BODY, "            %s;", c.syntax().setter(c.mapperTarget(f.name()), f.name(), "members"))
             .line(MapperBaseRules.REV_BODY, "        }");
        }
    }

    /** What arrives with the root is validated with it: a line's @min(1) quantity is the request's to meet. */
    @Rule(value = "mybatis-dto-members-valid", target = "dto", order = 22)
    void valid(EntitySpec e, FieldSpec f) {
        if (!MyBatisSql.applies(e) || !f.isList()) return;
        if (member(e, f) == null) return;
        ctx(e).field(f).ensure(io.elasticore.base.model.pub.rules.AnnoCode.of("jakarta.validation.Valid"));
    }
}
