package ggee.alarmsender.notification.usecase.listnotifications

import ggee.alarmsender.notification.testfixture.NotificationFixtures
import ggee.alarmsender.notification.teststub.InMemoryNotificationRepository
import ggee.alarmsender.notification.usecase.listnotifications.ListNotificationsQuery
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals

class ListNotificationsServiceTest {

    private val notifications = InMemoryNotificationRepository()
    private val sut = ListNotificationsService(notifications)

    @BeforeEach
    fun setUp() {
        notifications.clear()
    }

    @Test
    fun `recipient 별로만 반환하고 unreadOnly 필터 적용`() {
        val now = Instant.parse("2026-05-02T10:00:00Z")
        notifications.save(NotificationFixtures.notification(idempotencyKey = "k1", recipientId = "u1", refId = "1", createdAt = now))
        notifications.save(NotificationFixtures.notification(idempotencyKey = "k2", recipientId = "u1", refId = "2", createdAt = now.plusSeconds(1), readAt = now.plusSeconds(10)))
        notifications.save(NotificationFixtures.notification(idempotencyKey = "k3", recipientId = "u2", refId = "3"))

        val all = sut.execute(ListNotificationsQuery("u1", unreadOnly = false, limit = 10, offset = 0))
        assertEquals(2, all.size)

        val unread = sut.execute(ListNotificationsQuery("u1", unreadOnly = true, limit = 10, offset = 0))
        assertEquals(1, unread.size)
    }

    @Test
    fun `잘못된 limit offset 은 거부`() {
        assertThrows<IllegalArgumentException> { ListNotificationsQuery("u1", false, limit = 0, offset = 0) }
        assertThrows<IllegalArgumentException> { ListNotificationsQuery("u1", false, limit = 1, offset = -1) }
    }
}
