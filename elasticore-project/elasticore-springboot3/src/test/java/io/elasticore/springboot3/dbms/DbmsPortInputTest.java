package io.elasticore.springboot3.dbms;

import io.elasticore.runtime.port.DbmsService;
import io.elasticore.springboot3.bean.ApplicationContextProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * What a DBMS port method hands its SQL as the input. One value - a
 * {@code Long}, as the generator declares a DSL {@code long}, a String, a
 * date - is bound by the parameter's name, as several parameters are; one
 * object by its getters. A single {@code Long} used to be taken for an object,
 * so ":minPrice" looked for {@code Long.getMinPrice()} and bound nothing.
 */
public class DbmsPortInputTest {

    public static class Search {
        public String getKeyword() {
            return "pen";
        }
    }

    @DbmsService(id = "test.ReportPort", sqlSource = "")
    public interface ReportPort {
        String nameOf(Long productId);

        String byCode(String code);

        String byPrimitive(long productId);

        String between(Long from, Long to);

        String search(Search search);

        org.springframework.data.domain.Page<String> page(
                String code, org.springframework.data.domain.Pageable pageable);

        String byIds(java.util.List<Long> ids);

        String byCodes(String[] codes);

        String byNumbers(long[] numbers);

        int deleteByIds(java.util.List<Long> ids);

        int insertRows(java.util.List<Search> rows);
    }

    /**
     * A lone list or array of values is bound by its name - IN (:ids) - as one
     * value is (issue 164). It was read by its getters, and :ids bound as null.
     */
    @Test
    public void aListOfValuesAloneIsBoundByItsName() throws Exception {
        java.util.List<Long> ids = java.util.Arrays.asList(1L, 3L);
        port.byIds(ids);
        assertEquals(ids, ((Map<?, ?>) inputOf("byIds")).get(parameterName("byIds", 0)));
        String[] codes = {"A", "B"};
        port.byCodes(codes);
        assertSame(codes, ((Map<?, ?>) inputOf("byCodes")).get(parameterName("byCodes", 0)));
        long[] numbers = {1L, 2L};
        port.byNumbers(numbers);
        assertSame(numbers, ((Map<?, ?>) inputOf("byNumbers")).get(parameterName("byNumbers", 0)));
    }

    /** A delete by a list of values is one statement with the list bound; a list of rows stays a batch. */
    @Test
    public void aDeleteByAListOfValuesIsOneStatement() throws Exception {
        java.util.List<Long> ids = java.util.Arrays.asList(1L, 3L);
        port.deleteByIds(ids);
        ArgumentCaptor<Object> input = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(executor).executeUpdate(any(), eq("deleteByIds"), input.capture());
        assertEquals(ids, ((Map<?, ?>) input.getValue()).get(parameterName("deleteByIds", 0)));

        java.util.List<Search> rows = java.util.Arrays.asList(new Search(), new Search());
        port.insertRows(rows);
        Mockito.verify(executor).executeBatchUpdate(any(), eq("insertRows"), eq(rows));
    }

    /** A Pageable among a method's parameters is the page it asks for, not page 0 of 50. */
    @Test
    public void aPageableParameterIsThePage() throws Exception {
        org.springframework.data.domain.PageRequest asked = org.springframework.data.domain.PageRequest.of(3, 7);
        port.page("A", asked);
        ArgumentCaptor<org.springframework.data.domain.Pageable> pageable =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        Mockito.verify(executor).inqueryPage(any(), eq("page"), any(), eq(String.class), pageable.capture());
        assertSame(asked, pageable.getValue());
    }

    private AnnotationConfigApplicationContext context;
    private DbmsSqlExecutor executor;
    private ReportPort port;

    @BeforeEach
    void start() {
        executor = Mockito.mock(DbmsSqlExecutor.class);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(ApplicationContextProvider.class);
        context.registerBean(DbmsSqlExecutor.class, () -> executor);
        context.refresh();
        port = DbmsServiceProxyFactory.createService(ReportPort.class,
                ReportPort.class.getAnnotation(DbmsService.class), context, context.getEnvironment());
    }

    @AfterEach
    void stop() {
        context.close();
    }

    private Object inputOf(String methodName) throws Exception {
        ArgumentCaptor<Object> input = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(executor).inqueryFirst(any(), eq(methodName), input.capture(), eq(String.class));
        return input.getValue();
    }

    /** The name the compiler kept for a parameter (arg0 without -parameters). */
    private static String parameterName(String methodName, int index) {
        for (Method m : ReportPort.class.getMethods()) {
            if (m.getName().equals(methodName)) return m.getParameters()[index].getName();
        }
        throw new IllegalArgumentException(methodName);
    }

    @Test
    public void aWrappedNumberIsBoundByItsName() throws Exception {
        port.nameOf(1L);
        Object input = inputOf("nameOf");
        assertTrue(input instanceof Map, String.valueOf(input));
        assertEquals(1L, ((Map<?, ?>) input).get(parameterName("nameOf", 0)));
    }

    @Test
    public void aStringAndAPrimitiveAreBoundByTheirNames() throws Exception {
        port.byCode("A");
        assertEquals("A", ((Map<?, ?>) inputOf("byCode")).get(parameterName("byCode", 0)));
        port.byPrimitive(2L);
        assertEquals(2L, ((Map<?, ?>) inputOf("byPrimitive")).get(parameterName("byPrimitive", 0)));
    }

    @Test
    public void severalParametersAreBoundByTheirNames() throws Exception {
        port.between(1L, 9L);
        Map<?, ?> input = (Map<?, ?>) inputOf("between");
        assertEquals(1L, input.get(parameterName("between", 0)));
        assertEquals(9L, input.get(parameterName("between", 1)));
    }

    /**
     * Another context starting and closing - a cached test context, a
     * @DirtiesContext test - clears the static provider; a port asks its own
     * bean factory and keeps working instead of answering null.
     */
    @Test
    public void anotherContextClosingDoesNotStopThePort() throws Exception {
        AnnotationConfigApplicationContext other = new AnnotationConfigApplicationContext();
        other.registerBean(ApplicationContextProvider.class);
        other.refresh();
        other.close();
        assertEquals(null, ApplicationContextProvider.getApplicationContext());

        port.nameOf(3L);
        assertEquals(3L, ((Map<?, ?>) inputOf("nameOf")).get(parameterName("nameOf", 0)));
    }

    @Test
    public void anObjectIsBoundByItsGetters() throws Exception {
        Search search = new Search();
        port.search(search);
        assertSame(search, inputOf("search"));
    }
}
