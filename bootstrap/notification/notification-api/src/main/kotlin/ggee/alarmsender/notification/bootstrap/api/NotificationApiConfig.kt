package ggee.alarmsender.notification.bootstrap.api

import ggee.alarmsender.notification.domain.ExponentialBackoffRetryPolicy
import ggee.alarmsender.notification.domain.RetryPolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
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
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager)
}
