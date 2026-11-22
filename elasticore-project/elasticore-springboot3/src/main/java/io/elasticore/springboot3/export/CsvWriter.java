package io.elasticore.springboot3.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Rows written as CSV, as a spreadsheet opens them: UTF-8 with a byte order mark (so Excel reads the text as UTF-8),
 * a field quoted as RFC 4180 says - one holding a comma, a double quote or a line break is put in double quotes, a
 * double quote in it doubled - and every row ended with CRLF.
 *
 * <p>A text that starts with {@code =}, {@code +}, {@code -}, {@code @}, a tab or a carriage return is written with a
 * single quote before it, so a spreadsheet shows it as text instead of running it as a formula (CSV injection, as
 * OWASP advises). A {@link Number} or a {@link Boolean} is written as it is: a negative number is a number, not a
 * formula. {@code null} is an empty field.
 *
 * <p>Streamed: each row goes to the output as it is written; {@link #close()} flushes and closes it.
 */
public final class CsvWriter implements ExportSheet {

    /** The byte order mark a UTF-8 file opens with, for the spreadsheets that look for it. */
    private static final String BOM = "\uFEFF";

    private static final String CRLF = "\r\n";

    private final Writer out;

    private boolean started;

    public CsvWriter(OutputStream out) {
        this.out = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    /** The first row, the column titles: written as a row is. */
    @Override
    public void header(List<String> titles) throws IOException {
        row(titles);
    }

    /** One row: each value in its field, as the class describes. */
    @Override
    public void row(List<?> values) throws IOException {
        if (!started) {
            out.write(BOM);
            started = true;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) line.append(',');
            line.append(field(values.get(i)));
        }
        line.append(CRLF);
        out.write(line.toString());
    }

    /** One value as its field: guarded when it is text, then quoted where RFC 4180 needs it. */
    public static String field(Object value) {
        if (value == null) return "";
        if (value instanceof Number || value instanceof Boolean) return quoted(value.toString());
        return quoted(guarded(value.toString()));
    }

    /**
     * The text a spreadsheet shows as text: one that starts with =, +, -, @, a tab or a carriage return gets a single
     * quote before it; any other is returned as it is.
     */
    public static String guarded(String text) {
        if (text == null || text.isEmpty()) return text;
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + text;
        }
        return text;
    }

    /** The field as RFC 4180 writes it: in double quotes, each one doubled, where it holds a comma, a quote or a line break. */
    public static String quoted(String text) {
        if (text == null) return "";
        boolean needs = false;
        for (int i = 0; i < text.length() && !needs; i++) {
            char c = text.charAt(i);
            needs = c == ',' || c == '"' || c == '\n' || c == '\r';
        }
        if (!needs) return text;
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * A failed export: the rows not flushed yet are dropped and the output is left to the response - what was
     * already sent stays sent, a file cut short.
     */
    @Override
    public void abandon() {
        // Nothing to release: the writer holds only the response's stream.
    }

    /** Flushes what is written and closes the output - with the byte order mark alone when no row was written. */
    @Override
    public void close() throws IOException {
        if (!started) {
            out.write(BOM);
            started = true;
        }
        out.close();
    }
}
