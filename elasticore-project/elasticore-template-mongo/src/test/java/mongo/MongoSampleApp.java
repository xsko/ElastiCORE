package mongo;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Boot configuration for the tests. The generated sources are a domain, not a
 * program, so the test supplies the application class and points it at them -
 * the documents too, whose indexes are made at startup.
 */
@SpringBootApplication(scanBasePackages = "io.elasticore.sample.mongo")
@EnableMongoRepositories(basePackages = "io.elasticore.sample.mongo")
@EntityScan("io.elasticore.sample.mongo")
public class MongoSampleApp {
}
