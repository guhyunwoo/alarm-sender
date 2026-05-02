package ggee.alarmsender.notification.bootstrap.worker

import ggee.alarmsender.notification.usecase.dispatchnotification.port.DispatchNotificationCommand
import ggee.alarmsender.notification.usecase.dispatchnotification.port.DispatchNotificationUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 워커 폴링 루프. fixedDelayString 으로 application.yaml 의 notification.worker.poll-interval-ms 를 참조한다.
 *
 * 단일 워커 실행 환경이든 다중 워커 실행이든 SKIP LOCKED 가 동시 처리 안전성을 책임지므로
 * 별도 분산 락은 사용하지 않는다.
 */
@Component
class NotificationWorkerScheduler(
    private val dispatchUseCase: DispatchNotificationUseCase,
    private val workerProperties: WorkerProperties,
) {

    private val log = LoggerFactory.getLogger(NotificationWorkerScheduler::class.java)

    @Scheduled(fixedDelayString = "\${notification.worker.poll-interval-ms:1000}")
    fun pollAndDispatch() {
        val result = dispatchUseCase.execute(
            DispatchNotificationCommand(
                workerId = workerProperties.workerId,
                batchSize = workerProperties.batchSize,
                leaseDuration = workerProperties.leaseDuration,
            ),
        )
        if (result.claimed > 0) {
            log.info(
                "dispatch worker={} claimed={} succeeded={} failed={} dead={}",
                workerProperties.workerId,
                result.claimed,
                result.succeeded,
                result.failed,
                result.deadLettered,
            )
        }
    }
}
