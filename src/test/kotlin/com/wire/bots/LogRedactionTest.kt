package com.wire.bots

import com.wire.bots.application.EventTypeDTO
import com.wire.bots.application.MessageEventDTO
import com.wire.bots.application.TextContent
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.infrastructure.toDomain
import com.wire.bots.infrastructure.toEntity
import com.wire.bots.infrastructure.repository.ReminderEntity
import com.wire.bots.infrastructure.utils.HIDDEN
import com.wire.sdk.model.QualifiedId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

private const val SECRET = "call the doctor about my test results"

private val CONVERSATION_ID = QualifiedId(UUID.randomUUID(), "domain")
private val SENDER_ID = QualifiedId(UUID.randomUUID(), "domain")

/**
 * Whatever the user wrote is theirs, so the types that carry it are printed without it: these
 * end up interpolated into log lines.
 */
class LogRedactionTest {
    @Test
    fun givenAnEventDTO_whenPrinted_thenTheMessageTextIsHidden() {
        val event = MessageEventDTO(
            type = EventTypeDTO.NEW_TEXT,
            senderId = SENDER_ID,
            conversationId = CONVERSATION_ID,
            text = TextContent("/remind to \"$SECRET\" \"tomorrow at 18:00\"")
        )

        assertHidden(event.toString())
    }

    @Test
    fun givenANewReminderCommand_whenPrinted_thenTheTaskIsHidden() {
        val command = Command.NewReminder(
            conversationId = CONVERSATION_ID,
            requesterId = SENDER_ID,
            task = SECRET,
            schedule = "tomorrow at 18:00"
        )

        assertHidden(command.toString())
        // The schedule is the expression we may have failed to parse, it stays readable.
        assertTrue(command.toString().contains("tomorrow at 18:00"))
    }

    @Test
    fun givenASingleReminder_whenPrinted_thenTheTaskIsHidden() {
        val reminder = Reminder.SingleReminder(
            conversationId = CONVERSATION_ID,
            taskId = UUID.randomUUID().toString(),
            task = SECRET,
            zoneId = ZoneId.of("Europe/Berlin"),
            scheduledAt = Instant.now()
        )

        assertHidden(reminder.toString())
    }

    @Test
    fun givenARecurringReminder_whenPrinted_thenTheTaskIsHidden() {
        val reminder = Reminder.RecurringReminder(
            taskId = UUID.randomUUID().toString(),
            conversationId = CONVERSATION_ID,
            task = SECRET,
            zoneId = ZoneId.of("Europe/Berlin"),
            scheduledCron = "0 0 10 * * ?"
        )

        assertHidden(reminder.toString())
    }

    @Test
    fun givenAReminderEntity_whenPrinted_thenTheTaskIsHidden() {
        val entity = ReminderEntity(
            conversationId = CONVERSATION_ID,
            taskId = UUID.randomUUID().toString(),
            task = SECRET,
            zoneId = ZoneId.of("Europe/Berlin"),
            scheduledAt = Instant.now()
        )

        assertHidden(entity.toString())
    }

    /**
     * Hiding the task is a logging concern only: the value that reaches the database, and the one
     * read back from it, is still the text the user wrote.
     */
    @Test
    fun givenAReminder_whenMappedToTheEntityAndBack_thenTheTaskIsUnchanged() {
        val reminder = Reminder.SingleReminder(
            conversationId = CONVERSATION_ID,
            taskId = UUID.randomUUID().toString(),
            task = SECRET,
            zoneId = ZoneId.of("Europe/Berlin"),
            scheduledAt = Instant.now()
        )

        val entity = reminder.toEntity()

        assertEquals(SECRET, entity.task)
        assertEquals(reminder, entity.toDomain())
    }

    @Test
    fun givenTwoReminders_whenComparing_thenTheTaskStillCounts() {
        val reminder = Reminder.RecurringReminder(
            taskId = UUID.randomUUID().toString(),
            conversationId = CONVERSATION_ID,
            task = SECRET,
            zoneId = ZoneId.of("Europe/Berlin"),
            scheduledCron = "0 0 10 * * ?"
        )

        assertEquals(reminder, reminder.copy())
        assertNotEquals(reminder, reminder.copy(task = "something else"))
    }

    private fun assertHidden(printed: String) {
        assertFalse(printed.contains(SECRET), "Printed the user content: $printed")
        assertTrue(printed.contains(HIDDEN), "Missing the $HIDDEN placeholder: $printed")
    }
}
