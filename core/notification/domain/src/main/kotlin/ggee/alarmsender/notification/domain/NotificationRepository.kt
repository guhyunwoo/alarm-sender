package ggee.alarmsender.notification.domain

import java.time.Instant

interface NotificationRepository {
    fun findById(id: Long): Notification?

    fun findByIdempotencyKey(key: String): Notification?

    fun findByNaturalKey(
        recipientId: String,
        type: NotificationType,
        refType: String?,
        refId: String?,
    ): Notification?

    fun findByRecipient(
        recipientId: String,
        unreadOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<Notification>

    fun save(notification: Notification): Notification

    fun update(notification: Notification): Notification

    /**
     * 동시 read 시 첫 호출만 성공시키는 저장소 경계 조건부 UPDATE.
     * 호출자는 반환값으로 effective `read_at` 을 받아서 그대로 응답에 쓰면 된다.
     * (1차 캐시·세션 분리에 따라 재조회 시 stale 한 readAt=null 이 보일 수 있어
     *  내부에서 일관된 값을 돌려준다.)
     *
     * @return effective `read_at` — 이번 호출이 set 했으면 입력 `readAt`,
     *   이미 다른 호출이 set 했으면 그 값. row 가 없으면 null.
     */
    fun markAsReadIfUnread(id: Long, readAt: Instant): Instant?
}
