package io.elasticore.springboot3.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The blind index: one value however its separators are written, 22 characters, the index key's own. */
public class BidxTest {

    @AfterEach
    void forget() {
        FieldCrypto.reset();
    }

    @Test
    public void aNumberWrittenWithOrWithoutSeparatorsIsOneValue() {
        assertEquals("01012345678", Bidx.normalize("010-1234-5678"));
        assertEquals("01012345678", Bidx.normalize(" 010 1234 5678 "));
        assertEquals("01012345678", Bidx.normalize("010.1234.5678"));
        assertEquals("900101-1234567".replace("-", ""), Bidx.normalize("900101-1234567"));
        // Anything else is only trimmed: a mail address keeps its dot and dash.
        assertEquals("ho-gil.dong@example.com", Bidx.normalize("  ho-gil.dong@example.com "));
        assertEquals("ABC-123", Bidx.normalize("ABC-123"));
        assertEquals("- .", Bidx.normalize(" - . "));
        assertNull(Bidx.normalize(null));

        FieldCrypto.install(FieldCrypto.of(FieldCryptoTest.keys()::get));
        String index = Bidx.of("010-1234-5678");
        assertEquals(22, index.length(), index);
        assertTrue(index.matches("[A-Za-z0-9_-]{22}"), index);
        assertEquals(index, Bidx.of("01012345678"));
        assertEquals(index, Bidx.of(" 010 1234 5678"));
        assertNotEquals(index, Bidx.of("01012345679"));
        assertNull(Bidx.of(null));
    }

    @Test
    public void theIndexIsTheIndexKeysAndNotTheEncryptionKeys() {
        Map<String, String> a = FieldCryptoTest.keys();
        Map<String, String> b = new HashMap<String, String>(a);
        b.put(FieldCrypto.INDEX_KEY, FieldCryptoTest.newKey());
        assertNotEquals(
                FieldCrypto.of(a::get).index("01012345678"),
                FieldCrypto.of(b::get).index("01012345678"));
        Map<String, String> c = new HashMap<String, String>(a);
        c.put(FieldCrypto.KEY, FieldCryptoTest.newKey());
        assertEquals(
                FieldCrypto.of(a::get).index("01012345678"),
                FieldCrypto.of(c::get).index("01012345678"));
    }

    @Test
    public void withoutAnIndexKeyTheSearchSaysWhichPropertyIsMissing() {
        Map<String, String> p = FieldCryptoTest.keys();
        p.remove(FieldCrypto.INDEX_KEY);
        FieldCrypto.install(FieldCrypto.of(p::get));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> Bidx.of("01012345678"));
        assertTrue(ex.getMessage().startsWith("elasticore.crypto.index-key is not set"), ex.getMessage());
    }
}
