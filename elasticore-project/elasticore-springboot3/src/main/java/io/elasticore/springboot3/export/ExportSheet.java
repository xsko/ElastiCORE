package io.elasticore.springboot3.export;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.List;

/** What an export writes its rows into: a CSV file or an .xlsx sheet. Not for an application to implement. */
interface ExportSheet extends Closeable, Flushable {

    /** The column titles, the first row. */
    void header(List<String> titles) throws IOException;

    /** One row of values, a column each. */
    void row(List<?> values) throws IOException;

    /** Gives up on a failed export: what is not written yet is not written, and nothing is left open. */
    void abandon();
}
