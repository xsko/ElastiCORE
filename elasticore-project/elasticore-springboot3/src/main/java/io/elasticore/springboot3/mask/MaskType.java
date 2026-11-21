package io.elasticore.springboot3.mask;

/**
 * How a value is masked in a response. Separators (dashes, spaces, dots) are kept where they stand; only the
 * characters that identify the person are replaced with '*'.
 */
public enum MaskType {

    /**
     * A phone number: written in three groups, the middle one masked - 010-1234-5678 becomes 010-****-5678 and
     * 02-123-4567 02-***-4567; without separators, the first three and the last four digits kept.
     */
    PHONE {
        @Override
        public String apply(String value) {
            String[] groups = value.split("[- .]", -1);
            if (groups.length == 3) {
                int start = groups[0].length() + 1;
                int end = start + groups[1].length();
                StringBuilder sb = new StringBuilder(value);
                for (int i = start; i < end; i++) {
                    char c = sb.charAt(i);
                    if (c >= '0' && c <= '9') sb.setCharAt(i, '*');
                }
                return sb.toString();
            }
            return maskDigits(value, 3, 4);
        }
    },

    /** An e-mail address: the first two characters of the local part and the domain kept - ho*****@example.com. */
    EMAIL {
        @Override
        public String apply(String value) {
            int at = value.lastIndexOf('@');
            if (at < 0) return keepFirst(value, 2);
            String local = value.substring(0, at);
            return keepFirst(local, local.codePointCount(0, local.length()) > 2 ? 2 : 1) + value.substring(at);
        }
    },

    /**
     * A person's name: the first and the last character kept - a three-letter Korean name keeps its family name and
     * its last letter, a two-letter one its family name only.
     */
    NAME {
        @Override
        public String apply(String value) {
            int[] cps = value.codePoints().toArray();
            int letters = 0;
            for (int cp : cps) if (!Character.isWhitespace(cp)) letters++;
            if (letters <= 1) return maskAll(value);
            int seen = 0;
            StringBuilder sb = new StringBuilder();
            for (int cp : cps) {
                if (Character.isWhitespace(cp)) {
                    sb.appendCodePoint(cp);
                    continue;
                }
                seen++;
                boolean keep = seen == 1 || (letters > 2 && seen == letters);
                if (keep) sb.appendCodePoint(cp);
                else sb.append('*');
            }
            return sb.toString();
        }
    },

    /** A car's number plate: its last four digits masked - 12GA3456 becomes 12GA****. */
    PLATE {
        @Override
        public String apply(String value) {
            int digits = countDigits(value);
            return maskRange(value, Math.max(0, digits - 4), digits);
        }
    },

    /** A card number: the first four and the last four digits kept - 1234-****-****-3456. */
    CARD {
        @Override
        public String apply(String value) {
            return maskDigits(value, 4, 4);
        }
    },

    /** A resident registration number: the birth date and the next digit kept - 900101-1******. */
    RRN {
        @Override
        public String apply(String value) {
            return maskDigits(value, 7, 0);
        }
    };

    /** The value as a response shows it. */
    public abstract String apply(String value);

    /** Every digit but the first {@code head} and the last {@code tail} replaced; a short number keeps none. */
    static String maskDigits(String value, int head, int tail) {
        int digits = countDigits(value);
        if (digits <= head + tail) return maskRange(value, 0, digits);
        return maskRange(value, head, digits - tail);
    }

    /** The digits from the {@code from}-th (counting from 0) up to but not including the {@code to}-th replaced. */
    static String maskRange(String value, int from, int to) {
        StringBuilder sb = new StringBuilder(value.length());
        int seen = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(seen >= from && seen < to ? '*' : c);
                seen++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int countDigits(String value) {
        int n = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') n++;
        }
        return n;
    }

    private static String keepFirst(String value, int keep) {
        StringBuilder sb = new StringBuilder();
        int[] cps = value.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            if (i < keep) sb.appendCodePoint(cps[i]);
            else sb.append('*');
        }
        return sb.toString();
    }

    private static String maskAll(String value) {
        StringBuilder sb = new StringBuilder();
        value.codePoints().forEach(cp -> {
            if (Character.isWhitespace(cp)) sb.appendCodePoint(cp);
            else sb.append('*');
        });
        return sb.toString();
    }
}
