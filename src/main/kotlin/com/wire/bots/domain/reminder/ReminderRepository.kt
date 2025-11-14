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
}
