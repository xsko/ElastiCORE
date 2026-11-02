package io.elasticore.springboot3.entity;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * checkEmpty drops a condition whose value is empty - a search field left blank. IS_NULL and
 * IS_NOT_NULL take no value: theirs is null every time, and checkEmpty must not drop them.
 */
@DisplayName("FieldInfo.where with checkEmpty")
class FieldInfoCheckEmptyTest {

    private final FieldInfo<Object> deletedAt =
            new FieldInfo<>(Object.class, "deletedAt", Object.class, Object.class, null);

    @SuppressWarnings("unchecked")
    private Predicate predicate(Op op, Object value, CriteriaBuilder cb, Path<Object> path) {
        Root<Object> root = mock(Root.class);
        when(root.get("deletedAt")).thenReturn(path);
        return deletedAt.where(op, value, true).toPredicate(root, mock(CriteriaQuery.class), cb);
    }

    @Test
    @DisplayName("IS_NULL and IS_NOT_NULL stand with a null value")
    @SuppressWarnings("unchecked")
    void operatorsWithoutAValueAreKept() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> path = mock(Path.class);
        Predicate isNull = mock(Predicate.class);
        Predicate isNotNull = mock(Predicate.class);
        when(cb.isNull(path)).thenReturn(isNull);
        when(cb.isNotNull(path)).thenReturn(isNotNull);

        assertThat(predicate(Op.IS_NULL, null, cb, path)).isSameAs(isNull);
        assertThat(predicate(Op.IS_NOT_NULL, null, cb, path)).isSameAs(isNotNull);
        verify(cb, never()).conjunction();
    }

    @Test
    @DisplayName("an operator that takes a value is dropped when the value is empty")
    @SuppressWarnings("unchecked")
    void anEmptyValueDropsTheCondition() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> path = mock(Path.class);
        Predicate always = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(always);

        assertThat(predicate(Op.EQ, null, cb, path)).isSameAs(always);
        assertThat(predicate(Op.LIKE, "  ", cb, path)).isSameAs(always);
        assertThat(predicate(Op.IN, Collections.emptyList(), cb, path)).isSameAs(always);
        verify(cb, never()).equal(path, (Object) null);
    }
}
