package mybatis;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Keys for the encrypted column (shop: Gadget.serialNo), made fresh for each test run: no key is kept in the
 * repository. An application sets elasticore.crypto.key and elasticore.crypto.index-key itself - from an environment
 * variable or a vault - and without them it does not start (registered in META-INF/spring.factories).
 */
public class TestCryptoKeys implements EnvironmentPostProcessor {

    private static final String KEY = newKey();
    private static final String INDEX_KEY = newKey();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> keys = new HashMap<String, Object>();
        if (!environment.containsProperty("elasticore.crypto.key")) keys.put("elasticore.crypto.key", KEY);
        if (!environment.containsProperty("elasticore.crypto.index-key")) keys.put("elasticore.crypto.index-key", INDEX_KEY);
        if (!keys.isEmpty()) environment.getPropertySources().addLast(new MapPropertySource("testCryptoKeys", keys));
    }

    static String newKey() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }
}
