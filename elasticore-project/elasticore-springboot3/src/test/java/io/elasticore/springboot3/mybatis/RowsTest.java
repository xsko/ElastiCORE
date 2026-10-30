package io.elasticore.springboot3.mybatis;

import io.elasticore.springboot3.bean.PortRows;
import io.elasticore.springboot3.domain.Lookup;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a generated MyBatis adapter leans on: a field set on a class loaded
 * again, a LIKE value matched as written, a row of NULLs, a row the
 * transaction handed out before, and a root of another store.
 */
class RowsTest {

    static class Parent {
        private String name;
    }

    static class Child extends Parent {
        private Integer size;
    }

    @Test
    void aFieldOfTheClassOrOneItExtendsIsSet() {
        Child c = new Child();
        Rows.set(c, "name", "a");
        Rows.set(c, "size", 3);
        assertThat(((Parent) c).name).isEqualTo("a");
        assertThat(c.size).isEqualTo(3);
        assertThatThrownBy(() -> Rows.set(c, "missing", 1)).hasMessageContaining("has no field missing");
    }

    /** A class loaded again - a DevTools restart - is looked up afresh, not by its name. */
    @Test
    void aClassLoadedAgainIsItsOwn() throws Exception {
        ClassLoader isolated = new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (!name.equals(Parent.class.getName())) throw new ClassNotFoundException(name);
                try (java.io.InputStream in = RowsTest.class.getClassLoader()
                        .getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] bytes = in.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (java.io.IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        };
        Rows.set(new Parent(), "name", "first");
        Class<?> again = isolated.loadClass(Parent.class.getName());
        assertThat(again).isNotSameAs(Parent.class);
        java.lang.reflect.Constructor<?> ctor = again.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object row = ctor.newInstance();
        Rows.set(row, "name", "second");
        java.lang.reflect.Field f = again.getDeclaredField("name");
        f.setAccessible(true);
        assertThat(f.get(row)).isEqualTo("second");
    }

    @Test
    void aLikeValueMatchesAsWritten() {
        assertThat(Rows.like("50%_off!", "%", "%", false, "h2")).isEqualTo("%50!%!_off!!%");
        assertThat(Rows.like("AbC", "", "%", true, "postgresql")).isEqualTo("abc%");
        assertThat(Rows.like("[x]", "%", "", false, "sqlserver")).isEqualTo("%![x]");
        assertThat(Rows.like("[x]", "%", "", false, "oracle")).isEqualTo("%[x]");
        assertThat(Rows.like(null, "%", "%", true, "h2")).isNull();
        // A statement's OGNL hands a one-character literal as a Character.
        assertThat(Rows.like("a", '%', '%', false, null)).isEqualTo("%a%");
    }

    public static class NameRow {
        public String nickName;
        public Long points;
    }

    /** MyBatis answers a row of NULLs alone as null, and leaves a NULL column out of a map. */
    @Test
    void aRowOfNullsIsOneOfNullFields() {
        Map<String, Object> partial = new LinkedHashMap<String, Object>();
        partial.put("POINTS", 7L);
        List<NameRow> rows = PortRows.list(Arrays.asList(null, partial), NameRow.class);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isNotNull();
        assertThat(rows.get(0).nickName).isNull();
        assertThat(rows.get(1).points).isEqualTo(7L);
        List<Map> maps = PortRows.list(Collections.singletonList(null), Map.class);
        assertThat(maps).hasSize(1);
        assertThat(maps.get(0)).isEmpty();
        assertThat(PortRows.first(Collections.singletonList(null), NameRow.class)).isNotNull();
    }

    @Test
    void aRowIsLoadedOncePerTransaction() {
        Object row = new Object();
        assertThat(LoadedRows.first(row)).as("outside a transaction, always").isTrue();
        assertThat(LoadedRows.first(row)).isTrue();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(LoadedRows.first(row)).isTrue();
            assertThat(LoadedRows.first(row)).as("the same object again").isFalse();
            assertThat(LoadedRows.first(new Object())).isTrue();
            for (org.springframework.transaction.support.TransactionSynchronization s
                    : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(LoadedRows.first(row)).as("a new transaction").isTrue();
        } finally {
            for (org.springframework.transaction.support.TransactionSynchronization s
                    : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED);
            }
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** A root no source serves - one kept on JPA - is found by what finds the rest. */
    @Test
    void aTypeNoSourceServesIsFoundOtherwise() {
        Lookup.Source children = new Lookup.Source() {
            @Override
            public Class<?> type() {
                return Child.class;
            }

            @Override
            public Object find(Object id) {
                return "child " + id;
            }
        };
        Lookup lookup = Lookup.over(Collections.singletonList(children), (type, id) -> "jpa " + type.getSimpleName() + " " + id);
        assertThat(lookup.find(Child.class, 1)).isEqualTo("child 1");
        assertThat(lookup.find(String.class, 2)).isEqualTo("jpa String 2");
        assertThatThrownBy(() -> Lookup.over(Collections.singletonList(children)).find(String.class, 2))
                .hasMessageContaining("No repository finds java.lang.String");
    }
}
