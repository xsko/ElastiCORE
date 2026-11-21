package ktmongo;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Boot configuration for the tests, in Java over the Kotlin domain. The generated sources are a domain, not a
 * program, so the test supplies the application class and points it at them -
 * the documents too, whose indexes are made at startup.
 */
@SpringBootApplication(scanBasePackages = "io.elasticore.sample.ktmongo")
@EnableMongoRepositories(basePackages = "io.elasticore.sample.ktmongo")
@EntityScan("io.elasticore.sample.ktmongo")
public class MongoSampleApp {
}
