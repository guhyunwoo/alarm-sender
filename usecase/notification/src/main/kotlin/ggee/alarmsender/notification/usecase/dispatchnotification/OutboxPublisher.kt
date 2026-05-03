package ggee.alarmsender.notification.usecase.dispatchnotification

import ggee.alarmsender.notification.domain.NotificationOutbox
import java.time.Duration
import java.time.Instant

/**
 * Outbox 큐의 publish 추상화. 워커/배치/수동 재시도가 outbox row 와 상호작용할 때 사용.
 *
 * 현재 구현은 DB 폴링 ([DbPollingOutboxPublisher]).
 * Kafka / SQS 도입 시 본 인터페이스를 구현하는 새 어댑터(예: KafkaOutboxPublisher) 로 교체하면
 * 도메인·유즈케이스 코드는 변경되지 않는다.
 *
 * Send 유즈케이스의 outbox INSERT 는 본 인터페이스가 아니라 [NotificationOutboxRepository] 를 직접 사용한다.
 * 이는 Outbox 패턴의 본질(트랜잭션 안에서 비즈니스 + 메시지 의도를 함께 영속화)과 맞물려,
 * publish 자체는 다른 컴포넌트가 책임짐을 명시적으로 분리하기 위함이다.
 */
interface OutboxPublisher {

    /**
     * 처리 가능한 PENDING row 를 [batchSize] 만큼 잠그고 IN_PROGRESS 로 전이시킨다.
     * 다중 워커가 같은 row 를 두 번 잡지 않도록 구현이 보장한다.
     */
    fun claim(workerId: String, now: Instant, leaseDuration: Duration, batchSize: Int): List<NotificationOutbox>

    /**
     * lease 가 만료된 IN_PROGRESS row 를 조회한다 (배치 reclaim 대상).
     */
    fun findExpired(now: Instant, limit: Int): List<NotificationOutbox>

    /**
     * 단일 outbox row 를 변경된 도메인 상태로 영속화한다.
     * (markSucceeded / markFailedTransient / reclaim / manualRetry 후 호출)
     */
    fun update(outbox: NotificationOutbox): NotificationOutbox

    /**
     * notification id 로 outbox row 를 찾는다 (수동 재시도 / 운영 도구 등이 사용).
     */
    fun findByNotificationId(notificationId: Long): NotificationOutbox?
}
