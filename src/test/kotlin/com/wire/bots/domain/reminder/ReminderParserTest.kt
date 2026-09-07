package com.wire.bots.domain.reminder

import com.wire.bots.domain.event.BotError
import com.wire.bots.shouldFail
import com.wire.bots.shouldSucceed
import com.wire.sdk.model.QualifiedId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ReminderParserTest {
    @Test
    fun givenASchedule_whenIsRecurringByTimeIncrement_ThenRaiseError() {
        val result = parse(schedule = "every hour")

        result.shouldFail {
            assertInstanceOf(BotError.ReminderError::class.java, it)
            assertEquals(
                BotError.ErrorType.INCREMENT_IN_TIMEUNIT.message,
                (it as BotError.ReminderError).reason
            )
        }
    }

    @Test
    fun givenASchedule_whenIsInThePast_ThenRaiseError() {
        val result = parse(schedule = "on 1990-01-01")

        result.shouldFail {
            assertInstanceOf(BotError.ReminderError::class.java, it)
            assertEquals(
                BotError.ErrorType.DATE_IN_PAST.message,
                (it as BotError.ReminderError).reason
            )
        }
    }

    @Test
    fun givenASchedule_whenContainsNotBeingAbleToParse_ThenRaiseError() {
        val result = parse(schedule = "SOME INVALID SCHEDULE")

        result.shouldFail {
            assertEquals(
                BotError.ErrorType.PARSE_ERROR.message,
                (it as BotError.ReminderError).reason
            )
        }
    }

    @Test
    fun givenASchedule_whenContainsNotBeingAbleToParseExpression_ThenRaiseError() {
        val result = parse(schedule = "not/valid/expression")

        result.shouldFail {
            assertEquals(
                BotError.ErrorType.PARSE_ERROR.message,
                (it as BotError.ReminderError).reason
            )
        }
    }

    @Test
    fun givenAnEmptyTask_whenParsingReminder_thenRaiseParseError() {
        val result = parse(task = "   ", schedule = "tomorrow at 10:00")

        result.shouldFail {
            assertEquals(
                BotError.ErrorType.EMPTY_REMINDER_TASK.message,
                (it as BotError.ReminderError).reason
            )
        }
    }

    @Test
    fun givenAnInvalidDate_whenParsingReminder_thenRaiseParseError() {
        val result = parse(schedule = "31.02.2025 at 10:00")

        result.shouldFail {
            assertEquals(
                BotError.ErrorType.PARSE_ERROR.message,
                (it as BotError.ReminderError).reason
            )
        }
    }

    @Test
    fun givenAValidReminder_whenParsingReminder_thenSucceeds() {
        val result = parse(task = "Buy milk", schedule = "tomorrow at 10:00")

        result.shouldSucceed {
            assertEquals("Buy milk", it.task)
        }
    }

    @Test
    fun givenAWallClockSchedule_whenParsedInAZone_thenTheInstantIsThatZonesWallClock() {
        val result = parse(schedule = "tomorrow at 18:00", zoneId = BERLIN)

        result.shouldSucceed {
            val scheduledAt = (it as Reminder.SingleReminder).scheduledAt
            val inBerlin = scheduledAt.atZone(BERLIN)
            assertEquals(18, inBerlin.hour)
            assertEquals(0, inBerlin.minute)
            assertEquals(LocalDate.now(BERLIN).plusDays(1), inBerlin.toLocalDate())
        }
    }

    @Test
    fun givenTheSameWallClock_whenParsedInDifferentZones_thenTheInstantsDiffer() {
        val berlin = parse(schedule = "tomorrow at 18:00", zoneId = BERLIN)
        val utc = parse(schedule = "tomorrow at 18:00", zoneId = UTC)

        berlin.shouldSucceed { berlinReminder ->
            utc.shouldSucceed { utcReminder ->
                val berlinAt = (berlinReminder as Reminder.SingleReminder).scheduledAt
                val utcAt = (utcReminder as Reminder.SingleReminder).scheduledAt
                assertTrue(
                    berlinAt.isBefore(utcAt),
                    "18:00 in Berlin happens before 18:00 in UTC, was $berlinAt vs $utcAt"
                )
            }
        }
    }

    @Test
    fun givenARecurringSchedule_whenParsed_thenTheZoneIsKeptOnTheReminder() {
        val result = parse(schedule = "every monday at 10:00", zoneId = BERLIN)

        result.shouldSucceed {
            assertInstanceOf(Reminder.RecurringReminder::class.java, it)
            assertEquals("0 00 10 ? * MON", (it as Reminder.RecurringReminder).scheduledCron)
            assertEquals(BERLIN, it.zoneId)
        }
    }

    @Test
    fun givenARecurringSchedule_whenTheNextSchedulesAreComputed_thenTheyUseTheReminderZone() {
        val reminder = Reminder.RecurringReminder(
            conversationId = CONVERSATION_ID,
            taskId = "r1",
            task = "Daily meeting",
            zoneId = BERLIN,
            scheduledCron = "0 0 10 ? * *"
        )

        val schedules = reminder.getNextSchedules(3)

        assertEquals(3, schedules.size)
        schedules.forEach { schedule ->
            assertEquals(10, schedule.toInstant().atZone(BERLIN).hour)
        }
    }

    private fun parse(
        task: String = "task",
        schedule: String,
        zoneId: ZoneId = UTC
    ) = ReminderParser.parse(
        conversationId = CONVERSATION_ID,
        task = task,
        schedule = schedule,
        zoneId = zoneId
    )

    private companion object {
        val CONVERSATION_ID = QualifiedId(UUID.randomUUID(), "example.com")
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
        val UTC: ZoneId = ZoneId.of("UTC")
    }
}
