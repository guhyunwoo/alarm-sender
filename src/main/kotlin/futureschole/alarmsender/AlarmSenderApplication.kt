package futureschole.alarmsender

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AlarmSenderApplication

fun main(args: Array<String>) {
    runApplication<AlarmSenderApplication>(*args)
}
