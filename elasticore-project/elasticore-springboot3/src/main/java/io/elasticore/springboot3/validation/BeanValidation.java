package io.elasticore.springboot3.validation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bean Validation for the calls the controller cannot validate.
 *
 * A generated PUT carries only what changed: the mapper merges non-null
 * fields onto the stored row. Putting @Valid on it would reject every such
 * request for the @NotNull fields it left out - which is why the update
 * endpoint had none, and why a 60-character value for a 50-character field
 * went straight through to the database and came back as a 500.
 *
 * {@link #validatePresent} is the check that fits a partial update: every
 * constraint on every property the request did send, and nothing about the
 * ones it did not. A skip-null merge can never null a field, so a required
 * field that is absent from the request is still present on the row.
 *
 * Where the application has no Bean Validation provider on its classpath,
 * both methods do nothing - the same as a controller's @Valid would.
 */
public final class BeanValidation {

    private BeanValidation() {
    }

    private static final boolean AVAILABLE = present("jakarta.validation.Validation");

    private static boolean present(String className) {
        try {
            Class.forName(className, false, BeanValidation.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Every constraint on the object, as @Valid would check it. */
    public static void validate(Object target) {
        if (target == null || !AVAILABLE) return;
        Checks.all(target);
    }

    /**
     * The validator Spring configured, when the application has one - so
     * constraint validators with injected dependencies, and the application's
     * messages, are the ones used. Set by the application (for example from
     * a @Bean method or a SmartInitializingSingleton); the default factory
     * is used until then.
     */
    public static void use(Object springValidator) {
        if (AVAILABLE) Checks.use(springValidator);
    }

    /**
     * A built-in constraint message said again in the given language, with
     * its numbers: "size must be between 1 and 10" for Locale.ROOT, the
     * provider's Korean for Locale.KOREAN.
     *
     * The error is a ConstraintViolation, or a Spring error that carries one
     * (a FieldError from @Valid). Only the provider's own templates
     * ({jakarta.validation.constraints.Size.message} ...) are said again: a
     * DSL sentence is a literal in one language already, and a custom
     * validator's message may name values only its own validation knew.
     *
     * @return the message, or null when there is nothing to say again
     */
    public static String messageIn(Object error, java.util.Locale locale) {
        if (error == null || locale == null || !AVAILABLE) return null;
        try {
            return Checks.messageIn(error, locale);
        } catch (RuntimeException | LinkageError cannot) {
            return null;
        }
    }

    /**
     * The values Spring gives a constraint's message codes: the field, then
     * the constraint's attributes in the order of their names (Size: max,
     * min) - so a translation of Size.lines reads {1} and {2} alike whether
     * {@code @Valid} or a service-side check refused the value.
     *
     * @return the values, or none when the argument is not a ConstraintViolation
     */
    public static Object[] argumentsOf(Object violation) {
        if (violation == null || !AVAILABLE) return new Object[0];
        try {
            return Checks.argumentsOf(violation);
        } catch (RuntimeException | LinkageError cannot) {
            return new Object[0];
        }
    }

    /**
     * The constraints of the properties that carry a value - a partial
     * update's rules.
     *
     * A value that is itself a DTO, or a collection of them - the members an
     * aggregate is sent with - is checked too: a collection's elements whole,
     * since they replace what was there, and a single member as a partial
     * update of its own, since it is merged into the member in place.
     */
    public static void validatePresent(Object target) {
        if (target == null || !AVAILABLE) return;
        validatePresent(target, 0);
    }

    private static void validatePresent(Object target, int depth) {
        List<String> present = new ArrayList<String>();
        List<Object> nested = new ArrayList<Object>();
        List<Object> nestedWhole = new ArrayList<Object>();
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value == null) continue;
                    present.add(field.getName());
                    if (value instanceof Iterable) {
                        for (Object each : (Iterable<?>) value) {
                            if (isModel(each)) nestedWhole.add(each);
                        }
                    } else if (isModel(value)) {
                        nested.add(value);
                    }
                } catch (Exception ignored) {
                    // A field reflection cannot read is not one this can check.
                }
            }
        }
        Checks.properties(target, present);
        if (depth >= 4) return;
        for (Object each : nestedWhole) Checks.all(each);
        for (Object each : nested) validatePresent(each, depth + 1);
    }

    /** An application object - not a JDK value, an enum or an array. */
    private static boolean isModel(Object value) {
        if (value == null || value.getClass().isEnum() || value.getClass().isArray()) return false;
        String name = value.getClass().getName();
        return !(name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.")
                || name.startsWith("kotlin.") || name.startsWith("org.springframework."));
    }

    /**
     * Everything that names a jakarta.validation type, kept out of the outer
     * class so that loading BeanValidation never requires the API.
     */
    private static final class Checks {

        private static volatile jakarta.validation.Validator validator;
        /** The factory the validator came from, when known: its interpolator says messages again. */
        private static volatile jakarta.validation.ValidatorFactory factory;
        private static volatile boolean unavailable;

        static void use(Object springValidator) {
            if (springValidator instanceof jakarta.validation.Validator) {
                validator = (jakarta.validation.Validator) springValidator;
                // Spring's LocalValidatorFactoryBean is both: the application's
                // message interpolator, with its MessageSource if it set one.
                factory = springValidator instanceof jakarta.validation.ValidatorFactory
                        ? (jakarta.validation.ValidatorFactory) springValidator : null;
                unavailable = false;
            }
        }

        private static jakarta.validation.Validator validator() {
            if (validator == null && !unavailable) {
                synchronized (Checks.class) {
                    if (validator == null && !unavailable) {
                        try {
                            jakarta.validation.ValidatorFactory made = jakarta.validation.Validation.buildDefaultValidatorFactory();
                            factory = made;
                            validator = made.getValidator();
                        } catch (RuntimeException noProvider) {
                            // The API without an implementation: nothing to check with.
                            unavailable = true;
                        }
                    }
                }
            }
            return validator;
        }

        private static jakarta.validation.MessageInterpolator interpolator() {
            jakarta.validation.ValidatorFactory f = factory;
            if (f == null) {
                validator();
                f = factory;
            }
            if (f == null) {
                // A validator handed over without its factory: the provider's default.
                synchronized (Checks.class) {
                    if (factory == null && !unavailable) {
                        try {
                            factory = jakarta.validation.Validation.buildDefaultValidatorFactory();
                        } catch (RuntimeException noProvider) {
                            return null;
                        }
                    }
                    f = factory;
                }
            }
            return f == null ? null : f.getMessageInterpolator();
        }

        static String messageIn(Object error, java.util.Locale locale) {
            jakarta.validation.ConstraintViolation<?> v = violationOf(error);
            if (v == null) return null;
            String template = v.getMessageTemplate();
            if (!isProviderTemplate(template)) return null;
            jakarta.validation.MessageInterpolator interpolator = interpolator();
            if (interpolator == null) return null;
            return interpolator.interpolate(template, new Context(v), locale);
        }

        static Object[] argumentsOf(Object error) {
            jakarta.validation.ConstraintViolation<?> v = violationOf(error);
            if (v == null) return new Object[0];
            List<Object> args = new ArrayList<Object>();
            args.add(String.valueOf(v.getPropertyPath()));
            java.util.Map<String, Object> attributes =
                    new java.util.TreeMap<String, Object>(v.getConstraintDescriptor().getAttributes());
            attributes.remove("message");
            attributes.remove("groups");
            attributes.remove("payload");
            args.addAll(attributes.values());
            return args.toArray();
        }

        /** The violation itself, or the one a Spring error (a FieldError from @Valid) carries. */
        private static jakarta.validation.ConstraintViolation<?> violationOf(Object error) {
            if (error instanceof jakarta.validation.ConstraintViolation) {
                return (jakarta.validation.ConstraintViolation<?>) error;
            }
            if (error instanceof org.springframework.validation.ObjectError) {
                org.springframework.validation.ObjectError spring = (org.springframework.validation.ObjectError) error;
                if (spring.contains(jakarta.validation.ConstraintViolation.class)) {
                    return spring.unwrap(jakarta.validation.ConstraintViolation.class);
                }
            }
            return null;
        }

        /** A template of the provider's own bundle: {jakarta.validation.constraints.Size.message}. */
        private static boolean isProviderTemplate(String template) {
            if (template == null || !template.endsWith("}") || template.indexOf('}') != template.length() - 1) return false;
            return template.startsWith("{jakarta.validation.constraints.")
                    || template.startsWith("{org.hibernate.validator.constraints.");
        }

        /** What an interpolator needs of a violation to say its message again. */
        private static final class Context implements jakarta.validation.MessageInterpolator.Context {
            private final jakarta.validation.ConstraintViolation<?> violation;

            Context(jakarta.validation.ConstraintViolation<?> violation) {
                this.violation = violation;
            }

            @Override
            public jakarta.validation.metadata.ConstraintDescriptor<?> getConstraintDescriptor() {
                return violation.getConstraintDescriptor();
            }

            @Override
            public Object getValidatedValue() {
                return violation.getInvalidValue();
            }

            @Override
            public <T> T unwrap(Class<T> type) {
                if (type.isInstance(this)) return type.cast(this);
                throw new jakarta.validation.ValidationException("Not available here: " + type.getName());
            }
        }

        static void all(Object target) {
            jakarta.validation.Validator v = validator();
            if (v == null) return;
            raise(v.validate(target));
        }

        static void properties(Object target, List<String> names) {
            jakarta.validation.Validator v = validator();
            if (v == null) return;
            Set<jakarta.validation.ConstraintViolation<Object>> found =
                    new LinkedHashSet<jakarta.validation.ConstraintViolation<Object>>();
            for (String name : names) {
                try {
                    found.addAll(v.validateProperty(target, name));
                } catch (IllegalArgumentException notAProperty) {
                    // A field with no bean property behind it carries no constraint to check.
                }
            }
            raise(found);
        }

        private static void raise(Set<? extends jakarta.validation.ConstraintViolation<?>> violations) {
            if (violations == null || violations.isEmpty()) return;
            throw new jakarta.validation.ConstraintViolationException(
                    new LinkedHashSet<jakarta.validation.ConstraintViolation<?>>(violations));
        }
    }
}
