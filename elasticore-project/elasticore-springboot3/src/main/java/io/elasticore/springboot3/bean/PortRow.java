package io.elasticore.springboot3.bean;

import java.util.LinkedHashMap;

/**
 * One row of a database port's SQL as a generated MyBatis mapper reads it:
 * each column by its label, in the order the SQL selects them. MyBatis leaves
 * a NULL column out of a map it reads, and answers a row of NULLs alone as
 * null; for a statement that answers this type,
 * {@link io.elasticore.springboot3.mybatis.PortRowColumns} puts every column
 * in, a NULL one as null - the row the port runtime reads over JDBC.
 */
public class PortRow extends LinkedHashMap<String, Object> {

    private static final long serialVersionUID = 1L;
}
