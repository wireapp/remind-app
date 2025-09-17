/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.bots.application

import arrow.core.Either
import com.wire.bots.domain.event.BotError
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.event.EventProcessor
import com.wire.integrations.jvm.WireAppSdk
import com.wire.integrations.jvm.WireEventsHandlerSuspending
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.service.WireApplicationManager
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("RemindAppMlsSdk")

/**
 * MlsSdkClient is the entry point for Wire Apps SDK integration.
 * It handles the connection to Wire backend and processes messages using reminder logic.
 */
@ApplicationScoped
@Startup
class MlsSdkClient(
    private val eventProcessor: EventProcessor,
    @ConfigProperty(name = "wire-sdk-api.bot-key")
    private val apiToken: String,
    @ConfigProperty(name = "wire-sdk-api.url")
    private val apiHost: String
) {
    private lateinit var manager: WireApplicationManager

    fun getManager(): WireApplicationManager = manager

    @PostConstruct
    fun init() {
        val wireAppSdk =
            WireAppSdk(
                applicationId = UUID.randomUUID(),
                apiToken = apiToken,
                apiHost = apiHost,
                cryptographyStoragePassword = "myDummyPasswordOfRandom32BytesCH",
                wireEventsHandler = object : WireEventsHandlerSuspending() {
                    override suspend fun onMessage(wireMessage: WireMessage.Text) {
                        logger.info("Received Text Message : $wireMessage")
                        processEvent(
                            EventDTO(
                                type = EventTypeDTO.NEW_TEXT,
                                userId = wireMessage.sender.id.toString().orEmpty(),
                                conversationId = wireMessage.conversationId,
                                text = wireMessage.text.let { TextContent(it) }
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

                    override suspend fun onAsset(wireMessage: WireMessage.Asset) {
                        logger.info("Received Asset Message: $wireMessage")
                        // Assets are not handled by reminder bot
                    }

                    override suspend fun onComposite(wireMessage: WireMessage.Composite) {
                        logger.info("Received Composite Message: $wireMessage")
                        // Composite messages are not handled by reminder bot
                    }

                    override suspend fun onButtonAction(wireMessage: WireMessage.ButtonAction) {
                        logger.info("Received ButtonAction Message: $wireMessage")
                        // Button actions are not handled by reminder bot
                    }

                    override suspend fun onButtonActionConfirmation(
                        wireMessage: WireMessage.ButtonActionConfirmation
                    ) {
                        logger.info("Received ButtonActionConfirmation Message: $wireMessage")
                        // Button action confirmations are not handled by reminder bot
                    }

                    override suspend fun onKnock(wireMessage: WireMessage.Knock) {
                        logger.info("Received onKnockSuspending Message : $wireMessage")
                        // Button knocks/pings are not handled by reminder bot
                    }

                    override suspend fun onLocation(wireMessage: WireMessage.Location) {
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
                }
            )

        logger.info("Starting Wire Apps SDK...")
        wireAppSdk.startListening()
        val applicationManager = wireAppSdk.getApplicationManager()
        manager = applicationManager
        logger.info("Wire Apps SDK started successfully.")
        // Use wireAppSdk.stop() to stop the SDK or just stop it with Ctrl+C/Cmd+C
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
