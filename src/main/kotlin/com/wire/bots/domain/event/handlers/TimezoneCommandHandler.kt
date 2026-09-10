package com.wire.bots.domain.event.handlers

import arrow.core.Either
import arrow.core.flatMap
import com.wire.bots.domain.DomainComponent
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.domain.user.UserTimezoneRepository
import com.wire.sdk.model.QualifiedId
import org.slf4j.LoggerFactory

/**
 * Handles the personal timezone preference: storing the timezone a user picked, and asking for
 * it in the 1:1 conversation with the app when a reminder cannot be scheduled without it.
 */
@DomainComponent
class TimezoneCommandHandler(
    private val outgoingMessageRepository: OutgoingMessageRepository,
    private val userTimezoneRepository: UserTimezoneRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Stores the timezone the user picked. It is a personal setting, so it is only accepted in
     * the 1:1 conversation with the app; anywhere else the user is pointed there.
     */
    fun setTimezone(command: Command.SetTimezone): Either<Throwable, Unit> =
        outgoingMessageRepository
            .findOneToOneConversation(command.requesterId)
            .flatMap { oneToOneId ->
                if (oneToOneId == command.conversationId) {
                    storeTimezone(command)
                } else {
                    outgoingMessageRepository.sendMessage(
                        conversationId = command.conversationId,
                        messageContent = BuildMsg.setTimezoneInPrivateMessage
                    )
                }
            }

    /**
     * Asks [requesterId] for their timezone in the 1:1 conversation with the app, and tells
     * [conversationId] why nothing was scheduled when the request came from somewhere else.
     */
    fun requestTimezone(
        conversationId: QualifiedId,
        requesterId: QualifiedId
    ): Either<Throwable, Unit> {
        logger.info(
            "Timezone preference is missing, asking the user for it. " +
                "conversationId: $conversationId"
        )
        return outgoingMessageRepository
            .sendMessageToOneToOne(
                userId = requesterId,
                messageContent = BuildMsg.timezoneRequestMessage
            ).flatMap { oneToOneId ->
                if (oneToOneId == conversationId) {
                    Either.Right(Unit)
                } else {
                    outgoingMessageRepository.sendMessage(
                        conversationId = conversationId,
                        messageContent = BuildMsg.timezoneRequestNotice
                    )
                }
            }
    }

    private fun storeTimezone(command: Command.SetTimezone): Either<Throwable, Unit> =
        userTimezoneRepository
            .save(command.requesterId, command.zoneId)
            .flatMap {
                outgoingMessageRepository.sendMessage(
                    conversationId = command.conversationId,
                    messageContent = BuildMsg.timezoneSavedMessage(command.zoneId)
                )
            }
}
