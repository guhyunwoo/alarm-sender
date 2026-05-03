package ggee.alarmsender.notification.data

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * 모든 통합 테스트가 공유하는 단일 PostgreSQL 컨테이너. JVM 종료 시까지 살아있어 부팅 비용 절감.
 */
abstract class PostgresIntegrationTest {

    companion object {
        @JvmStatic
        protected val POSTGRES: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("alarm_sender_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true)
                .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun dataSourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { POSTGRES.jdbcUrl }
            registry.add("spring.datasource.username") { POSTGRES.username }
            registry.add("spring.datasource.password") { POSTGRES.password }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }
}
