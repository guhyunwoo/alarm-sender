package ggee.alarmsender.notification.domain

import java.time.Instant

data class NotificationTemplate(
    val id: Long? = null,
    val type: NotificationType,
    val channel: NotificationChannel,
    val subjectTemplate: String,
    val bodyTemplate: String,
    val updatedAt: Instant,
) {
    init {
        require(subjectTemplate.isNotBlank()) { "subjectTemplate 은 비어 있을 수 없다" }
        require(bodyTemplate.isNotBlank()) { "bodyTemplate 은 비어 있을 수 없다" }
    }

    fun render(payload: Map<String, Any?>): RenderedNotification =
        RenderedNotification(
            subject = renderText(subjectTemplate, payload),
            body = renderText(bodyTemplate, payload),
        )

    private fun renderText(template: String, payload: Map<String, Any?>): String =
        PLACEHOLDER.replace(template) { match ->
            val key = match.groupValues[1]
            payload[key]?.toString().orEmpty()
        }

    fun requireId(): Long = id ?: error("아직 영속화되지 않은 NotificationTemplate")

    companion object {
        private val PLACEHOLDER = Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*}}""")
    }
}

data class RenderedNotification(
    val subject: String,
    val body: String,
)
