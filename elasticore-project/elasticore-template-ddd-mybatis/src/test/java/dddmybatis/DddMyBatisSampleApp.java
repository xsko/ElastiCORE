package dddmybatis;

import io.elasticore.runtime.port.DbmsService;
import io.elasticore.sample.dddmybatis.shop.application.port.out.ShopReportService;
import io.elasticore.springboot3.bean.ApplicationContextProvider;
import io.elasticore.springboot3.bean.JdbcDbmsGateway;
import io.elasticore.springboot3.dbms.DbmsServiceProxyFactory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.AuditorAware;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Boot configuration for the tests: the generated domain over MyBatis, its
 * mappers, and the two runtime pieces a dbms port needs - the executor that
 * runs its SQL (JDBC here: there is no JPA) and the context provider its
 * proxy finds the executor through.
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

    @Bean
    JdbcDbmsGateway dbmsGateway(DataSource dataSource) {
        return new JdbcDbmsGateway(dataSource);
    }

    /** The outbound port, as a bean: the adapter is a runtime proxy over the gateway above. */
    @Bean
    ShopReportService shopReportService(BeanFactory beanFactory, Environment environment) {
        return DbmsServiceProxyFactory.createService(
                ShopReportService.class,
                ShopReportService.class.getAnnotation(DbmsService.class),
                beanFactory, environment);
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
