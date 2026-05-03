package ggee.alarmsender.notification.usecase.managenotificationtemplate

import ggee.alarmsender.notification.domain.NotificationTemplate
import ggee.alarmsender.notification.domain.NotificationTemplateRepository
import ggee.alarmsender.notification.domain.RequesterRole
import ggee.alarmsender.notification.domain.exception.NotificationTemplateNotFoundException
import ggee.alarmsender.notification.domain.exception.OperatorOnlyException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class ManageNotificationTemplateService(
    private val repository: NotificationTemplateRepository,
    private val clock: Clock,
) : ListNotificationTemplatesUseCase,
    GetNotificationTemplateUseCase,
    UpdateNotificationTemplateUseCase {

    override fun execute(): List<NotificationTemplate> = repository.findAll()

    override fun execute(query: GetNotificationTemplateQuery): NotificationTemplate =
        repository.findByTypeAndChannel(query.type, query.channel)
            ?: throw NotificationTemplateNotFoundException(query.type, query.channel)

    override fun execute(command: UpdateNotificationTemplateCommand): NotificationTemplate {
        if (command.requesterRole != RequesterRole.OPERATOR) {
            throw OperatorOnlyException(operationName = "템플릿 수정", requesterId = command.requesterId)
        }
        val existing = repository.findByTypeAndChannel(command.type, command.channel)
        val now = Instant.now(clock)
        return repository.save(
            NotificationTemplate(
                id = existing?.id,
                type = command.type,
                channel = command.channel,
                subjectTemplate = command.subjectTemplate,
                bodyTemplate = command.bodyTemplate,
                updatedAt = now,
            ),
        )
    }
}
