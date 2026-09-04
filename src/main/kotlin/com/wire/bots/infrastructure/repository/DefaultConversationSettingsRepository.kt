package com.wire.bots.infrastructure.repository

import arrow.core.Either
import com.wire.bots.domain.reminder.ConversationSettingsRepository
import com.wire.bots.infrastructure.utils.toRawString
import com.wire.sdk.model.QualifiedId
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.ZoneId

@ApplicationScoped
class DefaultConversationSettingsRepository :
    PanacheRepositoryBase<ConversationSettingsEntity, String>,
    ConversationSettingsRepository {

    @Transactional
    override fun getTimezone(conversationId: QualifiedId): ZoneId =
        findById(conversationId.toRawString())
            ?.let { runCatching { ZoneId.of(it.timezone) }.getOrNull() }
            ?: ZoneId.of("CET")

    @Transactional
    override fun setTimezone(
        conversationId: QualifiedId,
        zoneId: ZoneId
    ): Either<Throwable, Unit> =
        Either.catch {
            getEntityManager().merge(
                ConversationSettingsEntity(conversationId.toRawString(), zoneId.id)
            )
            Unit
        }
}
