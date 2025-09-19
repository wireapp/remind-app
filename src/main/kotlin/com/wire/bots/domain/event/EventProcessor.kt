package com.wire.bots.domain.event

import arrow.core.Either
import arrow.core.right
import com.wire.bots.domain.DomainComponent
import com.wire.bots.domain.event.handlers.CommandHandler
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.domain.usecase.SaveReminderSchedule
import org.slf4j.LoggerFactory

/**
 * Process the command and acts accordingly, also handles errors
 *
 * For example, if the command is "help", it will send a help message to the conversation, or
 * if the command is unknown, it will do nothing and send an error message to the conversation.
 */
@DomainComponent
class EventProcessor(
    val commandHandler: CommandHandler,
    val outgoingMessageRepository: OutgoingMessageRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun process(command: Command): Either<Throwable, Unit> =
        commandHandler
            .onEvent(command)
            .mapLeft { unhandled ->
                logger.error("Error processing command: $command", unhandled)
                outgoingMessageRepository.sendMessage(
                    conversationId = command.conversationId,
                    messageContent = getErrorMessage(unhandled)
                )
                unhandled
            }

    private fun getErrorMessage(error: Throwable): String =
        if (error is SaveReminderSchedule.MaxReminderJobsReached) {
            "❌ Maximum numbers of active reminders reached (currently ${error.max})." +
                "Please delete some reminders first."
        } else {
            "❌ An error occurred while processing the command, please try again later."
        }

    fun process(error: BotError): Either<Throwable, Unit> =
        when (error) {
            is BotError.Unknown -> handleErrorMessage(error)
            is BotError.Skip -> logger.warn("Event skipped, not necessary to handle").right()
            is BotError.ReminderError -> handleErrorMessage(error)
        }.mapLeft { unhandled ->
            logger.error(
                "Fatal! Error while processing error, closing the door from outside",
                unhandled
            )
            unhandled
        }

    private fun handleErrorMessage(error: BotError): Either<Throwable, Unit> =
        outgoingMessageRepository.sendMessage(
            conversationId = error.conversationId,
            messageContent = when (error) {
                is BotError.ReminderError -> error.errorType.message
                else -> error.reason
            }
        )
}
