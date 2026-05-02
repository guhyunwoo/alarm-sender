package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.domain.NotificationType
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
@Table(name = "notification")
class NotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "recipient_id", nullable = false)
    var recipientId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: NotificationType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var channel: NotificationChannel,

    @Column(nullable = false, columnDefinition = "TEXT")
    var payload: String,

    @Column(name = "idempotency_key")
    var idempotencyKey: String? = null,

    @Column(name = "ref_type")
    var refType: String? = null,

    @Column(name = "ref_id")
    var refId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: NotificationStatus,

    @Column(name = "read_at")
    var readAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "sent_at")
    var sentAt: Instant? = null,
)
