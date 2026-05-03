package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.NotificationHistory
import ggee.alarmsender.notification.domain.NotificationHistoryRepository
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.testfixture.NotificationFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(classes = [DataLayerTestApp::class])
@Testcontainers
class NotificationHistoryRepositoryImplTest @Autowired constructor(
    private val sut: NotificationHistoryRepository,
    private val notificationRepo: NotificationRepository,
    private val jdbcTemplate: JdbcTemplate,
) : PostgresIntegrationTest() {

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE notification_history, notification_outbox, notification RESTART IDENTITY CASCADE")
    }

    @Test
    fun `append 후 발급된 id 와 함께 시간 순 조회 가능`() {
        val n = notificationRepo.save(NotificationFixtures.notification(idempotencyKey = "h1"))
        val now = Instant.parse("2026-05-02T10:00:00Z")

        // notification.status 는 PENDING / SENT / DEAD_LETTER 3개. 워커 처리 중간 상태(IN_PROGRESS) 는 outbox 에만 존재.
        val first = sut.append(NotificationHistory.of(n.id!!, null, NotificationStatus.PENDING, HistoryReason.CREATED, now))
        val second = sut.append(NotificationHistory.of(n.id!!, NotificationStatus.PENDING, NotificationStatus.PENDING, HistoryReason.CLAIMED, now.plusSeconds(1)))
        val third = sut.append(NotificationHistory.of(n.id!!, NotificationStatus.PENDING, NotificationStatus.SENT, HistoryReason.SENT, now.plusSeconds(2)))

        assertNotNull(first.id)
        assertNotNull(second.id)
        assertNotNull(third.id)

        val histories = sut.findByNotificationId(n.id!!)
        assertEquals(3, histories.size)
        assertEquals(HistoryReason.CREATED, histories[0].reason)
        assertEquals(HistoryReason.CLAIMED, histories[1].reason)
        assertEquals(HistoryReason.SENT, histories[2].reason)
    }
}
