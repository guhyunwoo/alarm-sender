package ggee.alarmsender.notification.usecase.readnotification

import ggee.alarmsender.notification.domain.exception.NotificationAccessDeniedException
import ggee.alarmsender.notification.domain.exception.NotificationNotFoundException
import ggee.alarmsender.notification.testfixture.NotificationFixtures
import ggee.alarmsender.notification.teststub.InMemoryNotificationHistoryRepository
import ggee.alarmsender.notification.teststub.InMemoryNotificationRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReadNotificationServiceTest {

    private val now = Instant.parse("2026-05-02T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val notifications = InMemoryNotificationRepository()
    private val history = InMemoryNotificationHistoryRepository()

    private val sut = ReadNotificationService(notifications, history, clock)

    @Test
    fun `첫 read — newlyRead=true, readAt set, history READ 1건`() {
        val saved = notifications.save(NotificationFixtures.notification(idempotencyKey = "k1", recipientId = "u1"))

        val r = sut.execute(ReadNotificationCommand(saved.id!!, "u1"))

        assertTrue(r.newlyRead)
        assertNotNull(r.notification.readAt)
        assertEquals(1, history.all().size)
    }

    @Test
    fun `같은 사용자 다시 read — newlyRead=false, history 추가 적재 없음 (멀티 디바이스 멱등)`() {
        val saved = notifications.save(NotificationFixtures.notification(idempotencyKey = "k1", recipientId = "u1"))

        sut.execute(ReadNotificationCommand(saved.id!!, "u1"))
        val r2 = sut.execute(ReadNotificationCommand(saved.id!!, "u1"))

        assertFalse(r2.newlyRead)
        // history 는 첫 read 때 1건만 적재
        assertEquals(1, history.all().size)
    }

    @Test
    fun `본인 외 사용자가 시도하면 접근 거부 예외 발생`() {
        val saved = notifications.save(NotificationFixtures.notification(idempotencyKey = "k1", recipientId = "u1"))
        assertThrows<NotificationAccessDeniedException> {
            sut.execute(ReadNotificationCommand(saved.id!!, "u2"))
        }
    }

    @Test
    fun `존재하지 않는 알림 read 시 알림 미발견 예외 발생`() {
        assertThrows<NotificationNotFoundException> {
            sut.execute(ReadNotificationCommand(notificationId = 999, requesterId = "u1"))
        }
    }
}
