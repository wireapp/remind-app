package com.wire.bots.domain.reminder

import arrow.core.Either
import com.wire.sdk.model.QualifiedId

interface ReminderJobRepository {
    fun scheduleReminderJob(reminder: Reminder): Either<Throwable, ReminderNextSchedule>

    fun cancelReminderJob(
        reminderId: String,
        conversationId: QualifiedId
    ): Either<Throwable, Unit>

    /**
     * Cancels every scheduled job of [conversationId], whichever reminder it belongs to.
     *
     * The schedules outlive a restart, so they have to be cancelled explicitly once the
     * conversation is gone, otherwise they keep firing into a conversation we cannot reach.
     */
    fun cancelAllReminderJobsInConversation(conversationId: QualifiedId): Either<Throwable, Unit>
}
