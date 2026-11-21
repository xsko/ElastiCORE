package ktmongo;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The generated Kotlin club on a MongoDB that Testcontainers starts - every
 * build where Docker runs (CI does); skipped where it does not.
 */
@Testcontainers(disabledWithoutDocker = true)
public class ClubApiTest extends ClubApi {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl(database()));
    }
}
