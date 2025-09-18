package com.wire.bots.domain.usecase

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.wire.bots.domain.DomainComponent
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.domain.reminder.ReminderJobRepository
import com.wire.bots.domain.reminder.ReminderNextSchedule
import com.wire.bots.domain.reminder.ReminderRepository
import com.wire.bots.domain.usecase.SaveReminderSchedule.Companion.MAX_REMINDER_JOBS
import jakarta.transaction.Transactional

/**
 * Save the reminder to the database and schedule the reminder job.
 * This will succeed if:
 *
 * - We have not reached the maximum number of jobs per conversation. [MAX_REMINDER_JOBS].
 * - The reminder is saved correctly.
 * - The job is scheduled correctly.
 *
 * This use case is transactional.
 */
@DomainComponent
class SaveReminderSchedule(
    private val reminderRepository: ReminderRepository,
    private val reminderJobRepository: ReminderJobRepository
) {
    @Transactional
    operator fun invoke(reminder: Reminder): Either<Throwable, ReminderNextSchedule> =
        Either.catch {
            if (reminderRepository.countRemindersByConversationId(reminder.conversationId) >=
                MAX_REMINDER_JOBS
            ) {
                return MaxReminderJobsReached().left()
            }

            return reminderRepository
                .persistReminder(reminder)
                .flatMap { reminderJobRepository.scheduleReminderJob(reminder) }
                .flatMap { it.right() }
        }

    data class MaxReminderJobsReached(
        val max: Int = MAX_REMINDER_JOBS
    ) : Throwable("❌ Max reminder jobs reached: $max")

    companion object {
        const val MAX_REMINDER_JOBS = 5
    }
}
