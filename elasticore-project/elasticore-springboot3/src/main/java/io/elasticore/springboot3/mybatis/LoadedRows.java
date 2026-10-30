package io.elasticore.springboot3.mybatis;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The rows a transaction has already handed out, by identity.
 *
 * Inside a transaction MyBatis answers a second read of a row from its
 * session: the same object, as JPA's persistence context answers the same
 * entity. A generated adapter fills a row's collections and tells it it was
 * loaded once, the first time - filling them again from the rows the session
 * kept would undo what the aggregate changed in memory since.
 */
public final class LoadedRows {

    private LoadedRows() {
    }

    private static final Object KEY = new Object();

    /** Whether this is the first time the current transaction sees the row; always, outside one. */
    @SuppressWarnings("unchecked")
    public static boolean first(Object row) {
        if (row == null || !TransactionSynchronizationManager.isSynchronizationActive()) return true;
        Set<Object> seen = (Set<Object>) TransactionSynchronizationManager.getResource(KEY);
        if (seen == null) {
            seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            TransactionSynchronizationManager.bindResource(KEY, seen);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(KEY);
                }
            });
        }
        return seen.add(row);
    }
}
