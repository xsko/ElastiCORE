package mongo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.DockerClientFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MongoApiTest is skipped where Docker does not run; on CI it must run, so a
 * runner without Docker fails here instead of passing with the tests skipped.
 */
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
public class DockerOnCiTest {

    @Test
    public void dockerRunsOnCi() {
        assertTrue(DockerClientFactory.instance().isDockerAvailable(),
                "CI runs the MongoDB tests on a container; Docker is not available");
    }
}
