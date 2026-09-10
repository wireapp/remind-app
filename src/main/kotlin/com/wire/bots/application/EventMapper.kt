package com.wire.bots.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wire.bots.domain.event.BotError
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.user.Timezones
import com.wire.sdk.model.QualifiedId
import java.util.UUID

object EventMapper {
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
                        senderId = eventDTO.senderId,
                        rawCommand = eventDTO.text?.data.orEmpty()
                    )
                }

                EventTypeDTO.BUTTON_ACTION -> {
                    require(eventDTO is ButtonActionEventDTO) { "Wrong DTO for this event type." }
                    val buttonId = eventDTO.buttonId.orEmpty()

                    val parsedUuid = runCatching { UUID.fromString(buttonId) }.getOrNull()
                    if (parsedUuid != null) {
                        Command
                            .DeleteReminder(
                                conversationId = eventDTO.conversationId,
                                reminderId = buttonId,
                                referencedMessageId = eventDTO.referencedMessageId,
                                senderId = eventDTO.senderId
                            ).right()
                    } else {
                        parseCommand(
                            conversationId = eventDTO.conversationId,
                            senderId = eventDTO.senderId,
                            rawCommand = buttonId
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
        senderId: QualifiedId,
        rawCommand: String
    ): Either<BotError, Command> {
        val words = rawCommand.split(COMMAND_EXPRESSION)
        if (words[0] != "/remind") return BotError.Skip.left()
        return parseCommandArgs(
            conversationId = conversationId,
            senderId = senderId,
            args = rawCommand.substringAfter("/remind").trimStart()
        )
    }

    private fun parseCommandArgs(
        conversationId: QualifiedId,
        senderId: QualifiedId,
        args: String
    ): Either<BotError, Command> =
        when {
            args.trim() == "help" -> Command.Help(conversationId).right()
            args.trim() == "list" -> Command.ListReminders(conversationId).right()
            args.startsWith(SET_TIMEZONE_ARG) ->
                parseSetTimezoneCommand(conversationId, senderId, args)
            args.startsWith("to") -> parseToCommand(conversationId, senderId, args)
            else ->
                BotError
                    .Unknown(
                        conversationId = conversationId,
                        reason = COMMAND_HINT
                    ).left()
        }

    private fun parseToCommand(
        conversationId: QualifiedId,
        senderId: QualifiedId,
        args: String
    ): Either<BotError, Command> {
        val matches = QUOTED_ARGUMENT
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
            else ->
                Command
                    .NewReminder(
                        conversationId = conversationId,
                        requesterId = senderId,
                        task = matches[0],
                        schedule = matches[1]
                    ).right()
        }
    }

    /**
     * Parses `set-timezone "Europe/Berlin"`. The quotes are optional, so a plain
     * `set-timezone Europe/Berlin` is accepted too.
     */
    private fun parseSetTimezoneCommand(
        conversationId: QualifiedId,
        senderId: QualifiedId,
        args: String
    ): Either<BotError, Command> {
        val remainder = args.substringAfter(SET_TIMEZONE_ARG).trim()
        val input = QUOTED_ARGUMENT
            .find(remainder)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: remainder
        if (input.isBlank()) {
            return BotError
                .ReminderError(
                    conversationId = conversationId,
                    errorType = BotError.ErrorType.INVALID_SET_TIMEZONE_USAGE
                ).left()
        }
        val zoneId = Timezones.parse(input)
            ?: return BotError.InvalidTimezone(conversationId, input).left()
        return Command
            .SetTimezone(
                conversationId = conversationId,
                requesterId = senderId,
                zoneId = zoneId
            ).right()
    }
}

internal const val SET_TIMEZONE_ARG = "set-timezone"
internal val COMMAND_EXPRESSION: Regex = "\\s+".toRegex()
internal val QUOTED_ARGUMENT: Regex = Regex("[\"“”]([^\"“”]*)[\"“”]")
internal val COMMAND_HINT =
    """
    Unknown command, valid options are:
    ```
    /remind help
    /remind list
    /remind to "what" "when"
    /remind set-timezone "Europe/Berlin"
    ```
    """.trimIndent()
