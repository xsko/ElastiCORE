package io.elasticore.springboot3.export;

import io.elasticore.springboot3.validation.BadRequestException;
import io.elasticore.springboot3.validation.ErrorMessages;
import io.elasticore.springboot3.validation.Label;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.ClassUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;

/**
 * The rows of a search written as a file: what {@code POST /api/<domain>/<entity>/export} of a generated controller
 * answers, where the DSL lists {@code export} in {@code @expose(methods="...")}. The controller only wires it: it
 * hands over the request's body and a call of its service's search, and this writes every page of it.
 *
 * <ul>
 *   <li>The rows: the search the service makes for {@code POST .../search} - its conditions, its access check and
 *       {@code @owner} filter - every page of it, in the order asked for ({@code sortCode}).
 *   <li>The columns: the response DTO's properties as its JSON writes them, in the order the DTO declares them, or
 *       those the body lists ({@code columns}); a value as the JSON shows it - masked where {@code @Mask} masks it,
 *       an enumeration as its code.
 *   <li>The titles: the body's {@code headers}; else each column's label in the request's language, from the
 *       domain's label bundle ({@code i18n/<domain>-labels_<lang>.properties}, {@code Type.field} keys - the
 *       response DTO's, then the entity's), as a refusal names a field; else the column's name.
 *   <li>The file: CSV (UTF-8 with a byte order mark, RFC 4180, CRLF), or an .xlsx workbook when the body says
 *       {@code "format": "xlsx"} or the Accept header asks for one, and Apache POI is on the application's classpath
 *       - without it, 406. A text a spreadsheet would run as a formula is written as text.
 *   <li>The limit: a search of more rows than {@code elasticore.export.max-rows} (100,000 unless set) is refused
 *       with 400 and the count, before anything is written.
 * </ul>
 */
public final class SearchExport {

    /** The Spring property: the most rows an export writes. */
    public static final String MAX_ROWS = "elasticore.export.max-rows";

    /** The most rows an export writes where {@link #MAX_ROWS} is not set. */
    public static final long DEFAULT_MAX_ROWS = 100_000L;

    /** The media type of an .xlsx workbook. */
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** The media type of the CSV file. */
    public static final String CSV = "text/csv";

    /** The rows read from the search at a time. */
    static final int PAGE_SIZE = 500;

    private static final MediaType XLSX_TYPE = MediaType.parseMediaType(XLSX);

    private static final MediaType CSV_TYPE = MediaType.parseMediaType(CSV);

    /** The class a workbook is written with: there only where the application has Apache POI. */
    private static final String POI = "org.apache.poi.xssf.streaming.SXSSFWorkbook";

    /** Each domain's labels, by its name. */
    private static final Map<String, ErrorMessages> LABELS = new ConcurrentHashMap<String, ErrorMessages>();

    private final String domain;

    private final String type;

    private final Class<?> rowType;

    private final Class<?> view;

    /** Set by a test; otherwise found in the request's application context. */
    private JsonRows rows;

    private Long maxRows;

    private Boolean poi;

    private SearchExport(String domain, String type, Class<?> rowType, Class<?> view) {
        this.domain = domain;
        this.type = type;
        this.rowType = rowType;
        this.view = view;
    }

    /**
     * An export of an entity's search.
     *
     * @param domain  the domain, whose label bundle names the columns: {@code i18n/<domain>-labels}
     * @param type    the entity: the file's name, and the label keys after the response DTO's
     * @param rowType the response DTO the search answers with
     * @param view    the JsonView the search answers with, or null
     */
    public static SearchExport of(String domain, String type, Class<?> rowType, Class<?> view) {
        return new SearchExport(domain, type, rowType, view);
    }

    /** A test's collaborators, in place of the application's. */
    SearchExport with(JsonRows rows, long maxRows, boolean poi) {
        this.rows = rows;
        this.maxRows = maxRows;
        this.poi = poi;
        return this;
    }

    /**
     * Writes a paged search: page 0 first, whose total is the count the limit is checked against before anything
     * is written, then each next page while there is one.
     *
     * @param page the search: page number (from 0) and size to a page of rows
     */
    public void pages(
            Request<?> request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            BiFunction<Integer, Integer, Page<?>> page)
            throws IOException, HttpMediaTypeNotAcceptableException {
        Plan plan = plan(request, httpRequest);
        Page<?> first = page.apply(0, PAGE_SIZE);
        plan.limit(first.getTotalElements());
        ExportSheet sheet = plan.open(httpResponse);
        boolean complete = false;
        try {
            long written = plan.write(sheet, first.getContent(), 0);
            Page<?> current = first;
            int number = 0;
            while (current.hasNext() && !current.getContent().isEmpty() && written < plan.max) {
                current = page.apply(++number, PAGE_SIZE);
                written = plan.write(sheet, current.getContent(), written);
                sheet.flush();
            }
            complete = true;
        } finally {
            plan.finish(sheet, httpResponse, complete);
        }
    }

    /** Writes a search that is not paged: every row at once, counted before anything is written. */
    public void list(
            Request<?> request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            Supplier<List<?>> search)
            throws IOException, HttpMediaTypeNotAcceptableException {
        Plan plan = plan(request, httpRequest);
        List<?> all = search.get();
        plan.limit(all.size());
        ExportSheet sheet = plan.open(httpResponse);
        boolean complete = false;
        try {
            plan.write(sheet, all, 0);
            complete = true;
        } finally {
            plan.finish(sheet, httpResponse, complete);
        }
    }

    /** The response's stream, asked for only when the first byte is written: a workbook that is never written leaves it alone. */
    private static final class LazyOutput extends OutputStream {
        private final HttpServletResponse response;
        private OutputStream out;

        LazyOutput(HttpServletResponse response) {
            this.response = response;
        }

        private OutputStream out() throws IOException {
            if (out == null) out = new BufferedOutputStream(response.getOutputStream());
            return out;
        }

        @Override
        public void write(int b) throws IOException {
            out().write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out().write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            if (out != null) out.flush();
        }

        @Override
        public void close() throws IOException {
            if (out != null) out.close();
        }
    }

    /**
     * The order the pages are read in: the sort asked for (the body's, else the search's own), with the key after it
     * where it does not name the key already - so that rows of the same sort value do not move between two pages.
     * The key alone where nothing was asked for.
     */
    public static String sortCode(String asked, String own, String... key) {
        return sortCode(asked, own, null, null, key);
    }

    /**
     * As {@link #sortCode(String, String, String...)}, where the search may name its order the old way, by
     * {@code sortColumn} and {@code sortAscending} - which win over its own sort code there - so that the order stays
     * the search's and the key still goes after it. The caller clears sortColumn, so that the code is what is read.
     */
    public static String sortCode(String asked, String own, String column, Boolean ascending, String... key) {
        String sort;
        if (asked != null && !asked.trim().isEmpty()) sort = asked.trim();
        else if (column != null && !column.trim().isEmpty())
            sort = column.trim() + (Boolean.FALSE.equals(ascending) ? "-" : "+");
        else sort = own;
        StringBuilder out = new StringBuilder(sort == null ? "" : sort.trim());
        // A term orders only with its + or -: the search reads no other (name alone is no order).
        List<String> named = new ArrayList<String>();
        for (String term : COMMA.split(out, -1)) {
            String t = term.trim();
            if (t.endsWith("+") || t.endsWith("-"))
                named.add(t.substring(0, t.length() - 1).trim());
        }
        for (String k : key) {
            if (k == null || k.isEmpty() || named.contains(k)) continue;
            if (out.length() > 0) out.append(',');
            out.append(k).append('+');
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static final java.util.regex.Pattern COMMA = java.util.regex.Pattern.compile(",");

    /** Whether Apache POI is on the application's classpath, so an .xlsx workbook can be written. */
    public static boolean xlsxAvailable() {
        return ClassUtils.isPresent(POI, ClassUtils.getDefaultClassLoader());
    }

    // ---- one export ---------------------------------------------------------------------

    /** What one request asks for, checked before the search runs. */
    private Plan plan(Request<?> request, HttpServletRequest httpRequest)
            throws IOException, HttpMediaTypeNotAcceptableException {
        Request<?> body = request == null ? new Request<Object>() : request;
        ApplicationContext context = rows == null || maxRows == null ? contextOf(httpRequest) : null;
        JsonRows json = rows != null ? rows : JsonRows.of(context);
        long max = maxRows != null ? maxRows : maxRowsOf(context);
        boolean xlsx = xlsx(body.getFormat(), httpRequest);
        if (xlsx && !(poi != null ? poi : xlsxAvailable())) {
            throw new HttpMediaTypeNotAcceptableException(Collections.singletonList(CSV_TYPE));
        }
        List<String> known = json.columns(rowType, view);
        List<String> columns = columns(body.getColumns(), known);
        List<String> titles = titles(body.getHeaders(), columns);
        return new Plan(json, xlsx, max, columns, titles);
    }

    private final class Plan {
        final JsonRows json;
        final boolean xlsx;
        /** The rows elasticore.export.max-rows lets an export write. */
        final long maxRows;
        /** The rows this one writes at most: maxRows, and for a workbook what one sheet holds below its title row. */
        final long max;

        final List<String> columns;
        final List<String> titles;

        Plan(JsonRows json, boolean xlsx, long maxRows, List<String> columns, List<String> titles) {
            this.json = json;
            this.xlsx = xlsx;
            this.maxRows = maxRows;
            this.max = xlsx ? Math.min(maxRows, XlsxWriter.MAX_ROWS - 1L) : maxRows;
            this.columns = columns;
            this.titles = titles;
        }

        /** 400 with the count when the search matches more rows than an export writes. */
        void limit(long count) {
            if (count <= max) return;
            if (max < maxRows) {
                throw new BadRequestException(
                        "export.too-many-rows",
                        "The search matches {0} rows; an .xlsx sheet holds at most {1}. Narrow the search, or export"
                                + " it as CSV.",
                        String.valueOf(count),
                        String.valueOf(max));
            }
            throw new BadRequestException(
                    "export.too-many-rows",
                    "The search matches {0} rows; an export writes at most {1} ({2}). Narrow the search.",
                    String.valueOf(count),
                    String.valueOf(max),
                    MAX_ROWS);
        }

        /**
         * The sheet, its title row written. A CSV file goes out as it is written, so its answer is set now; a
         * workbook is written whole when every page is in (finish), so its answer - and the response's stream - wait
         * until then: an export that fails on the way answers the error, not a workbook of fewer rows.
         */
        ExportSheet open(HttpServletResponse response) throws IOException {
            ExportSheet sheet;
            if (xlsx) {
                sheet = new XlsxWriter(new LazyOutput(response), type);
            } else {
                answer(response);
                sheet = new CsvWriter(response.getOutputStream());
            }
            sheet.header(titles);
            return sheet;
        }

        /** The status and the headers of the file. */
        void answer(HttpServletResponse response) {
            String file = (type == null || type.isEmpty() ? "export" : type) + (xlsx ? ".xlsx" : ".csv");
            response.setStatus(HttpServletResponse.SC_OK);
            // The entity's name: a Java identifier, plain ASCII.
            response.setHeader(
                    "Content-Disposition",
                    ContentDisposition.attachment().filename(file).build().toString());
            response.setContentType(xlsx ? XLSX : CSV + ";charset=UTF-8");
        }

        /** Every page is in: the file is completed (a workbook written). Otherwise it is abandoned. */
        void finish(ExportSheet sheet, HttpServletResponse response, boolean complete) throws IOException {
            if (!complete) {
                sheet.abandon();
                return;
            }
            if (xlsx) answer(response);
            sheet.close();
        }

        /** The rows, up to the limit; the count written so far. */
        long write(ExportSheet sheet, List<?> rows, long written) throws IOException {
            long count = written;
            for (Object row : rows) {
                if (count >= max) break;
                sheet.row(json.values(row, view, columns, xlsx));
                count++;
            }
            return count;
        }
    }

    /** Whether to write a workbook: the body says xlsx, or it says nothing and the Accept header asks for one. */
    private static boolean xlsx(String format, HttpServletRequest request) {
        if (format != null && !format.trim().isEmpty()) {
            String f = format.trim().toLowerCase(Locale.ROOT);
            if ("xlsx".equals(f)) return true;
            if ("csv".equals(f)) return false;
            throw new BadRequestException("export.format", "format is csv or xlsx, not {0}", format);
        }
        String accept = request == null ? null : request.getHeader("Accept");
        if (accept == null || accept.trim().isEmpty()) return false;
        double sheet = 0;
        double csv = 0;
        try {
            for (MediaType m : MediaType.parseMediaTypes(accept)) {
                if (m.equalsTypeAndSubtype(XLSX_TYPE)) sheet = Math.max(sheet, m.getQualityValue());
                else if (m.equalsTypeAndSubtype(CSV_TYPE)) csv = Math.max(csv, m.getQualityValue());
            }
        } catch (InvalidMediaTypeException unreadable) {
            return false;
        }
        // Both asked for alike: CSV, the default.
        return sheet > csv;
    }

    /** The columns asked for, each one the response has; all of them when none is asked for. */
    private List<String> columns(List<String> asked, List<String> known) {
        if (asked == null || asked.isEmpty()) return known;
        List<String> out = new ArrayList<String>();
        for (String c : asked) {
            String name = c == null ? "" : c.trim();
            if (!known.contains(name)) {
                throw new BadRequestException(
                        "export.no-such-column",
                        "{0} is not a column of {1}; the columns are {2}",
                        name,
                        type,
                        String.join(", ", known));
            }
            out.add(name);
        }
        return out;
    }

    /** The titles: as the body gives them, one a column; else each column's label in the request's language. */
    private List<String> titles(List<String> headers, List<String> columns) {
        if (headers != null && !headers.isEmpty()) {
            if (headers.size() != columns.size()) {
                throw new BadRequestException(
                        "export.headers",
                        "headers gives {0} titles for {1} columns; give one a column, in the order of columns",
                        String.valueOf(headers.size()),
                        String.valueOf(columns.size()));
            }
            List<String> out = new ArrayList<String>();
            for (String h : headers) out.add(h == null ? "" : h);
            return out;
        }
        ErrorMessages labels = labels();
        List<String> out = new ArrayList<String>();
        for (String c : columns) out.add(title(labels, c));
        return out;
    }

    /** A column's label: the response DTO's, then the entity's; else its name. */
    private String title(ErrorMessages labels, String column) {
        if (labels == null) return column;
        String said = labels.label(Label.of(rowType.getSimpleName() + "." + column));
        if (!column.equals(said)) return said;
        if (type == null || type.isEmpty()) return column;
        return labels.label(Label.of(type + "." + column));
    }

    private ErrorMessages labels() {
        if (domain == null || domain.isEmpty()) return null;
        // The label bundle beside the message bundle, and a language that has a label file counts as translated.
        return LABELS.computeIfAbsent(
                domain, d -> new ErrorMessages(null, "i18n/" + d + "-messages", "i18n/" + d + "-labels"));
    }

    private static ApplicationContext contextOf(HttpServletRequest request) {
        if (request == null) return null;
        try {
            return org.springframework.web.servlet.support.RequestContextUtils.findWebApplicationContext(request);
        } catch (RuntimeException | LinkageError none) {
            return null;
        }
    }

    private static long maxRowsOf(ApplicationContext context) {
        if (context == null) return DEFAULT_MAX_ROWS;
        Long set = context.getEnvironment().getProperty(MAX_ROWS, Long.class);
        return set != null ? set : DEFAULT_MAX_ROWS;
    }

    // ---- the body -----------------------------------------------------------------------

    /**
     * The body of {@code POST .../export}.
     *
     * @param <S> the entity's search DTO, the one {@code POST .../search} takes
     */
    public static class Request<S> {

        /** The search's conditions; none: every row the caller may see. */
        @jakarta.validation.Valid
        private S search;

        /** The order, as the search takes it ({@code name+,price-}); none: the search's own. */
        private String sortCode;

        /** The response's properties to write, in this order; none: every one, in the DTO's order. */
        private List<String> columns;

        /** The column titles, one a column; none: each column's label in the request's language. */
        private List<String> headers;

        /** {@code csv} (none: CSV, unless Accept asks for an .xlsx workbook) or {@code xlsx}. */
        private String format;

        public S getSearch() {
            return search;
        }

        public void setSearch(S search) {
            this.search = search;
        }

        public String getSortCode() {
            return sortCode;
        }

        public void setSortCode(String sortCode) {
            this.sortCode = sortCode;
        }

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> columns) {
            this.columns = columns;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }
}
