package io.elasticore.springboot3.validation;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * The optimistic lock a REST update actually takes.
 *
 * A generated update loads the row and copies the request onto it. The
 * version the client sent is then either copied onto a loaded entity - which
 * Hibernate ignores, comparing against the version it loaded itself - or not
 * copied at all. Either way two clients that read the same row both succeed,
 * and the second one silently undoes the first.
 *
 * So the version the request carries is compared with the one on the row
 * before anything is written. A request that sends no version is not
 * checked, which keeps a client that never read the row working.
 *
 * The DTO's version is read by name rather than through a getter, because a
 * DTO may have been declared without it and the generated call has to
 * compile either way.
 */
public final class VersionCheck {

    private VersionCheck() {
    }

    /**
     * @param dto          the request
     * @param property     the name of the version property on the DTO
     * @param current      the version on the loaded row
     * @param entityClass  the entity, for the exception
     * @param identifier   the row's identity, for the exception
     */
    public static void check(Object dto, String property, Object current,
                             Class<?> entityClass, Object identifier) {
        if (dto == null || property == null) return;
        Object sent = read(dto, property);
        if (sent == null) return;
        if (!same(sent, current)) {
            // In a class of its own, so this one's verifier never meets a type
            // the application may not have.
            if (ORM) throw Orm.stale(entityClass, identifier);
            // An application without Spring's ORM module - MyBatis - gets the
            // exception that one extends, which the error handler answers alike.
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "Object of class [" + (entityClass == null ? "?" : entityClass.getName())
                            + "] with identifier [" + identifier + "]: optimistic locking failed");
        }
    }

    /** Whether spring-orm is there: a JPA application has it, a MyBatis one need not. */
    private static final boolean ORM = present("org.springframework.orm.ObjectOptimisticLockingFailureException");

    private static final class Orm {
        static RuntimeException stale(Class<?> entityClass, Object identifier) {
            return new ObjectOptimisticLockingFailureException(entityClass, identifier);
        }
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, VersionCheck.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Equal as numbers when both are numbers, so a Long and an Integer compare. */
    private static boolean same(Object sent, Object current) {
        if (sent instanceof Number && current instanceof Number) {
            return ((Number) sent).longValue() == ((Number) current).longValue();
        }
        return Objects.equals(sent, current);
    }

    private static Object read(Object target, String property) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(property);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException next) {
                // Look in the parent.
            } catch (IllegalAccessException unreadable) {
                return null;
            }
        }
        return null;
    }
}
