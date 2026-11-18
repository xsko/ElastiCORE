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

/**
 * The blind index: by default the value trimmed and nothing else, so values that differ stay apart; with
 * {@code @encrypt(digits)} a number written with or without its separators is one value. 22 characters, the index
 * key's own.
 */
public class BidxTest {

    @AfterEach
    void forget() {
        FieldCrypto.reset();
    }

    @Test
    public void byDefaultValuesThatDifferStayApart() {
        assertEquals("010-1234-5678", Bidx.normalize(" 010-1234-5678 "));
        assertNull(Bidx.normalize(null));

        FieldCrypto.install(FieldCrypto.of(FieldCryptoTest.keys()::get));
        String index = Bidx.of("123-45-678");
        assertEquals(22, index.length(), index);
        assertTrue(index.matches("[A-Za-z0-9_-]{22}"), index);
        assertEquals(index, Bidx.of("  123-45-678 "));
        // Another person's number, another address, another amount: never the same index.
        assertNotEquals(index, Bidx.of("12-345-678"));
        assertNotEquals(Bidx.of("10.11.1.1"), Bidx.of("101.1.1.1"));
        assertNotEquals(Bidx.of("1.5"), Bidx.of("15"));
        assertNotEquals(Bidx.of("010-1234-5678"), Bidx.of("01012345678"));
        assertNull(Bidx.of(null));
    }

    @Test
    public void withDigitsANumberWrittenWithOrWithoutSeparatorsIsOneValue() {
        assertEquals("01012345678", Bidx.digits("010-1234-5678"));
        assertEquals("01012345678", Bidx.digits(" 010 1234 5678 "));
        assertEquals("01012345678", Bidx.digits("010.1234.5678"));
        // Anything else is only trimmed: a mail address keeps its dot and dash.
        assertEquals("ho-gil.dong@example.com", Bidx.digits("  ho-gil.dong@example.com "));
        assertEquals("ABC-123", Bidx.digits("ABC-123"));
        assertEquals("- .", Bidx.digits(" - . "));
        assertNull(Bidx.digits(null));

        FieldCrypto.install(FieldCrypto.of(FieldCryptoTest.keys()::get));
        String index = Bidx.ofDigits("010-1234-5678");
        assertEquals(index, Bidx.ofDigits("01012345678"));
        assertEquals(index, Bidx.ofDigits(" 010 1234 5678"));
        assertEquals(index, Bidx.of("01012345678"));
        assertNotEquals(index, Bidx.ofDigits("01012345679"));
        assertNull(Bidx.ofDigits(null));
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
