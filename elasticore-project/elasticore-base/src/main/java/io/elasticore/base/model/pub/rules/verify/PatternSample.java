package io.elasticore.base.model.pub.rules.verify;

import java.util.ArrayList;
import java.util.List;

/**
 * A value that matches a @pattern's regular expression, and one that does
 * not - for the contract tests, which send both.
 *
 * Read from the expression itself: literals, escapes (\d \w \s and an
 * escaped character), classes ([a-z0-9_], [^...]), the dot, groups and the
 * first of their alternatives, and the quantifiers ? * + {n} {n,} {n,m}.
 * Anything else (a lookaround, a back reference) is no sample - the caller
 * says so. Every value is checked with java.util.regex, the engine Bean
 * Validation's @Pattern runs, before it is offered: a sample that would not
 * match is never written into a test.
 */
final class PatternSample {

    private PatternSample() {}

    /** A value that matches, of a length in [min, max]; null when none is found. */
    static String matching(String regexp, int min, int max) {
        java.util.regex.Pattern compiled = compile(regexp);
        if (compiled == null) return null;
        Node node;
        try {
            node = new Parser(regexp).parse();
        } catch (RuntimeException unsupported) {
            return null;
        }
        for (int extra : new int[] {0, 1, 2, 4, 8, 16, 32}) {
            String sample;
            try {
                StringBuilder sb = new StringBuilder();
                node.write(sb, extra);
                sample = sb.toString();
            } catch (RuntimeException unsupported) {
                return null;
            }
            if (max > 0 && sample.length() > max) continue;
            if (sample.length() < min) continue;
            if (compiled.matcher(sample).matches()) return sample;
        }
        return null;
    }

    /** A value that does not match; null when none of the candidates fails it. */
    static String notMatching(String regexp, String matching) {
        java.util.regex.Pattern compiled = compile(regexp);
        if (compiled == null) return null;
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
        /** Writes a matching value; extra is how many more repetitions an open quantifier takes. */
        void write(StringBuilder out, int extra);
    }

    private static final class Literal implements Node {
        final char ch;

        Literal(char ch) {
            this.ch = ch;
        }

        @Override
        public void write(StringBuilder out, int extra) {
            out.append(ch);
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
        public void write(StringBuilder out, int extra) {
            out.append(pick());
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
        public void write(StringBuilder out, int extra) {
            for (Node n : items) n.write(out, extra);
        }
    }

    /** Alternatives: the first is written. */
    private static final class Choice implements Node {
        final List<Node> options = new ArrayList<Node>();

        @Override
        public void write(StringBuilder out, int extra) {
            options.get(0).write(out, extra);
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
        public void write(StringBuilder out, int extra) {
            int times = min + extra;
            if (max >= 0) times = Math.min(times, max);
            for (int i = 0; i < times; i++) item.write(out, extra);
        }
    }

    private static final class Empty implements Node {
        @Override
        public void write(StringBuilder out, int extra) {}
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
                    } else if (at < re.length() && re.charAt(at) == '?') {
                        throw new IllegalArgumentException("lookaround or flags");
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
