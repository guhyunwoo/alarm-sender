package ggee.alarmsender.notification.bootstrap.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(
    scanBasePackages = [
        "ggee.alarmsender.notification.bootstrap.worker",
        "ggee.alarmsender.notification.data",
        "ggee.alarmsender.notification.platform.email",
        "ggee.alarmsender.notification.usecase.dispatchnotification",
    ],
)
@EntityScan(basePackages = ["ggee.alarmsender.notification.data"])
@EnableJpaRepositories(basePackages = ["ggee.alarmsender.notification.data"])
@EnableScheduling
class NotificationWorkerApplication

fun main(args: Array<String>) {
    runApplication<NotificationWorkerApplication>(*args)
}
