package com.wire.bots.application

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.wire.bots.domain.event.BotError
import com.wire.bots.domain.event.Command
import com.wire.integrations.jvm.model.QualifiedId
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
            return when (words[0]) {
                "/help" -> Command.LegacyHelp(conversationId).right()
                "/remind" ->
                    parseCommandArgs(
                        conversationId = conversationId,
                        args = rawCommand.substringAfter("/remind").trimStart(),
                        referencedMessageId = referencedMessageId,
                        senderId = senderId
                    )
                else -> BotError.Skip.left()
            }
        }

    private fun parseCommandArgs(
        conversationId: QualifiedId,
        args: String,
        referencedMessageId: String? = null,
        senderId: QualifiedId? = null
    ): Either<BotError, Command> =
        when {
            args.trim() == "help" -> Command.Help(conversationId).right()
            args.trim() == "list" -> Command.ListReminders(conversationId).right()
            args.startsWith("to") -> parseToCommand(conversationId, args)
            args.startsWith(
                "delete"
            ) -> parseDeleteCommand(conversationId, args, referencedMessageId, senderId)
            else ->
                BotError
                    .Unknown(
                        conversationId = conversationId,
                        reason = COMMAND_HINT
                    ).left()
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
                ReminderMapper
                    .parseReminder(
                        conversationId = conversationId,
                        task = task,
                        schedule = schedule
                    ).mapLeft { error ->
                        error as? BotError.ReminderError ?: error("❌ Unexpected error type: $error")
                    }
            }
        }
    }

    private fun parseDeleteCommand(
        conversationId: QualifiedId,
        args: String,
        referencedMessageId: String? = null,
        senderId: QualifiedId? = null
    ): Either<BotError, Command> {
        val reminderId = args.substringAfter("delete").trim()
        return if (reminderId.isBlank()) {
            BotError
                .ReminderError(
                    conversationId = conversationId,
                    errorType = BotError.ErrorType.INVALID_REMINDER_ID
                ).left()
        } else {
            Command
                .DeleteReminder(
                    conversationId = conversationId,
                    reminderId = reminderId,
                    referencedMessageId = referencedMessageId,
                    senderId = senderId
                ).right()
        }
    }
}

internal val COMMAND_EXPRESSION: Regex = "\\s+".toRegex()
internal val COMMAND_HINT =
    """
    Unknown command, valid options are:
    ```
    /remind help
    /remind list
    /remind to "what" "when"
    /remind delete <reminderId>
    ```
    """.trimIndent()
