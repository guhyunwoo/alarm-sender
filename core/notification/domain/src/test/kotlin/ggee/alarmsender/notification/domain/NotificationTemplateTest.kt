package ggee.alarmsender.notification.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class NotificationTemplateTest {

    @Test
    fun `payload 값을 placeholder 에 치환해 제목과 본문을 렌더링한다`() {
        val template = NotificationTemplate(
            type = NotificationType.ENROLL_COMPLETED,
            channel = NotificationChannel.EMAIL,
            subjectTemplate = "수강 신청 완료: {{courseName}}",
            bodyTemplate = "{{recipientName}}님, {{courseName}} 신청이 완료되었습니다.",
            updatedAt = Instant.parse("2026-05-03T10:00:00Z"),
        )

        val rendered = template.render(
            mapOf("recipientName" to "홍길동", "courseName" to "Kotlin 입문"),
        )

        assertEquals("수강 신청 완료: Kotlin 입문", rendered.subject)
        assertEquals("홍길동님, Kotlin 입문 신청이 완료되었습니다.", rendered.body)
    }
}
