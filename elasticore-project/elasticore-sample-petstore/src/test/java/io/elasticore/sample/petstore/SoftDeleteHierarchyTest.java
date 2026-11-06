package io.elasticore.sample.petstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** @deleted in the refdata hierarchies (Party JOINED, Tag TABLE_PER_CLASS) over HTTP, on the generated H2 schema. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:petstore-kept-hierarchy;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-h2.sql",
        "spring.sql.init.data-locations=classpath:data.sql"})
@AutoConfigureMockMvc
@Import(SoftDeleteHierarchyCheck.class)
class SoftDeleteHierarchyTest {

    @Autowired MockMvc mvc;
    @Autowired SoftDeleteHierarchyCheck kept;

    @Test
    void joinedKeepsTheRowsOfBothTables() throws Exception {
        kept.aDeletedPersonIsKeptAndNotFound(mvc);
    }

    @Test
    void tablePerClassKeepsTheSubclasssRow() throws Exception {
        kept.aDeletedTagIsKeptAndNotFound(mvc);
    }
}
