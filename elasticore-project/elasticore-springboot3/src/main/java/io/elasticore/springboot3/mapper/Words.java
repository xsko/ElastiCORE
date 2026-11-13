package io.elasticore.springboot3.mapper;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One search box over several fields: {@code @search(words: title, body, author.nickname)}.
 *
 * <p>The text is split on white space. Every word has to be found, and each word may be in any of the
 * fields - an AND over the words of an OR over the fields. Each comparison is a contains, ignoring case,
 * with the word's own {@code %}, {@code _} and {@code !} escaped, as {@code @search(like)} compares one
 * field. A field reached through a relation ({@code author.nickname}) is joined with a LEFT JOIN, so a row
 * without an author is still found by its title.
 */
public final class Words {

    /** The words of one search: more are not searched for, so a long text stays one bounded query. */
    public static final int MAX_WORDS = 5;

    private Words() {}

    /** The words of a text: split on white space, each once, the first {@link #MAX_WORDS}. */
    public static List<String> of(String text) {
        Set<String> words = new LinkedHashSet<String>();
        if (text != null) {
            for (String word : text.trim().split("\\s+")) {
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
        for (String path : paths) {
            fields.add(cb.lower(field(root, query, joins, path)));
        }
        List<Predicate> all = new ArrayList<Predicate>();
        for (String word : words) {
            String pattern = "%" + escaped(word.toLowerCase(Locale.ROOT)) + "%";
            List<Predicate> any = new ArrayList<Predicate>();
            for (Expression<String> field : fields) {
                any.add(cb.like(field, pattern, '!'));
            }
            all.add(cb.or(any.toArray(new Predicate[0])));
        }
        return cb.and(all.toArray(new Predicate[0]));
    }

    /** The field a path names, joining each relation on the way once. */
    private static Expression<String> field(
            Root<?> root, CriteriaQuery<?> query, Map<String, From<?, ?>> joins, String path) {
        String[] parts = path.trim().split("\\.");
        From<?, ?> from = root;
        StringBuilder walked = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (walked.length() > 0) walked.append('.');
            walked.append(parts[i]);
            From<?, ?> joined = joins.get(walked.toString());
            if (joined == null) {
                Join<?, ?> join = from.join(parts[i], JoinType.LEFT);
                // A collection on the way makes a row once per member that matches.
                if (join.getAttribute() != null && join.getAttribute().isCollection()) query.distinct(true);
                joins.put(walked.toString(), join);
                joined = join;
            }
            from = joined;
        }
        return from.<String>get(parts[parts.length - 1]);
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
