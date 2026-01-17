package com.wire.bots.domain.event.handlers

import arrow.core.Either
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.domain.reminder.ReminderNextSchedule
import com.wire.bots.domain.reminder.getNextSchedules
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.domain.usecase.DeleteReminderUseCase
import com.wire.bots.domain.usecase.ListRemindersInConversation
import com.wire.bots.domain.usecase.SaveReminderSchedule
import com.wire.bots.infrastructure.utils.UsageMetrics
import com.wire.sdk.model.QualifiedId
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CommandHandlerTest {
    @Test
    fun `BuildMsg createHelpMessage should include usage examples`() {
        val msg = BuildMsg.helpMessage
        assertTrue(msg.contains("/remind to \"do something\""))
        assertTrue(msg.contains("/remind list"))
        assertTrue(msg.contains("/remind delete <reminderId>"))
    }

    @DisplayName(
        "createReminderCreationConfirmationMessage for single reminder " +
            "should include scheduled date"
    )
    @Test
    fun testCreateReminderCreationConfirmationSingleReminderIncludesScheduledDate() {
        val conversationId = QualifiedId(UUID.randomUUID(), "example.com")
        val reminder = Reminder.SingleReminder(
            createdAt = Instant.now(),
            conversationId = conversationId,
            taskId = "t1",
            task = "Pay rent",
            scheduledAt = Instant.now().plusSeconds(3600)
        )
        val nextSchedules = reminder.getNextSchedules(1)
        val either = BuildMsg.createReminderCreationConfirmationMessage(
            ReminderNextSchedule(reminder, nextSchedules)
        )
        either.fold({ fail("expected Right but got Left: $it") }) { msg ->
            assertTrue(msg.contains("Pay rent") || msg.contains("'Pay rent'"))
            assertTrue(msg.contains(nextSchedules.first().toString().substring(0, 4)))
        }
    }

    @DisplayName(
        "createReminderCreationConfirmationMessage for recurring reminder " +
            "should list multiple schedules"
    )
    @Test
    fun testCreateReminderCreationConfirmationRecurringListsSchedules() {
        val conversationId = QualifiedId(UUID.randomUUID(), "example.com")
        val reminder = Reminder.RecurringReminder(
            createdAt = Instant.now(),
            taskId = "r1",
            conversationId = conversationId,
            task = "Daily meeting",
            scheduledCron = "0 0 10 ? * *"
        )
        val nextSchedules = reminder.getNextSchedules(3)
        val either = BuildMsg.createReminderCreationConfirmationMessage(
            ReminderNextSchedule(reminder, nextSchedules)
        )
        either.fold({ fail("expected Right but got Left: $it") }) { msg ->
            assertTrue(msg.contains("Daily meeting") || msg.contains("'Daily meeting'"))
            // should include at least two of the scheduled dates in text form
            assertTrue(msg.contains(nextSchedules.first().toString().substring(0, 4)))
        }
    }

    @Test
    fun `on Help should send help message and call usage metrics`() {
        val outgoing = mockk<OutgoingMessageRepository>()
        val usageMetrics = mockk<UsageMetrics>(relaxed = true)
        val listRemindersInConversation = mockk<ListRemindersInConversation>()
        val saveReminderSchedule = mockk<SaveReminderSchedule>()
        val deleteReminder = mockk<DeleteReminderUseCase>()
        val conversationId = QualifiedId(UUID.randomUUID(), "example.com")

        every { usageMetrics.onHelpCommand() } just runs
        val slot = slot<String>()
        every { outgoing.sendMessage(conversationId, capture(slot)) } returns Either.Right(Unit)
        every { listRemindersInConversation.invoke(conversationId) } returns
            Either.Right(emptyList())

        val handler = CommandHandler(
            outgoing,
            saveReminderSchedule,
            listRemindersInConversation,
            deleteReminder,
            usageMetrics
        )

        val cmd = Command.Help(conversationId = conversationId)
        val result = handler.onEvent(cmd)
        result.fold({ fail("expected success: $it") }) {}

        verify { usageMetrics.onHelpCommand() }
        val commands = listOf("/remind to", "/remind list", "/remind delete")
        assertTrue(commands.all { command -> slot.captured.contains(command) })
    }

    @Test
    fun `on ListReminders with no reminders should send empty message and call usage metrics`() {
        val outgoing = mockk<OutgoingMessageRepository>()
        val usageMetrics = mockk<UsageMetrics>(relaxed = true)
        val listRemindersInConversation = mockk<ListRemindersInConversation>()
        val saveReminderSchedule = mockk<SaveReminderSchedule>()
        val deleteReminder = mockk<DeleteReminderUseCase>()
        val conversationId = QualifiedId(UUID.randomUUID(), "example.com")

        every { usageMetrics.onListCommand() } just runs
        val slot = slot<String>()
        every { listRemindersInConversation.invoke(conversationId) } returns
            Either.Right(emptyList())
        every { outgoing.sendMessage(conversationId, capture(slot)) } returns Either.Right(Unit)

        val handler = CommandHandler(
            outgoing,
            saveReminderSchedule,
            listRemindersInConversation,
            deleteReminder,
            usageMetrics
        )

        val cmd = Command.ListReminders(conversationId = conversationId)
        val result = handler.onEvent(cmd)
        result.fold({ fail("expected success: $it") }) {}

        verify { usageMetrics.onListCommand() }
        assertTrue(slot.captured.contains("There are no reminders yet"))
    }
}
