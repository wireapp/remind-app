package com.wire.bots.domain.message

import arrow.core.Either
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.WireMessage

interface OutgoingMessageRepository {
    fun sendMessage(
        conversationId: QualifiedId,
        messageContent: String
    ): Either<Throwable, Unit>

    fun sendCompositeMessage(
        conversationId: QualifiedId,
        messageContent: String,
        buttonList: List<WireMessage.Button>
    ): Either<Throwable, Unit>
}
