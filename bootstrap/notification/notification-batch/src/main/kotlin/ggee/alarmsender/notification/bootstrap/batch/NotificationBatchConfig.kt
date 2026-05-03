package ggee.alarmsender.notification.bootstrap.batch

import ggee.alarmsender.notification.domain.NotificationOutboxRepository
import ggee.alarmsender.notification.usecase.dispatchnotification.DbPollingOutboxPublisher
import ggee.alarmsender.notification.usecase.dispatchnotification.OutboxPublisher
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@ConfigurationProperties(prefix = "notification.batch")
data class BatchProperties(
    val reclaimIntervalMs: Long = 5_000,
    val reclaimLimit: Int = 50,
)

@Configuration
@EnableConfigurationProperties(BatchProperties::class)
class NotificationBatchConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun outboxPublisher(repository: NotificationOutboxRepository): OutboxPublisher =
        DbPollingOutboxPublisher(repository)
}
