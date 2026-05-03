package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationJpaRepository : JpaRepository<NotificationEntity, Long> {

    fun findByIdempotencyKey(idempotencyKey: String): NotificationEntity?

    fun findFirstByRecipientIdAndTypeAndRefTypeAndRefId(
        recipientId: String,
        type: NotificationType,
        refType: String?,
        refId: String?,
    ): NotificationEntity?

    @Query(
        value = """
            SELECT * FROM notification
            WHERE recipient_id = :recipientId
              AND (:unreadOnly = false OR read_at IS NULL)
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true,
    )
    fun findByRecipientPage(
        @Param("recipientId") recipientId: String,
        @Param("unreadOnly") unreadOnly: Boolean,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int,
    ): List<NotificationEntity>
}
