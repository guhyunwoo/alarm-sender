package ggee.alarmsender.notification.domain.exception

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BusinessBaseExceptionTest {

    @Test
    fun `NotificationNotFoundException 은 BusinessBaseException 을 상속하고 code 와 message 를 노출한다`() {
        val e = NotificationNotFoundException(notificationId = 42)

        assertTrue(e is BusinessBaseException)
        assertEquals("NOTIFICATION_NOT_FOUND", e.code)
        assertEquals(42, e.notificationId)
        assertTrue(e.message.contains("42"))
    }

    @Test
    fun `NotificationAccessDeniedException 은 BusinessBaseException 을 상속하고 컨텍스트를 노출한다`() {
        val e = NotificationAccessDeniedException(notificationId = 100, requesterId = "u-99")

        assertTrue(e is BusinessBaseException)
        assertEquals("NOTIFICATION_ACCESS_DENIED", e.code)
        assertEquals(100, e.notificationId)
        assertEquals("u-99", e.requesterId)
    }
}
