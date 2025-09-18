package com.wire.bots.infrastructure.repository

import arrow.core.Either
import arrow.core.raise.either
import com.wire.bots.application.MlsSdkClient
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.WireMessage
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MlsSdkOutgoingMessageRepository(
    val conversationRemoteApi: MlsSdkClient
) : OutgoingMessageRepository {
    override fun sendMessage(
        conversationId: QualifiedId,
        messageContent: String
    ): Either<Throwable, Unit> =
        either {
            val manager = conversationRemoteApi.getManager()
            val message = WireMessage.Text.create(
                conversationId = conversationId,
                text = messageContent
            )
            manager.sendMessage(
                message = message
            )
        }

    override fun sendCompositeMessage(
        conversationId: QualifiedId,
        messageContent: String,
        buttonList: List<WireMessage.Button>
    ): Either<Throwable, Unit> =
        either {
            val manager = conversationRemoteApi.getManager()
            val message = WireMessage.Composite.create(
                conversationId = conversationId,
                text = messageContent,
                buttonList = buttonList
            )
            manager.sendMessage(
                message = message
            )
        }
}
