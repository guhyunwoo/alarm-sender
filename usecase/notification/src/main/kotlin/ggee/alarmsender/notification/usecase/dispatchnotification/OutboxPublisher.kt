package ggee.alarmsender.notification.usecase.dispatchnotification

import ggee.alarmsender.notification.domain.NotificationOutbox
import java.time.Duration
import java.time.Instant

/**
 * Outbox 큐 publish 추상화. 워커/배치/수동 재시도가 outbox row 와 상호작용할 때 쓴다.
 *
 * 현재 구현은 DB 폴링 ([DbPollingOutboxPublisher]).
 * Kafka / SQS 도입 시 이 인터페이스를 구현하는 어댑터(예: KafkaOutboxPublisher) 로 갈아끼우면
 * 도메인·유즈케이스 코드는 그대로 둘 수 있다.
 *
 * Send 유즈케이스의 outbox INSERT 는 이 인터페이스가 아니라 [NotificationOutboxRepository] 를 직접 호출한다.
 * Outbox 패턴 자체가 비즈니스 트랜잭션 안에서 메시지 의도를 함께 영속화하는 것이므로,
 * publish 책임은 별도 컴포넌트로 분리되어 있다.
 */
interface OutboxPublisher {

    /**
     * 처리 가능한 PENDING row 를 [batchSize] 만큼 잠그고 IN_PROGRESS 로 전이시킨다.
     * 다중 워커가 같은 row 를 두 번 잡지 않는 건 구현체 책임이다.
     */
    fun claim(workerId: String, now: Instant, leaseDuration: Duration, batchSize: Int): List<NotificationOutbox>

    /**
     * lease 만료된 IN_PROGRESS row 조회 (배치 reclaim 대상).
     */
    fun findExpired(now: Instant, limit: Int): List<NotificationOutbox>

    /**
     * outbox row 를 변경된 도메인 상태로 영속화.
     * (markSucceeded / markFailedTransient / reclaim / manualRetry 호출 뒤)
     */
    fun update(outbox: NotificationOutbox): NotificationOutbox

    /**
     * notification id 로 outbox row 찾기 (수동 재시도 / 운영 도구가 쓴다).
     */
    fun findByNotificationId(notificationId: Long): NotificationOutbox?
}
