package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

/**
 * What a generated write throws when it is refused after the request was put onto the row it read
 * (issue 344): the refusal, once the row is discarded from what the transaction reads - the JPA entity
 * read again (io.elasticore.springboot3.entity.RefusedWrite), the MyBatis session's cache cleared
 * (io.elasticore.springboot3.mybatis.RefusedWrite).
 *
 * The service templates write it for update and update by condition; the commands (DddCommandRules)
 * take it from here, since they are written by a rule for every store.
 */
public final class RefusedWrites {

    private RefusedWrites() {}

    /**
     * The throw statement for a refusal caught as {@code refused}, discarding {@code row}; null where the
     * store hands out no shared row object (MongoDB reads a new one each time).
     */
    public static String rethrow(SourceCode c, EntitySpec e, String row) {
        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String discard;
        if (io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(e)) {
            discard = "io.elasticore.springboot3.mybatis.RefusedWrite.discard(refused)";
        } else if (io.elasticore.base.model.pub.rules.mongo.MongoDocs.applies(e)) {
            // Asked of the model, as every rule asks it, so any architecture over MongoDB writes no discard.
            return null;
        } else {
            String em = c.varValue("emRef");
            if (em == null || em.isEmpty()) em = "entityManager";
            discard = String.format("io.elasticore.springboot3.entity.RefusedWrite.discard(%s, %s, refused)", em, row);
        }
        return kotlin ? "throw " + discard : "throw " + discard + ";";
    }

    /** The catch clause's head, as the language writes it. */
    public static String catchClause(SourceCode c) {
        return c.syntax() == Syntax.KOTLIN
                ? "} catch (refused: RuntimeException) {"
                : "} catch (RuntimeException refused) {";
    }
}
