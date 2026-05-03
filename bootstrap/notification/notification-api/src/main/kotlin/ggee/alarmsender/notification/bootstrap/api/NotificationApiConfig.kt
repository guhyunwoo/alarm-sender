package ggee.alarmsender.notification.bootstrap.api

import ggee.alarmsender.notification.domain.ExponentialBackoffRetryPolicy
import ggee.alarmsender.notification.domain.NotificationOutboxRepository
import ggee.alarmsender.notification.domain.RetryPolicy
import ggee.alarmsender.notification.usecase.dispatchnotification.DbPollingOutboxPublisher
import ggee.alarmsender.notification.usecase.dispatchnotification.OutboxPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * use case 모듈을 단일 :usecase:notification 으로 통합하면서, API 부트스트랩도
 * dispatch / reclaim 서비스를 컴포넌트 스캔 대상으로 포함하게 됐다.
 * 따라서 그들이 의존하는 Clock / RetryPolicy / TransactionTemplate 빈을 함께 등록한다.
 * (API 가 직접 dispatch 를 호출하지는 않으며, 실제 트리거는 worker 부트스트랩의 스케줄러)
 */
@Configuration
class NotificationApiConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun retryPolicy(): RetryPolicy = ExponentialBackoffRetryPolicy()

    @Bean
    fun outboxPublisher(repository: NotificationOutboxRepository): OutboxPublisher =
        DbPollingOutboxPublisher(repository)

    /**
     * 항상 새 트랜잭션을 시작 (REQUIRES_NEW). API 가 직접 호출하지는 않지만,
     * use case 의 row-격리 의미를 보존하기 위해 worker 와 동일한 정책 사용.
     */
    @Bean
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
}
