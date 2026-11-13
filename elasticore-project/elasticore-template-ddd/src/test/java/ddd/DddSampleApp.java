package ddd;

import io.elasticore.springboot3.bean.ApplicationContextProvider;
import io.elasticore.springboot3.bean.DbTransactionGateway;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.elasticore.runtime.port.DbmsService;
import io.elasticore.springboot3.dbms.DbmsServiceProxyFactory;
import io.elasticore.sample.ddd.shop.application.port.out.ShopReportService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Boot configuration for the tests.
 *
 * The generated sources have no application class - they are a domain, not a
 * program - so the test supplies one and points it at them. The scan reaches
 * the services and controllers as well, because the question the API test
 * asks is whether the whole stack the DSL describes actually starts.
 */
@SpringBootApplication
// The two runtime pieces a dbms port needs: the executor that runs its SQL
// and the context provider its proxy finds the executor through. The full
// @EnableElastiCore registrar detects the main package off the call stack,
// which a test context does not walk the same way - so the pieces are
// imported directly and the proxy is built in the test, which is also the
// honest shape: the test states exactly what the port depends on.
@Import({DbTransactionGateway.class, ApplicationContextProvider.class})
@EnableJpaAuditing
@ComponentScan("io.elasticore.sample.ddd")
@EntityScan({"io.elasticore.sample.ddd.shop.domain.model", "io.elasticore.sample.ddd.catalog.entity",
        "io.elasticore.sample.ddd.billing.domain.model", "io.elasticore.sample.ddd.board.domain.model",
        // the outbox table is an adapter concern and lives with them
        "io.elasticore.sample.ddd.shop.infrastructure.persistence"})
// Spring Data scans infrastructure, not the domain. The domain package next
// door holds the ports - plain interfaces the domain owns - and pointing
// the scan at them would have Spring Data try to implement them.
@EnableJpaRepositories({"io.elasticore.sample.ddd.shop.infrastructure.persistence",
        "io.elasticore.sample.ddd.catalog.repository",
        "io.elasticore.sample.ddd.billing.infrastructure.persistence",
        "io.elasticore.sample.ddd.board.infrastructure.persistence"})
public class DddSampleApp {

    /**
     * The outbound port, as a bean.
     *
     * The generated file is an interface and the adapter is a runtime proxy -
     * that is the whole of what makes a dbms or http declaration a port. But
     * a proxy nobody registers is a port nothing can be given, and a domain
     * service that names one then fails to start: "No qualifying bean of type
     * ShopReportService". That is what happened the first time the sample
     * wired the two together.
     *
     * So this is the adapter's registration, and it is one method. In an
     * application the @EnableElastiCore registrar does it by scanning; a test
     * context does not walk the call stack it detects the main package from,
     * so the sample states it, which is also the clearest place to read what
     * the seam actually is.
     */
    @Bean
    ShopReportService shopReportService(BeanFactory beanFactory, Environment environment) {
        return DbmsServiceProxyFactory.createService(
                ShopReportService.class,
                ShopReportService.class.getAnnotation(DbmsService.class),
                beanFactory, environment);
    }
}
