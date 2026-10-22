package io.elasticore.base.model.pub.rules.merge;

/**
 * A source file with each character classified as code or not, and the brace
 * depth at every position.
 *
 * A merge has to find member boundaries in a file a developer wrote, where a
 * brace can appear inside a string, a character literal, a comment or a text
 * block and means nothing there. Counting braces without this distinction
 * mis-locates the boundaries, and a merge that mis-locates a boundary damages
 * the developer's code - so the scan is separated out and stated on its own.
 */
public final class SourceScan {

    private final String text;
    private final boolean[] code;
    private final int[] depth;

    public SourceScan(String source) {
        this.text = source.replace("\r\n", "\n");
        int n = this.text.length();
        this.code = new boolean[n];
        this.depth = new int[n];
        scan();
    }

    public String text() {
        return text;
    }

    public int length() {
        return text.length();
    }

    public char charAt(int i) {
        return text.charAt(i);
    }

    /** Whether the character at i is code rather than comment or literal. */
    public boolean isCode(int i) {
        return code[i];
    }

    /** The brace depth after the character at i has been taken into account. */
    public int depthAt(int i) {
        return depth[i];
    }

    /** The index of the next code character at or after i, or -1. */
    public int nextCode(int i) {
        for (int p = i; p < code.length; p++) {
            if (code[p] && !Character.isWhitespace(text.charAt(p))) return p;
        }
        return -1;
    }

    private void scan() {
        final int NONE = 0, LINE = 1, BLOCK = 2, STR = 3, CHR = 4, TEXT = 5;
        int state = NONE;
        int level = 0;
        int filled = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = (i + 1 < text.length()) ? text.charAt(i + 1) : '\0';

            switch (state) {
                case NONE:
                    if (c == '/' && next == '/') {
                        state = LINE;
                    } else if (c == '/' && next == '*') {
                        state = BLOCK;
                    } else if (c == '"' && next == '"' && i + 2 < text.length()
                            && text.charAt(i + 2) == '"') {
                        state = TEXT;
                        i += 2;
                    } else if (c == '"') {
                        state = STR;
                    } else if (c == '\'') {
                        state = CHR;
                    } else {
                        // The only position where a brace counts.
                        code[i] = true;
                        if (c == '{') level++;
                        else if (c == '}') level--;
                    }
                    break;

                case LINE:
                    if (c == '\n') state = NONE;
                    break;

                case BLOCK:
                    if (c == '*' && next == '/') {
                        state = NONE;
                        i++;
                    }
                    break;

                case STR:
                    if (c == '\\') i++;
                    else if (c == '"') state = NONE;
                    break;

                case CHR:
                    if (c == '\\') i++;
                    else if (c == '\'') state = NONE;
                    break;

                case TEXT:
                    if (c == '\\') {
                        i++;
                    } else if (c == '"' && next == '"' && i + 2 < text.length()
                            && text.charAt(i + 2) == '"') {
                        state = NONE;
                        i += 2;
                    }
                    break;

                default:
                    break;
            }

            // The switch advances i past multi-character tokens, so fill every
            // index up to here and not just this one - a caller indexes the
            // array directly and there must be no gaps.
            while (filled <= i) depth[filled++] = level;
        }
    }
}
