package io.elasticore.sample.mybatis.petstore;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shop on a database the CI job gives: Oracle, PostgreSQL, SQL Server.
 * Each from the migrations the generator wrote for it
 * (db/migration/{vendor}), run by Flyway, with the same catalog and the same flows
 * as on H2. A database not given is skipped (./gradlew ...:dbTest).
 */
@Tag("database")
@SpringBootTest
@AutoConfigureMockMvc
@Import(ShopFlow.Counting.class)
abstract class ShopFlowOnDatabase extends ShopFlow {

    @Autowired SqlSessionFactory sessions;

    /** The database id the runtime library told MyBatis, which chooses the page's syntax. */
    abstract String databaseId();

    @Test
    void theDatabaseIdChoosesThePage() {
        assertThat(sessions.getConfiguration().getDatabaseId()).isEqualTo(databaseId());
    }

    static void set(DynamicPropertyRegistry r, Map<String, String> properties) {
        for (Map.Entry<String, String> p : properties.entrySet()) {
            String value = p.getValue();
            r.add(p.getKey(), () -> value);
        }
    }
}
