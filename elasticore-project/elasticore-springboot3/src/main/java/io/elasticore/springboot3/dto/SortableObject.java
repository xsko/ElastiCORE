package io.elasticore.springboot3.dto;


import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface for objects that support sorting functionality.
 * 
 * This interface provides sorting capabilities through sort codes, column names,
 * and direction specifications for Spring Data queries.
 */
public interface SortableObject {

    /**
     * Gets the sort code string for multiple column sorting.
     * 
     * Sort code format: "column1+,column2-" where + means ascending and - means descending.
     * 
     * @return the sort code string, or null if no sorting specified
     */
    String getSortCode();

    /**
     * Gets the primary sort column name.
     * 
     * This is used when sorting by a single column along with getSortAscending().
     * 
     * @return the column name to sort by, or null if not specified
     */
    String getSortColumn();

    /**
     * Gets the sort direction for the primary sort column.
     * 
     * Used in combination with getSortColumn() for single column sorting.
     * 
     * @return true for ascending, false for descending, or null if not specified
     */
    Boolean getSortAscending();

    /**
     * The properties a sort may name - what the search's response shows - or null for any.
     *
     * A 3.0 search DTO that implements this interface (env.yml interface.sortable names it) lists its
     * own, and getSort() refuses any other property (400) through {@link SortCheck}. Null, as every
     * other implementation answers, leaves getSort() as it was.
     *
     * @return the properties a sort may name, or null for any
     */
    default java.util.Set<String> sortableProperties() {
        return null;
    }

    /**
     * Creates a Spring Data Sort instance from the sorting configuration.
     * 
     * This method processes both sort codes and single column sorting to create
     * a Sort object that can be used with Spring Data repositories.
     * 
     * @return Sort instance based on the current sorting configuration, or null if no sorting specified
     */
    default Sort getSort() {
        String sortTxt = getSortCode();

        String sortCol = getSortColumn();
        if(sortCol !=null && !sortCol.isEmpty()) {
            sortTxt = sortCol;
            if(getSortAscending())
                sortTxt=sortTxt+"+";
            else
                sortTxt=sortTxt+"-";
        }

        if (sortTxt == null || sortTxt.isEmpty()) return null;

        List<Sort.Order> orders = new ArrayList<>();
        String[] sortParams = sortTxt.split(",");
        java.util.Set<String> sortable = sortableProperties();

        for (String param : sortParams) {
            if (param.endsWith("+")) {
                orders.add(Sort.Order.asc(SortCheck.sortable(param.substring(0, param.length() - 1), sortable)));
            } else if (param.endsWith("-")) {
                orders.add(Sort.Order.desc(SortCheck.sortable(param.substring(0, param.length() - 1), sortable)));
            }
        }
        return Sort.by(orders);
    }
}
