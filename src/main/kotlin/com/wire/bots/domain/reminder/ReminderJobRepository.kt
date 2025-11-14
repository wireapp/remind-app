package com.wire.bots.domain.reminder

import arrow.core.Either
import com.wire.sdk.model.QualifiedId

interface ReminderJobRepository {
    fun scheduleReminderJob(reminder: Reminder): Either<Throwable, ReminderNextSchedule>

    fun cancelReminderJob(
        reminderId: String,
        conversationId: QualifiedId
    ): Either<Throwable, Unit>
}
