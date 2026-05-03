package ggee.alarmsender.notification.usecase.managenotificationtemplate

import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationTemplate
import ggee.alarmsender.notification.domain.NotificationType
import ggee.alarmsender.notification.domain.RequesterRole

interface ListNotificationTemplatesUseCase {
    fun execute(): List<NotificationTemplate>
}

interface GetNotificationTemplateUseCase {
    fun execute(query: GetNotificationTemplateQuery): NotificationTemplate
}

interface UpdateNotificationTemplateUseCase {
    fun execute(command: UpdateNotificationTemplateCommand): NotificationTemplate
}

data class GetNotificationTemplateQuery(
    val type: NotificationType,
    val channel: NotificationChannel,
)

data class UpdateNotificationTemplateCommand(
    val type: NotificationType,
    val channel: NotificationChannel,
    val subjectTemplate: String,
    val bodyTemplate: String,
    val requesterId: String,
    val requesterRole: RequesterRole,
)
