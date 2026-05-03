package ggee.alarmsender.notification.usecase.retrynotification

import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.domain.OutboxStatus
import ggee.alarmsender.notification.domain.exception.NotificationAccessDeniedException
import ggee.alarmsender.notification.domain.exception.NotificationNotFoundException
import ggee.alarmsender.notification.testfixture.NotificationFixtures
import ggee.alarmsender.notification.teststub.InMemoryNotificationHistoryRepository
import ggee.alarmsender.notification.teststub.InMemoryNotificationOutboxRepository
import ggee.alarmsender.notification.teststub.InMemoryNotificationRepository
import ggee.alarmsender.notification.usecase.dispatchnotification.DbPollingOutboxPublisher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class RetryNotificationServiceTest {

    private val now = Instant.parse("2026-05-03T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val notifications = InMemoryNotificationRepository()
    private val outbox = InMemoryNotificationOutboxRepository()
    private val outboxPublisher = DbPollingOutboxPublisher(outbox)
    private val history = InMemoryNotificationHistoryRepository()

    private val sut = RetryNotificationService(notifications, outboxPublisher, history, clock)

    @BeforeEach
    fun setUp() {
        notifications.clear()
        outbox.clear()
        history.clear()
    }

    private fun seedDeadLetter(recipient: String = "u1"): Long {
        val n = notifications.save(
            NotificationFixtures.notification(
                idempotencyKey = "dead-1",
                recipientId = recipient,
                status = NotificationStatus.DEAD_LETTER,
            ),
        )
        outbox.save(
            NotificationFixtures.outbox(
                notificationId = n.id!!,
                status = OutboxStatus.DEAD,
                attemptCount = 5,
                lastError = "smtp 5xx",
            ),
        )
        return n.id!!
    }

    @Test
    fun `DEAD_LETTER 알림 수동 재시도 시 PENDING 으로 복귀하고 attempt_count 가 0 으로 초기화된다`() {
        val id = seedDeadLetter()

        val result = sut.execute(RetryNotificationCommand(id, "u1"))

        assertEquals(NotificationStatus.PENDING, result.status)

        val outboxAfter = outbox.findByNotificationId(id)!!
        assertEquals(OutboxStatus.PENDING, outboxAfter.status)
        assertEquals(0, outboxAfter.attemptCount, "수동 재시도 시 attempt_count 는 0 으로 리셋되어야 함")
        assertEquals(null, outboxAfter.lastError)
        assertEquals(now, outboxAfter.availableAt)
    }

    @Test
    fun `MANUAL_RETRY history 가 적재되어 자동 재시도와 구분된다`() {
        val id = seedDeadLetter()
        sut.execute(RetryNotificationCommand(id, "u1"))

        val recent = history.all().last()
        assertEquals(HistoryReason.MANUAL_RETRY, recent.reason)
        assertEquals(NotificationStatus.DEAD_LETTER, recent.fromStatus)
        assertEquals(NotificationStatus.PENDING, recent.toStatus)
    }

    @Test
    fun `본인 외 사용자가 시도하면 접근 거부 예외 발생`() {
        val id = seedDeadLetter(recipient = "u1")
        assertThrows<NotificationAccessDeniedException> {
            sut.execute(RetryNotificationCommand(id, "u2"))
        }
    }

    @Test
    fun `존재하지 않는 알림 재시도 시 알림 미발견 예외 발생`() {
        assertThrows<NotificationNotFoundException> {
            sut.execute(RetryNotificationCommand(notificationId = 999, requesterId = "u1"))
        }
    }

    @Test
    fun `DEAD_LETTER 가 아닌 알림에 재시도 시도 시 도메인 상태 위반 예외 발생`() {
        val n = notifications.save(
            NotificationFixtures.notification(
                idempotencyKey = "pending-1",
                recipientId = "u1",
                status = NotificationStatus.PENDING,
            ),
        )
        outbox.save(NotificationFixtures.outbox(notificationId = n.id!!, status = OutboxStatus.PENDING))

        // domain.Notification.resetForManualRetry 의 check(...) 가 IllegalStateException 던짐 → handler 409
        assertThrows<IllegalStateException> {
            sut.execute(RetryNotificationCommand(n.id!!, "u1"))
        }
    }
}
