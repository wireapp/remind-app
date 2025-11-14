package com.wire.bots.domain.usecase

import arrow.core.Either
import arrow.core.flatMap
import com.wire.bots.domain.DomainComponent
import com.wire.sdk.model.QualifiedId
import com.wire.bots.domain.reminder.ReminderRepository
import com.wire.bots.domain.reminder.ReminderJobRepository

/**
 * Delete reminder for a conversation.
 */
@DomainComponent
class DeleteReminderUseCase(
    private val reminderRepository: ReminderRepository,
    private val reminderJobRepository: ReminderJobRepository
) {
    operator fun invoke(
        reminderId: String,
        conversationId: QualifiedId
    ): Either<Throwable, Unit> =
        reminderRepository
            .deleteReminder(reminderId, conversationId)
            .flatMap { reminderJobRepository.cancelReminderJob(reminderId, conversationId) }
}
