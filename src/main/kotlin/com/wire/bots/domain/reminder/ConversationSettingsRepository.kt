package com.wire.bots.domain.reminder

import arrow.core.Either
import com.wire.sdk.model.QualifiedId
import java.time.ZoneId

interface ConversationSettingsRepository {
    /**
     * Returns the timezone configured for this conversation, or UTC if none has been set.
     */
    fun getTimezone(conversationId: QualifiedId): ZoneId

    /**
     * Sets the timezone for this conversation. Applies to reminders created after this point.
     */
    fun setTimezone(
        conversationId: QualifiedId,
        zoneId: ZoneId
    ): Either<Throwable, Unit>
}
