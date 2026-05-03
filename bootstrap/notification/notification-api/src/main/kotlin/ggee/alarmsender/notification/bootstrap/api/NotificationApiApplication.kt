package ggee.alarmsender.notification.bootstrap.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["ggee.alarmsender"])
@EntityScan(basePackages = ["ggee.alarmsender.notification.data"])
@EnableJpaRepositories(basePackages = ["ggee.alarmsender.notification.data"])
class NotificationApiApplication

fun main(args: Array<String>) {
    runApplication<NotificationApiApplication>(*args)
}
