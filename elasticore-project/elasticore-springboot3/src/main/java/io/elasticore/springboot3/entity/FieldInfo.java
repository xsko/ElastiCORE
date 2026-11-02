package io.elasticore.springboot3.entity;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;

import java.util.Collection;

/**
 * Represents metadata and query-building capabilities for an entity field.
 * This class provides type-safe access to entity fields and supports building
 * JPA Criteria API specifications and sort orders.
 * 
 * <p>FieldInfo encapsulates field information including type, name, and hierarchical
 * relationships (for nested fields), and provides convenient methods for creating
 * database queries and sort expressions.</p>
 * 
 * @param <T> the entity type that contains this field
 * @author ElastiCore Framework
 * @since 1.0
 */
public final class FieldInfo<T> {
    private final Class<T> entityType;
    private final String name;
    private final Class<?> type;
    private final Class<?> baseType;

    private final FieldInfo parentFieldInfo;

    /**
     * Constructs a new FieldInfo with the specified parameters.
     * 
     * @param entityType the class of the entity containing this field
     * @param name the name of the field
     * @param type the type of the field (may be a collection type)
     * @param baseType the base type of the field (element type for collections)
     * @param parentFieldInfo the parent field info for nested fields, null for root fields
     */
    public FieldInfo(Class<T> entityType, String name, Class<?> type, Class<?> baseType ,FieldInfo parentFieldInfo) {
        this.entityType = entityType;
        this.name = name;
        this.type = type;
        this.baseType = baseType;
        this.parentFieldInfo = parentFieldInfo;
    }

    /**
     * Returns the entity type that contains this field.
     * 
     * @return the entity class
     */
    public Class<T> getEntityType() {
        return entityType;
    }

    /**
     * Returns the name of the field.
     * 
     * @return the field name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the type of the field.
     * For collection fields, this returns the collection type.
     * 
     * @return the field type
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Returns the base type of the field.
     * For collection fields, this returns the element type.
     * For non-collection fields, this is the same as getType().
     * 
     * @return the base field type
     */
    public Class<?> getBaseType() {
        return baseType;
    }

    /**
     * Returns the ascending sort expression for this field (e.g. "name+").
     * @return the ascending sort expression
     */
    public String ASC() {
        return name + "+";
    }

    /**
     * Returns the descending sort expression for this field (e.g. "name-").
     * @return the descending sort expression
     */
    public String DESC() {
        return name + "-";
    }


    /**
     * Builds a Specification for the given operation and value.
     * Empty or null values are not filtered unless checkEmpty is true.
     *
     * @param op    the operation to apply (e.g. equals, like, etc.)
     * @param value the value to filter with
     * @return a Specification for this field
     */
    public Specification<T> where(Op op, Object value) {
        return where(op,value,false);
    }

    /**
     * Builds a Specification for the given operation and value, optionally checking for empty input.
     * If checkEmpty is true and value is null, an empty string, or an empty collection,
     * it returns a Specification that always evaluates to true (cb.conjunction()) - except for
     * IS_NULL and IS_NOT_NULL, which take no value: theirs is always null, and the condition stands.
     *
     * @param op         the filtering operation
     * @param value      the value to compare
     * @param checkEmpty whether to ignore empty/null values
     * @return a Specification to be used in a JPA query
     */
    public Specification<T> where(Op op, Object value, boolean checkEmpty) {

        boolean takesValue = op != Op.IS_NULL && op != Op.IS_NOT_NULL;
        if (checkEmpty
                && takesValue
                && (value == null
                        || (value instanceof String s && s.trim().isEmpty())
                        || (value instanceof Collection<?> c && c.isEmpty()))) {
            return (root, query, cb) -> cb.conjunction();
        }

        if( value instanceof FieldInfo<?>) {
            FieldInfo fi = (FieldInfo) value;

            return (root, query, cb) -> {
                Path<?> path = resolvePath(root);
                return op.toPredicate(cb, path, fi.resolvePath(root));
            };
        }

        return (root, query, cb) -> {
            Path<?> path = resolvePath(root);
            return op.toPredicate(cb, path, value);
        };
    }

    /**
     * Recursively resolves the full path to the field using parentFieldInfo.
     *
     * For example, if parentFieldInfo represents "address" and name is "city",
     * this returns root.get("address").get("city")
     *
     * @param root the root of the query
     * @return the resolved Path
     */
    private Path<?> resolvePath(From<?, ?> root) {
        if (parentFieldInfo == null) {
            return root.get(name);
        }

        Path<?> parentPath = parentFieldInfo.resolvePath(root);
        return parentPath.get(name);
    }
    /**
     * Creates an ascending sort order for this field.
     * 
     * @return a Sort.Order object for ascending sort
     */
    public Sort.Order getAscOrder() {
        return Sort.Order.asc(this.name);
    }

    /**
     * Creates a descending sort order for this field.
     * 
     * @return a Sort.Order object for descending sort
     */
    public Sort.Order getDescOrder() {
        return Sort.Order.desc(this.name);
    }

    /**
     * Returns the parent FieldInfo for nested fields, or null for root-level fields.
     *
     * @return the parent FieldInfo, or null if this is a root field
     */
    public FieldInfo<?> getParentFieldInfo() {
        return parentFieldInfo;
    }
}