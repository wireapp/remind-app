package com.wire.bots.domain.usecase

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import com.wire.bots.domain.DomainComponent
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.domain.reminder.ReminderNextSchedule
import com.wire.bots.domain.reminder.ReminderParser
import com.wire.bots.domain.reminder.getNextSchedules
import com.wire.bots.domain.usecase.SaveReminderSchedule.Companion.MAX_REMINDER_JOBS
import com.wire.bots.domain.user.UserTimezoneRepository
import java.time.ZoneId

/**
 * Creates the reminder described by a `/remind to` command.
 *
 * Every reminder is scheduled and shown in the requester's timezone, so it needs their
 * preference. When that preference is missing nothing is created and [Result.TimezoneMissing]
 * is returned, so the caller can ask for it.
 */
@DomainComponent
class CreateReminder(
    private val userTimezoneRepository: UserTimezoneRepository,
    private val saveReminderSchedule: SaveReminderSchedule
) {
    sealed interface Result {
        data class Created(
            val schedule: ReminderNextSchedule
        ) : Result

        data object TimezoneMissing : Result
    }

    operator fun invoke(command: Command.NewReminder): Either<Throwable, Result> =
        userTimezoneRepository.findByUserId(command.requesterId).flatMap { zoneId ->
            if (zoneId == null) {
                Result.TimezoneMissing.right()
            } else {
                scheduleReminder(command, zoneId).map { Result.Created(it) }
            }
        }

    private fun scheduleReminder(
        command: Command.NewReminder,
        zoneId: ZoneId
    ): Either<Throwable, ReminderNextSchedule> {
        val parsed: Either<Throwable, Reminder> = ReminderParser.parse(
            conversationId = command.conversationId,
            task = command.task,
            schedule = command.schedule,
            zoneId = zoneId
        )
        return parsed.flatMap { reminder ->
            saveReminderSchedule(reminder).flatMap {
                Either.catch {
                    ReminderNextSchedule(reminder, reminder.getNextSchedules(MAX_REMINDER_JOBS))
                }
            }
        }
    }
}
