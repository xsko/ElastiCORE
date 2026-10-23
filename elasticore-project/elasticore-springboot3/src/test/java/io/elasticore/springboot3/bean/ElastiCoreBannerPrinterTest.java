package io.elasticore.springboot3.bean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The banner prints unless elasticore.banner.enabled says false. */
public class ElastiCoreBannerPrinterTest {

    @Test
    public void onUnlessSwitchedOff() {
        assertTrue(ElastiCoreBannerPrinter.enabled(new MockEnvironment()));
        assertTrue(ElastiCoreBannerPrinter.enabled(new MockEnvironment().withProperty(ElastiCoreBannerPrinter.ENABLED, "true")));
        assertFalse(ElastiCoreBannerPrinter.enabled(new MockEnvironment().withProperty(ElastiCoreBannerPrinter.ENABLED, "false")));
        assertFalse(ElastiCoreBannerPrinter.enabled(new MockEnvironment().withProperty(ElastiCoreBannerPrinter.ENABLED, " FALSE ")));
        // not a boolean: printed, and the start does not fail on it
        assertTrue(ElastiCoreBannerPrinter.enabled(new MockEnvironment().withProperty(ElastiCoreBannerPrinter.ENABLED, "abc")));
    }
}
