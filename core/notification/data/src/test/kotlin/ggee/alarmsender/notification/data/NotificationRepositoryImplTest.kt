package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.testfixture.NotificationFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(classes = [DataLayerTestApp::class])
@Testcontainers
class NotificationRepositoryImplTest @Autowired constructor(
    private val sut: ggee.alarmsender.notification.domain.NotificationRepository,
    private val jdbcTemplate: JdbcTemplate,
) : PostgresIntegrationTest() {

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE notification_history, notification_outbox, notification RESTART IDENTITY CASCADE")
    }

    @Test
    fun `save 시 id 가 발급되고 findById 로 동일한 도메인 객체를 조회한다`() {
        val saved = sut.save(NotificationFixtures.notification())

        assertNotNull(saved.id)
        val found = sut.findById(saved.id!!)
        assertEquals(saved.recipientId, found?.recipientId)
        assertEquals(saved.type, found?.type)
        assertEquals(saved.channel, found?.channel)
        assertEquals(saved.payload, found?.payload)
        assertEquals(saved.idempotencyKey, found?.idempotencyKey)
        assertEquals(saved.status, found?.status)
        assertEquals(saved.createdAt, found?.createdAt)
    }
}
