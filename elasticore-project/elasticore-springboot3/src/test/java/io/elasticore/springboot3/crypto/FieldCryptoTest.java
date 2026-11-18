package io.elasticore.springboot3.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** AES-256-GCM with a random IV, the key version prefix, rotation, and what a wrong key or text is told. */
public class FieldCryptoTest {

    /** A fresh key for each use: no key is in the repository. */
    static String newKey() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    static FieldCrypto crypto(Map<String, String> props) {
        return FieldCrypto.of(props::get);
    }

    static Map<String, String> keys() {
        Map<String, String> p = new HashMap<String, String>();
        p.put(FieldCrypto.KEY, newKey());
        p.put(FieldCrypto.INDEX_KEY, newKey());
        return p;
    }

    @AfterEach
    void forget() {
        FieldCrypto.reset();
    }

    @Test
    public void aValueComesBackAsItWasWritten() {
        FieldCrypto c = crypto(keys());
        String stored = c.encrypt("010-1234-5678");
        assertTrue(stored.startsWith("v1:"), stored);
        assertFalse(stored.contains("1234"), stored);
        assertEquals("010-1234-5678", c.decrypt(stored));
        assertEquals("", c.decrypt(c.encrypt("")));
        assertEquals("홍길동 ✓", c.decrypt(c.encrypt("홍길동 ✓")));
        assertNull(c.encrypt(null));
        assertNull(c.decrypt(null));
    }

    @Test
    public void theSameValueIsStoredDifferentlyEachTime() {
        FieldCrypto c = crypto(keys());
        String a = c.encrypt("01012345678");
        String b = c.encrypt("01012345678");
        assertNotEquals(a, b);
        assertEquals(c.decrypt(a), c.decrypt(b));
    }

    @Test
    public void anAlteredValueIsRefused() {
        FieldCrypto c = crypto(keys());
        String stored = c.encrypt("01012345678");
        byte[] raw = Base64.getDecoder().decode(stored.substring(3));
        raw[raw.length - 1] ^= 1;
        String altered = "v1:" + Base64.getEncoder().encodeToString(raw);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> c.decrypt(altered));
        assertTrue(ex.getMessage().contains("altered"), ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> c.decrypt("v1:AAAA"));
        assertThrows(IllegalArgumentException.class, () -> c.decrypt("v1:not base64!"));
        // Another key's text is told apart from a missing one.
        String other = crypto(keys()).encrypt("01012345678");
        assertThrows(IllegalArgumentException.class, () -> c.decrypt(other));
    }

    @Test
    public void aPlainValueIsNotTakenForAnEncryptedOne() {
        FieldCrypto c = crypto(keys());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> c.decrypt("01012345678"));
        assertTrue(ex.getMessage().contains("not encrypted"), ex.getMessage());
    }

    @Test
    public void afterARotationTheOldRowsAreReadAndNewOnesUseTheNewKey() {
        Map<String, String> before = keys();
        String old = crypto(before).encrypt("01012345678");

        Map<String, String> after = new HashMap<String, String>();
        after.put(FieldCrypto.KEY, newKey());
        after.put(FieldCrypto.KEY_VERSION, "v2");
        after.put(FieldCrypto.KEYS + "v1", before.get(FieldCrypto.KEY));
        FieldCrypto rotated = crypto(after);
        assertEquals("01012345678", rotated.decrypt(old));
        String now = rotated.encrypt("01012345678");
        assertTrue(now.startsWith("v2:"), now);
        assertEquals("01012345678", rotated.decrypt(now));

        // Without the old key the old row says which property is missing.
        after.remove(FieldCrypto.KEYS + "v1");
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> crypto(after).decrypt(old));
        assertTrue(ex.getMessage().contains("elasticore.crypto.keys.v1 is not set"), ex.getMessage());
    }

    @Test
    public void aMissingKeyIsNamedWhenItIsNeeded() {
        FieldCrypto none = crypto(new HashMap<String, String>());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> none.encrypt("x"));
        assertTrue(ex.getMessage().startsWith("elasticore.crypto.key is not set"), ex.getMessage());
        assertThrows(IllegalStateException.class, none::requireKey);
        IllegalStateException idx = assertThrows(IllegalStateException.class, () -> none.index("x"));
        assertTrue(idx.getMessage().startsWith("elasticore.crypto.index-key is not set"), idx.getMessage());
        IllegalStateException nothing = assertThrows(IllegalStateException.class, FieldCrypto::current);
        assertTrue(nothing.getMessage().contains("elasticore.crypto.key"), nothing.getMessage());
    }

    @Test
    public void aMalformedKeyOrVersionStopsAtOnce() {
        Map<String, String> p = keys();
        p.put(FieldCrypto.KEY, Base64.getEncoder().encodeToString(new byte[16]));
        IllegalStateException shortKey = assertThrows(IllegalStateException.class, () -> crypto(p));
        assertTrue(shortKey.getMessage().contains("elasticore.crypto.key holds 16 bytes"), shortKey.getMessage());
        p.put(FieldCrypto.KEY, "*not base64*");
        IllegalStateException bad = assertThrows(IllegalStateException.class, () -> crypto(p));
        assertTrue(bad.getMessage().contains("elasticore.crypto.key is no base64"), bad.getMessage());
        Map<String, String> q = keys();
        q.put(FieldCrypto.KEY_VERSION, "2");
        assertThrows(IllegalStateException.class, () -> crypto(q));
    }

    private static void assertFalse(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertFalse(condition, message);
    }
}
