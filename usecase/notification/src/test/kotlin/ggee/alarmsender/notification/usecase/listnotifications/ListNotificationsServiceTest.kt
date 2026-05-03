package ggee.alarmsender.notification.usecase.listnotifications

import ggee.alarmsender.notification.domain.exception.RecipientForbiddenException
import ggee.alarmsender.notification.testfixture.NotificationFixtures
import ggee.alarmsender.notification.teststub.InMemoryNotificationRepository
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

    private fun query(
        recipientId: String,
        requesterId: String = recipientId,
        unreadOnly: Boolean = false,
        limit: Int = 10,
        offset: Int = 0,
    ) = ListNotificationsQuery(recipientId, requesterId, unreadOnly, limit, offset)

    @Test
    fun `recipient 별로만 반환하고 unreadOnly 필터 적용`() {
        val now = Instant.parse("2026-05-02T10:00:00Z")
        notifications.save(NotificationFixtures.notification(idempotencyKey = "k1", recipientId = "u1", refId = "1", createdAt = now))
        notifications.save(NotificationFixtures.notification(idempotencyKey = "k2", recipientId = "u1", refId = "2", createdAt = now.plusSeconds(1), readAt = now.plusSeconds(10)))
        notifications.save(NotificationFixtures.notification(idempotencyKey = "k3", recipientId = "u2", refId = "3"))

        val all = sut.execute(query("u1"))
        assertEquals(2, all.size)

        val unread = sut.execute(query("u1", unreadOnly = true))
        assertEquals(1, unread.size)
    }

    @Test
    fun `본인 외 사용자 목록 조회 시 접근 거부 예외 발생`() {
        assertThrows<RecipientForbiddenException> {
            sut.execute(query(recipientId = "u1", requesterId = "u2"))
        }
    }

    @Test
    fun `잘못된 limit offset 은 거부`() {
        assertThrows<IllegalArgumentException> { query("u1", limit = 0) }
        assertThrows<IllegalArgumentException> { query("u1", offset = -1) }
    }
}
