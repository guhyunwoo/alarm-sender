package ggee.alarmsender.notification.usecase.getnotification

import ggee.alarmsender.notification.testfixture.NotificationFixtures
import ggee.alarmsender.notification.teststub.InMemoryNotificationRepository
import ggee.alarmsender.notification.usecase.getnotification.port.GetNotificationQuery
import ggee.alarmsender.notification.usecase.getnotification.port.NotificationAccessDeniedException
import ggee.alarmsender.notification.usecase.getnotification.port.NotificationNotFoundException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class GetNotificationServiceTest {

    private val repo = InMemoryNotificationRepository()
    private val sut = GetNotificationService(repo)

    @Test
    fun `본인 알림 조회 성공`() {
        val saved = repo.save(NotificationFixtures.notification(idempotencyKey = "k1", recipientId = "u1"))
        val r = sut.execute(GetNotificationQuery(saved.id!!, "u1"))
        assertEquals(saved.id, r.id)
    }

    @Test
    fun `다른 사용자 조회 시 access denied`() {
        val saved = repo.save(NotificationFixtures.notification(idempotencyKey = "k1", recipientId = "u1"))
        assertThrows<NotificationAccessDeniedException> {
            sut.execute(GetNotificationQuery(saved.id!!, "u2"))
        }
    }

    @Test
    fun `없는 id 는 NotFound`() {
        assertThrows<NotificationNotFoundException> {
            sut.execute(GetNotificationQuery(999, "u1"))
        }
    }
}
