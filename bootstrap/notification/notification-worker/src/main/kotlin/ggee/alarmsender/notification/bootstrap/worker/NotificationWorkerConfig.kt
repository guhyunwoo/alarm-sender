package ggee.alarmsender.notification.bootstrap.worker

import ggee.alarmsender.notification.domain.ExponentialBackoffRetryPolicy
import ggee.alarmsender.notification.domain.RetryPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.util.UUID

@ConfigurationProperties(prefix = "notification.worker")
data class WorkerProperties(
    val pollIntervalMs: Long = 1_000,
    val batchSize: Int = 20,
    val leaseDurationSeconds: Long = 30,
    val maxAttempts: Int = 5,
) {
    val leaseDuration: Duration get() = Duration.ofSeconds(leaseDurationSeconds)
    val workerId: String = "worker-${UUID.randomUUID().toString().take(8)}"
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
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager)
}
