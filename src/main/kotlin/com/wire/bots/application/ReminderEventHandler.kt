package com.wire.bots.application

import arrow.core.Either
import com.wire.bots.domain.event.BotError
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.event.EventProcessor
import com.wire.bots.domain.event.handlers.BuildMsg.welcomeText
import com.wire.bots.domain.usecase.DeleteRemindersInConversation
import com.wire.bots.infrastructure.utils.UsageMetrics
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.model.Conversation
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import org.slf4j.LoggerFactory

class ReminderEventHandler(
    private val eventProcessor: EventProcessor,
    private val usageMetrics: UsageMetrics,
    private val deleteRemindersInConversation: DeleteRemindersInConversation
) : WireEventsHandlerSuspending() {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun onTextMessageReceived(wireMessage: WireMessage.Text) {
        logger.debug(
            "Received Text Message : {} in conversation {}",
            wireMessage.id,
            wireMessage.conversationId
        )

        processEvent(
            MessageEventDTO(
                type = EventTypeDTO.NEW_TEXT,
                senderId = wireMessage.sender,
                conversationId = wireMessage.conversationId,
                text = TextContent(wireMessage.text)
            )
        )

        // Sending a Read Receipt for the received message
        val receipt = WireMessage.Receipt.create(
            conversationId = wireMessage.conversationId,
            type = WireMessage.Receipt.Type.READ,
            messages = listOf(wireMessage.id.toString())
        )
        manager.sendMessageSuspending(message = receipt)
    }

    override suspend fun onButtonClicked(buttonAction: WireMessage.ButtonAction) {
        logger.info(
            "Received ButtonAction Message: ${buttonAction.id} in conversation ${buttonAction.conversationId}"
        )
        processEvent(
            ButtonActionEventDTO(
                type = EventTypeDTO.BUTTON_ACTION,
                senderId = buttonAction.sender,
                conversationId = buttonAction.conversationId,
                buttonId = buttonAction.buttonId,
                referencedMessageId = buttonAction.referencedMessageId
            )
        )
    }

    override suspend fun onAppAddedToConversation(
        conversation: Conversation,
        members: List<ConversationMember>
    ) {
        usageMetrics.onAppAddedToConversation()

        val welcomeMessage = WireMessage.Text.create(
            conversationId = conversation.id,
            text = welcomeText
        )

        manager.sendMessageSuspending(welcomeMessage)
    }

    /**
     * The conversation is gone for everyone, so its reminders can never be delivered again.
     */
    override suspend fun onConversationDeleted(conversationId: QualifiedId) {
        logger.info(
            "Conversation {} was deleted, deleting its reminders",
            conversationId
        )
        purgeReminders(conversationId)
    }

    /**
     * Fired for every member leaving, so the reminders are only dropped when the app itself is
     * the one that left: the conversation keeps its reminders when one of its users walks out.
     */
    override suspend fun onUserLeftConversation(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ) {
        // The app is a conversation member like any other, so its own id is what tells a
        // member-leave event about the app apart from one about a user.
        if (manager.getApplicationQualifiedId() !in members) {
            logger.debug(
                "A user left conversation {}, the app stays, keeping its reminders",
                conversationId
            )
            return
        }

        usageMetrics.onAppRemovedFromConversation()
        logger.info(
            "The app was removed from conversation {}, deleting its reminders",
            conversationId
        )
        purgeReminders(conversationId)
    }

    /**
     * Cleanup is silent: there is no conversation left to report a failure into, so a failure is
     * only logged. Reminders left behind would keep firing and fail with "Conversation access
     * denied" or "Conversation not found" instead.
     */
    private fun purgeReminders(conversationId: QualifiedId) {
        deleteRemindersInConversation(conversationId).fold(
            ifLeft = { error ->
                logger.error(
                    "Failed to delete the reminders of conversation $conversationId",
                    error
                )
            },
            ifRight = { deleted ->
                logger.info(
                    "Deleted {} reminder(s) of conversation {}",
                    deleted,
                    conversationId
                )
            }
        )
    }

    /**
     * Process an event using the reminder bot logic
     */
    private fun processEvent(eventDTO: EventDTO) {
        try {
            logger.debug("Processing event: $eventDTO")
            val result: Either<BotError, Command> = EventMapper.fromEvent(eventDTO)
            result.fold(
                ifLeft = { error -> eventProcessor.process(error) },
                ifRight = { command ->
                    logger.info("Processing event parsed to: $command")
                    eventProcessor.process(command)
                }
            )
        } catch (e: IllegalArgumentException) {
            logger.error("Error processing event", e)
        }
    }
}
