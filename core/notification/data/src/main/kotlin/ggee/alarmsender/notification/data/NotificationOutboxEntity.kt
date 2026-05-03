package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.OutboxStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

@Entity
@Table(name = "notification_outbox")
class NotificationOutboxEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "notification_id", nullable = false, unique = true)
    var notificationId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OutboxStatus,

    @Column(name = "available_at", nullable = false)
    var availableAt: Instant,

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "lease_owner")
    var leaseOwner: String? = null,

    @Column(name = "lease_expires_at")
    var leaseExpiresAt: Instant? = null,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,

    /**
     * 낙관적 락 버전. lease 만료로 reclaim 된 뒤 죽은 줄 알았던 워커가 살아나 stale write 를 시도하면
     * Hibernate 가 ObjectOptimisticLockingFailureException 을 던져서 덮어쓰기를 막는다.
     * (워커 try/catch 가 받아서 row 격리 처리한다)
     */
    @Version
    @Column(nullable = false)
    var version: Long = 0L,
)
