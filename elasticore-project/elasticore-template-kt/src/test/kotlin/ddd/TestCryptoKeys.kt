package ddd

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import java.security.SecureRandom
import java.util.Base64

/**
 * Keys for the encrypted column (content: Comment.authorPhone), made fresh for each test run: no key is kept in the
 * repository. An application sets elasticore.crypto.key and elasticore.crypto.index-key itself - from an environment
 * variable or a vault - and without them it does not start (registered in META-INF/spring.factories).
 */
class TestCryptoKeys : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val keys = HashMap<String, Any>()
        if (!environment.containsProperty("elasticore.crypto.key")) keys["elasticore.crypto.key"] = KEY
        if (!environment.containsProperty("elasticore.crypto.index-key")) keys["elasticore.crypto.index-key"] = INDEX_KEY
        if (keys.isNotEmpty()) environment.propertySources.addLast(MapPropertySource("testCryptoKeys", keys))
    }

    companion object {
        private val KEY = newKey()
        private val INDEX_KEY = newKey()

        fun newKey(): String {
            val b = ByteArray(32)
            SecureRandom().nextBytes(b)
            return Base64.getEncoder().encodeToString(b)
        }
    }
}
