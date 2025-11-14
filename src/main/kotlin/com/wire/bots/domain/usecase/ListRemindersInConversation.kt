package com.wire.bots.domain.usecase

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import com.wire.bots.domain.DomainComponent
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.domain.reminder.ReminderRepository
import com.wire.sdk.model.QualifiedId

/**
 * List all reminders for a conversation.
 */
@DomainComponent
class ListRemindersInConversation(
    private val reminderRepository: ReminderRepository
) {
    operator fun invoke(conversationId: QualifiedId): Either<Throwable, List<Reminder>> =
        Either.catch {
            return reminderRepository
                .getReminderOnConversationId(conversationId)
                .flatMap { it.right() }
        }
}
