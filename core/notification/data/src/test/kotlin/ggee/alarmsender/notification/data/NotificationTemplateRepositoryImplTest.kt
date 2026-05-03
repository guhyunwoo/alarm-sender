package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationTemplateRepository
import ggee.alarmsender.notification.domain.NotificationType
import ggee.alarmsender.notification.testfixture.NotificationFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(classes = [DataLayerTestApp::class])
@Testcontainers
class NotificationTemplateRepositoryImplTest @Autowired constructor(
    private val sut: NotificationTemplateRepository,
    private val jdbcTemplate: JdbcTemplate,
) : PostgresIntegrationTest() {

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE notification_template RESTART IDENTITY CASCADE")
    }

    @Test
    fun `type 과 channel 로 템플릿을 저장하고 조회한다`() {
        sut.save(NotificationFixtures.template(subjectTemplate = "제목 {{courseName}}"))

        val found = sut.findByTypeAndChannel(NotificationType.ENROLL_COMPLETED, NotificationChannel.EMAIL)

        assertNotNull(found)
        assertEquals("제목 {{courseName}}", found.subjectTemplate)
    }
}
