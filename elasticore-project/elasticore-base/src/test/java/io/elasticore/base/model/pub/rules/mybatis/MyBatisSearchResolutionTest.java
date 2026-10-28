package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A search condition the mapper XML cannot write - a path further than the
 * rows it joins - must neither be left out quietly nor count as a condition:
 * an update or a delete by condition naming only it would then reach every row.
 */
public class MyBatisSearchResolutionTest {

    @AfterEach
    void reset() {
        ModelCheck.reset();
    }

    @Test
    public void aPathFurtherThanAJoinedRowIsNotAConditionAndStopsTheBuild() {
        ModelCheck.reset();
        EntitySpec sale = new EntitySpec("Sale").domain("shop", "Shop")
                .config(MyBatisMapperRules.SEARCH_CONFIG, "eq\tshop.region.name\tregionName\tString");
        List<String[]> resolved = MyBatisMapperRules.resolved(sale, new ArrayList<MyBatisMapperRules.Join>());
        assertEquals(0, resolved.size());
        assertEquals(1, ModelCheck.failures().size(), ModelCheck.failures().toString());
        assertTrue(ModelCheck.failures().get(0).contains("shop.region.name is further than that"),
                ModelCheck.failures().toString());
    }
}
