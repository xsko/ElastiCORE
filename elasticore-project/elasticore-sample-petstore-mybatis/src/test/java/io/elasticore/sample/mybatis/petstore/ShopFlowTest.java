package io.elasticore.sample.mybatis.petstore;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/** The shop on H2, from the migrations Flyway runs (db/migration/h2) - every build. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ShopFlow.Counting.class)
class ShopFlowTest extends ShopFlow {
}
