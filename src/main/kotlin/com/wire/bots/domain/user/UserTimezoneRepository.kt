package com.wire.bots.domain.user

import arrow.core.Either
import com.wire.sdk.model.QualifiedId
import java.time.ZoneId

interface UserTimezoneRepository {
    /**
     * Returns the timezone [userId] picked, or `null` when they never set one.
     */
    fun findByUserId(userId: QualifiedId): Either<Throwable, ZoneId?>

    /**
     * Stores [zoneId] as the timezone of [userId], replacing any previous preference.
     */
    fun save(
        userId: QualifiedId,
        zoneId: ZoneId
    ): Either<Throwable, Unit>
}
