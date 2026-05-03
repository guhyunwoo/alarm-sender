package ggee.alarmsender.notification.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class NotificationTest {

    private val now = Instant.parse("2026-05-02T10:00:00Z")

    private fun newNotification(): Notification = Notification.create(
        recipientId = "user-1",
        type = NotificationType.ENROLL_COMPLETED,
        channel = NotificationChannel.EMAIL,
        payload = mapOf("course" to "Spring Boot 입문"),
        idempotencyKey = "idem-1",
        refType = "ENROLLMENT",
        refId = "100",
        now = now,
    )

    @Test
    fun `생성 직후 PENDING 상태이며 readAt sentAt 은 null 이다`() {
        val n = newNotification()
        assertEquals(NotificationStatus.PENDING, n.status)
        assertEquals(null, n.readAt)
        assertEquals(null, n.sentAt)
        assertEquals(now, n.createdAt)
    }

    @Test
    fun `markAsRead 는 readAt 이 null 일 때만 set 한다`() {
        val n = newNotification()
        val later = now.plusSeconds(10)
        val readOnce = n.markAsRead(later)
        assertEquals(later, readOnce.readAt)

        val laterAgain = later.plusSeconds(10)
        val readTwice = readOnce.markAsRead(laterAgain)
        assertEquals(later, readTwice.readAt) // 멱등 — 첫 시각 유지
    }

    @Test
    fun `markAsRead 가 같은 객체 식별자를 반환할 수 있다 (이미 읽힘)`() {
        val n = newNotification().markAsRead(now.plusSeconds(1))
        val again = n.markAsRead(now.plusSeconds(2))
        assertSame(n, again)
    }

    @Test
    fun `markSent 는 PENDING 에서만 가능하다`() {
        val sent = newNotification().markSent(now.plusSeconds(5))
        assertEquals(NotificationStatus.SENT, sent.status)
        assertNotNull(sent.sentAt)

        assertThrows<IllegalArgumentException> {
            newNotification().copy(status = NotificationStatus.SENT).markSent(now)
        }
        assertThrows<IllegalArgumentException> {
            newNotification().copy(status = NotificationStatus.DEAD_LETTER).markSent(now)
        }
    }

    @Test
    fun `DEAD_LETTER 만 수동 재시도로 PENDING 복귀 가능`() {
        val dead = newNotification().copy(status = NotificationStatus.DEAD_LETTER)
        val retried = dead.resetForManualRetry()
        assertEquals(NotificationStatus.PENDING, retried.status)

        assertThrows<IllegalArgumentException> {
            newNotification().resetForManualRetry()
        }
    }
}
