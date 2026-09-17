package com.wire.bots.domain.usecase

import com.wire.bots.domain.event.BotError
import com.wire.sdk.model.QualifiedId
import java.time.Instant

object ValidateReminder {
    const val MAX_TASK_LENGTH = 1000

    fun validateTaskNotEmpty(
        task: String,
        conversationId: QualifiedId
    ): BotError.ReminderError? =
        if (task.isBlank()) {
            BotError.ReminderError(
                conversationId = conversationId,
                errorType = BotError.ErrorType.EMPTY_REMINDER_TASK
            )
        } else {
            null
        }

    // Without this check an oversized task is only rejected when the transaction commits,
    // which is too late to turn into a helpful answer.
    fun validateTaskLength(
        task: String,
        conversationId: QualifiedId
    ): BotError.ReminderError? =
        if (task.length > MAX_TASK_LENGTH) {
            BotError.ReminderError(
                conversationId = conversationId,
                errorType = BotError.ErrorType.REMINDER_TASK_TOO_LONG
            )
        } else {
            null
        }

    fun validateScheduledTimeInFuture(
        scheduledAt: Instant,
        conversationId: QualifiedId
    ): BotError.ReminderError? =
        if (scheduledAt.isBefore(Instant.now())) {
            BotError.ReminderError(
                conversationId = conversationId,
                errorType = BotError.ErrorType.DATE_IN_PAST
            )
        } else {
            null
        }
}
