package io.elasticore.springboot3.domain;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@code @count}: a count kept on a row of the rows of another root that point to it - a post's
 * {@code commentCount}.
 *
 * <p>A write of a counted row says which row's count it touched ({@link #touch}); the count is counted again,
 * once per row, just before the transaction commits - after every write of the transaction, so a create, a
 * hide and a restore crossing in one transaction leave the right number, and two people pressing at once each
 * count what is there rather than adding to a number they read. Outside a transaction it is counted at once.
 */
public final class Counts {

    private Counts() {}

    /**
     * Counts again, before the commit, the row whose key is {@code id}.
     *
     * @param jpql an update that sets the count of the row {@code :id} to a count of what points to it
     */
    public static void touch(EntityManager em, String jpql, Object id) {
        if (id == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            run(em, jpql, id);
            return;
        }
        Pending pending = (Pending) TransactionSynchronizationManager.getResource(Counts.class);
        if (pending == null) {
            pending = new Pending();
            TransactionSynchronizationManager.bindResource(Counts.class, pending);
            TransactionSynchronizationManager.registerSynchronization(pending);
        }
        pending.add(em, jpql, id);
    }

    private static void run(EntityManager em, String jpql, Object id) {
        em.createQuery(jpql).setParameter("id", id).executeUpdate();
    }

    /** The counts a transaction touched, each row once, run just before it commits. */
    private static final class Pending implements TransactionSynchronization {
        private final Map<String, Set<Object>> ids = new LinkedHashMap<String, Set<Object>>();
        private EntityManager em;

        void add(EntityManager em, String jpql, Object id) {
            this.em = em;
            Set<Object> set = ids.get(jpql);
            if (set == null) {
                set = new LinkedHashSet<Object>();
                ids.put(jpql, set);
            }
            set.add(id);
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            // A count run here may not touch another; a copy, so a touch made meanwhile is not lost silently.
            List<Map.Entry<String, Set<Object>>> work = new ArrayList<Map.Entry<String, Set<Object>>>(ids.entrySet());
            for (Map.Entry<String, Set<Object>> each : work) {
                for (Object id : each.getValue()) run(em, each.getKey(), id);
            }
        }

        @Override
        public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(Counts.class);
        }
    }
}
