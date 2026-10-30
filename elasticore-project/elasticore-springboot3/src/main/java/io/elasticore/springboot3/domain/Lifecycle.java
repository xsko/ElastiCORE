package io.elasticore.springboot3.domain;

import java.util.Collection;
import java.util.Collections;

/**
 * The moments of an aggregate's life that JPA marks with its callbacks
 * (@PrePersist, @PreUpdate, @PreRemove, @PostLoad, @PostPersist) and Spring
 * Data with @DomainEvents, for a store that has no such moments of its own.
 *
 * A generated MyBatis repository adapter calls them where JPA would: before
 * the row is first written and before a change is, after it was read or first
 * written, before it is deleted - and, once it is saved, publishes the events
 * the aggregate raised. The aggregate implements what it has to say at each
 * one (its invariants, its delete guard, the flag that seals its creation
 * values); the rest are empty.
 *
 * The same aggregate stored by JPA keeps its annotations instead, so a domain
 * moved from one store to the other keeps its rules.
 */
public interface Lifecycle {

    /** Before the row is first written. */
    default void beforeInsert() {
    }

    /** Before a change to the row is written. */
    default void beforeUpdate() {
    }

    /** After the row was first written. */
    default void afterInsert() {
    }

    /** After the row was read. */
    default void afterLoad() {
    }

    /** Before the row is deleted. */
    default void beforeDelete() {
    }

    /** The events raised since the aggregate was last saved, to publish now that it is. */
    default Collection<Object> pendingEvents() {
        return Collections.emptyList();
    }

    /** The pending events were published: they are not published again. */
    default void eventsPublished() {
    }

    /** One that does nothing, for an object with nothing to say. */
    Lifecycle NONE = new Lifecycle() {
    };

    /** The object's own moments, or none. */
    static Lifecycle of(Object o) {
        return o instanceof Lifecycle ? (Lifecycle) o : NONE;
    }
}
