package com.wire.bots.application

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.wire.bots.domain.event.BotError
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.reminder.ConversationSettingsRepository
import com.wire.bots.domain.reminder.SupportedTimezones
import com.wire.sdk.model.QualifiedId
import jakarta.enterprise.context.ApplicationScoped
import java.time.ZoneId
import java.util.UUID

@ApplicationScoped
class EventMapper(
    private val conversationSettingsRepository: ConversationSettingsRepository
) {
    /**
     * Maps the [MessageEventDTO] to a [Command] object so it can be processed by the application.
     */
    fun fromEvent(eventDTO: EventDTO): Either<BotError, Command> =
        runCatching {
            when (eventDTO.type) {
                EventTypeDTO.NEW_TEXT -> {
                    require(eventDTO is MessageEventDTO) { "Wrong DTO for this event type." }
                    parseCommand(
                        conversationId = eventDTO.conversationId,
                        rawCommand = eventDTO.text?.data.orEmpty()
                    )
                }

                EventTypeDTO.BUTTON_ACTION -> {
                    require(eventDTO is ButtonActionEventDTO) { "Wrong DTO for this event type." }
                    val buttonId = eventDTO.buttonId.orEmpty()
                    val senderId = eventDTO.userId?.let {
                        QualifiedId(UUID.fromString(it), "")
                    }

                    val parsedUuid = runCatching { UUID.fromString(buttonId) }.getOrNull()
                    if (parsedUuid != null) {
                        Command
                            .DeleteReminder(
                                conversationId = eventDTO.conversationId,
                                reminderId = buttonId,
                                referencedMessageId = eventDTO.referencedMessageId,
                                senderId = senderId
                            ).right()
                    } else {
                        parseCommand(
                            conversationId = eventDTO.conversationId,
                            rawCommand = buttonId,
                            referencedMessageId = eventDTO.referencedMessageId,
                            senderId = senderId
                        )
                    }
                }

                else -> BotError.Skip.left()
            }
        }.getOrElse {
            BotError
                .ReminderError(
                    conversationId = eventDTO.conversationId,
                    errorType = BotError.ErrorType.PARSE_ERROR
                ).left()
        }

    /**
     * Parses the raw event string, and returns a [Command] object.
     */
    private fun parseCommand(
        conversationId: QualifiedId,
        rawCommand: String,
        referencedMessageId: String? = null,
        senderId: QualifiedId? = null
    ): Either<BotError, Command> =
        either {
            val words = rawCommand.split(COMMAND_EXPRESSION)
            if (words[0] == "/remind") {
                return parseCommandArgs(
                    conversationId = conversationId,
                    args = rawCommand.substringAfter("/remind").trimStart()
                )
            }
            return BotError.Skip.left()
        }

    private fun parseCommandArgs(
        conversationId: QualifiedId,
        args: String
    ): Either<BotError, Command> =
        when {
            args.trim() == "help" -> Command.Help(conversationId).right()
            args.trim() == "list" -> Command.ListReminders(conversationId).right()
            args.trim() == "show timezone" -> Command.ShowTimezone(conversationId).right()
            args.startsWith("set timezone") -> parseSetTimezoneCommand(conversationId, args)
            args.startsWith("to") -> parseToCommand(conversationId, args)
            else ->
                BotError
                    .Unknown(
                        conversationId = conversationId,
                        reason = COMMAND_HINT
                    ).left()
        }

    private fun parseSetTimezoneCommand(
        conversationId: QualifiedId,
        args: String
    ): Either<BotError, Command> {
        val label = args.substringAfter("set timezone").trim().uppercase()
        val zoneId = SupportedTimezones.resolve(label)
        return if (zoneId != null) {
            Command.SetTimezone(conversationId, zoneId, label).right()
        } else {
            BotError.ReminderError(conversationId, BotError.ErrorType.INVALID_TIMEZONE).left()
        }
    }

    private fun parseToCommand(
        conversationId: QualifiedId,
        args: String
    ): Either<BotError, Command> {
        val regex = Regex("[\"“”]([^\"“”]*)[\"“”]")
        val matches = regex
            .findAll(args.substringAfter("to"))
            .map { it.groupValues[1] }
            .toList()
        return when {
            matches.size < 2 ->
                BotError
                    .ReminderError(
                        conversationId = conversationId,
                        errorType = BotError.ErrorType.INVALID_REMINDER_USAGE
                    ).left()
            matches[0].isBlank() ->
                BotError
                    .ReminderError(
                        conversationId = conversationId,
                        errorType = BotError.ErrorType.EMPTY_REMINDER_TASK
                    ).left()
            matches[1].isBlank() ->
                BotError
                    .ReminderError(
                        conversationId = conversationId,
                        errorType = BotError.ErrorType.INVALID_REMINDER_USAGE
                    ).left()
            else -> {
                val task = matches[0]
                val schedule = matches[1]
                val zoneId = conversationSettingsRepository.getTimezone(conversationId)
                ReminderMapper
                    .parseReminder(
                        conversationId = conversationId,
                        task = task,
                        schedule = schedule,
                        zoneId = zoneId
                    ).mapLeft { error ->
                        error as? BotError.ReminderError ?: error("❌ Unexpected error type: $error")
                    }
            }
        }
    }
}

internal val COMMAND_EXPRESSION: Regex = "\\s+".toRegex()
internal val COMMAND_HINT =
    """
    Unknown command, valid options are:
/remind help
/remind list
/remind set timezone <zone>
/remind show timezone
    """.trimIndent()
