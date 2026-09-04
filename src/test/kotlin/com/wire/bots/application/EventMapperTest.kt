package com.wire.bots.application

import com.wire.bots.domain.event.BotError
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.reminder.ConversationSettingsRepository
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.shouldFail
import com.wire.bots.shouldSucceed
import com.wire.sdk.model.QualifiedId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.util.UUID

internal val TEST_CONVERSATION_ID = QualifiedId(
    UUID.fromString("00000000-000-0000-0000-000000000001"),
    "domain"
)

class EventMapperTest {
    private val conversationSettingsRepository = mockk<ConversationSettingsRepository> {
        every { getTimezone(any()) } returns ZoneId.of("UTC")
    }
    private val eventMapper = EventMapper(conversationSettingsRepository)

    @Test
    fun givenNotRelevantEvent_whenMapping_ThenReturnSkip() {
        val messageEventDTO =
            MessageEventDTO(
                type = EventTypeDTO.NEW_TEXT,
                conversationId = TEST_CONVERSATION_ID,
                text = TextContent("not relevant")
            )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldFail {
            assertInstanceOf(BotError.Skip::class.java, it)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsHelp_ThenReturnHelpCommand() {
        val messageEventDTO =
            MessageEventDTO(
                type = EventTypeDTO.NEW_TEXT,
                conversationId = TEST_CONVERSATION_ID,
                text = TextContent("/remind help")
            )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldSucceed {
            assertEquals(Command.Help(TEST_CONVERSATION_ID), it)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsOneTimeRemind_ThenReturnRemindCommandSingle() {
        val messageEventDTO =
            MessageEventDTO(
                type = EventTypeDTO.NEW_TEXT,
                conversationId = TEST_CONVERSATION_ID,
                text = TextContent(
                    """/remind to "join the refinement session" "tomorrow at 11:00""""
                )
            )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldSucceed {
            assertInstanceOf(Command.NewReminder::class.java, it)
            assertInstanceOf(
                Reminder.SingleReminder::class.java,
                (it as Command.NewReminder).reminder
            )
            val reminder = it.reminder as Reminder.SingleReminder
            assertEquals("join the refinement session", reminder.task)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsRecurringRemind_ThenReturnRemindCommandRecurring() {
        val messageEventDTO =
            MessageEventDTO(
                type = EventTypeDTO.NEW_TEXT,
                conversationId = TEST_CONVERSATION_ID,
                text = TextContent(
                    """/remind to "join the daily stand up" "every monday at 10:00""""
                )
            )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldSucceed {
            assertInstanceOf(Command.NewReminder::class.java, it)
            assertInstanceOf(
                Reminder.RecurringReminder::class.java,
                (it as Command.NewReminder).reminder
            )
            val reminder = it.reminder as Reminder.RecurringReminder
            assertEquals("join the daily stand up", reminder.task)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsRecurringByTimeIncrementRemind_ThenRaiseError() {
        val messageEventDTO =
            MessageEventDTO(
                type = EventTypeDTO.NEW_TEXT,
                conversationId = TEST_CONVERSATION_ID,
                text = TextContent("""/remind to "drink water" "every 1 hours"""")
            )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldFail {
            assertInstanceOf(BotError.ReminderError::class.java, it)
            assertEquals(
                BotError.ErrorType.INCREMENT_IN_TIMEUNIT,
                (it as BotError.ReminderError).errorType
            )
        }
    }

    @Test
    fun givenTextEvent_whenTextTargetDayInPast_ThenRaiseError() {
        val messageEventDTO =
            MessageEventDTO(
                type = EventTypeDTO.NEW_TEXT,
                conversationId = TEST_CONVERSATION_ID,
                text = TextContent("""/remind to "drink water" "yesterday" """)
            )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldFail {
            assertInstanceOf(BotError.ReminderError::class.java, it)
            assertEquals(BotError.ErrorType.DATE_IN_PAST, (it as BotError.ReminderError).errorType)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsList_ThenReturnListRemindersCommand() {
        val messageEventDTO = MessageEventDTO(
            type = EventTypeDTO.NEW_TEXT,
            conversationId = TEST_CONVERSATION_ID,
            text = TextContent("/remind list")
        )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldSucceed {
            assertEquals(Command.ListReminders(TEST_CONVERSATION_ID), it)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsMalformedReminder_ThenRaiseInvalidReminderUsage() {
        val messageEventDTO = MessageEventDTO(
            type = EventTypeDTO.NEW_TEXT,
            conversationId = TEST_CONVERSATION_ID,
            text = TextContent("/remind to \"\" \"tomorrow\"")
        )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldFail {
            assertInstanceOf(BotError.ReminderError::class.java, it)
            assertEquals(
                BotError.ErrorType.EMPTY_REMINDER_TASK,
                (it as BotError.ReminderError).errorType
            )
        }
    }

    @Test
    fun givenTextEvent_whenTextIsMalformedReminderWithEmptyTime_ThenRaiseInvalidReminderUsage() {
        val messageEventDTO = MessageEventDTO(
            type = EventTypeDTO.NEW_TEXT,
            conversationId = TEST_CONVERSATION_ID,
            text = TextContent("/remind to \"task\" \"\"")
        )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldFail {
            assertInstanceOf(BotError.ReminderError::class.java, it)
            assertEquals(
                BotError.ErrorType.INVALID_REMINDER_USAGE,
                (it as BotError.ReminderError).errorType
            )
        }
    }

    @Test
    fun givenTextEvent_whenTextIsMalformedReminderWithOneArg_ThenRaiseInvalidReminderUsage() {
        val messageEventDTO = MessageEventDTO(
            type = EventTypeDTO.NEW_TEXT,
            conversationId = TEST_CONVERSATION_ID,
            text = TextContent("/remind to \"task\"")
        )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldFail {
            assertInstanceOf(BotError.ReminderError::class.java, it)
            assertEquals(
                BotError.ErrorType.INVALID_REMINDER_USAGE,
                (it as BotError.ReminderError).errorType
            )
        }
    }

    @Test
    fun givenTextEvent_whenTextIsMalformedReminderWithNoQuotes_ThenRaiseInvalidReminderUsage() {
        val messageEventDTO = MessageEventDTO(
            type = EventTypeDTO.NEW_TEXT,
            conversationId = TEST_CONVERSATION_ID,
            text = TextContent("/remind to task tomorrow")
        )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldFail {
            assertInstanceOf(BotError.ReminderError::class.java, it)
            assertEquals(
                BotError.ErrorType.INVALID_REMINDER_USAGE,
                (it as BotError.ReminderError).errorType
            )
        }
    }

    @Test
    fun givenTextEvent_whenTextIsUnknownCommand_ThenReturnUnknownError() {
        val messageEventDTO = MessageEventDTO(
            type = EventTypeDTO.NEW_TEXT,
            conversationId = TEST_CONVERSATION_ID,
            text = TextContent("/remind foo")
        )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldFail {
            assertInstanceOf(BotError.Unknown::class.java, it)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsTypedDeleteCommand_ThenReturnUnknownError() {
        val messageEventDTO = MessageEventDTO(
            type = EventTypeDTO.NEW_TEXT,
            conversationId = TEST_CONVERSATION_ID,
            text = TextContent("/remind delete 12345")
        )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldFail {
            assertInstanceOf(BotError.Unknown::class.java, it)
        }
    }

    @Test
    fun givenTextEvent_whenTextHasExtraSpacesAndMixedQuotes_ThenParseCorrectly() {
        val messageEventDTO = MessageEventDTO(
            type = EventTypeDTO.NEW_TEXT,
            conversationId = TEST_CONVERSATION_ID,
            text = TextContent("/remind   to   \"task\"   “tomorrow at 10:00”   ")
        )
        val event = eventMapper.fromEvent(messageEventDTO)
        event.shouldSucceed {
            assertInstanceOf(Command.NewReminder::class.java, it)
            val reminder = (it as Command.NewReminder).reminder
            assertEquals("task", reminder.task)
        }
    }
}
