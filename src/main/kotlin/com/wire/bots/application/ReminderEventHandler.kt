package com.wire.bots.application

import arrow.core.Either
import com.wire.bots.domain.event.BotError
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.event.EventProcessor
import com.wire.bots.domain.event.handlers.BuildMsg.welcomeText
import com.wire.bots.infrastructure.utils.UsageMetrics
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.model.Conversation
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.WireMessage
import org.slf4j.LoggerFactory

class ReminderEventHandler(
    private val eventProcessor: EventProcessor,
    private val usageMetrics: UsageMetrics
) : WireEventsHandlerSuspending() {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun onTextMessageReceived(wireMessage: WireMessage.Text) {
        logger.info("Received Text Message : ${wireMessage.id} in conversation ${wireMessage.conversationId}")
        processEvent(
            MessageEventDTO(
                type = EventTypeDTO.NEW_TEXT,
                userId = wireMessage.sender.id.toString(),
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
        logger.info("Received ButtonAction Message: ${buttonAction.id} in conversation ${buttonAction.conversationId}")
        processEvent(
            ButtonActionEventDTO(
                type = EventTypeDTO.BUTTON_ACTION,
                userId = buttonAction.sender.id.toString(),
                conversationId = buttonAction.conversationId,
                buttonId = buttonAction.buttonId,
                referencedMessageId = buttonAction.referencedMessageId
            )
        )
    }

    override suspend fun onLocationMessageReceived(locationMessage: WireMessage.Location) {
        logger.info("Received onLocationSuspending Message : ${locationMessage.id} in conversation ${locationMessage.conversationId}")

        val message = WireMessage.Text.create(
            conversationId = locationMessage.conversationId,
            text = "Received Location\n\n" +
                "Latitude: ${locationMessage.latitude}\n\n" +
                "Longitude: ${locationMessage.longitude}\n\n" +
                "Name: ${locationMessage.name}\n\n" +
                "Zoom: ${locationMessage.zoom}"
        )

        manager.sendMessageSuspending(message = message)
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
     * Process an event using the reminder bot logic
     */
    private fun processEvent(eventDTO: EventDTO) {
        try {
            logger.debug("Processing event: $eventDTO")
            val result: Either<BotError, Command> = EventMapper.fromEvent(eventDTO)
            result.fold(
                ifLeft = { error ->
                    logger.warn("Processing event with error: $error")
                    eventProcessor.process(error)
                },
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
