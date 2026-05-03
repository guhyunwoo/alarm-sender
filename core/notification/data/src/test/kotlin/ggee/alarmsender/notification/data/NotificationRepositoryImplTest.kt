package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.NotificationType
import ggee.alarmsender.notification.testfixture.NotificationFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(classes = [DataLayerTestApp::class])
@Testcontainers
class NotificationRepositoryImplTest @Autowired constructor(
    private val sut: NotificationRepository,
    private val jdbcTemplate: JdbcTemplate,
) : PostgresIntegrationTest() {

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE notification_history, notification_outbox, notification RESTART IDENTITY CASCADE")
    }

    @Test
    fun `save 시 id 가 발급되고 findById 로 동일한 도메인 객체를 조회한다`() {
        val saved = sut.save(NotificationFixtures.notification())

        assertNotNull(saved.id)
        val found = sut.findById(saved.id!!)
        assertEquals(saved.recipientId, found?.recipientId)
        assertEquals(saved.type, found?.type)
        assertEquals(saved.channel, found?.channel)
        assertEquals(saved.payload, found?.payload)
        assertEquals(saved.idempotencyKey, found?.idempotencyKey)
        assertEquals(saved.status, found?.status)
        assertEquals(saved.createdAt, found?.createdAt)
    }

    @Test
    fun `findByIdempotencyKey 로 클라이언트 멱등성 키 조회 가능`() {
        sut.save(NotificationFixtures.notification(idempotencyKey = "idem-A"))
        sut.save(NotificationFixtures.notification(idempotencyKey = "idem-B", refId = "200"))

        val found = sut.findByIdempotencyKey("idem-A")
        assertNotNull(found)
        assertEquals("idem-A", found.idempotencyKey)

        assertNull(sut.findByIdempotencyKey("idem-MISSING"))
    }

    @Test
    fun `같은 idempotencyKey 두 번 저장 시 DB UNIQUE 제약 위반`() {
        sut.save(NotificationFixtures.notification(idempotencyKey = "dup-key"))
        assertFailsWith<DataIntegrityViolationException> {
            sut.save(NotificationFixtures.notification(idempotencyKey = "dup-key", refId = "999"))
        }
    }

    @Test
    fun `findByNaturalKey 로 자연 키 조회 가능 (recipient + type + ref)`() {
        sut.save(
            NotificationFixtures.notification(
                idempotencyKey = "k1",
                recipientId = "user-1",
                type = NotificationType.ENROLL_COMPLETED,
                refType = "ENROLLMENT",
                refId = "100",
            ),
        )

        val found = sut.findByNaturalKey(
            recipientId = "user-1",
            type = NotificationType.ENROLL_COMPLETED,
            refType = "ENROLLMENT",
            refId = "100",
        )
        assertNotNull(found)

        val miss = sut.findByNaturalKey("user-1", NotificationType.ENROLL_COMPLETED, "ENROLLMENT", "999")
        assertNull(miss)
    }

    @Test
    fun `자연 키 UNIQUE 제약 — 같은 (recipient, type, refType, refId) 두 번 저장 시 위반`() {
        sut.save(
            NotificationFixtures.notification(
                idempotencyKey = "k1",
                recipientId = "user-1",
                refType = "ENROLLMENT",
                refId = "100",
            ),
        )
        assertFailsWith<DataIntegrityViolationException> {
            sut.save(
                NotificationFixtures.notification(
                    idempotencyKey = "k2",
                    recipientId = "user-1",
                    refType = "ENROLLMENT",
                    refId = "100",
                ),
            )
        }
    }

    @Test
    fun `자연 키 UNIQUE 는 ref가 NULL 인 경우에는 적용되지 않는다 (partial index)`() {
        // ref_type / ref_id 모두 NULL 인 경우 자연 키 UNIQUE 비적용
        sut.save(NotificationFixtures.notification(idempotencyKey = "k1", refType = null, refId = null))
        sut.save(NotificationFixtures.notification(idempotencyKey = "k2", refType = null, refId = null))
        // 두 row 모두 저장 성공해야 함 — 예외 없이 통과하면 OK
    }

    @Test
    fun `findByRecipient — 최신순 정렬, unread 필터, offset, limit 적용`() {
        val now = Instant.parse("2026-05-02T10:00:00Z")
        // user-1: 3건 (1건 읽음)
        sut.save(NotificationFixtures.notification(idempotencyKey = "k1", recipientId = "user-1", refId = "1", createdAt = now.plusSeconds(1)))
        val read = sut.save(NotificationFixtures.notification(idempotencyKey = "k2", recipientId = "user-1", refId = "2", createdAt = now.plusSeconds(2), readAt = now.plusSeconds(10)))
        sut.save(NotificationFixtures.notification(idempotencyKey = "k3", recipientId = "user-1", refId = "3", createdAt = now.plusSeconds(3)))
        // 다른 사용자
        sut.save(NotificationFixtures.notification(idempotencyKey = "k4", recipientId = "user-2", refId = "4"))

        val all = sut.findByRecipient("user-1", unreadOnly = false, limit = 10, offset = 0)
        assertEquals(3, all.size)
        // 최신 순
        assertEquals("3", all[0].refId)

        val unread = sut.findByRecipient("user-1", unreadOnly = true, limit = 10, offset = 0)
        assertEquals(2, unread.size)
        assertTrue(unread.all { it.readAt == null })

        val paged = sut.findByRecipient("user-1", unreadOnly = false, limit = 1, offset = 1)
        assertEquals(1, paged.size)
    }

    @Test
    fun `update 으로 읽음 처리 영속화 (멀티 디바이스 멱등)`() {
        val saved = sut.save(NotificationFixtures.notification(idempotencyKey = "k1"))
        val readAt = Instant.parse("2026-05-02T10:30:00Z")
        sut.update(saved.markAsRead(readAt))

        val refetched = sut.findById(saved.id!!)
        assertEquals(readAt, refetched?.readAt)
    }

    @Test
    fun `markAsReadIfUnread 는 최초 set 시 입력 readAt 을 돌려주고 이후엔 기존 readAt 을 그대로 돌려준다`() {
        val saved = sut.save(NotificationFixtures.notification(idempotencyKey = "k1"))
        val readAt = Instant.parse("2026-05-02T10:30:00Z")

        // 최초 set — 우리가 넘긴 readAt 이 그대로 effective 값
        assertEquals(readAt, sut.markAsReadIfUnread(saved.id!!, readAt))
        // 이미 읽음 — 두 번째 호출의 입력은 무시되고 첫 read 의 readAt 이 돌아온다
        assertEquals(readAt, sut.markAsReadIfUnread(saved.id!!, readAt.plusSeconds(1)))

        val refetched = sut.findById(saved.id!!)
        assertEquals(readAt, refetched?.readAt)
    }

    @Test
    fun `markAsReadIfUnread 는 row 가 없으면 null 을 돌려준다`() {
        val readAt = Instant.parse("2026-05-02T10:30:00Z")
        assertNull(sut.markAsReadIfUnread(id = 9_999_999, readAt = readAt))
    }
}
