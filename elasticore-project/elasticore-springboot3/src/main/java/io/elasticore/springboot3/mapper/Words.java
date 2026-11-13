package io.elasticore.springboot3.mapper;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One search box over several fields: {@code @search(words:title, body, author.nickname)}.
 *
 * <p>The text is split on white space. Every word has to be found, and each word may be in any of the
 * fields - an AND over the words of an OR over the fields. Each comparison is a contains, ignoring case,
 * with the word's own {@code %}, {@code _} and {@code !} escaped, as {@code @search(like)} compares one
 * field. A field reached through a to-one relation ({@code author.nickname}) is joined LEFT, so a row
 * without an author is still found by its title. A field reached through a collection ({@code
 * posts.title}) is asked per word with EXISTS: each word may be in another member, and the row comes
 * once without a DISTINCT.
 */
public final class Words {

    /** The words of one search: more are not searched for, so a long text stays one bounded query. */
    public static final int MAX_WORDS = 5;

    private Words() {}

    /** The words of a text: split on white space (the ideographic space too), each once, the first five. */
    public static List<String> of(String text) {
        Set<String> words = new LinkedHashSet<String>();
        if (text != null) {
            for (String word : text.trim().split("[\\s\\u3000]+", -1)) {
                if (!word.isEmpty() && words.size() < MAX_WORDS) words.add(word);
            }
        }
        return new ArrayList<String>(words);
    }

    /**
     * Whether a row has every word of the text in one of the fields; true for a text without a word.
     *
     * @param paths the fields, as the entity names them: {@code title}, {@code author.nickname}
     */
    public static Predicate match(
            Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb, String text, String... paths) {
        List<String> words = of(text);
        if (words.isEmpty() || paths.length == 0) return cb.conjunction();
        Map<String, From<?, ?>> joins = new HashMap<String, From<?, ?>>();
        List<Expression<String>> fields = new ArrayList<Expression<String>>();
        List<String[]> throughCollections = new ArrayList<String[]>();
        for (String path : paths) {
            String[] parts = path.trim().split("\\.", -1);
            // A delete by specification has no query to make a subquery of: it joins as a to-one would.
            if (query != null && crossesACollection(root.getModel(), parts)) throughCollections.add(parts);
            else fields.add(cb.lower(field(root, joins, parts)));
        }
        List<Predicate> all = new ArrayList<Predicate>();
        for (String word : words) {
            String pattern = "%" + escaped(word.toLowerCase(Locale.ROOT)) + "%";
            List<Predicate> any = new ArrayList<Predicate>();
            for (Expression<String> field : fields) {
                any.add(cb.like(field, pattern, '!'));
            }
            for (String[] parts : throughCollections) {
                any.add(cb.exists(member(root, query, cb, parts, pattern)));
            }
            all.add(cb.or(any.toArray(new Predicate[0])));
        }
        return cb.and(all.toArray(new Predicate[0]));
    }

    /** The field a path names, joining each relation on the way once, LEFT. */
    private static Expression<String> field(Root<?> root, Map<String, From<?, ?>> joins, String[] parts) {
        From<?, ?> from = root;
        StringBuilder walked = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (walked.length() > 0) walked.append('.');
            walked.append(parts[i]);
            From<?, ?> joined = joins.get(walked.toString());
            if (joined == null) {
                joined = from.join(parts[i], JoinType.LEFT);
                joins.put(walked.toString(), joined);
            }
            from = joined;
        }
        return from.<String>get(parts[parts.length - 1]);
    }

    /** EXISTS (a member on the path whose field has the word): the row is asked, not multiplied. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Subquery<Integer> member(
            Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb, String[] parts, String pattern) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        From<?, ?> from = sq.correlate((Root) root);
        for (int i = 0; i < parts.length - 1; i++) {
            from = from.join(parts[i]);
        }
        Expression<String> field = cb.lower(from.<String>get(parts[parts.length - 1]));
        return sq.select(cb.literal(1)).where(cb.like(field, pattern, '!'));
    }

    /** Whether a relation on the path is a collection. */
    static boolean crossesACollection(ManagedType<?> type, String[] parts) {
        ManagedType<?> at = type;
        for (int i = 0; i < parts.length - 1 && at != null; i++) {
            Attribute<?, ?> a = at.getAttribute(parts[i]);
            if (a.isCollection()) return true;
            Type<?> next = a instanceof PluralAttribute
                    ? ((PluralAttribute<?, ?, ?>) a).getElementType()
                    : ((SingularAttribute<?, ?>) a).getType();
            at = next instanceof ManagedType ? (ManagedType<?>) next : null;
        }
        return false;
    }

    /** The word matched as written: its own %, _ and the escape character ! escaped with !. */
    static String escaped(String word) {
        StringBuilder sb = new StringBuilder(word.length() + 4);
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            if (ch == '!' || ch == '%' || ch == '_') sb.append('!');
            sb.append(ch);
        }
        return sb.toString();
    }
}
