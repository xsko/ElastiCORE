package io.elasticore.sample.petstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.elasticore.sample.petstore.refdata.domain.model.CaseNote;
import io.elasticore.sample.petstore.refdata.domain.model.ColorTag;
import io.elasticore.sample.petstore.refdata.domain.model.Company;
import io.elasticore.sample.petstore.refdata.domain.model.Party;
import io.elasticore.sample.petstore.refdata.domain.model.Person;
import io.elasticore.sample.petstore.refdata.domain.model.SizeTag;
import io.elasticore.sample.petstore.refdata.domain.model.Tag;
import io.elasticore.sample.petstore.refdata.infrastructure.persistence.PartyRepository;
import io.elasticore.sample.petstore.refdata.infrastructure.persistence.TagRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @deleted in an @abstract(entity) hierarchy, on whichever database the test runs: a subclass's row
 * deleted over HTTP is kept in its tables with the time, and no read through the root finds it -
 * JOINED (Party: the column in the root's table, a version) and TABLE_PER_CLASS (Tag: the column
 * in each subclass's table, from an @abstract frame above the root), and a collection of the root's type.
 */
class SoftDeleteHierarchyCheck {

    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PartyRepository parties;
    @Autowired TagRepository tags;

    @Autowired PlatformTransactionManager transactions;

    /** Each row is written in a transaction of its own, committed before the request that deletes it. */
    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactions).execute(status -> work.get());
    }

    Long person(String name) {
        return inTransaction(() -> {
            Person p = new Person();
            p.setName(name);
            p.creation().setBirthYear(1990);
            em.persist(p);
            return p.getPartyId();
        });
    }

    Long company(String name) {
        return inTransaction(() -> {
            Company c = new Company();
            c.setName(name);
            c.creation().setRegistryNo("110-81");
            em.persist(c);
            return c.getPartyId();
        });
    }

    Long note(String title) {
        return inTransaction(() -> {
            CaseNote n = new CaseNote();
            n.creation().setTitle(title);
            em.persist(n);
            return n.getNoteId();
        });
    }

    Long tag(Tag t, String label, Long noteId) {
        return inTransaction(() -> {
            t.setLabel(label);
            t.setNote(em.getReference(CaseNote.class, noteId));
            em.persist(t);
            return t.getTagId();
        });
    }

    /** The note's tags, as its collection reads them (@SQLRestriction). */
    List<Long> tagsOf(Long noteId) {
        return inTransaction(() -> em.find(CaseNote.class, noteId).getTags().stream()
                .map(Tag::getTagId)
                .collect(Collectors.toList()));
    }

    /** The note's parties, as its collection of the root's type reads them (@SQLRestriction on the root's table). */
    List<Long> partiesOf(Long noteId) {
        return inTransaction(() -> em.find(CaseNote.class, noteId).getParties().stream()
                .map(Party::getPartyId)
                .collect(Collectors.toList()));
    }

    Long onNote(Long partyId, Long noteId) {
        return inTransaction(() -> {
            em.find(Party.class, partyId).setNote(em.getReference(CaseNote.class, noteId));
            return partyId;
        });
    }

    /** JOINED: the delete of a person writes the time in ref_party and keeps the ref_person row. */
    public void aDeletedPersonIsKeptAndNotFound(MockMvc mvc) throws Exception {
        Long kim = person("Kim");
        Long acme = company("Acme");
        Long lee = person("Lee");
        Long note = note("People");
        onNote(kim, note);
        onNote(lee, note);
        onNote(acme, note);
        long before = parties.count();

        mvc.perform(delete("/api/refdata/party/" + kim)).andExpect(status().isOk());

        mvc.perform(get("/api/refdata/party/" + kim)).andExpect(status().isNotFound());
        mvc.perform(get("/api/refdata/party/" + acme)).andExpect(status().isOk());
        assertThat(parties.findById(kim)).isEmpty();
        assertThat(parties.existsById(kim)).isFalse();
        assertThat(parties.findAll().stream().map(Party::getPartyId)).contains(acme).doesNotContain(kim);
        assertThat(parties.count()).isEqualTo(before - 1);
        String found = mvc.perform(post("/api/refdata/party/search").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(found).contains("\"Acme\"").doesNotContain("\"Kim\"");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ref_party WHERE party_id = ? AND left_at IS NOT NULL",
                Integer.class, kim)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ref_person WHERE party_id = ?", Integer.class, kim))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ref_party WHERE party_id = ? AND left_at IS NULL",
                Integer.class, acme)).isEqualTo(1);
        // A collection of the root's type leaves the deleted out.
        assertThat(partiesOf(note)).containsExactlyInAnyOrder(acme, lee);
    }

    /** TABLE_PER_CLASS: the delete of a colour tag writes the time in ref_color_tag; the note's tags leave it out. */
    public void aDeletedTagIsKeptAndNotFound(MockMvc mvc) throws Exception {
        Long note = note("Tagged");
        Long red = tag(new ColorTag(), "Red", note);
        Long large = tag(new SizeTag(), "Large", note);
        assertThat(tagsOf(note)).containsExactlyInAnyOrder(red, large);

        mvc.perform(delete("/api/refdata/tag/" + red)).andExpect(status().isOk());

        mvc.perform(get("/api/refdata/tag/" + red)).andExpect(status().isNotFound());
        mvc.perform(get("/api/refdata/tag/" + large)).andExpect(status().isOk());
        assertThat(tags.findById(red)).isEmpty();
        assertThat(tags.findAll().stream().map(Tag::getTagId)).contains(large).doesNotContain(red);
        assertThat(tagsOf(note)).containsExactly(large);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ref_color_tag WHERE tag_id = ? AND retired_at IS NOT NULL",
                Integer.class, red)).isEqualTo(1);
    }
}
