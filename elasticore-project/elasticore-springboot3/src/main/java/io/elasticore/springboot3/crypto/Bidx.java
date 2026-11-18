package io.elasticore.springboot3.crypto;

/**
 * The blind index of an encrypted field searched by equality: what the companion column ({@code phoneBidx},
 * {@code phone_bidx}) holds, and what a search compares it with - the encrypted column itself cannot be compared, as
 * the same value is stored differently each time.
 *
 * <pre>
 * entity.setPhoneBidx(Bidx.of(entity.getPhone()));                     // before the row is written
 * c.equal(r.get("phoneBidx"), Bidx.of(searchDTO.getPhone()));           // the search
 * </pre>
 *
 * {@link #of} takes the value trimmed and nothing else: two values that differ are two indexes. {@link #ofDigits}, for
 * a field marked {@code @encrypt(digits)}, also drops the dashes, spaces and dots of a value made of digits and those,
 * so 010-1234-5678, 010 1234 5678 and 01012345678 find each other - and so do 123-45-678 and 12-345-678: use it only
 * where the separators carry no meaning (a phone number). Only equality: a part of the value, a range or a
 * case-insensitive match cannot be searched this way.
 */
public final class Bidx {

    private Bidx() {}

    /** The index of a value, trimmed: 22 characters of base64url; null for null. */
    public static String of(String value) {
        if (value == null) return null;
        return FieldCrypto.current().index(normalize(value));
    }

    /** The index of a value kept to its digits where it is digits and separators ({@link #digits}); null for null. */
    public static String ofDigits(String value) {
        if (value == null) return null;
        return FieldCrypto.current().index(digits(value));
    }

    /** What {@link #of} takes the index of: the value trimmed. */
    public static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * What {@link #ofDigits} takes the index of: the value trimmed, and a value of digits and separators (a dash, a
     * space, a dot) kept to its digits. Any other value is only trimmed.
     */
    public static String digits(String value) {
        if (value == null) return null;
        String s = value.trim();
        boolean digit = false;
        StringBuilder digits = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                digit = true;
                digits.append(c);
            } else if (c != '-' && c != ' ' && c != '.') {
                return s;
            }
        }
        return digit ? digits.toString() : s;
    }
}
