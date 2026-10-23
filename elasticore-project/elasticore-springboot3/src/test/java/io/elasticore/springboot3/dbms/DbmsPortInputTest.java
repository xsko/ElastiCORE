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
