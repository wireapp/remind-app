package com.wire.bots.application

import arrow.core.Either
import com.wire.bots.domain.event.BotError
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.event.EventProcessor
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.model.WireMessage
import org.slf4j.LoggerFactory

class ReminderEventHandler(
    private val eventProcessor: EventProcessor
) : WireEventsHandlerSuspending() {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun onTextMessageReceived(wireMessage: WireMessage.Text) {
        logger.info("Received Text Message : $wireMessage")
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

    override suspend fun onButtonClicked(wireMessage: WireMessage.ButtonAction) {
        logger.info("Received ButtonAction Message: $wireMessage")
        processEvent(
            ButtonActionEventDTO(
                type = EventTypeDTO.BUTTON_ACTION,
                userId = wireMessage.sender.id.toString(),
                conversationId = wireMessage.conversationId,
                buttonId = wireMessage.buttonId,
                referencedMessageId = wireMessage.referencedMessageId
            )
        )
    }

    override suspend fun onLocationMessageReceived(wireMessage: WireMessage.Location) {
        logger.info("Received onLocationSuspending Message : $wireMessage")

        val message = WireMessage.Text.create(
            conversationId = wireMessage.conversationId,
            text = "Received Location\n\n" +
                "Latitude: ${wireMessage.latitude}\n\n" +
                "Longitude: ${wireMessage.longitude}\n\n" +
                "Name: ${wireMessage.name}\n\n" +
                "Zoom: ${wireMessage.zoom}"
        )

        manager.sendMessageSuspending(message = message)
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
