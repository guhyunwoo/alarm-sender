package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationHistory
import ggee.alarmsender.notification.domain.NotificationHistoryRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface NotificationHistoryJpaRepository : JpaRepository<NotificationHistoryEntity, Long> {
    fun findByNotificationIdOrderByOccurredAtAsc(notificationId: Long): List<NotificationHistoryEntity>
}

@Repository
class NotificationHistoryRepositoryImpl(
    private val jpa: NotificationHistoryJpaRepository,
) : NotificationHistoryRepository {

    override fun append(history: NotificationHistory): NotificationHistory {
        val entity = NotificationHistoryEntity(
            notificationId = history.notificationId,
            fromStatus = history.fromStatus,
            toStatus = history.toStatus,
            reason = history.reason,
            detail = history.detail,
            occurredAt = history.occurredAt,
        )
        val saved = jpa.save(entity)
        return history.copy(id = saved.id)
    }

    override fun findByNotificationId(notificationId: Long): List<NotificationHistory> =
        jpa.findByNotificationIdOrderByOccurredAtAsc(notificationId).map {
            NotificationHistory(
                id = it.id,
                notificationId = it.notificationId,
                fromStatus = it.fromStatus,
                toStatus = it.toStatus,
                reason = it.reason,
                detail = it.detail,
                occurredAt = it.occurredAt,
            )
        }
}
