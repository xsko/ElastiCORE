package ddd;

import com.jayway.jsonpath.JsonPath;
import io.elasticore.sample.ddd.forum.ForumContractSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Issue 321 over HTTP: an Award (read=MODERATOR) carries its Badge (read=MODERATOR) whole, since whoever may read
 * the award may read the badge - in what a read answers. A member may create an award (create=user) but not read
 * badges, so what the create answers leaves the badge out; its code stays.
 *
 * Here rather than beside ForumContractSupport: the generated test tree is cleared before each generation.
 */
class AwardCarriedRowTest extends ForumContractSupport {

    private static final String BADGES = "/api/v1/forum/badge";
    private static final String AWARDS = "/api/v1/forum/award";

    @Test
    void aReadCarriesTheBadgeAndWhatAMemberCreatesDoesNot() throws Exception {
        Caller moderator = signIn("M");
        Caller member = signIn("U");
        String code = text(8);
        assertAllowed(sentBy(moderator, creating(BADGES, obj("badgeCode", code, "name", "Helper"))));
        // A member may not read the badge itself.
        Assertions.assertTrue(refused(sentBy(member, reading(BADGES, code))));

        // The award points at the badge by its code: badgeCode (the hexagonal reading), badgeBadgeCode (layered).
        MvcResult made =
                sentBy(member, creating(AWARDS, obj("badgeCode", code, "badgeBadgeCode", code, "note", "thanks")));
        assertAllowed(made);
        Assertions.assertNull(value(made, "badge"), () -> "the create hands the member the badge: " + answer(made));
        Object pointed = value(made, "badgeCode") != null ? value(made, "badgeCode") : value(made, "badgeBadgeCode");
        Assertions.assertEquals(code, pointed, () -> "the identity is gone: " + answer(made));
        Object id = value(made, "awardId");

        // The moderator reads the award, and the badge with it.
        MvcResult read = sentBy(moderator, reading(AWARDS, id));
        assertAllowed(read);
        Assertions.assertEquals("Helper", JsonPath.read(body(read), "$.badge.name"), () -> answer(read));
        MvcResult found = sentBy(moderator, searching(AWARDS, obj()));
        assertFinds(found, "awardId", id);
        Assertions.assertTrue(body(found).contains("\"name\":\"Helper\""), () -> answer(found));

        // An update by the moderator answers without the badge too: update checks update, not read.
        MvcResult updated = sentBy(moderator, updating(AWARDS, obj("awardId", id, "note", "again")));
        assertAllowed(updated);
        Assertions.assertNull(value(updated, "badge"), () -> answer(updated));

        // The member may not read the award, and so never the badge through it.
        Assertions.assertTrue(refused(sentBy(member, reading(AWARDS, id))), () -> "the member reads the award");
    }
}
