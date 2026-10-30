package io.elasticore.springboot3.domain;

/**
 * Finds a row by its identity, for an entity filling in a @snapshot value
 * from the row a reference points at. The service hands it
 * {@code entityManager::find}; a test hands it a map.
 *
 * Not generic, so a Kotlin lambda can be one.
 */
@FunctionalInterface
public interface Lookup {

    /** The row of this type with this identity, or null. */
    Object find(Class<?> type, Object id);

    /**
     * The rows of one type, for a store with no EntityManager to find them -
     * a generated MyBatis repository adapter is one. The application's Lookup
     * asks the source of the type it is given (ElastiCoreLookupAutoConfiguration).
     */
    interface Source {

        /** The type whose rows this finds. */
        Class<?> type();

        /** The row with this identity, or null. */
        Object find(Object id);
    }

    /** A Lookup over the sources given, one per type; a type none serves is a mistake, said by name. */
    static Lookup over(Iterable<? extends Source> sources) {
        return over(sources, null);
    }

    /**
     * A Lookup over the sources given, one per type, and what finds the rest -
     * the JPA roots of an application that keeps some domains on JPA - or
     * null when a type none serves is a mistake.
     */
    static Lookup over(Iterable<? extends Source> sources, Lookup otherwise) {
        final java.util.Map<Class<?>, Source> byType = new java.util.LinkedHashMap<Class<?>, Source>();
        for (Source s : sources) byType.put(s.type(), s);
        return (type, id) -> {
            if (id == null) return null;
            Source s = byType.get(type);
            if (s == null) {
                for (java.util.Map.Entry<Class<?>, Source> e : byType.entrySet()) {
                    if (type.isAssignableFrom(e.getKey())) {
                        s = e.getValue();
                        break;
                    }
                }
            }
            if (s == null && otherwise != null) return otherwise.find(type, id);
            if (s == null) {
                throw new IllegalStateException("No repository finds " + type.getName() + " by its identity;"
                        + " a reference by identity points at a root with a repository port");
            }
            return s.find(id);
        };
    }
}
