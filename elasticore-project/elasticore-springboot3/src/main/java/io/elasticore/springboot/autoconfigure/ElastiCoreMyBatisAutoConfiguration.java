package io.elasticore.springboot.autoconfigure;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.Properties;

/**
 * Tells the generated MyBatis mappers which database they talk to.
 *
 * A generated mapper pages with the syntax of its database - LIMIT and OFFSET
 * on H2, MySQL, MariaDB and PostgreSQL, OFFSET ... FETCH on Oracle, Tibero
 * and SQL Server - and asks which one it is by MyBatis's {@code _databaseId}.
 * That is set only where the application has a DatabaseIdProvider, so this
 * registers one, naming the databases the way the mappers do. An application
 * with a provider of its own keeps it.
 *
 * Loaded before MyBatis's own auto-configuration, which takes the provider
 * when it builds the SqlSessionFactory; does nothing without MyBatis.
 *
 * With a database id set, a statement of the application's own mappers that
 * names a databaseId is used for that database (and one naming another is
 * not), where without one every such statement was left out. An application
 * that relies on that turns this off: elasticore.mybatis.database-id=false -
 * the generated mappers then page with LIMIT/OFFSET.
 */
@AutoConfiguration(beforeName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "org.apache.ibatis.mapping.VendorDatabaseIdProvider")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "elasticore.mybatis",
        name = "database-id", havingValue = "true", matchIfMissing = true)
public class ElastiCoreMyBatisAutoConfiguration {

    /** The vendor names the drivers report, and the ids the generated mappers test. */
    static Properties vendorIds() {
        Properties p = new Properties();
        p.setProperty("Oracle", "oracle");
        p.setProperty("Tibero", "oracle");
        p.setProperty("Microsoft SQL Server", "sqlserver");
        p.setProperty("MySQL", "mysql");
        p.setProperty("MariaDB", "mariadb");
        p.setProperty("PostgreSQL", "postgresql");
        p.setProperty("H2", "h2");
        p.setProperty("HSQL Database Engine", "hsqldb");
        p.setProperty("DB2", "db2");
        return p;
    }

    @Bean
    @ConditionalOnMissingBean(DatabaseIdProvider.class)
    public DatabaseIdProvider elastiCoreDatabaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        provider.setProperties(vendorIds());
        return provider;
    }
}
