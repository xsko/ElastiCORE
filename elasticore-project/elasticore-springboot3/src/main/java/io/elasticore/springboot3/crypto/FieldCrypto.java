package io.elasticore.springboot3.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The keys an encrypted field is written and read with, and what they do: AES-256-GCM with a random 12-byte IV per
 * write, so the same value is stored differently each time, and an HMAC-SHA256 blind index for an equality search.
 *
 * <pre>
 * elasticore.crypto.key=...           base64 of 32 bytes: the key every value is written with
 * elasticore.crypto.key-version=v2    the version that key is (v1 when not said)
 * elasticore.crypto.keys.v1=...       an earlier key: values it wrote are still read
 * elasticore.crypto.index-key=...     base64 of 32 bytes, another key: the blind index (Bidx)
 * </pre>
 *
 * A value is stored as {@code v1:} and the base64 of IV, ciphertext and tag; the prefix names the key that wrote it,
 * so after a rotation the old rows are read with the old key and every write uses the new one.
 *
 * <p>One instance serves the application ({@link #current()}), set by the runtime's auto-configuration from the Spring
 * properties - the JPA converter and the MyBatis type handler are made by Hibernate and MyBatis, not by Spring, and
 * read it from here. A key that is set but is no base64 of 32 bytes stops the start; a missing one stops it only where
 * an encrypted field is mapped ({@link #markUsed()}), and is named in the message either way.
 */
public final class FieldCrypto {

    /** The key every value is written with. */
    public static final String KEY = "elasticore.crypto.key";

    /** The version of {@link #KEY}: the prefix of what it writes. */
    public static final String KEY_VERSION = "elasticore.crypto.key-version";

    /** An earlier key, by its version: {@code elasticore.crypto.keys.v1}. */
    public static final String KEYS = "elasticore.crypto.keys.";

    /** The blind index's own key. */
    public static final String INDEX_KEY = "elasticore.crypto.index-key";

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int INDEX_BYTES = 16;
    /** v1 to v9: the column length leaves room for a one-digit version (EncryptRules.columnLength). */
    private static final Pattern VERSION = Pattern.compile("v[1-9]");

    private static volatile FieldCrypto current;
    private static volatile boolean used;

    private final String version;
    private final SecretKeySpec key;
    private final SecretKeySpec indexKey;
    private final Function<String, String> properties;
    private final Map<String, SecretKeySpec> earlier = new ConcurrentHashMap<String, SecretKeySpec>();
    private final SecureRandom random = new SecureRandom();

    /**
     * The keys the properties name, read with the lookup given ({@code environment::getProperty}). A key that is set
     * is checked here; one that is missing is said when it is needed.
     *
     * @throws IllegalStateException a key that is no base64 of 32 bytes, or a version that is no v1, v2, ...
     */
    public static FieldCrypto of(Function<String, String> properties) {
        return new FieldCrypto(properties);
    }

    private FieldCrypto(Function<String, String> properties) {
        this.properties = properties;
        String v = properties.apply(KEY_VERSION);
        this.version = v == null || v.trim().isEmpty() ? "v1" : v.trim();
        if (!VERSION.matcher(version).matches()) {
            throw new IllegalStateException(KEY_VERSION + "=" + version + " - a key version is v1 to v9");
        }
        this.key = aes(KEY, properties.apply(KEY));
        this.indexKey = hmac(INDEX_KEY, properties.apply(INDEX_KEY));
        if (key != null && indexKey != null && java.util.Arrays.equals(key.getEncoded(), indexKey.getEncoded())) {
            throw new IllegalStateException(INDEX_KEY + " is the same as " + KEY
                    + " - the blind index needs a key of its" + " own (openssl rand -base64 32)");
        }
    }

    /** Makes this the application's: the converter, the type handler and Bidx use it from now on. */
    public static void install(FieldCrypto crypto) {
        current = crypto;
    }

    /** The application's, as the auto-configuration installed it. */
    public static FieldCrypto current() {
        FieldCrypto c = current;
        if (c == null) {
            throw new IllegalStateException("an encrypted field is read or written before its keys are known: set "
                    + KEY + " and " + INDEX_KEY + " (base64 of 32 bytes each) - the runtime's auto-configuration reads"
                    + " them; without Spring Boot call FieldCrypto.install(FieldCrypto.of(...))");
        }
        return c;
    }

    /** Said by the JPA converter and the MyBatis type handler when one is made: an encrypted field is mapped. */
    public static void markUsed() {
        used = true;
    }

    /** Whether an encrypted field is mapped in this application. */
    public static boolean used() {
        return used;
    }

    /** Forgets the keys and the use - for a test. */
    static void reset() {
        current = null;
        used = false;
    }

    /**
     * Stops where the key to write with is missing, naming the property.
     *
     * @throws IllegalStateException without {@link #KEY}
     */
    public void requireKey() {
        if (key == null) throw new IllegalStateException(missing(KEY));
    }

    /**
     * Stops where either key is missing: what the start of an application with an encrypted field asks, so a blind
     * index does not fail on its first write instead.
     *
     * @throws IllegalStateException without {@link #KEY} or {@link #INDEX_KEY}
     */
    public void requireKeys() {
        requireKey();
        if (indexKey == null) throw new IllegalStateException(missing(INDEX_KEY));
    }

    /** The version every write is prefixed with. */
    public String version() {
        return version;
    }

    /** The value encrypted with the current key: {@code v1:} and the base64 of IV, ciphertext and tag. */
    public String encrypt(String plain) {
        if (plain == null) return null;
        requireKey();
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            // The prefix is bound to the text: a v1 text relabelled v2 does not decrypt.
            cipher.updateAAD(version.getBytes(StandardCharsets.US_ASCII));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer out = ByteBuffer.allocate(iv.length + sealed.length);
            out.put(iv).put(sealed);
            return version + ":" + Base64.getEncoder().encodeToString(out.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AES-GCM is not available: " + ex.getMessage(), ex);
        }
    }

    /**
     * The value a stored text holds, read with the key its prefix names.
     *
     * @throws IllegalArgumentException a text that is not one this wrote, or one that was altered
     * @throws IllegalStateException the key of its version is not set
     */
    public String decrypt(String stored) {
        if (stored == null) return null;
        int colon = stored.indexOf(':');
        String v = colon < 0 ? "" : stored.substring(0, colon);
        if (!VERSION.matcher(v).matches()) {
            throw new IllegalArgumentException("a stored value without a key version (v1:...) - not encrypted: a row"
                    + " written before the field was encrypted is encrypted once (see the annotation guide, @encrypt)");
        }
        SecretKeySpec k = keyOf(v);
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(stored.substring(colon + 1));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("a stored " + v + " value that is no base64 - altered");
        }
        if (raw.length < IV_BYTES + TAG_BITS / 8) {
            throw new IllegalArgumentException("a stored " + v + " value too short to be encrypted - altered");
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            cipher.updateAAD(v.getBytes(StandardCharsets.US_ASCII));
            byte[] plain = cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException ex) {
            throw new IllegalArgumentException("a stored " + v + " value that does not decrypt with the " + v
                    + " key - altered, or written with another key");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AES-GCM is not available: " + ex.getMessage(), ex);
        }
    }

    /**
     * The blind index of a value already normalized (Bidx.of normalizes): HMAC-SHA256 with the index key, its first
     * 16 bytes in base64url - 22 characters.
     *
     * @throws IllegalStateException without {@link #INDEX_KEY}
     */
    public String index(String normalized) {
        if (normalized == null) return null;
        if (indexKey == null) throw new IllegalStateException(missing(INDEX_KEY));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(indexKey);
            byte[] full = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            byte[] cut = new byte[INDEX_BYTES];
            System.arraycopy(full, 0, cut, 0, INDEX_BYTES);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(cut);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available: " + ex.getMessage(), ex);
        }
    }

    private SecretKeySpec keyOf(String v) {
        if (v.equals(version)) {
            requireKey();
            return key;
        }
        SecretKeySpec k = earlier.get(v);
        if (k != null) return k;
        k = aes(KEYS + v, properties.apply(KEYS + v));
        if (k == null) {
            throw new IllegalStateException("a stored value was written with the " + v + " key, and " + KEYS + v
                    + " is not set (the current key is " + version + ") - set the earlier key there");
        }
        earlier.put(v, k);
        return k;
    }

    private static SecretKeySpec aes(String property, String value) {
        byte[] bytes = bytes(property, value);
        return bytes == null ? null : new SecretKeySpec(bytes, "AES");
    }

    private static SecretKeySpec hmac(String property, String value) {
        byte[] bytes = bytes(property, value);
        return bytes == null ? null : new SecretKeySpec(bytes, "HmacSHA256");
    }

    private static byte[] bytes(String property, String value) {
        if (value == null || value.trim().isEmpty()) return null;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(property + " is no base64 value - make one with: openssl rand -base64 32");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException(property + " holds " + bytes.length + " bytes, and a key is 32 (256 bits)"
                    + " - make one with: openssl rand -base64 32");
        }
        return bytes;
    }

    private static String missing(String property) {
        return property + " is not set, and an encrypted field needs it - a base64 value of 32 bytes"
                + " (openssl rand -base64 32), kept out of the repository (an environment variable or a vault)";
    }
}
