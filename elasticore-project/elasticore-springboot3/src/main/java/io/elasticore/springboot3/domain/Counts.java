package io.elasticore.springboot3.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@code @count}: a count kept on a row of the rows of another root that point to it - a post's
 * {@code commentCount}.
 *
 * <p>A write of a counted row says which row's count it touched; the count is counted again, once per row, just
 * before the transaction commits - after every write of the transaction. The counting row is locked first and
 * then counted by a statement of its own, so a transaction that waited for another's lock counts what that one
 * committed (a single update with a count subquery would count the snapshot it started with). Rows are counted
 * in key order, so two transactions touching the same rows do not deadlock on each other. Outside a transaction
 * it is counted at once.
 */
public final class Counts {

    private Counts() {}

    /**
     * Counts again, before the commit, the row whose key is {@code id}.
     *
     * @param lock a select of the counting row {@code :id}, locked before it is counted
     * @param count an update that sets the count of the row {@code :id} to a count of what points to it
     */
    public static void touch(EntityManager em, String lock, String count, Object id) {
        if (id == null) return;
        Pending pending = pending();
        if (pending == null) {
            run(em, lock, count, id);
            return;
        }
        pending.work(em, lock, count).ids.add(id);
    }

    /**
     * The same for the row a counted row points to when the transaction commits: it is read from the row then,
     * after whatever else the transaction did to it ({@code beforeSave}, a listener).
     */
    public static <T> void touchLater(EntityManager em, String lock, String count, T row, Function<T, Object> id) {
        if (row == null) return;
        Pending pending = pending();
        if (pending == null) {
            run(em, lock, count, id.apply(row));
            return;
        }
        @SuppressWarnings("unchecked")
        Function<Object, Object> read = (Function<Object, Object>) (Function<?, Object>) id;
        pending.work(em, lock, count).rows.put(row, read);
    }

    /** The pending counts of this transaction, null outside one; a new one where the bound one is not this one's. */
    private static Pending pending() {
        // No transaction to wait for (none, or a NOT_SUPPORTED / SUPPORTS scope): counted at once.
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) return null;
        Pending pending = (Pending) TransactionSynchronizationManager.getResource(Counts.class);
        if (pending != null && pending.done) {
            // Past the commit of the transaction it belongs to (an after-commit handler): count at once.
            return null;
        }
        if (pending == null
                || !TransactionSynchronizationManager.getSynchronizations().contains(pending)) {
            if (pending != null) TransactionSynchronizationManager.unbindResource(Counts.class);
            pending = new Pending();
            TransactionSynchronizationManager.bindResource(Counts.class, pending);
            TransactionSynchronizationManager.registerSynchronization(pending);
        }
        return pending;
    }

    private static void run(EntityManager em, String lock, String count, Object id) {
        if (id == null) return;
        em.createQuery(lock)
                .setParameter("id", id)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        em.createQuery(count).setParameter("id", id).executeUpdate();
    }

    /** One count of one persistence unit: the rows it touched by key, and the rows to read the key off later. */
    private static final class Work {
        final EntityManager em;
        final String lock;
        final String count;
        final Set<Object> ids = new LinkedHashSet<Object>();
        final IdentityHashMap<Object, Function<Object, Object>> rows =
                new IdentityHashMap<Object, Function<Object, Object>>();

        Work(EntityManager em, String lock, String count) {
            this.em = em;
            this.lock = lock;
            this.count = count;
        }

        boolean isEmpty() {
            return ids.isEmpty() && rows.isEmpty();
        }
    }

    /** The counts a transaction touched, each row once, run just before it commits. */
    private static final class Pending implements TransactionSynchronization {
        private final IdentityHashMap<EntityManager, Map<String, Work>> work =
                new IdentityHashMap<EntityManager, Map<String, Work>>();
        boolean done;

        Work work(EntityManager em, String lock, String count) {
            Map<String, Work> byCount = work.get(em);
            if (byCount == null) {
                byCount = new LinkedHashMap<String, Work>();
                work.put(em, byCount);
            }
            Work w = byCount.get(count);
            if (w == null) {
                w = new Work(em, lock, count);
                byCount.put(count, w);
            }
            return w;
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            // A count may be touched while counting (a before-commit handler writing a counted row): until none is
            // left.
            while (true) {
                List<Work> batch = new ArrayList<Work>();
                for (Map<String, Work> byCount : work.values()) {
                    for (Work w : byCount.values()) {
                        if (!w.isEmpty()) batch.add(w);
                    }
                }
                if (batch.isEmpty()) break;
                batch.sort((a, b) -> a.count.compareTo(b.count));
                for (Work w : batch) {
                    Set<Object> ids = new LinkedHashSet<Object>(w.ids);
                    for (Map.Entry<Object, Function<Object, Object>> each : w.rows.entrySet()) {
                        Object id = each.getValue().apply(each.getKey());
                        if (id != null) ids.add(id);
                    }
                    w.ids.clear();
                    w.rows.clear();
                    for (Object id : sorted(ids)) run(w.em, w.lock, w.count, id);
                }
            }
            done = true;
        }

        @Override
        public void suspend() {
            TransactionSynchronizationManager.unbindResourceIfPossible(Counts.class);
        }

        @Override
        public void resume() {
            if (!TransactionSynchronizationManager.hasResource(Counts.class)) {
                TransactionSynchronizationManager.bindResource(Counts.class, this);
            }
        }

        @Override
        public void afterCompletion(int status) {
            done = true;
            if (TransactionSynchronizationManager.getResource(Counts.class) == this) {
                TransactionSynchronizationManager.unbindResource(Counts.class);
            }
        }
    }

    /** In key order where the keys compare, so two transactions lock the same rows in the same order. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Object> sorted(Set<Object> ids) {
        List<Object> out = new ArrayList<Object>(ids);
        boolean comparable = true;
        for (Object id : out) {
            if (!(id instanceof Comparable)) comparable = false;
        }
        if (comparable) {
            try {
                out.sort((a, b) -> ((Comparable) a).compareTo(b));
            } catch (ClassCastException mixed) {
                // keys of different kinds: as touched
            }
        }
        return out;
    }
}
