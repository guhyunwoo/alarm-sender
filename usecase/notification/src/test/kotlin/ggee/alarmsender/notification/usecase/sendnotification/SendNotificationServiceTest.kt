package ggee.alarmsender.notification.usecase.sendnotification

import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.domain.NotificationType
import ggee.alarmsender.notification.domain.OutboxStatus
import ggee.alarmsender.notification.teststub.InMemoryNotificationHistoryRepository
import ggee.alarmsender.notification.teststub.InMemoryNotificationOutboxRepository
import ggee.alarmsender.notification.teststub.InMemoryNotificationRepository
import ggee.alarmsender.notification.usecase.sendnotification.SendNotificationCommand
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SendNotificationServiceTest {

    private val now = Instant.parse("2026-05-02T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val notifications = InMemoryNotificationRepository()
    private val outbox = InMemoryNotificationOutboxRepository()
    private val history = InMemoryNotificationHistoryRepository()

    private val sut = SendNotificationService(notifications, outbox, history, clock)

    private fun newCommand(
        recipientId: String = "user-1",
        idempotencyKey: String? = "idem-1",
        refId: String? = "100",
    ) = SendNotificationCommand(
        recipientId = recipientId,
        type = NotificationType.ENROLL_COMPLETED,
        channel = NotificationChannel.EMAIL,
        payload = mapOf("title" to "수강 신청 완료"),
        idempotencyKey = idempotencyKey,
        refType = "ENROLLMENT",
        refId = refId,
    )

    @Test
    fun `신규 요청 — Notification PENDING 적재 + Outbox 1건 + history CREATED 1건`() {
        val result = sut.execute(newCommand())

        assertFalse(result.deduplicated)
        assertNotNull(result.notification.id)
        assertEquals(NotificationStatus.PENDING, result.notification.status)

        assertEquals(1, notifications.all().size)
        assertEquals(1, outbox.all().size)
        assertEquals(OutboxStatus.PENDING, outbox.all().single().status)
        assertEquals(1, history.all().size)
    }

    @Test
    fun `같은 idempotencyKey 두 번 호출 — 두 번째는 기존 알림 그대로 반환 (deduplicated=true)`() {
        val first = sut.execute(newCommand(idempotencyKey = "dup"))
        val second = sut.execute(newCommand(idempotencyKey = "dup"))

        assertFalse(first.deduplicated)
        assertTrue(second.deduplicated)
        assertEquals(first.notification.id, second.notification.id)

        // outbox 도 1건만 — 두 번째 호출에서 추가 row 적재 안 됨
        assertEquals(1, outbox.all().size)
    }

    @Test
    fun `idempotencyKey 가 다르더라도 자연 키가 같으면 기존 알림 반환`() {
        val first = sut.execute(newCommand(idempotencyKey = "k1", refId = "200"))
        val second = sut.execute(newCommand(idempotencyKey = "k2", refId = "200"))

        assertEquals(first.notification.id, second.notification.id)
        assertTrue(second.deduplicated)
    }

    @Test
    fun `idempotencyKey null 이고 자연 키 ref 도 null 이면 매번 새 알림 적재`() {
        val first = sut.execute(newCommand(idempotencyKey = null, refId = null).copy(refType = null))
        val second = sut.execute(newCommand(idempotencyKey = null, refId = null).copy(refType = null))

        assertFalse(first.deduplicated)
        assertFalse(second.deduplicated)
        assertEquals(2, notifications.all().size)
    }
}
