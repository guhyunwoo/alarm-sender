package ggee.alarmsender.notification.bootstrap.worker

import ggee.alarmsender.notification.domain.ExponentialBackoffRetryPolicy
import ggee.alarmsender.notification.domain.NotificationOutboxRepository
import ggee.alarmsender.notification.domain.RetryPolicy
import ggee.alarmsender.notification.usecase.dispatchnotification.DbPollingOutboxPublisher
import ggee.alarmsender.notification.usecase.dispatchnotification.OutboxPublisher
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.util.UUID

@ConfigurationProperties(prefix = "notification.worker")
data class WorkerProperties(
    val pollIntervalMs: Long = 1_000,
    val batchSize: Int = 20,
    val leaseDurationSeconds: Long = 30,
    val maxAttempts: Int = 5,
    /**
     * 명시적으로 설정하지 않으면 hostname-pid-shortuuid 형태로 자동 생성.
     * K8s 환경에서는 `notification.worker.id=${HOSTNAME}` 으로 pod 이름 그대로 사용 권장.
     */
    val id: String? = null,
) {
    val leaseDuration: Duration get() = Duration.ofSeconds(leaseDurationSeconds)

    val workerId: String by lazy {
        id ?: defaultWorkerId()
    }

    private fun defaultWorkerId(): String {
        val host = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("unknown")
        val pid = ManagementFactory.getRuntimeMXBean().pid
        val short = UUID.randomUUID().toString().take(8)
        return "$host-$pid-$short"
    }
}

@Configuration
@EnableConfigurationProperties(WorkerProperties::class)
class NotificationWorkerConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun retryPolicy(properties: WorkerProperties): RetryPolicy =
        ExponentialBackoffRetryPolicy(maxAttempts = properties.maxAttempts)

    @Bean
    fun outboxPublisher(repository: NotificationOutboxRepository): OutboxPublisher =
        DbPollingOutboxPublisher(repository)

    /**
     * 워커의 row 단위 결과 반영 트랜잭션. PROPAGATION_REQUIRES_NEW 명시 — 누군가 outer 메서드에
     * @Transactional 을 추가하더라도 row 격리 의미가 깨지지 않도록.
     */
    @Bean
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
}
