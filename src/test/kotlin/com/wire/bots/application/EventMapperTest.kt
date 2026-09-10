package com.wire.bots.application

import com.wire.bots.domain.event.BotError
import com.wire.bots.domain.event.Command
import com.wire.bots.shouldFail
import com.wire.bots.shouldSucceed
import com.wire.sdk.model.QualifiedId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.util.UUID

internal val TEST_CONVERSATION_ID = QualifiedId(
    UUID.fromString("00000000-000-0000-0000-000000000001"),
    "domain"
)

internal val TEST_SENDER_ID = QualifiedId(
    UUID.fromString("00000000-000-0000-0000-000000000002"),
    "domain"
)

class EventMapperTest {
    @Test
    fun givenNotRelevantEvent_whenMapping_ThenReturnSkip() {
        // given
        val messageEventDTO = textEvent("not relevant")

        // when
        val event = EventMapper.fromEvent(messageEventDTO)

        // then
        event.shouldFail {
            assertInstanceOf(BotError.Skip::class.java, it)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsHelp_ThenReturnHelpCommand() {
        // given
        val messageEventDTO = textEvent("/remind help")

        // when
        val event = EventMapper.fromEvent(messageEventDTO)

        // then
        event.shouldSucceed {
            assertEquals(Command.Help(TEST_CONVERSATION_ID), it)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsOneTimeRemind_ThenReturnRemindCommandWithRawSchedule() {
        // given
        val messageEventDTO = textEvent(
            """/remind to "join the refinement session" "tomorrow at 11:00""""
        )

        // when
        val event = EventMapper.fromEvent(messageEventDTO)

        // then
        event.shouldSucceed {
            assertEquals(
                Command.NewReminder(
                    conversationId = TEST_CONVERSATION_ID,
                    requesterId = TEST_SENDER_ID,
                    task = "join the refinement session",
                    schedule = "tomorrow at 11:00"
                ),
                it
            )
        }
    }

    @Test
    fun givenTextEvent_whenTextIsRecurringRemind_ThenReturnRemindCommandWithRawSchedule() {
        // given
        val messageEventDTO = textEvent(
            """/remind to "join the daily stand up" "every monday at 10:00""""
        )

        // when
        val event = EventMapper.fromEvent(messageEventDTO)

        // then
        event.shouldSucceed {
            assertInstanceOf(Command.NewReminder::class.java, it)
            val command = it as Command.NewReminder
            assertEquals("join the daily stand up", command.task)
            assertEquals("every monday at 10:00", command.schedule)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsList_ThenReturnListRemindersCommand() {
        val event = EventMapper.fromEvent(textEvent("/remind list"))
        event.shouldSucceed {
            assertEquals(Command.ListReminders(TEST_CONVERSATION_ID), it)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsSetTimezone_ThenReturnSetTimezoneCommand() {
        val event = EventMapper.fromEvent(textEvent("""/remind set-timezone "Europe/Berlin""""))
        event.shouldSucceed {
            assertEquals(
                Command.SetTimezone(
                    conversationId = TEST_CONVERSATION_ID,
                    requesterId = TEST_SENDER_ID,
                    zoneId = ZoneId.of("Europe/Berlin")
                ),
                it
            )
        }
    }

    @Test
    fun givenTextEvent_whenSetTimezoneHasNoQuotes_ThenReturnSetTimezoneCommand() {
        val event = EventMapper.fromEvent(textEvent("/remind set-timezone Europe/Istanbul"))
        event.shouldSucceed {
            assertEquals(
                ZoneId.of("Europe/Istanbul"),
                (it as Command.SetTimezone).zoneId
            )
        }
    }

    @Test
    fun givenTextEvent_whenSetTimezoneIsWrittenInAnotherCase_ThenReturnSetTimezoneCommand() {
        val event = EventMapper.fromEvent(textEvent("""/remind set-timezone "europe/BERLIN""""))
        event.shouldSucceed {
            assertEquals(ZoneId.of("Europe/Berlin"), (it as Command.SetTimezone).zoneId)
        }
    }

    @Test
    fun givenTextEvent_whenSetTimezoneIsUnknown_ThenRaiseInvalidTimezone() {
        val event = EventMapper.fromEvent(textEvent("""/remind set-timezone "Mars/Olympus""""))
        event.shouldFail {
            assertInstanceOf(BotError.InvalidTimezone::class.java, it)
            assertEquals("Mars/Olympus", (it as BotError.InvalidTimezone).input)
        }
    }

    @Test
    fun givenTextEvent_whenSetTimezoneHasNoArgument_ThenRaiseUsageError() {
        val event = EventMapper.fromEvent(textEvent("/remind set-timezone"))
        event.shouldFail {
            assertInstanceOf(BotError.ReminderError::class.java, it)
            assertEquals(
                BotError.ErrorType.INVALID_SET_TIMEZONE_USAGE,
                (it as BotError.ReminderError).errorType
            )
        }
    }

    @Test
    fun givenTextEvent_whenTextIsDeleteCommand_ThenRaiseUnknownCommandError() {
        val event = EventMapper.fromEvent(textEvent("/remind delete 12345"))
        event.shouldFail {
            assertInstanceOf(BotError.Unknown::class.java, it)
        }
    }

    @Test
    fun givenTextEvent_whenTextIsMalformedReminder_ThenRaiseInvalidReminderUsage() {
        val event = EventMapper.fromEvent(textEvent("/remind to \"\" \"tomorrow\""))
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
        val event = EventMapper.fromEvent(textEvent("/remind to \"task\" \"\""))
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
        val event = EventMapper.fromEvent(textEvent("/remind to \"task\""))
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
        val event = EventMapper.fromEvent(textEvent("/remind to task tomorrow"))
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
        val event = EventMapper.fromEvent(textEvent("/remind foo"))
        event.shouldFail {
            assertInstanceOf(BotError.Unknown::class.java, it)
        }
    }

    @Test
    fun givenTextEvent_whenTextHasExtraSpacesAndMixedQuotes_ThenParseCorrectly() {
        val event = EventMapper.fromEvent(
            textEvent("/remind   to   \"task\"   “tomorrow at 10:00”   ")
        )
        event.shouldSucceed {
            assertInstanceOf(Command.NewReminder::class.java, it)
            val command = it as Command.NewReminder
            assertEquals("task", command.task)
            assertEquals("tomorrow at 10:00", command.schedule)
        }
    }

    @Test
    fun givenButtonActionEvent_whenButtonIdIsUuid_ThenReturnDeleteReminderCommand() {
        val reminderId = "11111111-1111-1111-1111-111111111111"
        val referencedMessageId = "33333333-3333-3333-3333-333333333333"
        val buttonActionEventDTO = ButtonActionEventDTO(
            type = EventTypeDTO.BUTTON_ACTION,
            senderId = TEST_SENDER_ID,
            conversationId = TEST_CONVERSATION_ID,
            buttonId = reminderId,
            referencedMessageId = referencedMessageId
        )

        val event = EventMapper.fromEvent(buttonActionEventDTO)

        event.shouldSucceed {
            assertEquals(
                Command.DeleteReminder(
                    conversationId = TEST_CONVERSATION_ID,
                    reminderId = reminderId,
                    referencedMessageId = referencedMessageId,
                    senderId = TEST_SENDER_ID
                ),
                it
            )
        }
    }

    @Test
    fun givenButtonActionEvent_whenButtonIdIsNotUuidNorCommand_ThenReturnSkip() {
        val buttonActionEventDTO = ButtonActionEventDTO(
            type = EventTypeDTO.BUTTON_ACTION,
            senderId = TEST_SENDER_ID,
            conversationId = TEST_CONVERSATION_ID,
            buttonId = "not-a-uuid"
        )

        val event = EventMapper.fromEvent(buttonActionEventDTO)

        event.shouldFail {
            assertInstanceOf(BotError.Skip::class.java, it)
        }
    }

    @Test
    fun givenButtonActionEvent_whenButtonIdIsDeleteCommand_ThenRaiseUnknownCommandError() {
        val buttonActionEventDTO = ButtonActionEventDTO(
            type = EventTypeDTO.BUTTON_ACTION,
            senderId = TEST_SENDER_ID,
            conversationId = TEST_CONVERSATION_ID,
            buttonId = "/remind delete 12345"
        )

        val event = EventMapper.fromEvent(buttonActionEventDTO)

        event.shouldFail {
            assertInstanceOf(BotError.Unknown::class.java, it)
        }
    }

    private fun textEvent(text: String) =
        MessageEventDTO(
            type = EventTypeDTO.NEW_TEXT,
            senderId = TEST_SENDER_ID,
            conversationId = TEST_CONVERSATION_ID,
            text = TextContent(text)
        )
}
