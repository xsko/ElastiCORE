package io.elasticore.springboot3.bean;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.elasticore.runtime.port.DbmsService;
import io.elasticore.springboot3.dbms.DbmsSqlExecutor;
import io.elasticore.springboot3.dbms.meta.SqlQueryInfo;
import io.elasticore.springboot3.util.ReflectUtils;
import io.elasticore.springboot3.validation.BadRequestException;
import org.apache.commons.jexl3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What a DBMS port does with the SQL its declaration holds, whatever runs it:
 * reads the declaration (the port's YAML, reloaded while it changes in a
 * development checkout), keeps or drops its conditional lines, appends the
 * request's sort, binds the parameters by name, and maps the rows onto the
 * method's return type - a DTO by column label, a map, or one value.
 *
 * Running the SQL is the subclass's: {@link DbTransactionGateway} runs it
 * through JPA's EntityManager, {@link JdbcDbmsGateway} through JDBC, for an
 * application with no JPA - a MyBatis one.
 */
public abstract class SqlPortGateway implements DbmsSqlExecutor {

    Logger logger = Logger.getLogger(getClass().getSimpleName());

    @Autowired
    private ResourceLoader resourceLoader;

    private static ClassLoader classLoader;

    private static final JexlEngine jexl = new JexlBuilder().create();

    private static ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private static Map<String, SqlQueryInfo> sqlQueryInfoMap = new HashMap<>();

    // ---- what runs the SQL ------------------------------------------------------

    /**
     * The rows a query answers, each as its column labels to its values in
     * the order they were selected: first rows on, max of them at most (every
     * one when max is negative).
     */
    protected abstract List<Map<String, Object>> rows(SqlQueryInfo entry, String sql, Object input, String dataSource,
                                                      int first, int max);

    /** The single number a COUNT query answers. */
    protected abstract long count(String sql, Object input, String dataSource);

    /** Runs an UPDATE, INSERT or DELETE; answers the rows it changed. */
    protected abstract int update(String sql, Object input, String dataSource);

    /**
     * Executes a query and returns the results as a list.
     * 
     * @param <I> the input parameter type
     * @param <O> the output result type
     * @param dbmsSvcMeta the DBMS service metadata configuration
     * @param methodName the name of the method/query to execute
     * @param input the input parameters for the query
     * @param outputType the class of the expected output type
     * @return a list of results mapped to the specified output type
     * @throws IOException if an I/O error occurs during execution
     * @throws InvocationTargetException if the underlying method throws an exception
     * @throws NoSuchMethodException if the specified method cannot be found
     * @throws IllegalAccessException if the method cannot be accessed
     */
    @Override
    @Transactional(readOnly = true)
    public <I, O> List<O> inqueryList(DbmsService dbmsSvcMeta, String methodName, I input, Class<O> outputType) throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String processId = dbmsSvcMeta.id() + "." + methodName;
        String datasource = dbmsSvcMeta.datasource();
        return executeQuery(dbmsSvcMeta, methodName, input, outputType, datasource);
    }


    /**
     * Executes a paginated query and returns the results as a Page.
     * 
     * @param <I> the input parameter type
     * @param <O> the output result type
     * @param dbmsSvcMeta the DBMS service metadata configuration
     * @param methodName the name of the method/query to execute
     * @param input the input parameters for the query
     * @param outputType the class of the expected output type
     * @param pageable the pagination information
     * @return a Page containing the results and pagination metadata
     * @throws IOException if an I/O error occurs during execution
     * @throws InvocationTargetException if the underlying method throws an exception
     * @throws NoSuchMethodException if the specified method cannot be found
     * @throws IllegalAccessException if the method cannot be accessed
     */
    @Override
    @Transactional(readOnly = true)
    public <I, O> Page<O> inqueryPage(DbmsService dbmsSvcMeta, String methodName, I input, Class<O> outputType, Pageable pageable) throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String processId = dbmsSvcMeta.id() + "." + methodName;
        String datasource = dbmsSvcMeta.datasource();
        return executePagedQuery(dbmsSvcMeta, methodName, input, outputType, pageable, datasource, true);
    }


    /**
     * Executes a query and returns the first result or null if no results found.
     * 
     * @param <I> the input parameter type
     * @param <O> the output result type
     * @param dbmsSvcMeta the DBMS service metadata configuration
     * @param methodName the name of the method/query to execute
     * @param input the input parameters for the query
     * @param outputType the class of the expected output type
     * @return the first result mapped to the specified output type, or null if no results
     * @throws IOException if an I/O error occurs during execution
     * @throws NoSuchMethodException if the specified method cannot be found
     * @throws IllegalAccessException if the method cannot be accessed
     * @throws InvocationTargetException if the underlying method throws an exception
     */
    @Override
    @Transactional(readOnly = true)
    public <I, O> O inqueryFirst(DbmsService dbmsSvcMeta, String methodName, I input, Class<O> outputType) throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        String processId = dbmsSvcMeta.id() + "." + methodName;
        String datasource = dbmsSvcMeta.datasource();
        Pageable pageable = PageRequest.of(0, 1);
        Page<O> page = executePagedQuery(dbmsSvcMeta, methodName, input, outputType, pageable, datasource, false);

        // Not stream().findFirst(): that throws on a null element - a SUM over no rows.
        List<O> content = page.getContent();
        return content.isEmpty() ? null : content.get(0);
    }

    /**
     * Executes an update, insert, or delete operation.
     *
     * @param dbmsSvcMeta the DBMS service metadata configuration
     * @param methodName the name of the method/operation to execute
     * @param input the input parameters for the operation
     * @return the number of rows affected by the operation
     * @throws IOException if an I/O error occurs during execution
     * @throws InvocationTargetException if the underlying method throws an exception
     * @throws NoSuchMethodException if the specified method cannot be found
     */
    @Transactional
    public int executeUpdate(DbmsService dbmsSvcMeta, String methodName, Object input) throws IOException, InvocationTargetException, NoSuchMethodException {
        String dataSource = dbmsSvcMeta.datasource();
        SqlQueryInfo entry = findSqlQueryInfo(dbmsSvcMeta, methodName);
        if (entry == null) {
            throw new RuntimeException("Process ID not found: " + dbmsSvcMeta.id() + "." + methodName);
        }
        String processedQuery = processConditionalQuery(entry.getSql(), input);
        return update(processedQuery, input, dataSource);
    }

    /**
     * Extracts sort code from the input object.
     * Supports various formats including sortCode, sortColumn fields and methods.
     * 
     * @param input the input object that may contain sorting information
     * @return the sort code string, or null if no sorting information found
     */
    protected String extractSortCode(Object input) {
        return sortCodeOf(input);
    }

    /** The sort code an input carries (sortCode, or sortColumn and sortAscending), or null. */
    static String sortCodeOf(Object input) {
        if (input == null) return null;

        String sortColumnNm= null;
        boolean sortAscending = true;

        if (input instanceof Map<?, ?> map) {
            Object sortVal = map.get("sortCode");
            if(sortVal != null)
                return sortVal.toString();

            sortVal = map.get("sortColumn");
            if(sortVal!=null)
                sortColumnNm = sortVal.toString();
         }

        Class c= input.getClass();
        try {
            Method method = c.getMethod("getSortCode");
            Object result = method.invoke(input);
            if(result!=null)
                return result.toString();
        } catch (Exception e) { }

        try {
            Method method = c.getMethod("getSortColumn");
            Object result = method.invoke(input);
            if(result!=null) {
                sortColumnNm = result.toString();

                Method method2 = c.getMethod("getSortAscending");
                Object result2 = method2.invoke(input);
                if (Boolean.FALSE == result2) {
                    return sortColumnNm + "-";
                }

                return sortColumnNm + "+";
            }

        } catch (Exception e) { }

        return null;
    }


    /**
     * Appends an ORDER BY clause to the SQL query based on sort information from input.
     *
     * @param sql the original SQL query
     * @param input the input object containing sort information
     * @return the SQL query with ORDER BY clause appended, or original SQL if no sort info
     */
    private String appendSortClause(String sql, Object input) {
        String orderByClause = toOrderByClause(extractSortCode(input));
        if (orderByClause.isEmpty()) return sql;

        return sql + " ORDER BY " + orderByClause;
    }


    /**
     * One term of a sort code: a column name, optionally qualified (o.created_at), and an
     * optional direction - '+' ascending, '-' descending, ascending when left out.
     * The quantifiers are possessive: a backtracking group loop recurses once per repetition
     * in java.util.regex, and a long "a.a.a..." term would overflow the stack.
     */
    private static final Pattern SORT_TERM = Pattern.compile(
            "([\\p{L}_][\\p{L}\\p{M}\\p{N}_$]*+(?:\\.[\\p{L}_][\\p{L}\\p{M}\\p{N}_$]*+)*+)\\s*+([+-]?)");

    /**
     * Far longer than column names are in practice (an identifier is 63 to 128 characters at
     * most, depending on the database); the cap keeps the check cheap whatever the request says.
     */
    private static final int MAX_SORT_TERM = 256;

    /** More columns than any screen sorts by; a request with more is not a sort. */
    private static final int MAX_SORT_TERMS = 32;

    /** 32 full-length terms and their commas; a longer sort code is refused before it is split. */
    private static final int MAX_SORT_CODE = MAX_SORT_TERMS * (MAX_SORT_TERM + 1);

    /**
     * Turns a sort code into the body of an ORDER BY clause: "title+,name-" -> "title ASC, name DESC".
     * The sort code usually comes straight from a request and is written into the SQL text, so a
     * term that is not a plain column name with an optional direction is refused, never passed on.
     *
     * @param sortCode the sort code, may be null or blank
     * @return the ORDER BY body, or an empty string when there is nothing to sort by
     * @throws BadRequestException if a term is anything but a column name and a direction,
     *                             or there are more than 32 terms, or the sort code is longer
     *                             than 32 terms can be
     */
    static String toOrderByClause(String sortCode) {
        if (sortCode == null || sortCode.isBlank()) return "";
        if (sortCode.length() > MAX_SORT_CODE)
            throw new BadRequestException("Sort code too long: more than " + MAX_SORT_CODE + " characters");

        StringJoiner clause = new StringJoiner(", ");
        int terms = 0;
        for (String term : sortCode.split(",")) {
            term = term.trim();
            if (term.isEmpty()) continue;
            if (++terms > MAX_SORT_TERMS)
                throw new BadRequestException("Too many sort terms: more than " + MAX_SORT_TERMS);

            Matcher matcher = SORT_TERM.matcher(term);
            if (term.length() > MAX_SORT_TERM || !matcher.matches())
                throw new BadRequestException("Invalid sort term: " + abbreviate(term));
            clause.add(matcher.group(1) + ("-".equals(matcher.group(2)) ? " DESC" : " ASC"));
        }
        return clause.toString();
    }

    /**
     * The refused term as the error repeats it: at most 40 characters, a surrogate pair never
     * cut in half, and control, line-separator and invisible format characters - a CR/LF that
     * would fake a log line, a right-to-left override that would reorder it - shown as '?'.
     */
    private static String abbreviate(String term) {
        String head = term;
        if (term.length() > 40) {
            int end = Character.isHighSurrogate(term.charAt(39)) ? 39 : 40;
            head = term.substring(0, end) + "...";
        }
        StringBuilder shown = new StringBuilder(head.length());
        for (int i = 0; i < head.length(); ) {
            int cp = head.codePointAt(i);
            int type = Character.getType(cp);
            boolean hidden = Character.isISOControl(cp) || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR || type == Character.FORMAT;
            if (hidden) shown.append('?');
            else shown.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return shown.toString();
    }


    /**
     * Executes a query using DBMS service metadata and returns results as a list.
     * 
     * @param <I> the input parameter type
     * @param <O> the output result type
     * @param dbmsSvcMeta the DBMS service metadata configuration
     * @param methodName the name of the method/query to execute
     * @param input the input parameters for the query
     * @param outputType the class of the expected output type
     * @param dataSource the name of the datasource to use
     * @return a list of results mapped to the specified output type
     * @throws InvocationTargetException if the underlying method throws an exception
     * @throws NoSuchMethodException if the specified method cannot be found
     * @throws IllegalAccessException if the method cannot be accessed
     * @throws IOException if an I/O error occurs during execution
     */
    @Transactional(readOnly = true)
    public <I, O> List<O> executeQuery(DbmsService dbmsSvcMeta, String methodName, I input, Class<O> outputType, String dataSource) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException {

            //ProcessConfig config = loadProcessConfig("process");
            //ProcessEntry entry = config.getProcessById(processId);
            //ProcessEntry entry = getProcessEntry(processId);
            SqlQueryInfo entry = findSqlQueryInfo(dbmsSvcMeta,methodName);

            return executeQuery(entry, input, outputType, dataSource);
    }

    /**
     * Executes a paginated query using SqlQueryInfo metadata and returns results as a Page.
     *
     * @param <I> the input parameter type
     * @param <O> the output result type
     * @param entry the SQL query information containing the query definition
     * @param input the input parameters for the query
     * @param outputType the class of the expected output type
     * @param dataSource the name of the datasource to use
     * @param pageable the pagination information
     * @param checkTotalCount whether to calculate the total count for pagination
     * @return a Page containing the results and pagination metadata
     * @throws InvocationTargetException if the underlying method throws an exception
     * @throws NoSuchMethodException if the specified method cannot be found
     */
    public <I, O> Page<O> executePageQuery(SqlQueryInfo entry, I input, Class<O> outputType, String dataSource, Pageable pageable, boolean checkTotalCount) throws InvocationTargetException, NoSuchMethodException {
        if (entry == null) {
            throw new RuntimeException("Process ID not found");
        }
        String processedQuery = processConditionalQuery(entry.getSql(), input);
        return page(entry, processedQuery, input, outputType, dataSource, pageable, checkTotalCount);
    }

    private static final Pattern ORDER_BY = Pattern.compile("\\border\\s+by\\b");
    private static final Pattern PAGES = Pattern.compile("\\b(limit|offset|fetch)\\b");

    /**
     * A query without its own last ORDER BY - the last at its top level,
     * outside parentheses, quotes and comments - for counting its rows. One
     * that a LIMIT, OFFSET or FETCH follows stays: it says which rows there
     * are, and SQL Server takes it in a derived table.
     */
    static String withoutOrderBy(String sql) {
        String top = topLevel(sql);
        Matcher m = ORDER_BY.matcher(top);
        int at = -1;
        while (m.find()) at = m.start();
        if (at < 0 || PAGES.matcher(top.substring(at)).find()) return sql;
        return sql.substring(0, at);
    }

    /** The SQL in lower case with the text in parentheses, quotes and comments blanked out, each at its place. */
    static String topLevel(String sql) {
        char[] out = sql.toLowerCase(java.util.Locale.ROOT).toCharArray();
        int depth = 0;
        for (int i = 0; i < out.length; i++) {
            char ch = out[i];
            int end = -1;
            if (ch == '\'' || ch == '"') {
                end = i + 1;
                while (end < out.length && out[end] != ch) end++;
            } else if (ch == '-' && i + 1 < out.length && out[i + 1] == '-') {
                end = i;
                while (end < out.length && out[end] != '\n') end++;
            } else if (ch == '/' && i + 1 < out.length && out[i + 1] == '*') {
                end = sql.indexOf("*/", i + 2);
                end = end < 0 ? out.length : end + 1;
            }
            if (end >= 0) {
                for (int j = i; j <= end && j < out.length; j++) if (out[j] != '\n') out[j] = ' ';
                i = end;
                continue;
            }
            if (ch == '(') depth++;
            if (depth > 0 && ch != '\n') out[i] = ' ';
            if (ch == ')') depth--;
        }
        return new String(out);
    }

    /** One page of a query's rows, mapped, and how many there are in all when asked. */
    private <I, O> Page<O> page(SqlQueryInfo entry, String processedQuery, I input, Class<O> outputType, String dataSource,
                                Pageable pageable, boolean checkTotalCount) {
        int totalRows = 0;
        if (checkTotalCount) {
            // No AS before the alias: Oracle takes none for a derived table. And
            // no ORDER BY inside it: SQL Server refuses one there, and a count needs none.
            String countQueryStr = "SELECT COUNT(*) FROM (" + withoutOrderBy(processedQuery) + ") X";
            totalRows = (int) count(countQueryStr, input, dataSource);
        }
        List<Map<String, Object>> resultList = rows(entry, processedQuery, input, dataSource,
                (int) pageable.getOffset(), pageable.getPageSize());
        if (!checkTotalCount && resultList != null) {
            totalRows = resultList.size();
        }
        return new PageImpl<>(map(resultList, outputType), pageable, totalRows);
    }

    @SuppressWarnings("unchecked")
    private <O> List<O> map(List<Map<String, Object>> resultList, Class<O> outputType) {
        if (Map.class.isAssignableFrom(outputType)) {
            return (List<O>) resultList.stream()
                    .map(this::convertRowToCamelCaseMap)
                    .collect(Collectors.toList());
        }
        return resultList.stream()
                .map(row -> convertRowToDto(row, outputType))
                .collect(Collectors.toList());
    }

    /**
     * Executes a query using SqlQueryInfo metadata and returns results as a list.
     *
     * @param <I> the input parameter type
     * @param <O> the output result type
     * @param entry the SQL query information containing the query definition
     * @param input the input parameters for the query
     * @param outputType the class of the expected output type
     * @param dataSource the name of the datasource to use
     * @return a list of results mapped to the specified output type
     * @throws InvocationTargetException if the underlying method throws an exception
     * @throws NoSuchMethodException if the specified method cannot be found
     */
    public <I, O> List<O> executeQuery(SqlQueryInfo entry, I input, Class<O> outputType, String dataSource) throws InvocationTargetException, NoSuchMethodException {
        if (entry == null) {
            throw new RuntimeException("Process ID not found");
        }
        String processedQuery = processConditionalQuery(entry.getSql(), input);
        return map(rows(entry, processedQuery, input, dataSource, 0, -1), outputType);
    }

    /**
     * Executes a paginated query using DBMS service metadata.
     * This method provides pagination support with optional total count calculation.
     *
     * @param <I> the input parameter type
     * @param <O> the output result type
     * @param dbmsSvcMeta the DBMS service metadata configuration
     * @param methodName the name of the method/query to execute
     * @param input the input parameters for the query
     * @param outputType the class of the expected output type
     * @param pageable the pagination information
     * @param dataSource the name of the datasource to use
     * @param checkTotalCount whether to calculate the total count for pagination
     * @return a Page containing the results and pagination metadata
     * @throws IOException if an I/O error occurs during execution
     * @throws RuntimeException if a runtime error occurs
     * @throws InvocationTargetException if the underlying method throws an exception
     * @throws NoSuchMethodException if the specified method cannot be found
     */
    @Transactional(readOnly = true)
    public <I, O> Page<O> executePagedQuery(DbmsService dbmsSvcMeta, String methodName, I input, Class<O> outputType, Pageable pageable, String dataSource, boolean checkTotalCount) throws IOException, RuntimeException, InvocationTargetException, NoSuchMethodException {
        SqlQueryInfo entry = findSqlQueryInfo(dbmsSvcMeta, methodName);
        if (entry == null) {
            throw new RuntimeException("Process ID not found: " + dbmsSvcMeta.id() + "." + methodName);
        }
        String processedQuery = processConditionalQuery(entry.getSql(), input);
        return page(entry, processedQuery, input, outputType, dataSource, pageable, checkTotalCount);
    }


    /**
     * Checks if a string is a valid Java identifier.
     * 
     * @param str the string to validate
     * @return true if the string is a valid Java identifier, false otherwise
     */
    public static boolean isValidJavaIdentifier(String str) {
        return str.matches("[a-zA-Z_$][a-zA-Z\\d_$]*");
    }

    /**
     * Processes conditional query logic and parameter substitution.
     * Handles template variables and conditional blocks.
     * 
     * @param query the SQL query template with conditional logic
     * @param input the input object containing parameters and conditions
     * @return the processed SQL query with conditions evaluated and parameters substituted
     */
    protected String processConditionalQuery(String query, Object input) {
        int chk1 = query.indexOf("${");
        int chk2 = query.indexOf("/* if");

        if(chk1>0) {
            try {
                query = replaceQueryParameters(query, input);
            }catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if(chk2<0) {
            return query;
        }


        String[] lines = query.split("\n");
        StringBuilder processedQuery = new StringBuilder();


        boolean isBlockScope = false;
        boolean isBlockCondition = false;
        for (String line : lines) {
            if (isBlockScope) {
                if (line.indexOf("/*") >= 0 && line.indexOf("if-end") > 0) {
                    isBlockScope = false;
                } else if (isBlockCondition) {
                    processedQuery.append(line).append("\n");
                }
                continue;
            }

            Matcher matcher = Pattern.compile("/\\* if:(.*?) \\*/").matcher(line);
            boolean conditionMet = false;

            if (matcher.find()) {
                String condition = matcher.group(1).trim();



                if (isValidJavaIdentifier(condition)) {
                    String paramName = condition;
                    conditionMet = input instanceof Map ? ((Map<?, ?>) input).containsKey(paramName) && ((Map<?, ?>) input).get(paramName) != null : hasValue(input, paramName);
                } else {
                    conditionMet = evaluateCondition(condition, input);
                }

                isBlockCondition = false;
                if (line.trim().indexOf("/*") == 0) {
                    isBlockScope = true;
                    isBlockCondition = conditionMet;
                }
                if (!conditionMet) {
                    continue;
                }

            }
            if (!isBlockScope) {
                if (conditionMet) {
                    int p = line.indexOf("/*");
                    line = line.substring(0, p - 1);
                }
                processedQuery.append(line).append("\n");
            }
        }

        //return processedQuery.toString().trim();
        // apply sort
        return appendSortClause(processedQuery.toString(), input);

    }

    /**
     * Evaluates a JEXL condition expression against the input object.
     * 
     * @param condition the JEXL expression to evaluate
     * @param input the input object to use as context for evaluation
     * @return true if the condition evaluates to true, false otherwise
     */
    private boolean evaluateCondition(String condition, Object input) {
        try {
            JexlContext context = new MapContext();
            context.set("input", input);
            JexlExpression expression = jexl.createExpression(condition);
            return (boolean) expression.evaluate(context);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if an input object has a non-null, non-empty value for the specified parameter.
     * 
     * @param input the input object to check
     * @param paramName the name of the parameter to check
     * @return true if the parameter has a value, false otherwise
     */
    private boolean hasValue(Object input, String paramName) {
        try {
            String getterMethod = "get" + Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);
            Method method = input.getClass().getMethod(getterMethod);
            Object value = method.invoke(input);
            if (value != null && value instanceof String) {
                String txtVal = (String) value;
                return !txtVal.isEmpty();
            }
            return value != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** A row as a map of camel-case column labels to its values. */
    protected Map<String, Object> convertRowToCamelCaseMap(Map<String, Object> row) {
        Map<String, Object> map = new LinkedHashMap<>();
        row.forEach((label, value) -> map.put(toCamelCase(label), value));
        return map;
    }

    /**
     * A port method that answers one value - {@code long countX()}, a name, a total -
     * gets the row's first column. It used to be instantiated as a DTO
     * ({@code new Long()}), which failed every call.
     */
    static boolean isSingleValue(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || type == String.class
                || type == Boolean.class
                || type == Character.class
                || java.time.temporal.Temporal.class.isAssignableFrom(type)
                || java.util.Date.class.isAssignableFrom(type);
    }

    @SuppressWarnings("unchecked")
    protected <O> O convertRowToDto(Map<String, Object> row, Class<O> outputType) {
        return rowToDto(row, outputType);
    }

    /** A row as the type a port method answers: its first column for one value, a DTO by column label otherwise. */
    @SuppressWarnings("unchecked")
    static <O> O rowToDto(Map<String, Object> row, Class<O> outputType) {
        if (isSingleValue(outputType)) {
            if (row.isEmpty()) return null;
            return (O) convertValue(row.values().iterator().next(), outputType);
        }
        try {
            O dto = outputType.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> element : row.entrySet()) {
                String colName = element.getKey();
                String fieldName = toCamelCase(colName);
                Field field = ReflectUtils.getField(outputType, fieldName);
                if(field==null) field = ReflectUtils.getField(outputType, colName);
                if (field != null) {
                    Object value = element.getValue();
                    Object convertedValue = convertValue(value, field.getType());
                    field.set(dto, convertedValue);
                }
            }
            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Failed to map tuple to DTO: " + outputType.getSimpleName(), e);
        }
    }


    static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;

        Class<?> sourceType = value.getClass();

        if (targetType.isAssignableFrom(sourceType)) {
            return value;
        }

        // Number
        if (value instanceof Number num) {
            if (targetType == Long.class || targetType == long.class) return num.longValue();
            if (targetType == Integer.class || targetType == int.class) return num.intValue();
            if (targetType == Double.class || targetType == double.class) return num.doubleValue();
            if (targetType == Float.class || targetType == float.class) return num.floatValue();
            if (targetType == Short.class || targetType == short.class) return num.shortValue();
            if (targetType == Byte.class || targetType == byte.class) return num.byteValue();
            if (targetType == java.math.BigDecimal.class) return new java.math.BigDecimal(num.toString());
            if (targetType == java.math.BigInteger.class) return new java.math.BigDecimal(num.toString()).toBigInteger();
        }

        // Boolean
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean b) return b;
            if (value instanceof String s) return Boolean.parseBoolean(s);
            if (value instanceof Number n) return n.intValue() != 0;
        }

        // java.sql.Date -> java.time.LocalDate
        if (value instanceof java.sql.Date sqlDate && targetType == java.time.LocalDate.class) {
            return sqlDate.toLocalDate();
        }

        // java.sql.Timestamp -> LocalDateTime or Instant
        if (value instanceof java.sql.Timestamp timestamp) {
            if (targetType == java.time.LocalDateTime.class) {
                return timestamp.toLocalDateTime();
            } else if (targetType == java.time.Instant.class) {
                return timestamp.toInstant();
            }
        }

        // java.sql.Time -> LocalTime
        if (value instanceof java.sql.Time sqlTime && targetType == java.time.LocalTime.class) {
            return sqlTime.toLocalTime();
        }


        // A one-letter column (CHAR(1), a code) read into a char
        if (targetType == Character.class || targetType == char.class) {
            if (value instanceof Character c) return c;
            String text = value.toString();
            if (text.length() == 1) return text.charAt(0);
        }

        if (targetType == String.class) {
            return value.toString();
        }

        throw new IllegalArgumentException(
                "Cannot convert value of type " + sourceType.getName() + " to " + targetType.getName()
        );
    }



    private String replaceQueryParameters(String sql, Object input) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Pattern pattern = Pattern.compile("\\$\\{(\\w+)}");
        Matcher matcher = pattern.matcher(sql);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value;

            if (input instanceof Map) {
                value = ((Map<?, ?>) input).get(paramName);
            } else {
                String getterMethod = "get" + Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);
                Method method = input.getClass().getMethod(getterMethod);
                value = method.invoke(input);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value.toString() : ""));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * The values of the named parameters a statement holds (:name), read
     * from the input by name - a map's entry, or a getter's value - in the
     * order they appear.
     */
    protected Map<String, Object> parameters(String sql, Object input) {
        Map<String, Object> values = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile("(?<!:):(\\w+)");
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = null;
            if (input instanceof Map) {
                value = ((Map<?, ?>) input).get(paramName);
            } else if (input != null) {
                try {
                    String getterMethod = "get" + Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);
                    Method method = input.getClass().getMethod(getterMethod);
                    value = method.invoke(input);
                } catch (NoSuchMethodException re) {
                    re.printStackTrace();
                } catch (IllegalAccessException iae) {
                    // left unbound, as before
                    continue;
                } catch (InvocationTargetException ite) {
                    throw new IllegalStateException("Reading parameter " + paramName + " failed", ite.getCause());
                }
            }
            values.put(paramName, value);
        }
        return values;
    }

    private static String getProjectRootDirectory() {
        try {
            Path currentPath = Paths.get("").toAbsolutePath();
            return currentPath.normalize().toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to determine project root directory", e);
        }
    }


    private ClassLoader getMainClassLoader() {
        if(classLoader!=null)
            return classLoader;

        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            try {
                Class<?> cls = Class.forName(element.getClassName());
                if (cls.isAnnotationPresent(SpringBootApplication.class)) {
                    classLoader = cls.getClassLoader();
                    break;
                }
            } catch (ClassNotFoundException e) {
            }

        }
        if(classLoader==null) {
            classLoader = getClass().getClassLoader();
        }
        return classLoader;
    }

    private String getNativeSqlId(String sqlSourcePath, String portSvcName, String methodName) {
        String id = sqlSourcePath+"!"+portSvcName+"."+methodName;
        return id;
    }

    private String getNativeSqlId(DbmsService dbmsSvcMeta, String methodName) {
        String idWithDomain = dbmsSvcMeta.id(); // with domain id;
        int p = idWithDomain.indexOf(".");

        String portSvcName = idWithDomain.substring(p+1);
        String id = dbmsSvcMeta.sqlSource()+"!"+portSvcName+"."+methodName;
        return id;
    }

    private String getBooleanStr(Object val, String defaultVal) {

        if(val instanceof String) {
            if("true".equals(val)) {
                return "true";
            }else if("false".equals(val)) {
                return "false";
            }
        }
        if(val instanceof Boolean) {
            Boolean b = (Boolean) val;
            if(Boolean.FALSE.equals(b)) {
                return "false";
            }else {
                return "true";
            }
        }

        return defaultVal;
    }

    private void setSqlQueryInfo(String sqlSourcePath, String portSvcName, Map<String, Map> values,long lastModifiedTime) {

        Map<String, Map> methods =  values.get("methods");
        methods.forEach((methodName, value) -> {

            String sqlTxt = (String) value.get("query");
            String nativeQuery = getBooleanStr(value.get("nativeQuery") , "true");

            String id = getNativeSqlId(sqlSourcePath, portSvcName, methodName);
            String ref = (String) value.get("ref");


            SqlQueryInfo q = new SqlQueryInfo();
            q.setId(id);
            q.setSql(sqlTxt);
            q.setNativeQuery(nativeQuery);
            q.setLastModifiedTime(lastModifiedTime);
            q.setRef(ref);

            sqlQueryInfoMap.put(id,q);
        });

    }

    private File findProjectRoot(File startDir) {
        File current = startDir;

        while (current != null) {
            String name = current.getName();

            // Maven: target/classes
            // Gradle: build/classes
            // IntelliJ: out/production/classes
            if ("target".equals(name) || "build".equals(name)) {
                File projectRoot = current.getParentFile();
                if (projectRoot != null &&
                        new File(projectRoot, "src/main/resources").isDirectory()) {
                    return projectRoot;
                }
            }

            if ("production".equals(name) &&
                    current.getParentFile() != null &&
                    "out".equals(current.getParentFile().getName())) {
                File projectRoot = current.getParentFile().getParentFile();
                if (projectRoot != null &&
                        new File(projectRoot, "src/main/resources").isDirectory()) {
                    return projectRoot;
                }
            }

            current = current.getParentFile();
        }
        return null;
    }

    protected SqlQueryInfo findSqlQueryInfo(DbmsService dbmsSvcMeta,String methodName) throws IOException {

        String sqlSourcePath = dbmsSvcMeta.sqlSource();

        String sqlId = getNativeSqlId(dbmsSvcMeta, methodName);

        File sqlSourceFile=null;

        boolean isReloadable = false;
        InputStream is = null;


        try {
            URL classesUrl = getMainClassLoader().getResource(".");
            File classesDir = new File(classesUrl.getFile());
            File projectRoot = findProjectRoot(classesDir);

            if(projectRoot !=null) {
                String devSrcPath = projectRoot.getAbsolutePath() + "/src/main/resources/" + sqlSourcePath;

                File devSrcFile = new File(devSrcPath);

                if (devSrcFile.exists() && devSrcFile.isFile()) {
                    logger.info("Port:SqlSource: "+devSrcPath);
                    sqlSourceFile = devSrcFile;
                    isReloadable = true;
                    is = new FileInputStream(devSrcFile);
                }
            }

            if(is==null) {
                is = getMainClassLoader().getResourceAsStream(sqlSourcePath);
                if (is == null) {
                    throw new FileNotFoundException("resource not found in JAR: " + sqlSourcePath);
                }
            }

            if (sqlSourceFile != null && !sqlSourceFile.exists()) {
                throw new FileNotFoundException("source file not found: " + sqlSourceFile.getAbsolutePath());
            }

            SqlQueryInfo sqlQueryInfo = sqlQueryInfoMap.get(sqlId);
            long lastModifiedTime = -1;

            if(sqlSourceFile!=null) {
                lastModifiedTime = sqlSourceFile.lastModified();
            }

            if (sqlQueryInfo != null && (!isReloadable || sqlQueryInfo.getLastModifiedTime() == lastModifiedTime)) {
                return sqlQueryInfo;

            }
            Map<String, Map> map = mapper.readValue(is, LinkedHashMap.class);
            Map<String, Map> port = map.get("port");

            long lastModifiedTime2 = lastModifiedTime;
            port.forEach((portSvcName, value) -> {
                //setSqlQueryInfo(String sqlSourcePath, String portSvcName, Map<String, Map> values,long lastModifiedTime)
                setSqlQueryInfo(sqlSourcePath, portSvcName, value, lastModifiedTime2);
            });

            SqlQueryInfo queryInfo = sqlQueryInfoMap.get(sqlId);
            if (queryInfo != null) {
                String ref = queryInfo.getRef();
                if (ref != null && !ref.isEmpty()) {
                    queryInfo = findSqlQueryInfo(dbmsSvcMeta, ref);
                }
            }

            return queryInfo;
        }finally {
            if(is!=null)
                is.close();
        }
    }


    static String toCamelCase(String columnName) {
        String[] parts = columnName.split("_");
        StringBuilder camelCaseString = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            camelCaseString.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1).toLowerCase());
        }
        return camelCaseString.toString();
    }
}
