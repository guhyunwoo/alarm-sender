package ggee.alarmsender.notification.usecase.managenotificationtemplate

import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationType
import ggee.alarmsender.notification.domain.RequesterRole
import ggee.alarmsender.notification.domain.exception.OperatorOnlyException
import ggee.alarmsender.notification.testfixture.NotificationFixtures
import ggee.alarmsender.notification.teststub.InMemoryNotificationTemplateRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class ManageNotificationTemplateServiceTest {

    private val now = Instant.parse("2026-05-03T10:00:00Z")
    private val repository = InMemoryNotificationTemplateRepository()
    private val sut = ManageNotificationTemplateService(repository, Clock.fixed(now, ZoneOffset.UTC))

    @BeforeEach
    fun setUp() {
        repository.clear()
    }

    @Test
    fun `OPERATOR 는 타입 채널별 템플릿을 수정할 수 있다`() {
        repository.save(NotificationFixtures.template())

        val result = sut.execute(
            UpdateNotificationTemplateCommand(
                type = NotificationType.ENROLL_COMPLETED,
                channel = NotificationChannel.EMAIL,
                subjectTemplate = "변경 {{courseName}}",
                bodyTemplate = "{{courseName}} 변경 완료",
                requesterId = "ops-1",
                requesterRole = RequesterRole.OPERATOR,
            ),
        )

        assertEquals("변경 {{courseName}}", result.subjectTemplate)
        assertEquals(now, result.updatedAt)
        assertEquals(1, repository.findAll().size)
    }

    @Test
    fun `USER 는 템플릿을 수정할 수 없다`() {
        assertThrows<OperatorOnlyException> {
            sut.execute(
                UpdateNotificationTemplateCommand(
                    type = NotificationType.ENROLL_COMPLETED,
                    channel = NotificationChannel.EMAIL,
                    subjectTemplate = "제목",
                    bodyTemplate = "본문",
                    requesterId = "user-1",
                    requesterRole = RequesterRole.USER,
                ),
            )
        }
    }
}
