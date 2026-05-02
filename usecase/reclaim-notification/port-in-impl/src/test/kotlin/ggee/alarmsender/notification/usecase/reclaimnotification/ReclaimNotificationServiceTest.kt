package ggee.alarmsender.notification.usecase.reclaimnotification

import ggee.alarmsender.notification.domain.OutboxStatus
import ggee.alarmsender.notification.testfixture.NotificationFixtures
import ggee.alarmsender.notification.teststub.InMemoryNotificationHistoryRepository
import ggee.alarmsender.notification.teststub.InMemoryNotificationOutboxRepository
import ggee.alarmsender.notification.teststub.InMemoryNotificationRepository
import ggee.alarmsender.notification.usecase.reclaimnotification.port.ReclaimCommand
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class ReclaimNotificationServiceTest {

    private val now = Instant.parse("2026-05-02T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val notifications = InMemoryNotificationRepository()
    private val outbox = InMemoryNotificationOutboxRepository()
    private val history = InMemoryNotificationHistoryRepository()

    private val sut = ReclaimNotificationService(outbox, history, clock)

    @Test
    fun `lease 만료 IN_PROGRESS row 만 PENDING 으로 복귀시킨다`() {
        val n1 = notifications.save(NotificationFixtures.notification(idempotencyKey = "k1", refId = "1")).id!!
        val n2 = notifications.save(NotificationFixtures.notification(idempotencyKey = "k2", refId = "2")).id!!

        // lease 만료
        val expired = outbox.save(NotificationFixtures.outbox(
            notificationId = n1,
            status = OutboxStatus.IN_PROGRESS,
            leaseOwner = "dead",
            leaseExpiresAt = now.minusSeconds(10),
        ))
        // lease 유효
        outbox.save(NotificationFixtures.outbox(
            notificationId = n2,
            status = OutboxStatus.IN_PROGRESS,
            leaseOwner = "live",
            leaseExpiresAt = now.plusSeconds(60),
        ))

        val r = sut.execute(ReclaimCommand(limit = 100))
        assertEquals(1, r.reclaimed)

        val expiredAfter = outbox.findById(expired.id!!)
        assertEquals(OutboxStatus.PENDING, expiredAfter?.status)
        assertEquals(null, expiredAfter?.leaseOwner)
        assertEquals(1, history.all().size)
    }

    @Test
    fun `만료된 row 가 없으면 0건`() {
        val r = sut.execute(ReclaimCommand(limit = 100))
        assertEquals(0, r.reclaimed)
    }
}
