package com.wire.bots.domain.usecase

import arrow.core.Either
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.domain.reminder.ReminderNextSchedule
import com.wire.bots.domain.user.UserTimezoneRepository
import com.wire.bots.shouldSucceed
import com.wire.sdk.model.QualifiedId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.util.Date
import java.util.UUID

class CreateReminderTest {
    @Test
    fun `given a wall clock schedule and no stored timezone, then nothing is created`() {
        val fixture = Fixture()
        every { fixture.timezones.findByUserId(REQUESTER_ID) } returns Either.Right(null)

        val result = fixture.createReminder(command(schedule = "tomorrow at 18:00"))

        result.shouldSucceed {
            assertInstanceOf(CreateReminder.Result.TimezoneMissing::class.java, it)
        }
        verify(exactly = 0) { fixture.saveReminderSchedule.invoke(any()) }
    }

    @Test
    fun `given a wall clock schedule and a stored timezone, then it is read in that timezone`() {
        val fixture = Fixture()
        every { fixture.timezones.findByUserId(REQUESTER_ID) } returns Either.Right(BERLIN)
        val saved = fixture.expectSave()

        val result = fixture.createReminder(command(schedule = "tomorrow at 18:00"))

        result.shouldSucceed {
            assertInstanceOf(CreateReminder.Result.Created::class.java, it)
        }
        val reminder = saved.captured as Reminder.SingleReminder
        assertEquals(BERLIN, reminder.zoneId)
        assertEquals(18, reminder.scheduledAt.atZone(BERLIN).hour)
    }

    @Test
    fun `given a purely relative schedule, then no timezone preference is needed`() {
        val fixture = Fixture()
        val saved = fixture.expectSave()

        val result = fixture.createReminder(command(schedule = "in 10 minutes"))

        result.shouldSucceed {
            assertInstanceOf(CreateReminder.Result.Created::class.java, it)
        }
        assertEquals(ZoneId.of("UTC"), saved.captured.zoneId)
        verify(exactly = 0) { fixture.timezones.findByUserId(any()) }
    }

    @Test
    fun `given a recurring schedule, then the stored timezone is needed`() {
        val fixture = Fixture()
        every { fixture.timezones.findByUserId(REQUESTER_ID) } returns Either.Right(null)

        val result = fixture.createReminder(command(schedule = "every day at 10:00"))

        result.shouldSucceed {
            assertInstanceOf(CreateReminder.Result.TimezoneMissing::class.java, it)
        }
    }

    private fun command(schedule: String) =
        Command.NewReminder(
            conversationId = CONVERSATION_ID,
            requesterId = REQUESTER_ID,
            task = "Pay rent",
            schedule = schedule
        )

    private class Fixture {
        val timezones = mockk<UserTimezoneRepository>()
        val saveReminderSchedule = mockk<SaveReminderSchedule>()
        val createReminder = CreateReminder(timezones, saveReminderSchedule)

        /** Accepts any reminder and hands back the one that was passed in. */
        fun expectSave(): io.mockk.CapturingSlot<Reminder> {
            val slot = slot<Reminder>()
            every { saveReminderSchedule.invoke(capture(slot)) } answers {
                Either.Right(ReminderNextSchedule(slot.captured, listOf(Date())))
            }
            return slot
        }
    }

    private companion object {
        val CONVERSATION_ID = QualifiedId(UUID.randomUUID(), "example.com")
        val REQUESTER_ID = QualifiedId(UUID.randomUUID(), "example.com")
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
