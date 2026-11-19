package ktmongo;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The same tests on the MongoDB server ELASTICORE_IT_MONGODB_URI names
 * (mongodb://host:port, no database) - for a machine without Docker. Each run
 * keeps to a database of its own.
 */
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_MONGODB_URI", matches = ".+")
public class MongoApiOnServerTest extends ShopApi {

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        String server = System.getenv("ELASTICORE_IT_MONGODB_URI");
        String base = server.endsWith("/") ? server : server + "/";
        registry.add("spring.data.mongodb.uri", () -> base + database());
    }
}
