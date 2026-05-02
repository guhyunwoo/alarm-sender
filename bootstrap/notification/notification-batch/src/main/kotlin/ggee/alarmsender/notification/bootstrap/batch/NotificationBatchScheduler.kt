package ggee.alarmsender.notification.bootstrap.batch

import ggee.alarmsender.notification.usecase.reclaimnotification.port.ReclaimCommand
import ggee.alarmsender.notification.usecase.reclaimnotification.port.ReclaimNotificationUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 스턱 복구 배치. lease 만료된 IN_PROGRESS row 를 PENDING 으로 복귀시킨다.
 * 워커가 죽거나 네트워크/디스크 문제로 lease 만료까지 응답하지 못한 row 가 대상.
 */
@Component
class NotificationBatchScheduler(
    private val reclaimUseCase: ReclaimNotificationUseCase,
    private val batchProperties: BatchProperties,
) {

    private val log = LoggerFactory.getLogger(NotificationBatchScheduler::class.java)

    @Scheduled(fixedDelayString = "\${notification.batch.reclaim-interval-ms:5000}")
    fun reclaimExpired() {
        val result = reclaimUseCase.execute(ReclaimCommand(limit = batchProperties.reclaimLimit))
        if (result.reclaimed > 0) {
            log.warn("reclaimed {} stuck outbox row(s)", result.reclaimed)
        }
    }
}
