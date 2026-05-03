package ggee.alarmsender.notification.usecase.getnotification

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.exception.NotificationAccessDeniedException
import ggee.alarmsender.notification.domain.exception.NotificationNotFoundException

interface GetNotificationUseCase {
    /**
     * 단건 조회.
     *
     * @throws NotificationNotFoundException 알림이 존재하지 않을 때
     * @throws NotificationAccessDeniedException 본인이 아닌 사용자가 조회할 때
     */
    fun execute(query: GetNotificationQuery): Notification
}

data class GetNotificationQuery(
    val notificationId: Long,
    val requesterId: String,
)
