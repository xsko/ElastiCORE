package io.elasticore.base.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringUtils {

    private static final Pattern SQL_VAR_CHK_PATTERN = Pattern.compile(":(\\w+)");

    /**
     *
     * @param inputString
     * @return
     */
    public static String capitalize(String inputString) {
        if(inputString==null) return null;
        char firstLetter = inputString.charAt(0);

        char capitalFirstLetter = Character.toUpperCase(firstLetter);

        return capitalFirstLetter + inputString.substring(1);

        //return inputString.replace(inputString.charAt(0), capitalFirstLetter);
    }

    public static String quoteString(String input) {
        if(input==null)
            return null;

        if(input.indexOf("\"")==0 && input.lastIndexOf("\"")==input.length()-1) {
            return input;
        }
        return "\"" + input + "\"";
    }

    public static String uncapitalize(String inputString) {
        if(inputString==null || inputString.isEmpty()) return inputString;
        char firstLetter = inputString.charAt(0);

        char capitalFirstLetter = Character.toLowerCase(firstLetter);

        return capitalFirstLetter + inputString.substring(1);

        //return inputString.replace(inputString.charAt(0), capitalFirstLetter);
    }

    /**
     * @param line
     * @return
     */
    public static String splitByDoubleQuotation(String line) {
        String[] parts = line.split("\n");

        StringBuilder formattedString = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i == 0)
                formattedString.append("\" ");
            else
                formattedString.append(" \" ");
            formattedString.append(parts[i]).append("\"");

            if (i < parts.length - 1) {
                formattedString.append("\n \t\t + ");
            }
        }
        return formattedString.toString();
    }

    public static String camelToSnake(String name) {
        return name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    public static String snakeToCamel(String name) {
        return snakeToCamel(name, "_");
    }

    public static String snakeToCamel(String name, String splitText) {
        String[] parts = name.split(splitText);
        StringBuilder camelCaseName = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            camelCaseName.append(parts[i].substring(0, 1).toUpperCase()).append(parts[i].substring(1).toLowerCase());
        }
        return camelCaseName.toString();
    }


    public static List<String> extractVariables(String sql) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = SQL_VAR_CHK_PATTERN.matcher(sql);

        while (matcher.find()) {
            variables.add(matcher.group(1));
        }

        return variables;
    }

    public static Set<String> extractVariablesSet(String sql) {
        Set<String> variableSet = new HashSet<>();
        Matcher matcher = SQL_VAR_CHK_PATTERN.matcher(sql);

        while (matcher.find()) {
            variableSet.add(matcher.group(1));
        }

        return variableSet;
    }


    // REGX for FIRST SELECT columns
    private static final Pattern FIRST_SELECT_COLMN_PATTERN = Pattern.compile(
            "(?i)SELECT\\s+(.*?)\\s+FROM",
            Pattern.DOTALL
    );

    // regx for column name
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
            "\\s*(?:\\w+\\s*\\(.*?\\)|\\w+)\\s*(?:AS\\s+(\\w+))?\\s*,?",
            Pattern.CASE_INSENSITIVE
    );

    public static List<String> extractFirstSelectColumnNames(String sql) {
        List<String> columnNames = new ArrayList<>();
        Matcher matcher = FIRST_SELECT_COLMN_PATTERN.matcher(sql);

        if (matcher.find()) {
            String columnsPart = matcher.group(1) + ",";
            Matcher columnMatcher = COLUMN_PATTERN.matcher(columnsPart);

            while (columnMatcher.find()) {
                if (columnMatcher.group(1) != null) {
                    columnNames.add(columnMatcher.group(1));
                } else {
                    String column = columnMatcher.group().trim();
                    if (column.endsWith(",")) {
                        column = column.substring(0, column.length() - 1).trim();
                    }
                    columnNames.add(column.replaceAll(".*\\s+(\\w+)$", "$1"));
                }
            }
        }

        return columnNames;
    }

    public static String escapeDoubleQuotes(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\"", "\\\"");
    }

    public static String removeQuotes(String str) {
        if (str == null || str.length() < 2) {
            return str;
        }
        String str2 = str.trim();
        if (str2.charAt(0) == '\'' && str2.charAt(str2.length() - 1) == '\'') {
            return str2.substring(1, str2.length() - 1);
        }
        return str;
    }


    public static String removeDoubleQuotes(String str) {
        if (str == null || str.length() < 2) {
            return str;
        }
        String str2 = str.trim();
        if (str2.charAt(0) == '\"' && str2.charAt(str2.length() - 1) == '\"') {
            return str2.substring(1, str2.length() - 1);
        }
        return str;
    }

    /**
     * List<Object[]>  -->  Object[]
     *
     * @param typeName
     * @return
     */
    public static String findParameterType(String typeName) {
        if(typeName==null)
            return null;

        if(typeName.indexOf("List<") <0)
            return null;

        return typeName.substring(5, typeName.length()-1);
    }


    public static String escapeQuotes(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\"", "\\\"");
    }


    /**
     * Splits the input text based on the delimiters "$" or " " (space).
     * This method separates the text at each occurrence of either "$" or " ",
     * producing an array of strings.
     *
     * @param text the input string to be split. If null, the method returns null.
     * @return an array of strings split around each occurrence of "$" or " ",
     *         or null if the input is null.
     */
    public static String[] splitConditionText(String text) {
        if(text==null) return null;
        String regex = "[$ ]";
        return text.split(regex);
    }


    /**
     * Generates an English description for a given search operator.
     *
     * @param operator the search operator, such as "<", ">", ">=", "<=", "=", or "like"
     * @return a description of the operator in English, or a default message if the operator is unrecognized
     */
    public static String getOperatorDescription(String operator) {
        switch (operator) {
            case "<":
                return "Field is less than value.";
            case ">":
                return "Field is greater than value.";
            case "<=":
                return "Field is less than or equal to value.";
            case ">=":
                return "Field is greater than or equal to value.";
            case "=":
            case "eq":
                return "Field equals value.";

            case "__=":
            case "=__":
                return "Field equals value(case-insensitive).";
            case "like":
            case "%%":
            case "%_%":
                return "Field matches pattern.";
            case "between":
                return "Field is between two values (inclusive).";
            case "%_":
                return "Field matches value with the trailing part being identical.";
            case "_%":
                return "Field matches value with the leading part being identical.";

            case "%__":
                return "Field matches value (case-insensitive) with the trailing part being identical.";
            case "__%":
                return "Field matches value (case-insensitive) with the leading part being identical.";

            case "%__%":
                return "Field matches value (case-insensitive) with identical content between start and end.";
            case "in":
                return "Field matches any value in the given list.";
            default:
                return "Use 'like' if value has %, else 'equal'";
        }
    }

    /**
     * The description of a search operator in the clean (3.0) output, which
     * knows two operators more than 2.x: "isnull", the three-state emptiness
     * check, and "!=". The 2.x publisher and the 2.x-compatible output still
     * read both as an equality, so they keep the text above for them.
     *
     * @param operator the search operator
     * @param clean    whether the description is for the clean (3.0) output
     * @return a description of the operator in English
     */
    public static String getOperatorDescription(String operator, boolean clean) {
        if (clean && "isnull".equals(operator)) {
            return "Field is empty (true) or set (false).";
        }
        if (clean && "!=".equals(operator)) {
            return "Field does not equal value.";
        }
        return getOperatorDescription(operator);
    }


    public static boolean equalsWithPureChars(String str1, String str2) {
        // Normalize the strings by trimming, removing newlines, and concatenating
        String normalizedStr1 = normalizeString(str1);
        String normalizedStr2 = normalizeString(str2);



        // Compare the normalized strings
        boolean result =  normalizedStr1.equals(normalizedStr2);

        return result;
    }

    private static String normalizeString(String input) {
        if(input==null)
            return "";
        // Split the string into lines
        String[] lines = input.split("\\r?\\n");

        // Use StringBuilder to efficiently concatenate strings
        StringBuilder normalized = new StringBuilder();

        for (String line : lines) {
            // Trim each line and append it to the result
            normalized.append(line.trim());
        }

        return normalized.toString();
    }


    /**
     * Adds a prefix to each line in the input string.
     *
     * Handles all standard line endings (\n, \r\n, \r), preserves empty lines,
     * and avoids adding trailing newline if not present in the input.
     *
     * @param input The input multiline string.
     * @param prefix The string to prepend to each line.
     * @return A new string with the prefix added to the beginning of each line.
     */
    public static String addPrefixToEachLine(String input, String prefix) {
        if (input == null || input.isEmpty()) return input;
        return Arrays.stream(input.split("\\R", -1)) // \R handles \n, \r\n, \r - and -1 preserves empty trailing lines
                .map(line -> prefix + line)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Checks whether a string contains non-whitespace characters.
     *
     * @param str the input string to check
     * @return true if the string is not null and contains at least one non-whitespace character
     */
    public static boolean hasValue(String str) {
        return str != null && !str.trim().isEmpty();
    }


    /**
     *
     * @param maps
     * @param keys
     * @return
     */
    public static boolean hasKey(Map maps, String...keys) {
        for(String key: keys) {
            if(maps.containsKey(key))
                return true;
        }
        return false;
    }

    public static String fixNull(String text) {
        return fixNull(text, "");
    }

    public static String fixNull(String text, String blankText) {
        if(text==null)
            return blankText;

        return text;
    }

    public static String limitLines(String text, int maxLines) {
        if (text == null || maxLines <= 0) return "";
        String[] lines = text.split("\r?\n");
        if (lines.length <= maxLines) return text;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) sb.append(lines[i]).append("\n");
        return sb.toString().trim();
    }

}
