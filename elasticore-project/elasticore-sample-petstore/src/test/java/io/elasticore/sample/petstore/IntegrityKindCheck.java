package io.elasticore.sample.petstore;

import static org.assertj.core.api.Assertions.assertThat;

import io.elasticore.sample.petstore.adapter.in.web.PetstoreApiErrors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What the generated API errors answer for each kind of integrity rule, on whichever database the
 * test runs: the exception that database raises, as Spring translates it. A CHECK is the request's
 * fault - 400 check - not a taken value or a reference; SQL Server says 547 for a CHECK and a
 * FOREIGN KEY alike (issue 167).
 */
class IntegrityKindCheck {

    @Autowired JdbcTemplate jdbc;
    @Autowired PetstoreApiErrors errors;

    public void eachRuleIsAnsweredAsItsKind() {
        jdbc.execute("CREATE TABLE vchk_parent (id INT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE vchk_item (id INT PRIMARY KEY, qty INT, code VARCHAR(10), parent_id INT,"
                + " CONSTRAINT vchk_qty CHECK (qty >= 0), CONSTRAINT vchk_code UNIQUE (code),"
                + " CONSTRAINT vchk_parent_fk FOREIGN KEY (parent_id) REFERENCES vchk_parent (id))");
        try {
            jdbc.update("INSERT INTO vchk_parent (id) VALUES (1)");
            jdbc.update("INSERT INTO vchk_item (id, qty, code, parent_id) VALUES (1, 1, 'A', 1)");

            ProblemDetail check = answer("INSERT INTO vchk_item (id, qty, code) VALUES (2, -1, 'B')");
            assertThat(check.getStatus()).as("CHECK").isEqualTo(400);
            assertThat(check.getProperties()).containsEntry("code", "check");

            ProblemDetail unique = answer("INSERT INTO vchk_item (id, qty, code) VALUES (3, 1, 'A')");
            assertThat(unique.getStatus()).as("UNIQUE").isEqualTo(409);
            assertThat(unique.getProperties()).containsEntry("code", "unique");

            ProblemDetail reference = answer("INSERT INTO vchk_item (id, qty, code, parent_id) VALUES (4, 1, 'C', 9)");
            assertThat(reference.getStatus()).as("FOREIGN KEY").isEqualTo(409);
            assertThat(reference.getProperties()).containsEntry("code", "referenced");
        } finally {
            jdbc.execute("DROP TABLE vchk_item");
            jdbc.execute("DROP TABLE vchk_parent");
        }
    }

    private ProblemDetail answer(String sql) {
        try {
            jdbc.update(sql);
        } catch (DataIntegrityViolationException ex) {
            return errors.conflict(ex);
        }
        throw new AssertionError("no integrity rule refused: " + sql);
    }
}
