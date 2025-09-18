package com.wire.bots.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.mdimension.jchronic.Chronic
import com.mdimension.jchronic.Options
import com.mdimension.jchronic.tags.Pointer
import com.wire.bots.domain.event.BotError
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.reminder.Reminder
import java.util.UUID
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.bots.domain.usecase.ValidateReminder
import com.wire.bots.infrastructure.utils.CronInterpreter

object ReminderMapper {
    private val INVALID_TIME_TOKENS = listOf("hour", "minute", "second")
    private val VALID_RECURRENT_TOKENS = listOf("every")
    // todo: expand later, each, daily, weekly, etc.

    private fun isRecurrentSchedule(schedule: String): Boolean =
        VALID_RECURRENT_TOKENS.any { schedule.contains(it) }

    private fun containsInvalidTimeTokens(schedule: String): Boolean =
        INVALID_TIME_TOKENS.any { schedule.contains(it) }

    fun parseReminder(
        conversationId: QualifiedId,
        task: String,
        schedule: String
    ): Either<BotError, Command> {
        ValidateReminder.validateTaskNotEmpty(task, conversationId)?.let { return it.left() }
        return when {
            isRecurrentSchedule(schedule) && containsInvalidTimeTokens(schedule) -> {
                BotError
                    .ReminderError(
                        conversationId = conversationId,
                        errorType = BotError.ErrorType.INCREMENT_IN_TIMEUNIT
                    ).left()
            }
            VALID_RECURRENT_TOKENS.any { schedule.contains(it) } -> {
                parseRecurrentTask(
                    conversationId = conversationId,
                    task = task,
                    schedule = schedule
                )
            }
            else -> parseSingleTask(
                conversationId = conversationId,
                task = task,
                schedule = schedule
            )
        }
    }

    private fun parseSingleTask(
        schedule: String,
        conversationId: QualifiedId,
        task: String
    ): Either<BotError.ReminderError, Command.NewReminder> {
        return runCatching {
            val parsedSchedule = Chronic.parse(
                schedule,
                Options(Pointer.PointerType.FUTURE)
            )
            val parsedDate = parsedSchedule.beginCalendar.toInstant()
            ValidateReminder
                .validateScheduledTimeInFuture(
                    parsedDate,
                    conversationId
                )?.let { return it.left() }
            Command
                .NewReminder(
                    conversationId = conversationId,
                    reminder = Reminder.SingleReminder(
                        conversationId = conversationId,
                        taskId = UUID.randomUUID().toString(),
                        task = task,
                        scheduledAt = parsedDate
                    )
                ).right()
        }.getOrElse {
            BotError
                .ReminderError(
                    conversationId = conversationId,
                    errorType = BotError.ErrorType.PARSE_ERROR
                ).left()
        }
    }

    private fun parseRecurrentTask(
        conversationId: QualifiedId,
        task: String,
        schedule: String
    ): Either<BotError.ReminderError, Command.NewReminder> =
        runCatching {
            Command
                .NewReminder(
                    conversationId = conversationId,
                    reminder = Reminder.RecurringReminder(
                        conversationId = conversationId,
                        taskId = UUID.randomUUID().toString(),
                        task = task,
                        scheduledCron = CronInterpreter.textToCron(schedule)
                    )
                ).right()
        }.getOrElse {
            BotError
                .ReminderError(
                    conversationId = conversationId,
                    errorType = BotError.ErrorType.PARSE_ERROR
                ).left()
        }
}
