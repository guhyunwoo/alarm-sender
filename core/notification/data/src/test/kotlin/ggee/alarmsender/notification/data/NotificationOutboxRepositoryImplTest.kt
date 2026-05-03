package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationOutboxRepository
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.OutboxStatus
import ggee.alarmsender.notification.testfixture.NotificationFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(classes = [DataLayerTestApp::class])
@Testcontainers
class NotificationOutboxRepositoryImplTest @Autowired constructor(
    private val sut: NotificationOutboxRepository,
    private val notificationRepo: NotificationRepository,
    private val jdbcTemplate: JdbcTemplate,
) : PostgresIntegrationTest() {

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE notification_history, notification_outbox, notification RESTART IDENTITY CASCADE")
    }

    private val now: Instant = Instant.parse("2026-05-02T10:00:00Z")
    private val workerA = "worker-A"
    private val leaseDuration: Duration = Duration.ofSeconds(30)

    private fun givenNotification(idempotencyKey: String): Long =
        notificationRepo.save(NotificationFixtures.notification(idempotencyKey = idempotencyKey, refId = idempotencyKey)).id!!

    @Test
    fun `save 후 findById 로 동일한 row 를 도메인 객체로 조회한다`() {
        val notificationId = givenNotification("save-find")
        val saved = sut.save(NotificationFixtures.outbox(notificationId = notificationId, createdAt = now, updatedAt = now, availableAt = now))

        assertNotNull(saved.id)
        val found = sut.findById(saved.id!!)
        assertNotNull(found)
        assertEquals(saved.notificationId, found.notificationId)
        assertEquals(OutboxStatus.PENDING, found.status)
        assertEquals(saved.availableAt, found.availableAt)
    }

    @Test
    fun `claimBatch 는 PENDING 이면서 availableAt 도래한 row 만 가져온다`() {
        val n1 = givenNotification("n-1")
        val n2 = givenNotification("n-2")
        val n3 = givenNotification("n-3")

        sut.save(NotificationFixtures.outbox(notificationId = n1, availableAt = now.minusSeconds(1), createdAt = now, updatedAt = now))
        sut.save(NotificationFixtures.outbox(notificationId = n2, availableAt = now.plusSeconds(60), createdAt = now, updatedAt = now)) // future
        sut.save(NotificationFixtures.outbox(notificationId = n3, availableAt = now, createdAt = now, updatedAt = now))

        val claimed = sut.claimBatch(workerA, now, leaseDuration, limit = 10)
        assertEquals(2, claimed.size)
        assertTrue(claimed.all { it.status == OutboxStatus.IN_PROGRESS })
        assertTrue(claimed.all { it.leaseOwner == workerA })
        assertTrue(claimed.all { it.leaseExpiresAt == now.plus(leaseDuration) })
    }

    @Test
    fun `이미 IN_PROGRESS 인 row 는 다시 claim 되지 않는다`() {
        val n1 = givenNotification("n-1")
        sut.save(NotificationFixtures.outbox(notificationId = n1, availableAt = now, createdAt = now, updatedAt = now))

        val first = sut.claimBatch(workerA, now, leaseDuration, limit = 10)
        assertEquals(1, first.size)

        // 같은 시각에 다시 claim 시도 — IN_PROGRESS 라 0건
        val second = sut.claimBatch("worker-B", now, leaseDuration, limit = 10)
        assertEquals(0, second.size)
    }

    @Test
    fun `findExpired — IN_PROGRESS 이면서 lease 만료된 row 만 반환한다`() {
        val n1 = givenNotification("n-1")
        val n2 = givenNotification("n-2")
        val claimedAt = now.minusSeconds(60)
        sut.save(NotificationFixtures.outbox(
            notificationId = n1,
            status = OutboxStatus.IN_PROGRESS,
            leaseOwner = "dead-worker",
            leaseExpiresAt = claimedAt.plusSeconds(30), // 만료됨
            createdAt = now,
            updatedAt = now,
        ))
        sut.save(NotificationFixtures.outbox(
            notificationId = n2,
            status = OutboxStatus.IN_PROGRESS,
            leaseOwner = "live-worker",
            leaseExpiresAt = now.plusSeconds(30), // 아직 유효
            createdAt = now,
            updatedAt = now,
        ))

        val expired = sut.findExpired(now, limit = 10)
        assertEquals(1, expired.size)
        assertEquals(n1, expired[0].notificationId)
    }

    @Test
    fun `다중 워커 동시 claim — 같은 row 를 두 번 잡지 않는다 (SKIP LOCKED)`() {
        val rowCount = 100
        repeat(rowCount) { i ->
            val nid = givenNotification("c-$i")
            sut.save(NotificationFixtures.outbox(notificationId = nid, availableAt = now, createdAt = now, updatedAt = now))
        }

        val workerCount = 8
        val limit = 20
        val executor = java.util.concurrent.Executors.newFixedThreadPool(workerCount)
        val futures = (1..workerCount).map { i ->
            executor.submit<List<Long>> {
                sut.claimBatch("worker-$i", now, leaseDuration, limit).map { it.requireId() }
            }
        }
        val claimedIds = futures.flatMap { it.get() }
        executor.shutdown()

        assertEquals(claimedIds.size, claimedIds.toSet().size, "다중 워커가 같은 row 를 잡았다 (중복 발견)")
        assertTrue(claimedIds.size <= rowCount)
        assertTrue(claimedIds.isNotEmpty())
    }

    @Test
    fun `낙관적 락 — claim 후 stale 도메인 객체로 update 시도 시 OptimisticLock 차단`() {
        // 시나리오: 워커 A 가 claim → 들고 있던 도메인 객체(version=1)를 보관.
        // 그 사이 batch 가 reclaim → version=2 로 증가.
        // 부활한 워커 A 가 stale 객체로 markSucceeded → DB UPDATE 실패해야 함.
        val nid = givenNotification("opt-lock")
        sut.save(NotificationFixtures.outbox(notificationId = nid, availableAt = now, createdAt = now, updatedAt = now))

        // 1. 워커 A 가 claim — version 이 +1 됨
        val claimedByA = sut.claimBatch(workerA, now, leaseDuration, 1).single()
        val versionAtClaim = claimedByA.version

        // 2. lease 만료 후 batch 가 reclaim → DB version 증가
        val expired = claimedByA.copy(leaseExpiresAt = now.minusSeconds(1))
        sut.update(expired) // lease 만료된 상태로 갱신
        val reclaimed = sut.findExpired(now, 10).single().reclaim(now)
        sut.update(reclaimed) // version 증가

        val versionAfterReclaim = sut.findById(claimedByA.requireId())!!.version
        assertTrue(versionAfterReclaim > versionAtClaim, "reclaim 으로 version 이 증가해야 함")

        // 3. 워커 A 가 stale 객체로 markSucceeded 시도 → OptimisticLockException 차단
        org.junit.jupiter.api.assertThrows<org.springframework.dao.OptimisticLockingFailureException> {
            sut.update(claimedByA.markSucceeded(now.plusSeconds(1)))
        }
    }

    @Test
    fun `update 로 markSucceeded 영속화 — DONE 상태로 갱신`() {
        val notificationId = givenNotification("succ")
        val saved = sut.save(NotificationFixtures.outbox(notificationId = notificationId, availableAt = now, createdAt = now, updatedAt = now))
        val claimed = sut.claimBatch(workerA, now, leaseDuration, 10).single()

        sut.update(claimed.markSucceeded(now.plusSeconds(1)))
        val found = sut.findById(saved.id!!)
        assertEquals(OutboxStatus.DONE, found?.status)
    }
}
