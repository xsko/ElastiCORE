package io.elasticore.springboot3.dto;

import io.elasticore.springboot3.validation.BadRequestException;
import io.elasticore.springboot3.validation.Refusals;

import java.util.Collection;

/**
 * Keeps a search's sort to what its response shows. The 3.0 output's SortableObject hands each property a
 * request sorts by (sortCode, or sortColumn and sortAscending) through here, with the properties its search
 * DTO lists: the values the search's response DTO shows, a shown reference's key and the values of a joined
 * row the response carries, the entity's key and the default order (@sort).
 *
 * <p>A property the response does not show is refused before the query is built: a password's hash, a
 * {@code @writeonly} card number, a masked or view-hidden value, or a joined row's field the response leaves
 * out. Sorted by, such a value's order would be told to whoever may search - with rows of one's own to sort
 * among, the value itself. The refusal is the one a property the entity does not have gets, so it does not
 * tell which of the two it was.
 *
 * <p>Nothing older calls it: a search DTO generated before, and the 2.x output, sort as they did.
 */
public final class SortCheck {

    /** The problem code of a refused sort - the code a sort on a property the entity does not have gets too. */
    public static final String CODE = "no-such-property";

    private SortCheck() {}

    /**
     * The property, when a sort may name it.
     *
     * @param property the property a request sorts by
     * @param sortable the properties a sort may name; null for any (a search DTO that lists none)
     * @return the property given
     * @throws BadRequestException (400, code {@value #CODE}) naming the property, at most 40 characters of it,
     *                             and the ones a sort may name
     */
    public static String sortable(String property, Collection<String> sortable) {
        if (sortable == null || (property != null && sortable.contains(property))) return property;
        throw new BadRequestException(
                CODE,
                "The search cannot be sorted by ''{0}''; sort by one of [{1}]",
                Refusals.shown(property),
                String.join(", ", sortable));
    }
}
