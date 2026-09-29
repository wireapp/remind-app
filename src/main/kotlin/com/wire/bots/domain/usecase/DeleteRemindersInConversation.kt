package com.wire.bots.domain.usecase

import arrow.core.Either
import arrow.core.flatMap
import com.wire.bots.domain.DomainComponent
import com.wire.bots.domain.reminder.ReminderJobRepository
import com.wire.bots.domain.reminder.ReminderRepository
import com.wire.sdk.model.QualifiedId

/**
 * Delete every reminder of a conversation, schedules included.
 *
 * Used once the conversation is gone: it was deleted, or the app was removed from it. Sending
 * into such a conversation fails, so the reminders are worthless and only produce errors.
 */
@DomainComponent
class DeleteRemindersInConversation(
    private val reminderRepository: ReminderRepository,
    private val reminderJobRepository: ReminderJobRepository
) {
    /**
     * The jobs are cancelled before the reminders are deleted: a job whose reminder is already
     * gone fails when it fires, while a reminder whose job is gone simply never fires again.
     *
     * Both steps are always attempted, so a failing one does not leave the other behind. The
     * first failure is the one returned.
     */
    operator fun invoke(conversationId: QualifiedId): Either<Throwable, Long> {
        val jobsCancelled = reminderJobRepository
            .cancelAllReminderJobsInConversation(conversationId)
        val remindersDeleted = reminderRepository
            .deleteRemindersByConversationId(conversationId)

        return jobsCancelled.flatMap { remindersDeleted }
    }
}
