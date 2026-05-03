package ggee.alarmsender.notification.bootstrap.api

import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationType
import ggee.alarmsender.notification.domain.RequesterRole
import ggee.alarmsender.notification.usecase.managenotificationtemplate.GetNotificationTemplateQuery
import ggee.alarmsender.notification.usecase.managenotificationtemplate.GetNotificationTemplateUseCase
import ggee.alarmsender.notification.usecase.managenotificationtemplate.ListNotificationTemplatesUseCase
import ggee.alarmsender.notification.usecase.managenotificationtemplate.UpdateNotificationTemplateCommand
import ggee.alarmsender.notification.usecase.managenotificationtemplate.UpdateNotificationTemplateUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notification-templates")
class NotificationTemplateController(
    private val listUseCase: ListNotificationTemplatesUseCase,
    private val getUseCase: GetNotificationTemplateUseCase,
    private val updateUseCase: UpdateNotificationTemplateUseCase,
) {

    @GetMapping
    fun list(): List<NotificationTemplateResponse> =
        listUseCase.execute().map(NotificationTemplateResponse::from)

    @GetMapping("/{type}/{channel}")
    fun get(
        @PathVariable type: NotificationType,
        @PathVariable channel: NotificationChannel,
    ): NotificationTemplateResponse =
        NotificationTemplateResponse.from(getUseCase.execute(GetNotificationTemplateQuery(type, channel)))

    @PutMapping("/{type}/{channel}")
    fun update(
        @RequestHeader("X-User-Id") requesterId: String,
        @RequestHeader(value = "X-User-Role", defaultValue = "USER") requesterRole: String,
        @PathVariable type: NotificationType,
        @PathVariable channel: NotificationChannel,
        @Valid @RequestBody request: UpdateNotificationTemplateRequest,
    ): NotificationTemplateResponse {
        val role = RequesterRole.parse(requesterRole)
        return NotificationTemplateResponse.from(
            updateUseCase.execute(
                UpdateNotificationTemplateCommand(
                    type = type,
                    channel = channel,
                    subjectTemplate = request.subjectTemplate,
                    bodyTemplate = request.bodyTemplate,
                    requesterId = requesterId,
                    requesterRole = role,
                ),
            ),
        )
    }
}
