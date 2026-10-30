package dddmybatis;

import io.elasticore.springboot3.bean.ApplicationContextProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;

import java.sql.Connection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Boot configuration for the tests: the generated domain over MyBatis and its
 * mappers - the dbms port too, which a MyBatis domain runs on MyBatis through
 * a generated adapter.
 */
@SpringBootApplication(scanBasePackages = "io.elasticore.sample.dddmybatis")
@MapperScan(basePackages = "io.elasticore.sample.dddmybatis", annotationClass = Mapper.class)
@Import(ApplicationContextProvider.class)
public class DddMyBatisSampleApp {

    /** Who writes: what @CreatedBy and @LastModifiedBy record, as JPA auditing would ask it. */
    public static final String AUDITOR = "tester";

    @Bean
    AuditorAware<String> auditor() {
        return () -> Optional.of(AUDITOR);
    }

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
