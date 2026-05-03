package ggee.alarmsender.notification.usecase.dispatchnotification

import ggee.alarmsender.notification.domain.NotificationOutbox
import ggee.alarmsender.notification.domain.NotificationOutboxRepository
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * DB 폴링 기반 [OutboxPublisher] 구현. 기본 어댑터.
 *
 * 운영 전환 시 이 클래스를 KafkaOutboxPublisher / SqsOutboxPublisher 등으로 갈아끼우면 된다.
 * 도메인 객체와 유즈케이스 코드는 그대로.
 */
@Component
class DbPollingOutboxPublisher(
    private val repository: NotificationOutboxRepository,
) : OutboxPublisher {

    override fun claim(workerId: String, now: Instant, leaseDuration: Duration, batchSize: Int): List<NotificationOutbox> =
        repository.claimBatch(workerId, now, leaseDuration, batchSize)

    override fun findExpired(now: Instant, limit: Int): List<NotificationOutbox> =
        repository.findExpired(now, limit)

    override fun update(outbox: NotificationOutbox): NotificationOutbox =
        repository.update(outbox)

    override fun findByNotificationId(notificationId: Long): NotificationOutbox? =
        repository.findByNotificationId(notificationId)
}
