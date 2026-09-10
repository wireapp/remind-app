package com.wire.bots.domain.message

import arrow.core.Either
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import java.util.UUID

interface OutgoingMessageRepository {
    fun sendMessage(
        conversationId: QualifiedId,
        messageContent: String
    ): Either<Throwable, Unit>

    /**
     * Sends [messageContent] to the 1:1 conversation between the app and [userId], creating that
     * conversation when there is none yet, and returns the conversation it was sent to.
     */
    fun sendMessageToOneToOne(
        userId: QualifiedId,
        messageContent: String
    ): Either<Throwable, QualifiedId>

    /**
     * The 1:1 conversation between the app and [userId], or `null` when there is none. This only
     * reads what the app already knows, so unlike [sendMessageToOneToOne] it never creates a
     * conversation, which makes it safe to use for deciding where a command came from.
     */
    fun findOneToOneConversation(userId: QualifiedId): Either<Throwable, QualifiedId?>

    fun sendCompositeMessage(
        conversationId: QualifiedId,
        messageContent: String,
        buttonList: List<WireMessage.Button>
    ): Either<Throwable, Unit>

    fun editCompositeMessage(
        replacingMessageId: UUID,
        conversationId: QualifiedId,
        messageContent: String,
        buttonList: List<WireMessage.Button>
    ): Either<Throwable, Unit>

    fun sendButtonActionConfirmation(
        conversationId: QualifiedId,
        sender: QualifiedId,
        referencedMessageId: String,
        buttonId: String
    ): Either<Throwable, Unit>
}
