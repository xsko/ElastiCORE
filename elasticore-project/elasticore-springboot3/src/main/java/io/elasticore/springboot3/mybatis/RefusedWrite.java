package io.elasticore.springboot3.mybatis;

import org.mybatis.spring.SqlSessionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * What a generated MyBatis service does when a write is refused - by
 * beforeSave, an invariant, a reference check, a lifecycle rule - after the
 * request was mapped onto the row it read (issue 344).
 *
 * Inside a transaction MyBatis answers a second read of a row from the
 * session's local cache (localCacheScope SESSION, its default): the same
 * object, which the request has changed. A refused write runs no statement,
 * so nothing clears that cache, and a later read of the row in the same
 * transaction answered the refused value. Here the local cache of each
 * MyBatis session the transaction holds is cleared - what an UPDATE would
 * have done - so the next read reads the database.
 *
 * Outside a transaction there is nothing to clear: each statement has a
 * session of its own.
 */
public final class RefusedWrite {

    private RefusedWrite() {}

    /**
     * Clears the transaction's MyBatis session caches and hands the refusal
     * back to be thrown: {@code throw RefusedWrite.discard(refused);}.
     */
    public static RuntimeException discard(RuntimeException refusal) {
        try {
            for (Object held :
                    TransactionSynchronizationManager.getResourceMap().values()) {
                if (held instanceof SqlSessionHolder holder)
                    holder.getSqlSession().clearCache();
            }
        } catch (RuntimeException ignored) {
            // The refusal is what the caller is told, not this.
        }
        return refusal;
    }
}
