package ggee.alarmsender.notification.domain.exception

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationExceptionsTest {

    @Test
    fun `알림 미발견 예외는 NoSuchElementException 을 상속하고 code 와 message 를 노출한다`() {
        val e = NotificationNotFoundException(notificationId = 42)

        assertTrue(e is NoSuchElementException)
        assertEquals("NOTIFICATION_NOT_FOUND", e.code)
        assertEquals(42, e.notificationId)
        assertTrue(e.message!!.contains("42"))
    }

    @Test
    fun `접근 거부 예외는 SecurityException 을 상속하고 컨텍스트를 노출한다`() {
        val e = NotificationAccessDeniedException(notificationId = 100, requesterId = "u-99")

        assertTrue(e is SecurityException)
        assertEquals("NOTIFICATION_ACCESS_DENIED", e.code)
        assertEquals(100, e.notificationId)
        assertEquals("u-99", e.requesterId)
    }
}
