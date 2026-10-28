package mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Boot configuration for the tests. The generated sources are a domain, not a
 * program, so the test supplies the application class and points it at them.
 */
@SpringBootApplication(scanBasePackages = "io.elasticore.sample.mybatis")
@MapperScan(basePackages = "io.elasticore.sample.mybatis", annotationClass = Mapper.class)
public class MyBatisSampleApp {

    /** Counts the statements MyBatis prepares, so a test can say how many a request took. */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    public static class StatementCounter implements Interceptor {
        public final AtomicInteger count = new AtomicInteger();
        /** The SQL of the statements prepared, newest last. */
        public final java.util.List<String> sql = new java.util.concurrent.CopyOnWriteArrayList<String>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            count.incrementAndGet();
            sql.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql());
            return invocation.proceed();
        }
    }

    @Bean
    public StatementCounter statementCounter() {
        return new StatementCounter();
    }
}
