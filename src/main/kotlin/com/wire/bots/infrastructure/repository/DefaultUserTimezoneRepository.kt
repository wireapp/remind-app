package com.wire.bots.infrastructure.repository

import arrow.core.Either
import com.wire.bots.domain.user.UserTimezoneRepository
import com.wire.sdk.model.QualifiedId
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant
import java.time.ZoneId

@ApplicationScoped
class DefaultUserTimezoneRepository :
    PanacheRepository<UserTimezoneEntity>,
    UserTimezoneRepository {
    @Transactional
    override fun findByUserId(userId: QualifiedId): Either<Throwable, ZoneId?> =
        Either.catch {
            find("userId", userId).firstResult()?.zoneId
        }

    @Transactional
    override fun save(
        userId: QualifiedId,
        zoneId: ZoneId
    ): Either<Throwable, Unit> =
        Either.catch {
            val stored = find("userId", userId).firstResult()
            if (stored == null) {
                persist(UserTimezoneEntity(userId = userId, zoneId = zoneId))
            } else {
                stored.zoneId = zoneId
                stored.updatedAt = Instant.now()
            }
        }
}
