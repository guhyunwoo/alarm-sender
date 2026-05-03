package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "notification_template",
    uniqueConstraints = [UniqueConstraint(name = "uk_notification_template_type_channel", columnNames = ["type", "channel"])],
)
class NotificationTemplateEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: NotificationType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var channel: NotificationChannel,

    @Column(name = "subject_template", nullable = false, columnDefinition = "TEXT")
    var subjectTemplate: String,

    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    var bodyTemplate: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
