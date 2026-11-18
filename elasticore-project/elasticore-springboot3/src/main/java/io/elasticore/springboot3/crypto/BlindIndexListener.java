package io.elasticore.springboot3.crypto;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The JPA listener that writes an entity's blind indexes just before its row is: the generated entity of an
 * {@code @encrypt @search(eq)} field names it in {@code @EntityListeners} and has {@code indexEncrypted()}, which this
 * calls on {@code @PrePersist} and {@code @PreUpdate} - after the service's {@code beforeSave}, the values the model
 * derives and any code of the application that set the field, whatever path saved the row.
 *
 * A listener rather than a callback on the entity: JPA allows one method per event per class, and an entity may spend
 * its own {@code @PrePersist} on generating an id.
 */
public class BlindIndexListener {

    /** The method the generated entity has. */
    public static final String METHOD = "indexEncrypted";

    private static final ClassValue<Method> METHODS = new ClassValue<Method>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod(METHOD);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    };

    @PrePersist
    @PreUpdate
    public void index(Object entity) {
        if (entity == null) return;
        Method m = METHODS.get(entity.getClass());
        if (m == null) return;
        try {
            m.invoke(entity);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(entity.getClass().getName() + "." + METHOD + "() is not public", ex);
        }
    }
}
