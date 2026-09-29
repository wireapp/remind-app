package com.wire.bots.domain.reminder

import arrow.core.Either
import com.wire.sdk.model.QualifiedId

interface ReminderRepository {
    fun persistReminder(reminder: Reminder): Either<Throwable, Unit>

    fun countRemindersByConversationId(conversationId: QualifiedId): Long

    fun getReminderOnConversationId(conversationId: QualifiedId): Either<Throwable, List<Reminder>>

    fun deleteReminder(
        reminderId: String,
        conversationId: QualifiedId
    ): Either<Throwable, Unit>

    /**
     * Deletes every reminder of [conversationId], and returns how many were deleted.
     *
     * Used when the conversation itself is gone, so there is nothing left to remind anyone about.
     */
    fun deleteRemindersByConversationId(conversationId: QualifiedId): Either<Throwable, Long>
}
