package com.wire.bots.domain.usecase

import com.wire.bots.domain.event.BotError
import com.wire.sdk.model.QualifiedId
import java.time.Instant

object ValidateReminder {
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
