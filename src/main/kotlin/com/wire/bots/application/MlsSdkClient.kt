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

import com.wire.bots.domain.event.EventProcessor
import com.wire.sdk.WireAppSdk
import com.wire.sdk.service.WireApplicationManager
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * MlsSdkClient is the entry point for Wire Apps SDK integration.
 * It handles the connection to Wire backend and processes messages using reminder logic.
 */
@ApplicationScoped
@Startup
class MlsSdkClient(
    private val eventProcessor: EventProcessor
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private lateinit var manager: WireApplicationManager

    private val applicationId: String = System.getenv("SDK_APP_ID")
        ?: throw IllegalStateException("SDK_APP_ID environment variable is required")
    private val apiToken: String = System.getenv("SDK_APP_TOKEN")
        ?: throw IllegalStateException("SDK_APP_TOKEN environment variable is required")
    private val apiHost: String = System.getenv("API_HOST_URL")
        ?: throw IllegalStateException("API_HOST_URL environment variable is required")
    private val cryptographyStoragePassword: String = System.getenv("CRYPTO_PASSWORD")
        ?: throw IllegalStateException("CRYPTO_PASSWORD environment variable is required")

    fun getManager(): WireApplicationManager = manager

    @PostConstruct
    fun init() {
        val wireAppSdk =
            WireAppSdk(
                applicationId = UUID.fromString(applicationId),
                apiToken = apiToken,
                apiHost = apiHost,
                cryptographyStoragePassword = cryptographyStoragePassword,
                wireEventsHandler = ReminderEventHandler(eventProcessor)
            )

        logger.info("Starting Wire Apps SDK...")
        wireAppSdk.startListening()
        val applicationManager = wireAppSdk.getApplicationManager()
        manager = applicationManager
        logger.info("Wire Apps SDK started successfully.")
        // Use wireAppSdk.stop() to stop the SDK or just stop it with Ctrl+C/Cmd+C
    }
}
