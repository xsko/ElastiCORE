package ddd

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.test.tester.HttpGraphQlTester

/**
 * The Kotlin shop turns GraphQL on: the Kotlin controllers are compiled, the
 * build reads the schema off the Kotlin classes, and /graphql answers through
 * the hexagon's use cases.
 */
@SpringBootTest(classes = [KotlinShopApp::class])
@AutoConfigureHttpGraphQlTester
class ShopGraphQlTest {

    @Autowired
    private lateinit var graphql: HttpGraphQlTester

    @Test
    @DisplayName("a category is created and read back through GraphQL")
    fun categoryRoundTrip() {
        val id = graphql.document("""mutation { createCategory(input: {name: "Kotlin books", sortOrder: 2}) { catId name } }""")
            .execute()
            .path("createCategory.name").entity(String::class.java).isEqualTo("Kotlin books")
            .path("createCategory.catId").entity(Long::class.java).get()

        graphql.document("{ category(catId: $id) { name sortOrder } }")
            .execute()
            .path("category.name").entity(String::class.java).isEqualTo("Kotlin books")
            .path("category.sortOrder").entity(Int::class.java).isEqualTo(2)
    }

    @Test
    @DisplayName("a category that is not there is null, and deleting it is NOT_FOUND")
    fun notThere() {
        graphql.document("{ category(catId: 987654) { name } }")
            .execute()
            .path("category").valueIsNull()
        graphql.document("mutation { deleteCategory(catId: 987654) }")
            .execute()
            .errors()
            .satisfy { errors ->
                assertThat(errors).hasSize(1)
                assertThat(errors[0].errorType.toString()).isEqualTo("NOT_FOUND")
            }
    }
}
