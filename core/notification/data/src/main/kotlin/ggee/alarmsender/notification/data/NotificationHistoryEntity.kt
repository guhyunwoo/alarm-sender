package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.NotificationStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "notification_history")
class NotificationHistoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "notification_id", nullable = false, updatable = false)
    var notificationId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    var fromStatus: NotificationStatus? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    var toStatus: NotificationStatus,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var reason: HistoryReason,

    @Column(columnDefinition = "TEXT")
    var detail: String? = null,

    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: Instant,
)
