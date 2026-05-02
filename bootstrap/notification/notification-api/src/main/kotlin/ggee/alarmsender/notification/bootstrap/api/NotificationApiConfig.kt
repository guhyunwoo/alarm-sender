package ggee.alarmsender.notification.bootstrap.api

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class NotificationApiConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
