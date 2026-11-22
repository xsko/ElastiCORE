package io.elasticore.base.model.pub.rules.verify;

import java.util.ArrayList;
import java.util.List;

/**
 * A value that matches a @pattern's regular expression, and one that does
 * not - for the contract tests, which send both.
 *
 * Read from the expression itself: literals, escapes (\d \w \s and an
 * escaped character), classes ([a-z0-9_], [^...]), the dot, groups and the
 * first of their alternatives, the quantifiers ? * + {n} {n,} {n,m}, and
 * inline flags ((?i), read past - the whole pattern checks the value).
 *
 * A password's rule - ^ and then lookaheads (?=.*X) or (?=.*?X), X one class,
 * one escape or one character - is read too: the rest is sampled as any
 * pattern, and a character of each X put where the rest lets it (see
 * Lookaheads). Any other lookaround (a negative lookahead, a lookbehind, a
 * lookahead further in) and a back reference are no sample - the caller says
 * so. Every value is checked with java.util.regex, the engine Bean
 * Validation's @Pattern runs, before it is offered: a sample that would not
 * match is never written into a test.
 */
final class PatternSample {

    private PatternSample() {}

    /** The characters a lookahead's class is filled from, and a value made to miss one: letters and digits first. */
    private static final String CANDIDATES =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#%&*+-.=?@_~";

    /** A value that matches, of a length in [min, max]; null when none is found. */
    static String matching(String regexp, int min, int max) {
        Sample s = sample(regexp, min, max);
        return s == null ? null : s.text;
    }

    /**
     * The expression @pattern names, as the DTO's @Pattern hands it to the
     * engine: the DSL writes the content of a string literal (FieldSpec.patternRegexp,
     * a backslash doubled: \\d), which the compiler reads - \\ is one
     * backslash, \" a quote, \t a tab, a backslash-u escape its character.
     * A lone backslash before anything else (which the loader warns of) stays.
     */
    static String unescape(String literal) {
        if (literal == null) return null;
        StringBuilder sb = new StringBuilder(literal.length());
        int i = 0;
        while (i < literal.length()) {
            char ch = literal.charAt(i);
            if (ch != '\\' || i + 1 >= literal.length()) {
                sb.append(ch);
                i++;
                continue;
            }
            char next = literal.charAt(i + 1);
            String hex = next == 'u' && i + 6 <= literal.length() ? literal.substring(i + 2, i + 6) : null;
            if (hex != null && hex.matches("[0-9A-Fa-f]{4}")) {
                sb.append((char) Integer.parseInt(hex, 16));
                i += 6;
                continue;
            }
            i += 2;
            switch (next) {
                case '\\':
                case '"':
                case '\'':
                    sb.append(next);
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                default:
                    sb.append('\\').append(next);
            }
        }
        return sb.toString();
    }

    /** Whether the pattern starts with the lookaheads the sample reads: ^(?=.*[A-Z])(?=.*\d)... */
    static boolean lookaheads(String regexp) {
        return Lookaheads.read(regexp) != null;
    }

    /**
     * A pattern read as ^, its leading lookaheads (?=.*X) and the rest: each X
     * as a pattern of one character, and the rest as a pattern of its own
     * (^ and what follows the lookaheads). A value matches the whole when it
     * matches the rest and holds a character of each X.
     */
    static final class Lookaheads {
        /** (?=.*X) or (?=.*?X): X a class, an escape or a character that is no operator. */
        private static final java.util.regex.Pattern ONE = java.util.regex.Pattern.compile(
                "\\(\\?=\\.\\*\\??(\\[(?:\\\\.|[^\\]\\\\])+\\]|\\\\.|[^\\\\\\[\\]()|*+?{}.^$])\\)");

        final List<java.util.regex.Pattern> needs = new ArrayList<java.util.regex.Pattern>();
        final String rest;

        private Lookaheads(String rest) {
            this.rest = rest;
        }

        /** The pattern's leading lookaheads; null when it does not start with ^ and at least one it reads. */
        static Lookaheads read(String regexp) {
            if (regexp == null || !regexp.startsWith("^")) return null;
            java.util.regex.Matcher m = ONE.matcher(regexp);
            int at = 1;
            List<String> classes = new ArrayList<String>();
            while (at < regexp.length()) {
                m.region(at, regexp.length());
                if (!m.lookingAt()) break;
                classes.add(m.group(1));
                at = m.end();
            }
            if (classes.isEmpty()) return null;
            Lookaheads out = new Lookaheads("^" + regexp.substring(at));
            for (String c : classes) {
                java.util.regex.Pattern one = compile(c);
                // A class no character can fill (\b, an empty range) is not one the sample reads.
                if (one == null || first(one, CANDIDATES) < 0) return null;
                out.needs.add(one);
            }
            return out;
        }

        /** Whether the character fills the lookahead. */
        static boolean fills(java.util.regex.Pattern need, char ch) {
            return need.matcher(String.valueOf(ch)).matches();
        }

        private static int first(java.util.regex.Pattern need, String among) {
            for (int i = 0; i < among.length(); i++) {
                if (fills(need, among.charAt(i))) return i;
            }
            return -1;
        }
    }

    /**
     * A value that matches, and the characters each position may take instead
     * - so that each row gets a value of its own. alphabets[i] is what position
     * i may hold ("" where it stays); every character of it was checked in
     * place, and a set of combinations of them together, against the pattern.
     */
    static final class Sample {
        final String text;
        final List<String> alphabets;

        Sample(String text, List<String> alphabets) {
            this.text = text;
            this.alphabets = alphabets;
        }

        /** Whether more than one value can be made: some position varies. */
        boolean varies() {
            return !alphabets.isEmpty();
        }
    }

    /** As matching, with the positions that may vary; null when no value is found. */
    static Sample sample(String regexp, int min, int max) {
        java.util.regex.Pattern compiled = compile(regexp);
        if (compiled == null) return null;
        Lookaheads lookaheads = Lookaheads.read(regexp);
        java.util.regex.Pattern rest = lookaheads == null ? compiled : compile(lookaheads.rest);
        if (rest == null) return null;
        Node node;
        try {
            node = new Parser(lookaheads == null ? regexp : lookaheads.rest).parse();
        } catch (RuntimeException unsupported) {
            return null;
        }
        for (int extra : new int[] {0, 1, 2, 4, 8, 16, 32}) {
            String text;
            List<CharClass> from = new ArrayList<CharClass>();
            try {
                StringBuilder sb = new StringBuilder();
                node.write(sb, from, extra);
                text = sb.toString();
            } catch (RuntimeException unsupported) {
                return null;
            }
            if (max > 0 && text.length() > max) continue;
            if (text.length() < min) continue;
            List<List<java.util.regex.Pattern>> held = null;
            if (lookaheads != null) {
                held = new ArrayList<List<java.util.regex.Pattern>>();
                text = fill(text, lookaheads, rest, held);
                if (text == null) continue;
            }
            if (compiled.matcher(text).matches()) return new Sample(text, alphabets(compiled, text, from, held));
        }
        return null;
    }

    /**
     * The rest's value with a character of each lookahead in it: one already
     * there is kept, else one is put - from the end - at a position whose
     * change the rest still matches. held gets, for each position, the
     * lookaheads it holds (that position varies only within them). Null when
     * a lookahead finds no such position.
     */
    private static String fill(
            String text,
            Lookaheads lookaheads,
            java.util.regex.Pattern rest,
            List<List<java.util.regex.Pattern>> held) {
        char[] t = text.toCharArray();
        for (int i = 0; i < t.length; i++) held.add(new ArrayList<java.util.regex.Pattern>());
        for (java.util.regex.Pattern need : lookaheads.needs) {
            int at = -1;
            for (int i = t.length - 1; i >= 0 && at < 0; i--) {
                if (Lookaheads.fills(need, t[i])) at = i;
            }
            for (int i = t.length - 1; i >= 0 && at < 0; i--) {
                // A position another lookahead holds keeps its character.
                if (!held.get(i).isEmpty()) continue;
                char was = t[i];
                for (int k = 0; k < CANDIDATES.length() && at < 0; k++) {
                    char ch = CANDIDATES.charAt(k);
                    if (!Lookaheads.fills(need, ch)) continue;
                    t[i] = ch;
                    if (rest.matcher(new String(t)).matches()) at = i;
                    else t[i] = was;
                }
            }
            if (at < 0) return null;
            held.get(at).add(need);
        }
        return new String(t);
    }

    /**
     * A value that matches the rest of a pattern with leading lookaheads but
     * misses one of them: each character of that lookahead in the matching
     * value changed for one the rest takes there. Null when no lookahead can
     * be missed so - the rest itself holds it (^(?=.*\d)\d+$).
     */
    private static String missingOne(java.util.regex.Pattern compiled, Lookaheads lookaheads, String matching) {
        java.util.regex.Pattern rest = compile(lookaheads.rest);
        if (rest == null || matching == null) return null;
        for (java.util.regex.Pattern need : lookaheads.needs) {
            char[] t = matching.toCharArray();
            boolean changed = true;
            for (int i = 0; i < t.length && changed; i++) {
                if (!Lookaheads.fills(need, t[i])) continue;
                changed = false;
                char was = t[i];
                for (int k = 0; k < CANDIDATES.length() && !changed; k++) {
                    char ch = CANDIDATES.charAt(k);
                    if (Lookaheads.fills(need, ch)) continue;
                    t[i] = ch;
                    if (rest.matcher(new String(t)).matches()) changed = true;
                    else t[i] = was;
                }
            }
            String missing = new String(t);
            if (changed
                    && rest.matcher(missing).matches()
                    && !compiled.matcher(missing).matches()) return missing;
        }
        return null;
    }

    /**
     * The characters that may stand at each position the classes wrote, kept
     * only where the pattern still matches. A position that holds a lookahead
     * (held) varies only among the characters that fill it, so every
     * combination still holds each lookahead.
     */
    private static List<String> alphabets(
            java.util.regex.Pattern compiled,
            String text,
            List<CharClass> from,
            List<List<java.util.regex.Pattern>> held) {
        String candidates = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        List<String> out = new ArrayList<String>();
        int last = -1;
        for (int i = 0; i < text.length(); i++) {
            CharClass c = i < from.size() ? from.get(i) : null;
            StringBuilder alphabet = new StringBuilder();
            if (c != null) {
                for (int k = 0; k < candidates.length(); k++) {
                    char ch = candidates.charAt(k);
                    if (c.holds(ch) == c.negated) continue;
                    if (held != null && !fillsAll(held.get(i), ch)) continue;
                    String one = text.substring(0, i) + ch + text.substring(i + 1);
                    if (compiled.matcher(one).matches()) alphabet.append(ch);
                }
            }
            String a = alphabet.length() >= 2 ? alphabet.toString() : "";
            out.add(a);
            if (!a.isEmpty()) last = i;
        }
        List<String> kept = new ArrayList<String>(out.subList(0, last + 1));
        // Positions checked one at a time; a pattern that ties them together is checked over combinations too.
        long n = 7919;
        for (int round = 0; round < 64 && !kept.isEmpty(); round++) {
            char[] combined = text.toCharArray();
            long m = n;
            for (int i = kept.size() - 1; i >= 0; i--) {
                String a = kept.get(i);
                if (a.isEmpty()) continue;
                combined[i] = a.charAt((int) (m % a.length()));
                m /= a.length();
                m += 31L * (i + 1);
            }
            if (!compiled.matcher(new String(combined)).matches()) return new ArrayList<String>();
            n = n * 6364136223846793005L + 1442695040888963407L;
            if (n < 0) n = -n;
        }
        return kept;
    }

    private static boolean fillsAll(List<java.util.regex.Pattern> needs, char ch) {
        for (java.util.regex.Pattern need : needs) {
            if (!Lookaheads.fills(need, ch)) return false;
        }
        return true;
    }

    /**
     * A value that does not match; null when none of the candidates fails it.
     * For a pattern with leading lookaheads: one that matches the rest and
     * misses a lookahead (missingOne), so that what is refused is the
     * lookahead's rule; null when no lookahead can be missed.
     */
    static String notMatching(String regexp, String matching) {
        java.util.regex.Pattern compiled = compile(regexp);
        if (compiled == null) return null;
        Lookaheads lookaheads = Lookaheads.read(regexp);
        if (lookaheads != null) return missingOne(compiled, lookaheads, matching);
        List<String> candidates = new ArrayList<String>();
        candidates.add("!");
        candidates.add("~~");
        candidates.add(" ");
        candidates.add("0");
        candidates.add("a");
        candidates.add("");
        if (matching != null && !matching.isEmpty()) {
            candidates.add(matching + "!");
            candidates.add("!" + matching);
        }
        for (String c : candidates) {
            if (!compiled.matcher(c).matches()) return c;
        }
        return null;
    }

    private static java.util.regex.Pattern compile(String regexp) {
        try {
            return java.util.regex.Pattern.compile(regexp);
        } catch (java.util.regex.PatternSyntaxException invalid) {
            return null;
        }
    }

    // ---- the expression, read --------------------------------------------------------

    private interface Node {
        /**
         * Writes a matching value; extra is how many more repetitions an open
         * quantifier takes. from gets, for each character written, the class it
         * came from, or null for a literal.
         */
        void write(StringBuilder out, List<CharClass> from, int extra);
    }

    private static final class Literal implements Node {
        final char ch;

        Literal(char ch) {
            this.ch = ch;
        }

        @Override
        public void write(StringBuilder out, List<CharClass> from, int extra) {
            out.append(ch);
            from.add(null);
        }
    }

    /** A class of characters: the ranges it holds, or - negated - those it does not. */
    private static final class CharClass implements Node {
        final List<char[]> ranges = new ArrayList<char[]>();
        boolean negated;

        boolean holds(char ch) {
            for (char[] r : ranges) {
                if (ch >= r[0] && ch <= r[1]) return true;
            }
            return false;
        }

        @Override
        public void write(StringBuilder out, List<CharClass> from, int extra) {
            out.append(pick());
            from.add(this);
        }

        char pick() {
            String preferred = "aA0x_-. ";
            for (int i = 0; i < preferred.length(); i++) {
                char ch = preferred.charAt(i);
                if (holds(ch) != negated) return ch;
            }
            if (!negated && !ranges.isEmpty()) return ranges.get(0)[0];
            for (char ch = '!'; ch <= '~'; ch++) {
                if (holds(ch) != negated) return ch;
            }
            throw new IllegalArgumentException("no character");
        }
    }

    private static final class Sequence implements Node {
        final List<Node> items = new ArrayList<Node>();

        @Override
        public void write(StringBuilder out, List<CharClass> from, int extra) {
            for (Node n : items) n.write(out, from, extra);
        }
    }

    /** Alternatives: the first is written. */
    private static final class Choice implements Node {
        final List<Node> options = new ArrayList<Node>();

        @Override
        public void write(StringBuilder out, List<CharClass> from, int extra) {
            options.get(0).write(out, from, extra);
        }
    }

    private static final class Repeat implements Node {
        final Node item;
        final int min;
        /** -1: no upper bound. */
        final int max;

        Repeat(Node item, int min, int max) {
            this.item = item;
            this.min = min;
            this.max = max;
        }

        @Override
        public void write(StringBuilder out, List<CharClass> from, int extra) {
            int times = min + extra;
            if (max >= 0) times = Math.min(times, max);
            for (int i = 0; i < times; i++) item.write(out, from, extra);
        }
    }

    private static final class Empty implements Node {
        @Override
        public void write(StringBuilder out, List<CharClass> from, int extra) {}
    }

    /** A recursive reader of the subset the class comment names. */
    private static final class Parser {
        final String re;
        int at;

        Parser(String re) {
            this.re = re;
        }

        Node parse() {
            Node n = alternatives();
            if (at < re.length()) throw new IllegalArgumentException("unread: " + re.substring(at));
            return n;
        }

        private Node alternatives() {
            Choice choice = new Choice();
            choice.options.add(sequence());
            while (at < re.length() && re.charAt(at) == '|') {
                at++;
                choice.options.add(sequence());
            }
            return choice.options.size() == 1 ? choice.options.get(0) : choice;
        }

        private Node sequence() {
            Sequence seq = new Sequence();
            while (at < re.length() && re.charAt(at) != '|' && re.charAt(at) != ')') {
                Node atom = atom();
                seq.items.add(quantified(atom));
            }
            return seq;
        }

        private Node quantified(Node atom) {
            if (at >= re.length()) return atom;
            char ch = re.charAt(at);
            Node out = atom;
            if (ch == '?') {
                at++;
                out = new Repeat(atom, 0, 1);
            } else if (ch == '*') {
                at++;
                out = new Repeat(atom, 0, -1);
            } else if (ch == '+') {
                at++;
                out = new Repeat(atom, 1, -1);
            } else if (ch == '{'
                    && re.indexOf('}', at) > at
                    && re.substring(at + 1, re.indexOf('}', at)).matches("\\d+(,\\d*)?")) {
                String body = re.substring(at + 1, re.indexOf('}', at));
                at = re.indexOf('}', at) + 1;
                int comma = body.indexOf(',');
                if (comma < 0) {
                    int n = Integer.parseInt(body);
                    out = new Repeat(atom, n, n);
                } else {
                    int lo = Integer.parseInt(body.substring(0, comma));
                    String hi = body.substring(comma + 1);
                    out = new Repeat(atom, lo, hi.isEmpty() ? -1 : Integer.parseInt(hi));
                }
            } else {
                return atom;
            }
            // Lazy and possessive forms match the same values.
            if (at < re.length() && (re.charAt(at) == '?' || re.charAt(at) == '+')) at++;
            return out;
        }

        private Node atom() {
            char ch = re.charAt(at++);
            switch (ch) {
                case '^':
                case '$':
                    return new Empty();
                case '.':
                    CharClass any = new CharClass();
                    any.ranges.add(new char[] {'a', 'a'});
                    return any;
                case '(':
                    if (re.startsWith("?:", at)) {
                        at += 2;
                    } else if (re.startsWith("?<", at)
                            && at + 2 < re.length()
                            && re.charAt(at + 2) != '='
                            && re.charAt(at + 2) != '!') {
                        int close = re.indexOf('>', at);
                        if (close < 0) throw new IllegalArgumentException("group");
                        at = close + 1;
                    } else if (re.startsWith("?", at) && flagsAt(at + 1) > 0) {
                        // (?i) or (?i:...): flags only. A value written as the sample is still matched (the
                        // pattern is checked whole), so they are read past.
                        int end = flagsAt(at + 1);
                        char after = re.charAt(end);
                        at = end + 1;
                        if (after == ')') return new Empty();
                    } else if (at < re.length() && re.charAt(at) == '?') {
                        throw new IllegalArgumentException("lookaround");
                    }
                    Node inner = alternatives();
                    if (at >= re.length() || re.charAt(at) != ')') throw new IllegalArgumentException("unclosed group");
                    at++;
                    return inner;
                case '[':
                    return charClass();
                case '\\':
                    return escape();
                default:
                    if (ch == '*' || ch == '+' || ch == '?') throw new IllegalArgumentException("quantifier");
                    return new Literal(ch);
            }
        }

        /** The index of the ')' or ':' ending inline flags (i, -i, im) that start here; -1 when none do. */
        private int flagsAt(int i) {
            int k = i;
            while (k < re.length() && (Character.isLetter(re.charAt(k)) || re.charAt(k) == '-')) k++;
            if (k == i || k >= re.length()) return -1;
            return re.charAt(k) == ')' || re.charAt(k) == ':' ? k : -1;
        }

        private Node escape() {
            if (at >= re.length()) throw new IllegalArgumentException("escape");
            char ch = re.charAt(at++);
            CharClass c = new CharClass();
            switch (ch) {
                case 'd':
                    c.ranges.add(new char[] {'0', '9'});
                    return c;
                case 'D':
                    c.ranges.add(new char[] {'0', '9'});
                    c.negated = true;
                    return c;
                case 'w':
                    c.ranges.add(new char[] {'a', 'z'});
                    c.ranges.add(new char[] {'A', 'Z'});
                    c.ranges.add(new char[] {'0', '9'});
                    c.ranges.add(new char[] {'_', '_'});
                    return c;
                case 'W':
                    c.ranges.add(new char[] {'a', 'z'});
                    c.ranges.add(new char[] {'A', 'Z'});
                    c.ranges.add(new char[] {'0', '9'});
                    c.ranges.add(new char[] {'_', '_'});
                    c.negated = true;
                    return c;
                case 's':
                    c.ranges.add(new char[] {' ', ' '});
                    return c;
                case 'S':
                    c.ranges.add(new char[] {' ', ' '});
                    c.ranges.add(new char[] {'\t', '\r'});
                    c.negated = true;
                    return c;
                case 't':
                    return new Literal('\t');
                case 'n':
                    return new Literal('\n');
                case 'u':
                    if (at + 4 > re.length()) throw new IllegalArgumentException("escape");
                    char u = (char) Integer.parseInt(re.substring(at, at + 4), 16);
                    at += 4;
                    return new Literal(u);
                default:
                    if (Character.isLetterOrDigit(ch)) throw new IllegalArgumentException("escape \\" + ch);
                    return new Literal(ch);
            }
        }

        private Node charClass() {
            CharClass c = new CharClass();
            if (at < re.length() && re.charAt(at) == '^') {
                c.negated = true;
                at++;
            }
            boolean first = true;
            while (at < re.length() && (re.charAt(at) != ']' || first)) {
                first = false;
                char lo;
                char ch = re.charAt(at);
                if (ch == '[') throw new IllegalArgumentException("nested class");
                if (ch == '\\') {
                    at++;
                    Node n = escape();
                    if (n instanceof CharClass) {
                        CharClass inner = (CharClass) n;
                        if (inner.negated) throw new IllegalArgumentException("negated escape in a class");
                        c.ranges.addAll(inner.ranges);
                        continue;
                    }
                    lo = ((Literal) n).ch;
                } else {
                    lo = ch;
                    at++;
                }
                char hi = lo;
                if (at + 1 < re.length() && re.charAt(at) == '-' && re.charAt(at + 1) != ']') {
                    at++;
                    char end = re.charAt(at);
                    if (end == '\\') {
                        at++;
                        Node n = escape();
                        if (!(n instanceof Literal)) throw new IllegalArgumentException("range");
                        hi = ((Literal) n).ch;
                    } else {
                        hi = end;
                        at++;
                    }
                }
                c.ranges.add(new char[] {lo, hi});
            }
            if (at >= re.length()) throw new IllegalArgumentException("unclosed class");
            at++;
            return c;
        }
    }
}
