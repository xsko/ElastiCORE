package io.elasticore.springboot3.entity;

import jakarta.persistence.EntityManager;

/**
 * What a generated JPA service does with the row it changed when the write is
 * refused - by beforeSave, an invariant, a reference check, a lifecycle rule -
 * after the request was mapped onto it (issue 344).
 *
 * The row is the managed entity of the transaction's persistence context. A
 * refusal commits nothing, but the change stayed in that entity: a later read
 * of the row in the same transaction (a batch, a caller that catches the
 * refusal) answered the refused value, and the next query's AUTO flush wrote
 * it into the table inside the transaction. Here the row is read again from
 * the database instead, so the transaction reads what is stored.
 *
 * Read again (refresh), not let go (detach): the entity stays the one object
 * the persistence context and everything that holds it share - a parent's
 * collection, a caller's variable - and refresh cascades to the members the
 * entity cascades to (REFRESH, ALL), putting back what the request changed in
 * them too. Where it cannot be read again - a member the request added that
 * was never written, a row no longer there - the row is detached, with the
 * members its cascade reaches, and the next read loads it afresh.
 */
public final class RefusedWrite {

    private RefusedWrite() {}

    /**
     * Puts the row back as stored and hands the refusal back to be thrown:
     * {@code throw RefusedWrite.discard(em, entity, refused);}. Nothing is done
     * without an entity manager or a row, or for a row the persistence context
     * does not hold (a new one never saved).
     */
    public static RuntimeException discard(EntityManager em, Object row, RuntimeException refusal) {
        if (em == null || row == null) return refusal;
        try {
            if (em.contains(row)) em.refresh(row);
        } catch (RuntimeException notReadAgain) {
            detach(em, row);
        }
        return refusal;
    }

    private static void detach(EntityManager em, Object row) {
        try {
            if (em.contains(row)) em.detach(row);
        } catch (RuntimeException ignored) {
            // The refusal is what the caller is told, not this.
        }
    }
}
