package com.wire.bots.domain.reminder

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.mdimension.jchronic.Chronic
import com.mdimension.jchronic.Options
import com.mdimension.jchronic.tags.Pointer
import com.wire.bots.domain.event.BotError
import com.wire.bots.domain.usecase.ValidateReminder
import com.wire.bots.infrastructure.utils.CronInterpreter
import com.wire.sdk.model.QualifiedId
import org.quartz.CronExpression
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.UUID

/**
 * Turns the "what" and "when" of a `/remind to` command into a [Reminder], reading the schedule
 * as a wall clock in the requester's timezone.
 */
object ReminderParser {
    private val INVALID_TIME_TOKENS = listOf("hour", "minute", "second")
    private val VALID_RECURRENT_TOKENS = listOf("every")
    // todo: expand later, each, daily, weekly, etc.

    fun parse(
        conversationId: QualifiedId,
        task: String,
        schedule: String,
        zoneId: ZoneId
    ): Either<BotError, Reminder> {
        ValidateReminder.validateTaskNotEmpty(task, conversationId)?.let { return it.left() }
        return when {
            isRecurrentSchedule(schedule) && containsInvalidTimeTokens(schedule) ->
                BotError
                    .ReminderError(
                        conversationId = conversationId,
                        errorType = BotError.ErrorType.INCREMENT_IN_TIMEUNIT
                    ).left()

            isRecurrentSchedule(schedule) ->
                parseRecurrentTask(conversationId, task, schedule, zoneId)

            else -> parseSingleTask(conversationId, task, schedule, zoneId)
        }
    }

    private fun parseSingleTask(
        conversationId: QualifiedId,
        task: String,
        schedule: String,
        zoneId: ZoneId
    ): Either<BotError, Reminder> =
        runCatching {
            val scheduledAt = parseWallClock(schedule, zoneId)
            ValidateReminder
                .validateScheduledTimeInFuture(scheduledAt, conversationId)
                ?.let { return it.left() }
            Reminder
                .SingleReminder(
                    conversationId = conversationId,
                    taskId = UUID.randomUUID().toString(),
                    task = task,
                    zoneId = zoneId,
                    scheduledAt = scheduledAt
                ).right()
        }.getOrElse { parseError(conversationId) }

    private fun parseRecurrentTask(
        conversationId: QualifiedId,
        task: String,
        schedule: String,
        zoneId: ZoneId
    ): Either<BotError, Reminder> =
        runCatching {
            val cron = CronInterpreter.textToCron(schedule)
            require(CronExpression.isValidExpression(cron)) { "Invalid cron expression: $cron" }
            Reminder
                .RecurringReminder(
                    conversationId = conversationId,
                    taskId = UUID.randomUUID().toString(),
                    task = task,
                    zoneId = zoneId,
                    scheduledCron = cron
                ).right()
        }.getOrElse { parseError(conversationId) }

    /**
     * jchronic is timezone-naive: it reads only the wall clock fields of the reference date and
     * builds its answer with `Calendar.getInstance()`, so it always works in the JVM default
     * zone. We therefore hand it the requester's local wall clock, and read its answer back as a
     * wall clock that we anchor in the requester's zone ourselves.
     */
    private fun parseWallClock(
        schedule: String,
        zoneId: ZoneId
    ): Instant {
        val options = Options(Pointer.PointerType.FUTURE)
        options.now = LocalDateTime.now(zoneId).toCalendar()
        val parsed = requireNotNull(Chronic.parse(schedule, options)) {
            "Schedule could not be understood: $schedule"
        }
        return parsed.beginCalendar
            .toLocalDateTime()
            .atZone(zoneId)
            .toInstant()
    }

    private fun LocalDateTime.toCalendar(): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, monthValue - 1, dayOfMonth, hour, minute, second)
        }

    private fun Calendar.toLocalDateTime(): LocalDateTime =
        LocalDateTime.of(
            get(Calendar.YEAR),
            get(Calendar.MONTH) + 1,
            get(Calendar.DAY_OF_MONTH),
            get(Calendar.HOUR_OF_DAY),
            get(Calendar.MINUTE),
            get(Calendar.SECOND)
        )

    private fun parseError(conversationId: QualifiedId): Either<BotError, Nothing> =
        BotError
            .ReminderError(
                conversationId = conversationId,
                errorType = BotError.ErrorType.PARSE_ERROR
            ).left()

    private fun isRecurrentSchedule(schedule: String): Boolean =
        VALID_RECURRENT_TOKENS.any { schedule.lowercase().contains(it) }

    private fun containsInvalidTimeTokens(schedule: String): Boolean =
        INVALID_TIME_TOKENS.any { schedule.lowercase().contains(it) }
}
