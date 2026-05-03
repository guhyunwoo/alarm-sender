package ggee.alarmsender.notification.usecase.getnotification

import ggee.alarmsender.notification.domain.exception.NotificationAccessDeniedException
import ggee.alarmsender.notification.domain.exception.NotificationNotFoundException
import ggee.alarmsender.notification.testfixture.NotificationFixtures
import ggee.alarmsender.notification.teststub.InMemoryNotificationRepository
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
    fun `본인 외 사용자가 조회 시 접근 거부 예외 발생`() {
        val saved = repo.save(NotificationFixtures.notification(idempotencyKey = "k1", recipientId = "u1"))
        assertThrows<NotificationAccessDeniedException> {
            sut.execute(GetNotificationQuery(saved.id!!, "u2"))
        }
    }

    @Test
    fun `존재하지 않는 id 조회 시 알림 미발견 예외 발생`() {
        assertThrows<NotificationNotFoundException> {
            sut.execute(GetNotificationQuery(999, "u1"))
        }
    }
}
