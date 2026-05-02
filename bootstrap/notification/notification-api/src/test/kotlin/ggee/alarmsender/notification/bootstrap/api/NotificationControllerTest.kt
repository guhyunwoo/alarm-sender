package ggee.alarmsender.notification.bootstrap.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class NotificationControllerTest @Autowired constructor(
    private val rest: TestRestTemplate,
    private val jdbcTemplate: JdbcTemplate,
) {

    @LocalServerPort
    var port: Int = 0

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE notification_history, notification_outbox, notification RESTART IDENTITY CASCADE")
    }

    private fun headers(userId: String, idempotencyKey: String? = null): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        set("X-User-Id", userId)
        if (idempotencyKey != null) set("Idempotency-Key", idempotencyKey)
    }

    private val createBody = """
        {
          "recipientId": "u1",
          "type": "ENROLL_COMPLETED",
          "channel": "EMAIL",
          "payload": {"title": "수강 신청 완료"},
          "refType": "ENROLLMENT",
          "refId": "100"
        }
    """.trimIndent()

    @Test
    fun `POST 신규 — 201 Created 와 PENDING 상태`() {
        val resp = rest.postForEntity("/api/v1/notifications", HttpEntity(createBody, headers("u1", "idem-1")), Map::class.java)

        assertEquals(HttpStatus.CREATED, resp.statusCode)
        val body = resp.body!!
        assertEquals("PENDING", body["status"])
        assertNotNull(body["id"])
    }

    @Test
    fun `같은 Idempotency-Key 두 번 — 두 번째는 200 OK 같은 id`() {
        val first = rest.postForEntity("/api/v1/notifications", HttpEntity(createBody, headers("u1", "dup-key")), Map::class.java)
        val second = rest.postForEntity("/api/v1/notifications", HttpEntity(createBody, headers("u1", "dup-key")), Map::class.java)

        assertEquals(HttpStatus.CREATED, first.statusCode)
        assertEquals(HttpStatus.OK, second.statusCode)
        assertEquals(first.body!!["id"], second.body!!["id"])
    }

    @Test
    fun `다른 사용자가 단건 조회 — 403`() {
        val created = rest.postForEntity("/api/v1/notifications", HttpEntity(createBody, headers("u1", "g-1")), Map::class.java)
        val id = created.body!!["id"]

        val resp = rest.exchange(
            "/api/v1/notifications/$id",
            HttpMethod.GET,
            HttpEntity<Void>(headers("u2")),
            Map::class.java,
        )
        assertEquals(HttpStatus.FORBIDDEN, resp.statusCode)
    }

    @Test
    fun `read — 첫 호출 newlyRead=true, 두 번째 false (멀티 디바이스 멱등)`() {
        val created = rest.postForEntity("/api/v1/notifications", HttpEntity(createBody, headers("u1", "r-1")), Map::class.java)
        val id = created.body!!["id"]

        val first = rest.postForEntity("/api/v1/notifications/$id/read", HttpEntity<Void>(headers("u1")), Map::class.java)
        assertEquals(HttpStatus.OK, first.statusCode)
        assertEquals(true, first.body!!["newlyRead"])

        val second = rest.postForEntity("/api/v1/notifications/$id/read", HttpEntity<Void>(headers("u1")), Map::class.java)
        assertEquals(false, second.body!!["newlyRead"])
    }

    @Test
    fun `목록 조회 — 본인 알림 + 최신순 + unreadOnly 필터`() {
        rest.postForEntity("/api/v1/notifications",
            HttpEntity("""{"recipientId":"u1","type":"ENROLL_COMPLETED","channel":"EMAIL","payload":{},"refType":"E","refId":"1"}""", headers("u1", "l1")), Map::class.java)
        rest.postForEntity("/api/v1/notifications",
            HttpEntity("""{"recipientId":"u1","type":"PAYMENT_CONFIRMED","channel":"EMAIL","payload":{},"refType":"E","refId":"2"}""", headers("u1", "l2")), Map::class.java)

        val list = rest.exchange(
            "/api/v1/notifications?unreadOnly=true&limit=10",
            HttpMethod.GET,
            HttpEntity<Void>(headers("u1")),
            List::class.java,
        )
        assertEquals(HttpStatus.OK, list.statusCode)
        assertEquals(2, list.body!!.size)
    }

    companion object {
        @JvmStatic
        private val POSTGRES = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("alarm_sender_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerPgProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { POSTGRES.jdbcUrl }
            registry.add("spring.datasource.username") { POSTGRES.username }
            registry.add("spring.datasource.password") { POSTGRES.password }
        }
    }
}
