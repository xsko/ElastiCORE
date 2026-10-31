package io.elasticore.base.model.pub.rules;

/**
 * {@link GuideCoverageTest} on the Korean guides: a project that asks for them
 * ({@code guides = 'ko'}) must find the same names in them.
 */
public class GuideCoverageKoTest extends GuideCoverageTest {

    @Override
    protected String directory() {
        return io.elasticore.base.util.GuideDeployer.directoryOf("ko");
    }
}
