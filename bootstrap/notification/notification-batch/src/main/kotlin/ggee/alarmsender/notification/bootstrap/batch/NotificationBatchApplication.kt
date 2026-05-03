package ggee.alarmsender.notification.bootstrap.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(
    scanBasePackages = [
        "ggee.alarmsender.notification.bootstrap.batch",
        "ggee.alarmsender.notification.data",
        "ggee.alarmsender.notification.usecase.reclaimnotification",
    ],
)
@EntityScan(basePackages = ["ggee.alarmsender.notification.data"])
@EnableJpaRepositories(basePackages = ["ggee.alarmsender.notification.data"])
@EnableScheduling
class NotificationBatchApplication

fun main(args: Array<String>) {
    runApplication<NotificationBatchApplication>(*args)
}
