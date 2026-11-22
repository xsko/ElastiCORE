package io.elasticore.springboot3.export;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * Rows written as an .xlsx workbook of one sheet, through Apache POI's streaming workbook (SXSSFWorkbook): only a
 * window of rows is held in memory, the rest goes to a temporary file until {@link #close()} writes the workbook.
 *
 * <p>Needs Apache POI ({@code org.apache.poi:poi-ooxml}) on the application's classpath; the runtime library does not
 * bring it. {@link SearchExport#xlsxAvailable()} says whether it is there - load this class only when it is.
 *
 * <p>A cell: {@code null} is blank; a {@link Number} a number (one of more than 15 digits, which a spreadsheet's
 * number cannot hold exactly, as text); a {@link Boolean} a boolean; a {@link LocalDate} a date and a
 * {@link LocalDateTime}, {@link Instant} (UTC), {@link OffsetDateTime}, {@link ZonedDateTime} or {@link Date} a date
 * and time; anything else a text cell - never a formula, so a text that starts with = is shown as written. A text
 * longer than a cell holds (32,767 characters) is cut there.
 */
public final class XlsxWriter implements ExportSheet {

    /** The longest text an .xlsx cell holds. */
    static final int MAX_TEXT = 32_767;

    /** The rows a sheet holds, the title row with them. */
    public static final int MAX_ROWS = 1_048_576;

    /** The rows kept in memory before they go to the temporary file. */
    private static final int WINDOW = 100;

    private final OutputStream out;

    private final SXSSFWorkbook workbook;

    private final Sheet sheet;

    private final CellStyle date;

    private final CellStyle dateTime;

    private final CellStyle title;

    private int next;

    /** A workbook of one sheet; its name is cut and cleaned to what a sheet name may be. */
    public XlsxWriter(OutputStream out, String sheetName) {
        this.out = out;
        this.workbook = new SXSSFWorkbook(WINDOW);
        String name = sheetName == null || sheetName.trim().isEmpty() ? "Export" : sheetName;
        this.sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(name));
        CreationHelper helper = workbook.getCreationHelper();
        this.date = workbook.createCellStyle();
        date.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
        this.dateTime = workbook.createCellStyle();
        dateTime.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        this.title = workbook.createCellStyle();
        Font bold = workbook.createFont();
        bold.setBold(true);
        title.setFont(bold);
    }

    /** The column titles, in bold, the first row. */
    @Override
    public void header(List<String> titles) {
        Row row = sheet.createRow(next++);
        for (int i = 0; i < titles.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(text(titles.get(i)));
            cell.setCellStyle(title);
        }
    }

    /** One row, each value in its cell as the class describes. */
    @Override
    public void row(List<?> values) {
        Row row = sheet.createRow(next++);
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value == null) continue;
            write(row.createCell(i), value);
        }
    }

    private void write(Cell cell, Object value) {
        if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else if (value instanceof Number n) {
            BigDecimal number = n instanceof BigDecimal d ? d : new BigDecimal(n.toString());
            // A double holds 15 significant digits: a longer number (an id) is kept exact as its text.
            if (number.precision() - number.scale() > 15
                    || number.stripTrailingZeros().precision() > 15) {
                cell.setCellValue(number.toPlainString());
            } else {
                cell.setCellValue(number.doubleValue());
            }
        } else if (value instanceof LocalDate d) {
            cell.setCellValue(d);
            cell.setCellStyle(date);
        } else if (value instanceof LocalDateTime t) {
            cell.setCellValue(t);
            cell.setCellStyle(dateTime);
        } else if (value instanceof Instant i) {
            cell.setCellValue(LocalDateTime.ofInstant(i, ZoneOffset.UTC));
            cell.setCellStyle(dateTime);
        } else if (value instanceof OffsetDateTime o) {
            cell.setCellValue(o.toLocalDateTime());
            cell.setCellStyle(dateTime);
        } else if (value instanceof ZonedDateTime z) {
            cell.setCellValue(z.toLocalDateTime());
            cell.setCellStyle(dateTime);
        } else if (value instanceof Date d) {
            cell.setCellValue(d);
            cell.setCellStyle(dateTime);
        } else {
            cell.setCellValue(text(value.toString()));
        }
    }

    private static String text(String value) {
        if (value == null) return "";
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
    }

    @Override
    public void flush() {
        // The workbook is written whole by close(); the rows wait in its temporary file.
    }

    /**
     * A failed export: the workbook is not written - a sheet of fewer rows would look complete - and its temporary
     * file is deleted; nothing has reached the output.
     */
    @Override
    public void abandon() {
        workbook.dispose();
        try {
            workbook.close();
        } catch (IOException ignored) {
            // Nothing was written; the failure that abandoned it is the one to report.
        }
    }

    /** Writes the workbook to the output, closes it and deletes the temporary file. */
    @Override
    public void close() throws IOException {
        try {
            workbook.write(out);
            out.flush();
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }
}
