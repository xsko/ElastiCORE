package io.elasticore.sample.mybatis.petstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/** The YAPS pet store, on ElastiCORE 3.0, MyBatis and Spring Boot 4. */
@SpringBootApplication
public class PetstoreMyBatisApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetstoreMyBatisApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
