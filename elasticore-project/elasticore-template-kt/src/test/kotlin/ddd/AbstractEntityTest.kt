package ddd

import io.elasticore.blueprint.domain.refdata.dto.RefdataMapper
import io.elasticore.blueprint.domain.refdata.dto.CaseNoteDTO
import io.elasticore.blueprint.domain.refdata.entity.Code
import io.elasticore.blueprint.domain.refdata.entity.RegionCode
import io.elasticore.blueprint.domain.refdata.entity.StatusCode
import io.elasticore.blueprint.domain.refdata.entity.CaseNote
import io.elasticore.blueprint.domain.refdata.entity.ColorMarker
import io.elasticore.blueprint.domain.refdata.entity.Person
import io.elasticore.blueprint.domain.refdata.repository.CodeRepository
import io.elasticore.blueprint.domain.refdata.repository.CaseNoteRepository
import io.elasticore.blueprint.domain.refdata.repository.MarkerRepository
import io.elasticore.blueprint.domain.refdata.repository.PartyRepository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.junit.jupiter.api.Assertions.assertFalse
import io.elasticore.springboot3.bean.ApplicationContextProvider
import jakarta.persistence.EntityManager
import io.elasticore.springboot3.bean.DbTransactionGateway
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

/** The application of the refdata domain alone: KotlinShopApp scans the shop's. */
@SpringBootApplication(proxyBeanMethods = false)
@Import(DbTransactionGateway::class, ApplicationContextProvider::class)
@EnableJpaAuditing
@ComponentScan("io.elasticore.blueprint.domain.refdata")
@EntityScan("io.elasticore.blueprint.domain.refdata.entity")
@EnableJpaRepositories("io.elasticore.blueprint.domain.refdata.repository")
open class KotlinRefdataApp

/**
 * The Kotlin abstract root, on the generated schema: one repository reads the rows of its
 * subclasses' tables, each as its own class.
 */
@SpringBootTest(
    classes = [KotlinRefdataApp::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:kt-abstract;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-h2.sql",
    ],
)
class AbstractEntityTest {

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var repository: CodeRepository
    @Autowired lateinit var notes: CaseNoteRepository
    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var parties: PartyRepository
    @Autowired lateinit var markers: MarkerRepository
    @Autowired lateinit var transactions: PlatformTransactionManager

    @Test
    @Transactional
    fun oneRepositoryReadsEverySubclassTable() {
        jdbc.update("INSERT INTO ref_status_code (code, name) VALUES (?, ?)", "OPEN", "Open")
        jdbc.update("INSERT INTO ref_region_code (code, name, country) VALUES (?, ?, ?)", "SEL", "Seoul", "KR")

        val codes = repository.findAll().map { it.code }
        assertTrue(codes.containsAll(listOf("OPEN", "SEL")), codes.toString())
        assertTrue(repository.findById("OPEN").orElseThrow() is StatusCode)
        val region: Code = repository.findById("SEL").orElseThrow()
        assertEquals("KR", (region as RegionCode).country)
    }

    /** A relation to the root holds a proxy, and its column is the row's key, whichever table has the row. */
    @Test
    @Transactional
    fun aRelationToTheRootIsWrittenByItsKey() {
        jdbc.update("INSERT INTO ref_region_code (code, name, country) VALUES (?, ?, ?)", "PUS", "Pusan", "KR")

        val note = CaseNote()
        RefdataMapper.mapping(CaseNoteDTO().apply { title = "Lost"; statusCode = "PUS" }, note, em)
        notes.saveAndFlush(note)

        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM ref_case_note WHERE title = ? AND status_code = ?",
                Int::class.java,
                "Lost",
                "PUS",
            ),
        )
    }

    /** @deleted under JOINED: the column in the root's table, a version, and the subclass's row kept. */
    @Test
    fun aDeletedPersonIsKeptInBothTablesAndNotFound() {
        val tx = TransactionTemplate(transactions)
        val id = tx.execute {
            val p = Person()
            p.name = "Kim"
            p.birthYear = 1990
            em.persist(p)
            p.partyId
        }!!
        tx.executeWithoutResult { parties.delete(parties.findById(id).orElseThrow()) }

        assertTrue(parties.findById(id).isEmpty)
        assertFalse(parties.existsById(id))
        assertFalse(parties.findAll().any { it.partyId == id })
        assertEquals(1, count("SELECT COUNT(*) FROM ref_party WHERE party_id = ? AND left_at IS NOT NULL", id))
        assertEquals(1, count("SELECT COUNT(*) FROM ref_person WHERE party_id = ?", id))
    }

    /** @deleted under TABLE_PER_CLASS: the column in the subclass's own table. */
    @Test
    fun aDeletedMarkerIsKeptInItsTableAndNotFound() {
        val tx = TransactionTemplate(transactions)
        val id = tx.execute {
            val m = ColorMarker()
            m.label = "Red"
            m.rgb = "FF0000"
            em.persist(m)
            m.markerId
        }!!
        tx.executeWithoutResult { markers.delete(markers.findById(id).orElseThrow()) }

        assertTrue(markers.findById(id).isEmpty)
        assertEquals(0L, markers.findAll().count { it.markerId == id }.toLong())
        assertEquals(1, count("SELECT COUNT(*) FROM ref_color_marker WHERE marker_id = ? AND retired_at IS NOT NULL", id))
    }

    private fun count(sql: String, id: Long): Int = jdbc.queryForObject(sql, Int::class.java, id)!!

    /** Without an EntityManager there is no proxy and no stub to make, and it is not left unset without a word. */
    @Test
    fun writingTheRelationWithoutAnEntityManagerSaysSo() {
        val thrown = assertThrows(IllegalStateException::class.java) {
            RefdataMapper.mapping(CaseNoteDTO().apply { statusCode = "PUS" }, CaseNote())
        }
        assertTrue(thrown.message!!.contains("needs an EntityManager"), thrown.message)
    }
}
