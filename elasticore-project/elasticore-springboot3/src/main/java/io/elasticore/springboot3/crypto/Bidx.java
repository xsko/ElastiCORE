package io.elasticore.springboot3.crypto;

/**
 * The blind index of an encrypted field searched by equality: what the companion column ({@code phoneBidx},
 * {@code phone_bidx}) holds, and what a search compares it with - the encrypted column itself cannot be compared, as
 * the same value is stored differently each time.
 *
 * <pre>
 * entity.setPhoneBidx(Bidx.of(entity.getPhone()));                    // before the row is written
 * c.equal(r.get("phoneBidx"), Bidx.of(searchDTO.getPhone()));          // the search
 * </pre>
 *
 * The value is normalized first ({@link #normalize}), so 010-1234-5678, 010 1234 5678 and 01012345678 find each
 * other. Only equality: a part of the value, a range or a case-insensitive match cannot be searched this way.
 */
public final class Bidx {

    private Bidx() {}

    /** The index of a value, normalized: 22 characters of base64url; null for null. */
    public static String of(String value) {
        if (value == null) return null;
        return FieldCrypto.current().index(normalize(value));
    }

    /**
     * What the index is taken of: the value trimmed, and a value of digits and separators (a dash, a space, a dot)
     * kept to its digits - a phone, card or registration number written either way is one value.
     */
    public static String normalize(String value) {
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
