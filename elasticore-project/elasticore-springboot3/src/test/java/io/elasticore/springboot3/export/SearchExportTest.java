package io.elasticore.springboot3.export;

import io.elasticore.springboot3.mask.Mask;
import io.elasticore.springboot3.mask.MaskType;
import io.elasticore.springboot3.validation.BadRequestException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The export of a search (issue 271), as the runtime writes it on either Jackson generation: CSV as RFC 4180 with a
 * byte order mark, a text a spreadsheet would run written as text, every page of the search, the limit checked before
 * anything is written, the columns and titles asked for or the labels of the request's language, @Mask applied, and an
 * .xlsx workbook where Apache POI is there - 406 where it is not.
 */
public class SearchExportTest {

    /** A response DTO: the fields declared in this order, not the alphabet's. */
    public static class AccountDTO {
        private final Long id;
        private final String name;
        private final BigDecimal annualRevenue;
        private final LocalDate since;
        private final String card;

        AccountDTO(Long id, String name, BigDecimal annualRevenue, LocalDate since, String card) {
            this.id = id;
            this.name = name;
            this.annualRevenue = annualRevenue;
            this.since = since;
            this.card = card;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public BigDecimal getAnnualRevenue() {
            return annualRevenue;
        }

        public LocalDate getSince() {
            return since;
        }

        @Mask(MaskType.CARD)
        public String getCard() {
            return card;
        }
    }

    /** The language a test does not set is English, not the machine's (a Korean one here). */
    @org.junit.jupiter.api.BeforeEach
    void english() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static final String BOM = "\uFEFF";

    private static AccountDTO account(long id, String name) {
        return new AccountDTO(id, name, new BigDecimal("1200.50"), LocalDate.of(2026, 11, 2), "7712-3456-7890-3456");
    }

    private static SearchExport export(long maxRows, boolean poi) {
        return SearchExport.of("crm", "Account", AccountDTO.class, null).with(JsonRows.of(null), maxRows, poi);
    }

    private static SearchExport.Request<Object> body(String format, List<String> columns, List<String> headers) {
        SearchExport.Request<Object> body = new SearchExport.Request<Object>();
        body.setFormat(format);
        body.setColumns(columns);
        body.setHeaders(headers);
        return body;
    }

    /** The search as the generated controller hands it over: page and size to a page of the rows. */
    private static java.util.function.BiFunction<Integer, Integer, Page<?>> pagesOf(
            List<AccountDTO> all, AtomicInteger calls) {
        return (page, size) -> {
            calls.incrementAndGet();
            int from = Math.min(all.size(), page * size);
            int to = Math.min(all.size(), from + size);
            return new PageImpl<AccountDTO>(all.subList(from, to), PageRequest.of(page, size), all.size());
        };
    }

    private static String csv(MockHttpServletResponse response) {
        return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    // ---- CSV ----------------------------------------------------------------------------

    @Test
    public void csvIsUtf8WithAByteOrderMarkQuotedAsRfc4180WithCrlf() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CsvWriter csv = new CsvWriter(bytes)) {
            csv.header(Arrays.asList("name", "note"));
            csv.row(Arrays.asList("Kim, Ann", "said \"hi\""));
            csv.row(Arrays.asList("two\nlines", null));
            csv.row(Arrays.asList("plain", "\uD55C\uAE00"));
        }
        byte[] raw = bytes.toByteArray();
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(raw, 3));
        assertEquals(
                BOM + "name,note\r\n\"Kim, Ann\",\"said \"\"hi\"\"\"\r\n\"two\nlines\",\r\nplain,\uD55C\uAE00\r\n",
                new String(raw, StandardCharsets.UTF_8));
    }

    @Test
    public void aTextASpreadsheetWouldRunIsWrittenAsText() {
        for (String formula : new String[] {"=1+2", "+82 10", "-2+3", "@SUM(A1)", "\tx", "\rx"}) {
            assertEquals("'" + formula, CsvWriter.guarded(formula), formula);
        }
        assertEquals("'=1+2", CsvWriter.field("=1+2"));
        // Guarded, then quoted for the line break.
        assertEquals("\"'\rx\"", CsvWriter.field("\rx"));
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"", CsvWriter.field("=HYPERLINK(\"http://x\",\"y\")"));
        // A number is a number: a negative one is not a formula.
        assertEquals("-5", CsvWriter.field(new BigDecimal("-5")));
        assertEquals("true", CsvWriter.field(Boolean.TRUE));
        assertEquals("plain", CsvWriter.field("plain"));
        assertEquals("", CsvWriter.field(null));
    }

    @Test
    public void everyPageOfTheSearchIsWrittenInTheDtosOrder() throws Exception {
        List<AccountDTO> all = new ArrayList<AccountDTO>();
        for (int i = 1; i <= SearchExport.PAGE_SIZE * 2 + 7; i++) all.add(account(i, "a" + i));
        AtomicInteger calls = new AtomicInteger();
        MockHttpServletResponse response = new MockHttpServletResponse();
        export(10_000, true).pages(body(null, null, null), new MockHttpServletRequest(), response, pagesOf(all, calls));

        assertEquals(3, calls.get());
        assertEquals("text/csv;charset=UTF-8", response.getContentType());
        assertEquals("attachment; filename=\"Account.csv\"", response.getHeader("Content-Disposition"));
        String[] lines = csv(response).split("\r\n", -1);
        // The DTO's declaration order, whichever order Jackson writes; English: the _en label, else the name.
        assertEquals(BOM + "id,name,Annual revenue,since,card", lines[0]);
        // As the JSON shows it: the decimal as written, the date as text, the card masked.
        assertEquals("1,a1,1200.50,2026-11-02,7712-****-****-3456", lines[1]);
        assertEquals(all.size() + 2, lines.length, "a title row, a row each, and the last line's end");
        assertEquals("1007,a1007,1200.50,2026-11-02,7712-****-****-3456", lines[all.size()]);
    }

    @Test
    public void aSearchOfMoreRowsThanTheLimitIsRefusedBeforeAnythingIsWritten() throws Exception {
        List<AccountDTO> all = new ArrayList<AccountDTO>();
        for (int i = 1; i <= 6; i++) all.add(account(i, "a" + i));
        AtomicInteger calls = new AtomicInteger();
        MockHttpServletResponse response = new MockHttpServletResponse();
        BadRequestException refused = assertThrows(BadRequestException.class, () -> export(5, true)
                .pages(body(null, null, null), new MockHttpServletRequest(), response, pagesOf(all, calls)));
        assertEquals("export.too-many-rows", refused.getCode());
        assertTrue(
                refused.getMessage().contains("matches 6 rows")
                        && refused.getMessage().contains("at most 5"),
                refused.getMessage());
        assertEquals(1, calls.get(), "counted by the first page");
        assertEquals(0, response.getContentAsByteArray().length);
        assertNull(response.getContentType());

        // As many as the limit: written.
        MockHttpServletResponse five = new MockHttpServletResponse();
        export(6, true).pages(body(null, null, null), new MockHttpServletRequest(), five, pagesOf(all, calls));
        assertEquals(8, csv(five).split("\r\n", -1).length);
    }

    @Test
    public void theColumnsAndTitlesAskedForInTheirOrder() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        export(10, true)
                .list(
                        body(null, Arrays.asList("name", "id"), Arrays.asList("Who", "=No")),
                        new MockHttpServletRequest(),
                        response,
                        () -> Collections.singletonList(account(7, "=cmd|' /C calc'!A0")));
        // A title of the request is guarded as a value is.
        assertEquals(BOM + "Who,'=No\r\n'=cmd|' /C calc'!A0,7\r\n", csv(response));

        BadRequestException unknown = assertThrows(BadRequestException.class, () -> export(10, true)
                .list(
                        body(null, Arrays.asList("name", "password"), null),
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        Collections::emptyList));
        assertEquals("export.no-such-column", unknown.getCode());
        assertTrue(unknown.getMessage().startsWith("password is not a column of Account"), unknown.getMessage());

        BadRequestException headers = assertThrows(BadRequestException.class, () -> export(10, true)
                .list(
                        body(null, Arrays.asList("name", "id"), Arrays.asList("Who")),
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        Collections::emptyList));
        assertEquals("export.headers", headers.getCode());

        BadRequestException format = assertThrows(BadRequestException.class, () -> export(10, true)
                .list(
                        body("pdf", null, null),
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        Collections::emptyList));
        assertEquals("export.format", format.getCode());
    }

    /** The titles by the label bundle (i18n/crm-labels*): the language's file, _en for English, else the name. */
    @Test
    public void theTitlesAreTheLabelsOfTheRequestsLanguage() throws Exception {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        MockHttpServletResponse en = new MockHttpServletResponse();
        export(10, true)
                .list(
                        body(null, Arrays.asList("name", "annualRevenue"), null),
                        new MockHttpServletRequest(),
                        en,
                        Collections::emptyList);
        assertEquals(BOM + "name,Annual revenue\r\n", csv(en));

        LocaleContextHolder.setLocale(Locale.KOREAN);
        MockHttpServletResponse ko = new MockHttpServletResponse();
        export(10, true)
                .list(
                        body(null, Arrays.asList("name", "annualRevenue"), null),
                        new MockHttpServletRequest(),
                        ko,
                        Collections::emptyList);
        assertEquals(BOM + "name,\uC5F0 \uB9E4\uCD9C\r\n", csv(ko));

        // A language without a label file: the names.
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        MockHttpServletResponse ja = new MockHttpServletResponse();
        export(10, true)
                .list(
                        body(null, Arrays.asList("name", "annualRevenue"), null),
                        new MockHttpServletRequest(),
                        ja,
                        Collections::emptyList);
        assertEquals(BOM + "name,annualRevenue\r\n", csv(ja));
    }

    /** The views a JsonView DTO is written in. */
    public static class Views {
        public interface Read {}

        public interface Write {}
    }

    public static class NoteDTO {
        @com.fasterxml.jackson.annotation.JsonView(Views.Read.class)
        private final Long noteId = 3L;

        @com.fasterxml.jackson.annotation.JsonView(Views.Write.class)
        private final String secret = "s";

        @com.fasterxml.jackson.annotation.JsonView(Views.Read.class)
        private final String text = "hello";

        public Long getNoteId() {
            return noteId;
        }

        public String getSecret() {
            return secret;
        }

        public String getText() {
            return text;
        }
    }

    /** A search that answers in a JsonView exports what that view shows, and only that. */
    @Test
    public void theViewTheSearchAnswersInIsTheViewExported() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        SearchExport.of("crm", "Note", NoteDTO.class, Views.Read.class)
                .with(JsonRows.of(null), 10, true)
                .list(
                        body(null, null, null),
                        new MockHttpServletRequest(),
                        response,
                        () -> Collections.singletonList(new NoteDTO()));
        assertEquals(BOM + "noteId,text\r\n3,hello\r\n", csv(response));
    }

    // ---- .xlsx --------------------------------------------------------------------------

    @Test
    public void aWorkbookWithNumbersDatesAndTextNeverAFormula() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        export(10, true)
                .list(
                        body("xlsx", null, null),
                        new MockHttpServletRequest(),
                        response,
                        () -> Arrays.asList(account(1, "=1+2"), account(12345678901234567L, "Kim")));
        assertEquals(SearchExport.XLSX, response.getContentType());
        assertEquals("attachment; filename=\"Account.xlsx\"", response.getHeader("Content-Disposition"));
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = book.getSheetAt(0);
            assertEquals("Account", sheet.getSheetName());
            assertEquals("Annual revenue", sheet.getRow(0).getCell(2).getStringCellValue());
            Row first = sheet.getRow(1);
            assertEquals(CellType.NUMERIC, first.getCell(0).getCellType());
            assertEquals(1d, first.getCell(0).getNumericCellValue());
            // Written as written: a string cell, not a formula.
            assertEquals(CellType.STRING, first.getCell(1).getCellType());
            assertEquals("=1+2", first.getCell(1).getStringCellValue());
            assertEquals(1200.5d, first.getCell(2).getNumericCellValue());
            assertTrue(DateUtil.isCellDateFormatted(first.getCell(3)));
            assertEquals(
                    LocalDate.of(2026, 11, 2),
                    first.getCell(3).getLocalDateTimeCellValue().toLocalDate());
            // Masked, as the JSON shows it.
            assertEquals("7712-****-****-3456", first.getCell(4).getStringCellValue());
            // More digits than a spreadsheet's number holds: kept exact as text.
            assertEquals("12345678901234567", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(2, sheet.getLastRowNum());
        }
    }

    @Test
    public void theAcceptHeaderAsksForAWorkbook() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", SearchExport.XLSX);
        MockHttpServletResponse response = new MockHttpServletResponse();
        export(10, true).list(body(null, null, null), request, response, Collections::emptyList);
        assertEquals(SearchExport.XLSX, response.getContentType());

        // Both alike: CSV; format wins over Accept.
        MockHttpServletRequest both = new MockHttpServletRequest();
        both.addHeader("Accept", "text/csv, " + SearchExport.XLSX);
        MockHttpServletResponse csv = new MockHttpServletResponse();
        export(10, true).list(body(null, null, null), both, csv, Collections::emptyList);
        assertEquals("text/csv;charset=UTF-8", csv.getContentType());
        MockHttpServletResponse asked = new MockHttpServletResponse();
        export(10, true).list(body("csv", null, null), request, asked, Collections::emptyList);
        assertEquals("text/csv;charset=UTF-8", asked.getContentType());
    }

    @Test
    public void withoutApachePoiAWorkbookIsNotAcceptable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", SearchExport.XLSX);
        assertThrows(HttpMediaTypeNotAcceptableException.class, () -> export(10, false)
                .list(body(null, null, null), request, new MockHttpServletResponse(), Collections::emptyList));
        assertThrows(HttpMediaTypeNotAcceptableException.class, () -> export(10, false)
                .list(
                        body("xlsx", null, null),
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        Collections::emptyList));
        assertTrue(SearchExport.xlsxAvailable(), "POI is on this test's classpath");
    }

    /**
     * A page that fails after the first: a workbook is not written - a sheet of fewer rows would look complete - and
     * the response is left as it was, so the error is what is answered. A CSV file is cut short where it failed.
     */
    @Test
    public void anExportThatFailsOnTheWayAnswersTheErrorNotAShortWorkbook() {
        List<AccountDTO> all = new ArrayList<AccountDTO>();
        for (int i = 1; i <= SearchExport.PAGE_SIZE + 3; i++) all.add(account(i, "a" + i));
        java.util.function.BiFunction<Integer, Integer, Page<?>> failing = (page, size) -> {
            if (page > 0) throw new IllegalStateException("the database went away");
            return pagesOf(all, new AtomicInteger()).apply(page, size);
        };
        MockHttpServletResponse sheet = new MockHttpServletResponse();
        assertThrows(IllegalStateException.class, () -> export(10_000, true)
                .pages(body("xlsx", null, null), new MockHttpServletRequest(), sheet, failing));
        assertEquals(0, sheet.getContentAsByteArray().length);
        assertNull(sheet.getContentType());
        assertNull(sheet.getHeader("Content-Disposition"));
        assertTrue(!sheet.isCommitted());

        MockHttpServletResponse csv = new MockHttpServletResponse();
        assertThrows(IllegalStateException.class, () -> export(10_000, true)
                .pages(body(null, null, null), new MockHttpServletRequest(), csv, failing));
        assertEquals("text/csv;charset=UTF-8", csv.getContentType());
    }

    /** One sheet holds 1,048,575 rows below its title: a larger search is refused as an .xlsx, naming the sheet. */
    @Test
    public void aWorkbookIsRefusedAboveWhatASheetHolds() {
        java.util.function.BiFunction<Integer, Integer, Page<?>> huge = (page, size) -> new PageImpl<AccountDTO>(
                Collections.singletonList(account(1, "a")), PageRequest.of(page, size), 2_000_000L);
        BadRequestException sheet = assertThrows(BadRequestException.class, () -> export(5_000_000, true)
                .pages(body("xlsx", null, null), new MockHttpServletRequest(), new MockHttpServletResponse(), huge));
        assertTrue(sheet.getMessage().contains("an .xlsx sheet holds at most 1048575"), sheet.getMessage());
        BadRequestException csv = assertThrows(BadRequestException.class, () -> export(1_000_000, true)
                .pages(body(null, null, null), new MockHttpServletRequest(), new MockHttpServletResponse(), huge));
        assertTrue(csv.getMessage().contains("at most 1000000 (elasticore.export.max-rows)"), csv.getMessage());
    }

    public static class PeriodDTO {
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM")
        private final LocalDate month = LocalDate.of(2026, 11, 2);

        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
        private final LocalDate day = LocalDate.of(2026, 11, 2);

        public LocalDate getMonth() {
            return month;
        }

        public LocalDate getDay() {
            return day;
        }
    }

    /** A date is a date cell only where the JSON shows it in full: a yyyy-MM pattern keeps its text, as the response. */
    @Test
    public void aDateTheResponseShowsInPartStaysItsText() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        SearchExport.of("crm", "Period", PeriodDTO.class, null)
                .with(JsonRows.of(null), 10, true)
                .list(
                        body("xlsx", null, null),
                        new MockHttpServletRequest(),
                        response,
                        () -> Collections.singletonList(new PeriodDTO()));
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Row row = book.getSheetAt(0).getRow(1);
            assertEquals(CellType.STRING, row.getCell(0).getCellType());
            assertEquals("2026-11", row.getCell(0).getStringCellValue());
            assertTrue(DateUtil.isCellDateFormatted(row.getCell(1)));
        }
    }

    // ---- an enumeration by its label (issue 289) ------------------------------------------

    /** As the generator writes an enumeration: its code in the JSON, a getter a column. */
    public enum OpportunityStage {
        OPEN("OPN", "진행 중"),
        WON("WON", "수주"),
        LOST("LST", "실주");

        private final String code;
        private final String label;

        OpportunityStage(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @com.fasterxml.jackson.annotation.JsonValue
        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }

    /** An enumeration the label bundle does not hold (no label_en column): its own label column. */
    public enum Channel {
        WEB("W", "Web"),
        PHONE("P", "Phone");

        private final String code;
        private final String label;

        Channel(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @com.fasterxml.jackson.annotation.JsonValue
        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }

    /** An enumeration with no label at all. */
    public enum Priority {
        HIGH("H"),
        LOW("L");

        private final String code;

        Priority(String code) {
            this.code = code;
        }

        @com.fasterxml.jackson.annotation.JsonValue
        public String getCode() {
            return code;
        }
    }

    public static class OpportunityDTO {
        private final String name;
        private final OpportunityStage stage;
        private final Channel channel;
        private final Priority priority;

        OpportunityDTO(String name, OpportunityStage stage, Channel channel, Priority priority) {
            this.name = name;
            this.stage = stage;
            this.channel = channel;
            this.priority = priority;
        }

        public String getName() {
            return name;
        }

        public OpportunityStage getStage() {
            return stage;
        }

        public Channel getChannel() {
            return channel;
        }

        public Priority getPriority() {
            return priority;
        }
    }

    private static final List<OpportunityDTO> DEALS = Arrays.asList(
            new OpportunityDTO("a", OpportunityStage.OPEN, Channel.WEB, Priority.HIGH),
            new OpportunityDTO("b", OpportunityStage.LOST, Channel.PHONE, Priority.LOW),
            new OpportunityDTO("c", null, null, null));

    private static String deals(String format, List<String> labels) throws Exception {
        return csv(deals(format, labels, null));
    }

    private static MockHttpServletResponse deals(String format, List<String> labels, List<String> columns)
            throws Exception {
        SearchExport.Request<Object> body = body(format, columns, null);
        body.setLabels(labels);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SearchExport.of("crm", "Opportunity", OpportunityDTO.class, null)
                .with(JsonRows.of(null), 10, true)
                .list(body, new MockHttpServletRequest(), response, () -> DEALS);
        return response;
    }

    /** Without labels, or with an empty list: every enumeration by its code, as before. */
    @Test
    public void withoutLabelsAnEnumerationIsItsCode() throws Exception {
        String codes = BOM + "name,stage,channel,priority\r\na,OPN,W,H\r\nb,LST,P,L\r\nc,,,\r\n";
        assertEquals(codes, deals(null, null));
        assertEquals(codes, deals(null, Collections.<String>emptyList()));
    }

    /**
     * The columns labels names, by their label in the request's language: the language's file (label_en), else the
     * default file (the label column); the others by their code.
     */
    @Test
    public void theColumnsLabelsNamesAreWrittenByTheirLabel() throws Exception {
        // English: OPEN has a label_en, LOST has none - the label column, from the default file.
        assertEquals(
                BOM + "name,stage,channel,priority\r\na,Open,W,H\r\nb,실주,P,L\r\nc,,,\r\n",
                deals(null, Collections.singletonList("stage")));
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals(
                BOM + "name,단계,channel,priority\r\na,진행 중,W,H\r\nb,실주,P,L\r\nc,,,\r\n",
                deals(null, Collections.singletonList("stage")));
        // A language without a label file: the default file's label column.
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        assertEquals(
                BOM + "name,stage,channel,priority\r\na,진행 중,W,H\r\nb,실주,P,L\r\nc,,,\r\n",
                deals(null, Collections.singletonList("stage")));
    }

    /**
     * "*": every enumeration column - one the bundle does not hold by the label column the enumeration holds, one
     * with no label at all by its code.
     */
    @Test
    public void everyEnumerationColumnForAStar() throws Exception {
        assertEquals(
                BOM + "name,stage,channel,priority\r\na,Open,Web,H\r\nb,실주,Phone,L\r\nc,,,\r\n",
                deals(null, Collections.singletonList("*")));
        assertEquals(
                csv(deals(null, Collections.singletonList("*"), null)),
                csv(deals(null, Arrays.asList("stage", "channel", "priority"), null)));
        // Only the columns written: the labels of those.
        assertEquals(
                BOM + "channel,name\r\nWeb,a\r\nPhone,b\r\n,c\r\n",
                csv(deals(null, Collections.singletonList("*"), Arrays.asList("channel", "name"))));
    }

    /** A name that is not a column of this export, or not an enumeration: 400 naming it, as columns refuses one. */
    @Test
    public void aLabelThatIsNotAnEnumerationColumnOfTheExportIsRefused() {
        BadRequestException absent = assertThrows(
                BadRequestException.class,
                () -> deals(null, Collections.singletonList("channel"), Arrays.asList("name", "stage")));
        assertEquals("export.labels", absent.getCode());
        assertTrue(
                absent.getMessage().startsWith("labels names channel, which is not a column of this export"),
                absent.getMessage());
        BadRequestException unknown =
                assertThrows(BadRequestException.class, () -> deals(null, Collections.singletonList("owner"), null));
        assertTrue(unknown.getMessage().startsWith("labels names owner, which is not a column"), unknown.getMessage());
        BadRequestException text =
                assertThrows(BadRequestException.class, () -> deals(null, Arrays.asList("stage", "name"), null));
        assertEquals("export.labels", text.getCode());
        assertTrue(
                text.getMessage().startsWith("labels names name, which is not an enumeration;")
                        && text.getMessage().endsWith("stage, channel, priority"),
                text.getMessage());
        // Refused before the search runs or anything is written, also for a workbook.
        assertThrows(BadRequestException.class, () -> deals("xlsx", Collections.singletonList("name"), null));
    }

    /** In a workbook a label is a text cell. */
    @Test
    public void aWorkbookWritesTheLabelAsText() throws Exception {
        MockHttpServletResponse response = deals("xlsx", Collections.singletonList("*"), null);
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Row first = book.getSheetAt(0).getRow(1);
            assertEquals(CellType.STRING, first.getCell(1).getCellType());
            assertEquals("Open", first.getCell(1).getStringCellValue());
            assertEquals("Web", first.getCell(2).getStringCellValue());
            assertEquals("H", first.getCell(3).getStringCellValue());
            assertNull(book.getSheetAt(0).getRow(3).getCell(1));
        }
        MockHttpServletResponse codes = deals("xlsx", null, null);
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(codes.getContentAsByteArray()))) {
            assertEquals("OPN", book.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
        }
    }

    /** The body takes a list or "*" alone. */
    @Test
    public void theBodyTakesAListOrAStar() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        SearchExport.Request<?> star = mapper.readValue("{\"labels\":\"*\"}", SearchExport.Request.class);
        assertEquals(Collections.singletonList("*"), star.getLabels());
        SearchExport.Request<?> list =
                mapper.readValue("{\"labels\":[\"stage\",\"channel\"]}", SearchExport.Request.class);
        assertEquals(Arrays.asList("stage", "channel"), list.getLabels());
        assertNull(mapper.readValue("{}", SearchExport.Request.class).getLabels());
    }

    // ---- the order the pages are read in -------------------------------------------------

    @Test
    public void thePagesAreReadInTheOrderAskedThenByTheKey() {
        assertEquals("name+,id+", SearchExport.sortCode("name+", "price-", "id"));
        assertEquals("price-,id+", SearchExport.sortCode(null, "price-", "id"));
        assertEquals("price-,id+", SearchExport.sortCode("  ", "price-", "id"));
        assertEquals("id+", SearchExport.sortCode(null, null, "id"));
        assertEquals("id-", SearchExport.sortCode("id-", null, "id"));
        assertEquals("a+,b+", SearchExport.sortCode(null, null, "a", "b"));
        assertEquals("b-,a+", SearchExport.sortCode("b-", null, "a", "b"));
        // The old sortColumn / sortAscending of the search: its order, as getSort() reads it, then the key.
        assertEquals("amount+,id+", SearchExport.sortCode(null, "price-", "amount", (Boolean) null, "id"));
        assertEquals("amount-,id+", SearchExport.sortCode(null, "price-", "amount", Boolean.FALSE, "id"));
        assertEquals("name+,id+", SearchExport.sortCode("name+", null, "amount", Boolean.FALSE, "id"));
        // A term without + or - orders nothing: it does not stand for the key.
        assertEquals("id,id+", SearchExport.sortCode("id", null, "id"));
    }
}
