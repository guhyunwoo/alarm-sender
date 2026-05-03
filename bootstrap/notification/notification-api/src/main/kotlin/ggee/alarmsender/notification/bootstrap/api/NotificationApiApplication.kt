package ggee.alarmsender.notification.bootstrap.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(
    scanBasePackages = [
        "ggee.alarmsender.notification.bootstrap.api",
        "ggee.alarmsender.notification.data",
        "ggee.alarmsender.notification.platform.email",
        "ggee.alarmsender.notification.usecase.getnotification",
        "ggee.alarmsender.notification.usecase.listnotifications",
        "ggee.alarmsender.notification.usecase.managenotificationtemplate",
        "ggee.alarmsender.notification.usecase.readnotification",
        "ggee.alarmsender.notification.usecase.retrynotification",
        "ggee.alarmsender.notification.usecase.sendnotification",
    ],
)
@EntityScan(basePackages = ["ggee.alarmsender.notification.data"])
@EnableJpaRepositories(basePackages = ["ggee.alarmsender.notification.data"])
class NotificationApiApplication

fun main(args: Array<String>) {
    runApplication<NotificationApiApplication>(*args)
}
