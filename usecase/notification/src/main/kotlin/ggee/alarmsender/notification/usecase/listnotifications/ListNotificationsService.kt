package ggee.alarmsender.notification.usecase.listnotifications

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.exception.RecipientForbiddenException
import org.springframework.stereotype.Service

@Service
class ListNotificationsService(
    private val repository: NotificationRepository,
) : ListNotificationsUseCase {

    override fun execute(query: ListNotificationsQuery): List<Notification> {
        // 권한 검사는 use case 가 한다. 다른 진입점(GraphQL / gRPC 등)이 생겨도 같은 검사가 적용된다.
        if (query.recipientId != query.requesterId) {
            throw RecipientForbiddenException(targetRecipientId = query.recipientId, requesterId = query.requesterId)
        }
        return repository.findByRecipient(
            recipientId = query.recipientId,
            unreadOnly = query.unreadOnly,
            limit = query.limit,
            offset = query.offset,
        )
    }
}
