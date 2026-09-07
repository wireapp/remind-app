package com.wire.bots.domain.event.handlers

import arrow.core.Either
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.domain.reminder.ReminderNextSchedule
import com.wire.bots.domain.reminder.getNextSchedules
import com.wire.bots.domain.usecase.CreateReminder
import com.wire.bots.domain.usecase.DeleteReminderUseCase
import com.wire.bots.domain.usecase.ListRemindersInConversation
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
import java.time.ZoneId
import java.util.UUID

class CommandHandlerTest {
    @Test
    fun `BuildMsg createHelpMessage should include usage examples`() {
        val msg = BuildMsg.helpMessage
        assertTrue(msg.contains("/remind to \"do something\""))
        assertTrue(msg.contains("/remind list"))
        assertTrue(msg.contains("Delete button"))
        assertTrue(msg.contains("/remind set-timezone"))
    }

    @DisplayName(
        "createReminderCreationConfirmationMessage for single reminder " +
            "should include scheduled date"
    )
    @Test
    fun testCreateReminderCreationConfirmationSingleReminderIncludesScheduledDate() {
        val reminder = Reminder.SingleReminder(
            createdAt = Instant.now(),
            conversationId = CONVERSATION_ID,
            taskId = "t1",
            task = "Pay rent",
            zoneId = BERLIN,
            // 18:00 in Berlin, which is two hours ahead of UTC in June.
            scheduledAt = Instant.parse("2030-06-15T16:00:00Z")
        )
        val either = BuildMsg.createReminderCreationConfirmationMessage(
            ReminderNextSchedule(reminder, reminder.getNextSchedules(1))
        )
        either.fold({ fail("expected Right but got Left: $it") }) { msg ->
            assertTrue(msg.contains("Pay rent"))
            assertTrue(msg.contains("15 Jun 2030 at 18:00"), msg)
        }
    }

    @DisplayName(
        "createReminderCreationConfirmationMessage for recurring reminder " +
            "should list multiple schedules"
    )
    @Test
    fun testCreateReminderCreationConfirmationRecurringListsSchedules() {
        val reminder = Reminder.RecurringReminder(
            createdAt = Instant.now(),
            taskId = "r1",
            conversationId = CONVERSATION_ID,
            task = "Daily meeting",
            zoneId = BERLIN,
            scheduledCron = "0 0 10 ? * *"
        )
        val nextSchedules = reminder.getNextSchedules(3)
        val either = BuildMsg.createReminderCreationConfirmationMessage(
            ReminderNextSchedule(reminder, nextSchedules)
        )
        either.fold({ fail("expected Right but got Left: $it") }) { msg ->
            assertTrue(msg.contains("Daily meeting"))
            // The zone the reminder was created in is part of the answer.
            assertTrue(msg.contains("Europe/Berlin"), msg)
            assertTrue(msg.contains("at 10:00"), msg)
        }
    }

    @Test
    fun `on Help should send help message and call usage metrics`() {
        val fixture = Fixture()
        val slot = slot<String>()
        every { fixture.usageMetrics.onHelpCommand() } just runs
        every {
            fixture.outgoing.sendMessage(CONVERSATION_ID, capture(slot))
        } returns Either.Right(Unit)

        val result = fixture.handler.onEvent(Command.Help(conversationId = CONVERSATION_ID))
        result.fold({ fail("expected success: $it") }) {}

        verify { fixture.usageMetrics.onHelpCommand() }
        val commands = listOf("/remind to", "/remind list")
        assertTrue(commands.all { command -> slot.captured.contains(command) })
    }

    @Test
    fun `on ListReminders with no reminders should send empty message and call usage metrics`() {
        val fixture = Fixture()
        val slot = slot<String>()
        every { fixture.usageMetrics.onListCommand() } just runs
        every { fixture.listReminders.invoke(CONVERSATION_ID) } returns Either.Right(emptyList())
        every {
            fixture.outgoing.sendMessage(CONVERSATION_ID, capture(slot))
        } returns Either.Right(Unit)

        val result = fixture.handler
            .onEvent(Command.ListReminders(conversationId = CONVERSATION_ID))
        result.fold({ fail("expected success: $it") }) {}

        verify { fixture.usageMetrics.onListCommand() }
        assertTrue(slot.captured.contains("There are no reminders yet"))
    }

    @Test
    fun `on NewReminder without a known timezone should ask for it and create nothing`() {
        val fixture = Fixture()
        val command = newReminderCommand()
        every { fixture.createReminder.invoke(command) } returns
            Either.Right(CreateReminder.Result.TimezoneMissing)
        every {
            fixture.timezoneHandler.requestTimezone(CONVERSATION_ID, REQUESTER_ID)
        } returns Either.Right(Unit)

        val result = fixture.handler.onEvent(command)
        result.fold({ fail("expected success: $it") }) {}

        verify { fixture.timezoneHandler.requestTimezone(CONVERSATION_ID, REQUESTER_ID) }
        verify(exactly = 0) { fixture.outgoing.sendCompositeMessage(any(), any(), any()) }
    }

    @Test
    fun `on NewReminder with a known timezone should confirm the created reminder`() {
        val fixture = Fixture()
        val command = newReminderCommand()
        val reminder = Reminder.SingleReminder(
            conversationId = CONVERSATION_ID,
            taskId = "t1",
            task = command.task,
            zoneId = BERLIN,
            scheduledAt = Instant.now().plusSeconds(3600)
        )
        every { fixture.createReminder.invoke(command) } returns
            Either.Right(
                CreateReminder.Result.Created(
                    ReminderNextSchedule(reminder, reminder.getNextSchedules(1))
                )
            )
        val slot = slot<String>()
        every {
            fixture.outgoing.sendCompositeMessage(CONVERSATION_ID, capture(slot), any())
        } returns Either.Right(Unit)

        val result = fixture.handler.onEvent(command)
        result.fold({ fail("expected success: $it") }) {}

        assertTrue(slot.captured.contains("Reminder created"), slot.captured)
        verify(exactly = 0) { fixture.timezoneHandler.requestTimezone(any(), any()) }
    }

    @Test
    fun `on SetTimezone should delegate to the timezone handler`() {
        val fixture = Fixture()
        val command = Command.SetTimezone(
            conversationId = CONVERSATION_ID,
            requesterId = REQUESTER_ID,
            zoneId = BERLIN
        )
        every { fixture.usageMetrics.onSetTimezoneCommand() } just runs
        every { fixture.timezoneHandler.setTimezone(command) } returns Either.Right(Unit)

        val result = fixture.handler.onEvent(command)
        result.fold({ fail("expected success: $it") }) {}

        verify { fixture.usageMetrics.onSetTimezoneCommand() }
        verify { fixture.timezoneHandler.setTimezone(command) }
    }

    private fun newReminderCommand() =
        Command.NewReminder(
            conversationId = CONVERSATION_ID,
            requesterId = REQUESTER_ID,
            task = "Pay rent",
            schedule = "tomorrow at 18:00"
        )

    private class Fixture {
        val outgoing = mockk<OutgoingMessageRepository>()
        val timezoneHandler = mockk<TimezoneCommandHandler>()
        val createReminder = mockk<CreateReminder>()
        val listReminders = mockk<ListRemindersInConversation>()
        val deleteReminder = mockk<DeleteReminderUseCase>()
        val usageMetrics = mockk<UsageMetrics>(relaxed = true)

        val handler = CommandHandler(
            outgoing,
            timezoneHandler,
            createReminder,
            listReminders,
            deleteReminder,
            usageMetrics
        )
    }

    private companion object {
        val CONVERSATION_ID = QualifiedId(UUID.randomUUID(), "example.com")
        val REQUESTER_ID = QualifiedId(UUID.randomUUID(), "example.com")
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
