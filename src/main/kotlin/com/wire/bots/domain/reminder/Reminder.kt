package com.wire.bots.domain.reminder

import com.wire.sdk.model.QualifiedId
import org.quartz.CronExpression
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

sealed interface Reminder {
    val createdAt: Instant
    val conversationId: QualifiedId
    val taskId: String
    val task: String
    val zoneId: ZoneId

    data class SingleReminder(
        override val createdAt: Instant = Instant.now(),
        override val conversationId: QualifiedId,
        override val taskId: String,
        override val task: String,
        val scheduledAt: Instant,
        override val zoneId: ZoneId = ZoneId.of("UTC")
    ) : Reminder

    data class RecurringReminder(
        override val createdAt: Instant = Instant.now(),
        override val taskId: String,
        override val conversationId: QualifiedId,
        override val task: String,
        val scheduledCron: String,
        override val zoneId: ZoneId = ZoneId.of("UTC")
    ) : Reminder
}

/**
 * Returns the next `count` schedules for the given reminder.
 */
fun Reminder.getNextSchedules(count: Int): List<Date> =
    when (this) {
        is Reminder.SingleReminder -> listOf(Date.from(this.scheduledAt))
        is Reminder.RecurringReminder -> {
            val cron = CronExpression(this.scheduledCron).apply {
                timeZone = TimeZone.getTimeZone(zoneId)
            }
            val schedules = mutableListOf<Date>()
            var next = Date.from(Instant.now())
            repeat(count) {
                next = cron.getNextValidTimeAfter(next)
                schedules.add(next)
            }
            schedules
        }
    }

class ReminderNextSchedule(
    val reminder: Reminder,
    val nextSchedules: List<Date>
)
